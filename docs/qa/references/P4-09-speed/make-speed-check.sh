#!/bin/bash
# The clip of the speed scenes (P4-09a): speed-check.mp4, 9:16, 1080 x 1920, 10 s, 30 fps, AAC 48 kHz mono.
#   A 12 x 12 lettered grid, the source time burned in to the centisecond ("03.27"), and a 1 kHz tone whose level steps down 3 dB each second and
#   starts over every 5 s, so the audio's mapping through a speed can be read from its level and its length.
# Desktop only (ffmpeg). usage: make-speed-check.sh <out dir>; then push speed-check.mp4 into the app's files/debug-media/ (steps: docs/qa/phase-4/engineer-hand-checks.md -> P4-09a).
set -e
OUT=${1:?out dir}
mkdir -p "$OUT"
W=1080; H=1920; CELLW=90; CELLH=120; FS=72
FONT=/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf
python3 - > "$OUT/grid.txt" <<'PY'
print('\n'.join(' '.join(chr(65 + ((c + r) % 26)) for c in range(12)) for r in range(12)))
PY
ffmpeg -v error -y \
  -f lavfi -i "color=c=0x1c3a5e:s=${W}x${H}:r=30:d=10,drawgrid=w=$CELLW:h=$CELLH:t=2:c=white@0.55,drawtext=textfile=$OUT/grid.txt:fontfile=$FONT:fontsize=$FS:fontcolor=white@0.9:x=$((CELLW/6)):y=$((CELLH/10)):line_spacing=$((CELLH-FS*117/100)),drawbox=x=0:y=700:w=${W}:h=420:color=black@0.8:t=fill,drawtext=fontfile=$FONT:fontsize=300:fontcolor=yellow:x=(w-text_w)/2:y=740:text='%{eif\:floor(t)\:d\:2}.%{eif\:mod(floor(t*100)\,100)\:d\:2}'" \
  -f lavfi -i "sine=frequency=1000:sample_rate=48000:duration=10,volume=volume='pow(10\,-mod(floor(t)\,5)*3/20)':eval=frame" \
  -c:v libx264 -crf 12 -preset slow -pix_fmt yuv420p -r 30 -ac 1 -c:a aac -b:a 128k -shortest "$OUT/speed-check.mp4"
rm -f "$OUT/grid.txt"
