#!/usr/bin/env bash
#
# Guard — labelled clap telemetry collection.
#
# Runs one capture per sound, with a marker line written into logcat itself rather than kept
# alongside it: the marker and the measurements then share a clock and a file, so a label can
# never drift away from the numbers it belongs to.
#
#   scripts/collect-clap-data.sh            # walks the standard sequence
#   GUARD_DEVICE=<serial> scripts/...       # when the Pixel is not the only Pixel attached
#
set -euo pipefail

readonly TAG="ClapTelemetry"
readonly REPETITIONS=10
readonly SETTLE_SECONDS=2

# Closes the last label. Without it the final section runs until the capture stops, and picks up
# every stray noise between the last sound and the summary.
readonly END_MARKER="END"

# How long to wait for the detector's config line after the user re-arms. It is printed the moment
# the recorder opens, so this only has to cover a human tapping a switch.
readonly ARM_TIMEOUT_SECONDS=30

# The order matters: the two claps come first while the room is unchanged, so everything after is
# compared against a clap recorded under the same conditions rather than a different session.
readonly LABELS=(
  "CLAP 1m"
  "CLAP 3m"
  "TABLE TAP"
  "DOOR"
  "FALSE TRIGGER SOUND"
  "SPEECH"
)

readonly ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly OUT_DIR="$ROOT/build/clap-telemetry"

adb_path() {
  if command -v adb >/dev/null 2>&1; then command -v adb; return; fi
  local sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
  [ -x "$sdk/platform-tools/adb" ] && echo "$sdk/platform-tools/adb" && return
  echo "adb not found. Put it on PATH or set ANDROID_HOME." >&2
  exit 1
}

# Two devices are attached and only the Pixel has the microphone this is about, so the target is
# chosen rather than left to adb's "more than one device" error.
pick_device() {
  if [ -n "${GUARD_DEVICE:-}" ]; then echo "$GUARD_DEVICE"; return; fi
  local matches count
  matches="$("$ADB" devices -l | awk '/model:Pixel/ {print $1}')"
  count="$(printf '%s' "$matches" | grep -c . || true)"
  if [ "$count" -ne 1 ]; then
    echo "Expected exactly one attached Pixel, found $count. Set GUARD_DEVICE=<serial>." >&2
    "$ADB" devices -l >&2
    exit 1
  fi
  echo "$matches"
}

ADB="$(adb_path)"
DEVICE="$(pick_device)"
readonly ADB DEVICE
adb_device() { "$ADB" -s "$DEVICE" "$@"; }

mkdir -p "$OUT_DIR"
readonly CAPTURE="$OUT_DIR/clap-$(date +%Y%m%d-%H%M%S).log"

marker() { adb_device shell log -p d -t "$TAG" "\"=== $1 ===\"" >/dev/null; }

echo "Device:  $DEVICE ($(adb_device shell getprop ro.product.model | tr -d '\r'))"
echo "Capture: $CAPTURE"
echo

# A capture left behind by an earlier run that was killed rather than exited keeps writing to its
# old file forever. It is ended here rather than tolerated: two readers on one buffer are harmless,
# but a file that keeps growing after its run is not a capture anyone can trust.
pkill -f "adb -s $DEVICE logcat" 2>/dev/null || true

# The config line is printed when the recorder opens. Clearing logcat after arming wipes it, which
# is why the capture starts first and the detector is re-armed into it.
adb_device logcat -c
adb_device logcat -v time -s "$TAG:D" > "$CAPTURE" &
readonly LOGCAT_PID=$!
# On every way out, including a Ctrl-C or a kill: the file stays readable and the reader stops.
trap 'kill "$LOGCAT_PID" 2>/dev/null || true' EXIT INT TERM

echo "Capture is running. Now toggle clap detection OFF and back ON in the app —"
echo "that reopens the microphone, which is what prints the configuration line."
for _ in $(seq "$ARM_TIMEOUT_SECONDS"); do
  grep -q "config " "$CAPTURE" && break
  sleep 1
done

if ! grep -q "config " "$CAPTURE"; then
  echo
  echo "No configuration line arrived in ${ARM_TIMEOUT_SECONDS}s. The detector did not reopen the" >&2
  echo "microphone, so this capture would have the same blind spot as the last one. Stopping." >&2
  exit 1
fi
echo
config="$(grep "config " "$CAPTURE" | head -1)"
echo "$config"

# A build that alarms on a clap would fire the siren into the microphone being measured. That
# capture would describe the siren, so it is not taken.
if ! grep -q "dryRun=true" <<< "$config"; then
  echo
  echo "This build is not in dry-run mode: the alarm would sound and contaminate every label" >&2
  echo "after the first clap. Set CLAP_DRY_RUN_ENABLED = true, reinstall, and rerun." >&2
  exit 1
fi

echo
echo "Each sound is made $REPETITIONS times, about a second apart — sounds closer than half a"
echo "second merge into one measurement. The alarm will not sound: this build logs the verdict"
echo "and stops there."
read -r -p "Press enter to start. "

for label in "${LABELS[@]}"; do
  echo
  echo "=== $label ==="
  marker "$label"
  before="$(grep -c "verdict=" "$CAPTURE" || true)"
  read -r -p "Make this sound $REPETITIONS times, ~1s apart, then press enter. "
  # The detector keeps measuring for about half a second after a sound; without this the tail of
  # the last repetition would be printed under the next label.
  sleep "$SETTLE_SECONDS"
  after="$(grep -c "verdict=" "$CAPTURE" || true)"
  echo "  -> $((after - before)) of $REPETITIONS measured"
done

marker "$END_MARKER"
# Long enough for the marker to travel through logcat into the file before it is closed.
sleep 1
kill "$LOGCAT_PID" 2>/dev/null || true
wait "$LOGCAT_PID" 2>/dev/null || true
trap - EXIT

echo
echo "Captured $(grep -c "verdict=" "$CAPTURE" || true) measurements and"
echo "$(grep -c "skip=" "$CAPTURE" || true) skip batches to $CAPTURE"
echo
python3 "$ROOT/scripts/summarize-clap-data.py" "$CAPTURE"
