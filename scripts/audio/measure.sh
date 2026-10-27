#!/usr/bin/env bash
# Measure duration, integrated loudness and true peak of one file (pads short clips to 3 s for the meter).
# Usage: measure.sh <file>
set -euo pipefail
f="$1"
d="$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$f")"
codec="$(ffprobe -v error -select_streams a:0 -show_entries stream=codec_name,bit_rate,sample_rate,channels -of csv=p=0 "$f")"
r="$(ffmpeg -nostdin -hide_banner -i "$f" -af "apad=whole_dur=3,ebur128=peak=true" -f null - 2>&1 | sed -n '/Summary:/,$p')"
I="$(awk '/I:/{print $2; exit}' <<<"$r")"; TP="$(awk '/Peak:/{print $2; exit}' <<<"$r")"
echo "$(basename "$f") duration_s=$d I_LUFS=$I truepeak_dBFS=$TP stream=$codec"
