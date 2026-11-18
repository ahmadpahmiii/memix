#!/bin/bash
# The clips of the reverse and freeze scenes (P4-10): reverse-check.mp4 (10 s) and reverse-long.mp4 (61 s), 9:16, 1080 x 1920, 30 fps, AAC 48 kHz mono.
#   The source time is burned in as seconds with centiseconds ("03.27") and the frame number ("f098") over a lettered grid, and the sound is a sine sweep that FALLS in
#   pitch (2 kHz down to 200 Hz over each 10 s), so a reversed clip must rise in pitch and its burned-in time must fall by exactly one frame per output frame.
# Desktop only (ffmpeg). usage: make-reverse-check.sh <out dir>; then push both files into the app's files/debug-media/ (steps: docs/qa/phase-4/engineer-hand-checks.md -> P4-10).
set -e
OUT=${1:?out dir}
mkdir -p "$OUT"
W=1080; H=1920; CELLW=90; CELLH=120; FS=72
FONT=/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf
python3 - > "$OUT/grid.txt" <<'PY'
print('\n'.join(' '.join(chr(65 + ((c + r) % 26)) for c in range(12)) for r in range(12)))
PY
make() { # name seconds
  ffmpeg -v error -y \
    -f lavfi -i "color=c=0x3a1c5e:s=${W}x${H}:r=30:d=$2,drawgrid=w=$CELLW:h=$CELLH:t=2:c=white@0.55,drawtext=textfile=$OUT/grid.txt:fontfile=$FONT:fontsize=$FS:fontcolor=white@0.9:x=$((CELLW/6)):y=$((CELLH/10)):line_spacing=$((CELLH-FS*117/100)),drawbox=x=0:y=700:w=${W}:h=520:color=black@0.8:t=fill,drawtext=fontfile=$FONT:fontsize=300:fontcolor=yellow:x=(w-text_w)/2:y=740:text='%{eif\:floor(t)\:d\:2}.%{eif\:mod(floor(t*100)\,100)\:d\:2}',drawtext=fontfile=$FONT:fontsize=140:fontcolor=white:x=(w-text_w)/2:y=1060:text='f%{eif\:trunc(t*30+0.5)\:d\:3}'" \
    -f lavfi -i "aevalsrc='sin(2*PI*(2000-90*mod(t\,10))*mod(t\,10))*0.5':s=48000:d=$2" \
    -c:v libx264 -crf 12 -preset slow -pix_fmt yuv420p -r 30 -ac 1 -c:a aac -b:a 128k -shortest "$OUT/$1.mp4"
}
make reverse-check 10
make reverse-long 61
rm -f "$OUT/grid.txt"
