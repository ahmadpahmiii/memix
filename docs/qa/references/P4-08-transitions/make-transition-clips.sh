#!/bin/bash
# The two clips of the transition scenes (P4-08): 9:16, 1080 x 1920, 6 s, 30 fps, AAC 48 kHz mono.
#   transition-a.mp4: a 12 x 12 lettered grid, a white square that moves, a 440 Hz tone.
#   transition-b.mp4: seven colour bars, a black square that moves, an 880 Hz tone.
# Desktop only (ffmpeg). usage: make-transition-clips.sh <out dir>; then push both into the app's files/debug-media/ (steps: docs/qa/phase-4/engineer-hand-checks.md -> P4-08).
set -e
OUT=${1:?out dir}
mkdir -p "$OUT"
W=1080; H=1920; CELLW=90; CELLH=120; FS=72
FONT=/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf
python3 - > "$OUT/grid.txt" <<'PY'
print('\n'.join(' '.join(chr(65 + ((c + r) % 26)) for c in range(12)) for r in range(12)))
PY
ffmpeg -v error -y \
  -f lavfi -i "color=c=0x1c3a5e:s=${W}x${H}:r=30:d=6,drawgrid=w=$CELLW:h=$CELLH:t=2:c=white@0.55,drawtext=textfile=$OUT/grid.txt:fontfile=$FONT:fontsize=$FS:fontcolor=white@0.9:x=$((CELLW/6)):y=$((CELLH/10)):line_spacing=$((CELLH-FS*117/100)),drawbox=x='60+780*t/6':y='500+220*sin(2*PI*t/6)':w=120:h=120:color=white:t=fill" \
  -f lavfi -i "sine=frequency=440:sample_rate=48000:duration=6" \
  -c:v libx264 -crf 12 -preset slow -pix_fmt yuv420p -r 30 -ac 1 -c:a aac -b:a 128k -shortest "$OUT/transition-a.mp4"
COLORS=(white yellow cyan green magenta red blue)
BARS=""
for i in 0 1 2 3 4 5 6; do BARS="$BARS,drawbox=x=$((i*W/7)):y=0:w=$((W/7+1)):h=$H:color=${COLORS[$i]}:t=fill"; done
ffmpeg -v error -y \
  -f lavfi -i "color=c=black:s=${W}x${H}:r=30:d=6${BARS},drawbox=x='780-720*t/6':y='1200+220*sin(2*PI*t/6)':w=120:h=120:color=black:t=fill" \
  -f lavfi -i "sine=frequency=880:sample_rate=48000:duration=6" \
  -c:v libx264 -crf 12 -preset slow -pix_fmt yuv420p -r 30 -ac 1 -c:a aac -b:a 128k -shortest "$OUT/transition-b.mp4"
rm -f "$OUT/grid.txt"
