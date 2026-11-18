#!/bin/bash
# The reference frames of P4-10 (spec docs/ux/specs/P4-10-reverse-and-freeze.md → Measured), made on a desktop from reverse-check.mp4 (make-reverse-check.sh) with ffmpeg's own
# `reverse` filter as the stand-in for what the phone's reversed copy must show, at 360 x 640 to keep the files small. They are the picture to compare an exported frame with:
#   reverse-t25 / t50 / t75   output frame 75, 150 and 225 of the reversed 10 s clip (2.5 s, 5 s, 7.5 s in): the burned-in frame number reads f224, f149 and f074 (source 07.46, 04.96, 02.46), +/- one frame.
#   freeze-mid                the still of a Freeze frame at the playhead 5.00 s of the clip: the source frame at 05.00.
#   freeze-near-start         Freeze frame within 0.1 s of the clip's start: it holds the first frame, 00.00.
# Desktop only (ffmpeg). usage: make-references.sh <reverse-check.mp4> <out dir>
set -e
IN=${1:?reverse-check.mp4}
OUT=${2:?out dir}
mkdir -p "$OUT"
for t in 25 50 75; do
  ffmpeg -v error -y -i "$IN" -vf "reverse,select=eq(n\,$((t * 30 / 10))),scale=360:640" -frames:v 1 "$OUT/reverse-t$t.png"
done
ffmpeg -v error -y -i "$IN" -vf "select=eq(n\,150),scale=360:640" -frames:v 1 "$OUT/freeze-mid.png"
ffmpeg -v error -y -i "$IN" -vf "select=eq(n\,0),scale=360:640" -frames:v 1 "$OUT/freeze-near-start.png"
