#!/usr/bin/env bash
# Normalize one sound to the content-intake target: -16 LUFS integrated, true peak <= -1 dBTP,
# AAC 128 kbps .m4a at 48 kHz.
# Usage: normalize.sh <input> <output.m4a> [start_s] [duration_s]
#   start_s/duration_s optionally cut an excerpt (e.g. 3 s of a long cricket recording).
# Method: trim silence -> measure with ffmpeg's ebur128 meter (short clips padded to 3 s; silence
# does not change gated loudness) -> one linear gain = min(-16 - I, -1.5 - TP) -> encode.
# Peaky sounds (rimshot, boom attack) land below -16 LUFS because the peak cap wins; no limiter,
# so no added distortion. -1.5 dBTP before encoding leaves room for AAC overshoot.
# Why not loudnorm: on a sub-second transient its pass-1 input_i read ~7.6 LU lower than ebur128.
set -euo pipefail
in="$1"; out="$2"; ss="${3:-0}"; dur="${4:-}"
cut=(-ss "$ss"); [[ -n "$dur" ]] && cut+=(-t "$dur")
trimmed="${out%.*}.trim.wav"
ffmpeg -nostdin -v error -y "${cut[@]}" -i "$in" -vn \
  -af "silenceremove=start_periods=1:start_threshold=-60dB,areverse,silenceremove=start_periods=1:start_threshold=-60dB,areverse" \
  -ar 48000 -c:a pcm_f32le "$trimmed"
r="$(ffmpeg -nostdin -hide_banner -i "$trimmed" -af "apad=whole_dur=3,ebur128=peak=true" -f null - 2>&1 | sed -n '/Summary:/,$p')"
I="$(awk '/ I:/{print $2; exit}' <<<"$r")"; TP="$(awk '/Peak:/{print $2; exit}' <<<"$r")"
gain="$(python3 -I -c "import sys; i,tp=float(sys.argv[1]),float(sys.argv[2]); print(f'{min(-16.0-i, -1.5-tp):.2f}')" "$I" "$TP")"
ffmpeg -nostdin -v error -y -i "$trimmed" -af "volume=${gain}dB" -ar 48000 -c:a aac -b:a 128k -movflags +faststart "$out"
len="$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$trimmed")"
echo "in_I=$I in_TP=$TP gain_dB=$gain trimmed_s=$len -> $out (intermediate kept: $trimmed)"
