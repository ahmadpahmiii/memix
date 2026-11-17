#!/bin/bash
# The effects-check scene (P4-06a): 9:16, 4 s, 30 fps; a 12 x 12 lettered grid over colour bars, a white square that moves. Desktop only (ffmpeg).
# usage: make-effects-check.sh <scale: 1 for the clip, 0.5 for the references> <out> [png-seq|mp4]   e.g. make-effects-check.sh 1 effects-check.mp4
set -e
SC=${1:-1}; OUT=$2; MODE=${3:-mp4}
W=$(python3 -c "print(int(1080*$SC))"); H=$(python3 -c "print(int(1920*$SC))")
CELLW=$(python3 -c "print(int(90*$SC))"); CELLH=$(python3 -c "print(int(120*$SC))")
FS=$(python3 -c "print(int(72*$SC))"); GRIDH=$((CELLH*12)); BARH=$((H-GRIDH))
FONT=/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf
TEXT=$(python3 - <<PY
rows=[]
for r in range(12):
    rows.append(' '.join(chr(65+((c+r)%26)) for c in range(12)))
print('\n'.join(rows))
PY
)
printf "%s" "$TEXT" > $OUT.txt
SQ=$(python3 -c "print(int(120*$SC))")
BARS=""
COLORS=(white yellow cyan green magenta red blue)
BW=$((W/7))
for i in 0 1 2 3 4 5 6; do BARS="$BARS,drawbox=x=$((i*BW)):y=$GRIDH:w=$BW:h=$BARH:color=${COLORS[$i]}:t=fill"; done
GRAPH="color=c=0x1c1c24:s=${W}x${H}:r=30:d=4,drawgrid=w=$CELLW:h=$CELLH:t=2:c=white@0.55,drawtext=textfile=$OUT.txt:fontfile=$FONT:fontsize=$FS:fontcolor=white@0.9:x=$((CELLW/6)):y=$((CELLH/10)):line_spacing=$((CELLH-FS*117/100))$BARS[bg];color=c=white:s=${SQ}x${SQ}:r=30:d=4[sq];[bg][sq]overlay=x='$(python3 -c "print(int(60*$SC))")+$(python3 -c "print(int(780*$SC))")*t/4':y='$(python3 -c "print(int(500*$SC))")+$(python3 -c "print(int(220*$SC))")*sin(2*PI*t/4)'"
if [ "$MODE" = "png-seq" ]; then
  mkdir -p $OUT; ffmpeg -v error -y -filter_complex "$GRAPH" -frames:v 120 $OUT/f%03d.png
else
  ffmpeg -v error -y -filter_complex "$GRAPH" -c:v libx264 -crf 12 -preset slow -pix_fmt yuv420p -r 30 -an $OUT
fi
