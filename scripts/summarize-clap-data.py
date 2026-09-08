#!/usr/bin/env python3
"""Summarise a labelled clap telemetry capture.

Reads the file written by collect-clap-data.sh and prints, per label, how each candidate was
judged and the spread of every measured feature. Ranges rather than averages, because the question
these captures exist to answer is whether two sounds overlap — and a mean hides exactly that.

Skip batches are counted alongside the verdicts. A sound that produced no measurement at all is
the failure this report exists to make visible, so the windows that were discarded before any
measurement opened are shown next to the ones that were judged.
"""

from __future__ import annotations

import re
import statistics
import sys
from collections import Counter, defaultdict

MARKER = re.compile(r"=== (?P<label>[^=]+?) ===")
# The detector pads every field to a fixed width so the raw log stays readable in a column, which
# means the space after an "=" belongs to the number, not to the separator.
MEASUREMENT = re.compile(
    r"verdict=\s*(?P<verdict>\S+)\s+"
    r"peakRms=\s*(?P<peakRms>\S+)\s+"
    r"baseline=\s*(?P<baseline>\S+)\s+"
    r"riseRatio=\s*(?P<riseRatio>\S+)\s+"
    r"decayMs=\s*(?P<decayMs>\S+)\s+"
    r"elevatedMs=\s*(?P<elevatedMs>\S+)\s+"
    r"zcr=\s*(?P<zcr>\S+)\s+"
    r"zcrPeak=\s*(?P<zcrPeak>\S+)"
)
SKIP = re.compile(r"skip=\s*(?P<reason>\S+)\s+windows=\s*(?P<windows>\d+)")
CONFIG = re.compile(r"(config |reads )\S")
FEATURES = ("riseRatio", "decayMs", "elevatedMs", "zcr", "zcrPeak")
UNLABELLED = "(before first label)"

# Written by the harness after the last label. Everything past it is the room between the final
# sound and the capture closing, and belongs to no label.
END_LABEL = "END"


def parse(path: str) -> tuple[list[str], dict[str, list[dict]], dict[str, Counter], list[str]]:
    order: list[str] = []
    rows: dict[str, list[dict]] = defaultdict(list)
    skips: dict[str, Counter] = defaultdict(Counter)
    header: list[str] = []
    label = UNLABELLED
    ended = False
    ignored = 0

    def seen(name: str) -> None:
        if name not in order:
            order.append(name)

    with open(path, encoding="utf-8", errors="replace") as handle:
        for line in handle:
            if ended:
                ignored += 1
                continue
            if CONFIG.search(line):
                header.append(line.split(": ", 1)[-1].strip())
                continue
            marker = MARKER.search(line)
            if marker:
                label = marker.group("label").strip()
                if label == END_LABEL:
                    ended = True
                    continue
                seen(label)
                continue
            skip = SKIP.search(line)
            if skip:
                seen(label)
                skips[label][skip.group("reason")] += int(skip.group("windows"))
                continue
            match = MEASUREMENT.search(line)
            if not match:
                continue
            row = {"verdict": match.group("verdict")}
            row.update({name: float(match.group(name)) for name in FEATURES})
            seen(label)
            rows[label].append(row)

    if not ended:
        header.append("(no END marker: the last label runs to the end of the capture)")
    elif ignored:
        header.append(f"({ignored} line(s) after END ignored)")
    return order, rows, skips, header


def spread(values: list[float]) -> str:
    """A feature whose decay never completed reports -1; that is a fact about the sound, so it is
    counted separately rather than averaged in as if it were a millisecond reading."""
    measured = [value for value in values if value >= 0]
    missing = len(values) - len(measured)
    if not measured:
        return f"{'—':>34}  (all {missing} never came down)"
    text = (
        f"{min(measured):10.2f} {statistics.median(measured):10.2f} {max(measured):10.2f}"
        f"  n={len(measured)}"
    )
    return text + (f"  (+{missing} never came down)" if missing else "")


def report(
    order: list[str],
    rows: dict[str, list[dict]],
    skips: dict[str, Counter],
    header: list[str],
) -> None:
    for line in header:
        print(line)

    for label in order:
        measurements = rows.get(label, [])
        discarded = skips.get(label, Counter())
        print(f"\n{label}  —  {len(measurements)} measurement(s)")
        if discarded:
            print(
                "  discarded: "
                + ", ".join(f"{name}={count} windows" for name, count in discarded.most_common())
            )
        if not measurements:
            print("  nothing was measured under this label")
            continue

        verdicts = Counter(row["verdict"] for row in measurements)
        print("  verdicts: " + ", ".join(f"{name}={count}" for name, count in verdicts.most_common()))
        print(f"  {'feature':<12}{'min':>10} {'median':>10} {'max':>10}")
        for feature in FEATURES:
            print(f"  {feature:<12}" + spread([row[feature] for row in measurements]))


def main() -> int:
    if len(sys.argv) != 2:
        print(f"usage: {sys.argv[0]} <capture.log>", file=sys.stderr)
        return 2
    order, rows, skips, header = parse(sys.argv[1])
    if not rows and not skips:
        print("No telemetry lines found. Was the debug build armed?", file=sys.stderr)
        return 1
    report(order, rows, skips, header)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
