#!/usr/bin/env bash
#
# Guard — labelled clap telemetry collection.
#
# Runs one capture per sound, with a marker line written into logcat itself rather than kept
# alongside it: the marker and the measurements then share a clock and a file, so a label can
# never drift away from the numbers it belongs to.
#
# Requires the debug build running with clap detection armed on the phone.
#
#   scripts/collect-clap-data.sh            # walks the standard sequence
#   GUARD_DEVICE=<serial> scripts/...       # when the Pixel is not the only Pixel attached
#
set -euo pipefail

readonly TAG="ClapTelemetry"
readonly REPETITIONS=5
readonly SETTLE_SECONDS=2

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
  local matches
  matches="$("$ADB" devices -l | awk '/model:Pixel/ {print $1}')"
  local count
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

echo "Device:  $DEVICE ($(adb_device shell getprop ro.product.model | tr -d '\r'))"
echo "Source:  $(adb_device shell settings get global guard_clap_audio_source | tr -d '\r')"
echo "Capture: $CAPTURE"
echo
echo "Arm clap detection in the app before continuing. Each sound is made $REPETITIONS times,"
echo "about a second apart — sounds closer than half a second merge into one measurement."
read -r -p "Press enter when clap detection is armed. "

adb_device logcat -c
adb_device logcat -v time -s "$TAG:D" > "$CAPTURE" &
readonly LOGCAT_PID=$!
# The capture outlives any single failure below, so a Ctrl-C still leaves a readable file.
trap 'kill "$LOGCAT_PID" 2>/dev/null || true' EXIT

for label in "${LABELS[@]}"; do
  echo
  echo "=== $label ==="
  adb_device shell log -p d -t "$TAG" "\"=== $label ===\"" >/dev/null
  read -r -p "Make this sound $REPETITIONS times, ~1s apart, then press enter. "
  # The detector keeps measuring for about half a second after a sound; without this the tail of
  # the last repetition would be printed under the next label.
  sleep "$SETTLE_SECONDS"
done

sleep "$SETTLE_SECONDS"
kill "$LOGCAT_PID" 2>/dev/null || true
wait "$LOGCAT_PID" 2>/dev/null || true
trap - EXIT

echo
echo "Captured $(grep -c "verdict=" "$CAPTURE" || true) measurements to $CAPTURE"
echo
python3 "$ROOT/scripts/summarize-clap-data.py" "$CAPTURE"
