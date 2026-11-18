#!/bin/bash
# The constant-speed reference strips (P4-09a): for each speed, three frames of the expected export of speed-check.mp4 at 25%, 50% and 75% of its output length.
# The expected frame at output time t is the source frame at t x speed (nearest earlier frame, no blending), so the burned-in number must read t x speed to
# within one source frame (33 ms). Made with desktop ffmpeg from the clip make-speed-check.sh produces; a device export is compared against these.
# usage: make-references.sh <speed-check.mp4> <out dir>
set -e
SRC=${1:?speed-check.mp4}
OUT=${2:?out dir}
mkdir -p "$OUT"
# name speed source-seconds-used (const-2 also stands for 10x: the same source frames at other output times)
while read -r NAME SPEED SPAN; do
  FRAMES=()
  for PCT in 25 50 75; do
    T=$(python3 -c "print(f'{$SPAN * $PCT / 100:.3f}')")
    ffmpeg -nostdin -v error -y -ss "$T" -i "$SRC" -frames:v 1 -vf "scale=270:480" "$OUT/.f$PCT.png"
    FRAMES+=("$OUT/.f$PCT.png")
  done
  ffmpeg -nostdin -v error -y -i "${FRAMES[0]}" -i "${FRAMES[1]}" -i "${FRAMES[2]}" -filter_complex "hstack=inputs=3" -frames:v 1 "$OUT/$NAME.png"
  rm -f "$OUT"/.f*.png
done <<'LIST'
const-0_1 0.1 1
const-0_5 0.5 4
const-2 2 10
LIST
