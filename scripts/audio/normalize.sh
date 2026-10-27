#!/usr/bin/env bash
# Normalize one sound to the content-intake target: -16 LUFS integrated, true peak <= -1 dBTP,
# AAC 128 kbps .m4a at 48 kHz.
# Usage: normalize.sh <input> <output.m4a> [start_s] [duration_s] [fade_in_s] [fade_out_s]
#   start_s/duration_s optionally cut an excerpt (e.g. 3 s of a long cricket recording).
#   fade_in_s/fade_out_s optionally fade the excerpt's edges (0 = none).
# Method: cut + fades -> trim leading/trailing silence below (peak - 60 dB), floor -90 dBFS, so quiet
# recordings keep their tails -> measure with ffmpeg's ebur128 meter (short clips padded to 3 s;
# silence does not change gated loudness) -> one linear gain = min(-16 - I, -1.5 - TP) -> encode.
# Peaky sounds (rimshot, boom attack) land below -16 LUFS because the peak cap wins; no limiter,
# so no added distortion. -1.5 dBTP before encoding leaves room for AAC overshoot.
# Why not loudnorm: on a sub-second transient its pass-1 input_i read ~7.6 LU lower than ebur128.
# Intermediates (<out>.cut.wav, <out>.trim.wav) are kept next to the output; write the output to a
# scratch folder and copy the .m4a into the repo.
set -euo pipefail
in="$1"; out="$2"; ss="${3:-0}"; dur="${4:-}"; fin="${5:-0}"; fout="${6:-0}"
cut=(-ss "$ss"); [[ -n "$dur" ]] && cut+=(-t "$dur")
base="${out%.*}"; cutwav="$base.cut.wav"; trimmed="$base.trim.wav"

fades="anull"
if [[ "$fin" != "0" || "$fout" != "0" ]]; then
  if [[ -z "$dur" ]]; then
    total="$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$in")"
    dur="$(python3 -I -c "import sys; print(float(sys.argv[1]) - float(sys.argv[2]))" "$total" "$ss")"
  fi
  st="$(python3 -I -c "import sys; print(max(0.0, float(sys.argv[1]) - float(sys.argv[2])))" "$dur" "$fout")"
  fades="afade=t=in:st=0:d=${fin},afade=t=out:st=${st}:d=${fout}"
fi
ffmpeg -nostdin -v error -y "${cut[@]}" -i "$in" -vn -af "$fades" -ar 48000 -c:a pcm_f32le "$cutwav"

peak="$(ffmpeg -nostdin -hide_banner -i "$cutwav" -af "astats=measure_perchannel=none:measure_overall=Peak_level" -f null - 2>&1 \
  | awk -F': ' '/Peak level dB/{print $2; exit}')"
thr="$(python3 -I -c "import sys; print(f'{max(-90.0, float(sys.argv[1]) - 60.0):.1f}')" "$peak")"
ffmpeg -nostdin -v error -y -i "$cutwav" \
  -af "silenceremove=start_periods=1:start_threshold=${thr}dB,areverse,silenceremove=start_periods=1:start_threshold=${thr}dB,areverse" \
  -c:a pcm_f32le "$trimmed"

r="$(ffmpeg -nostdin -hide_banner -i "$trimmed" -af "apad=whole_dur=3,ebur128=peak=true" -f null - 2>&1 | sed -n '/Summary:/,$p')"
I="$(awk '/ I:/{print $2; exit}' <<<"$r")"; TP="$(awk '/Peak:/{print $2; exit}' <<<"$r")"
gain="$(python3 -I -c "import sys; i,tp=float(sys.argv[1]),float(sys.argv[2]); print(f'{min(-16.0-i, -1.5-tp):.2f}')" "$I" "$TP")"
ffmpeg -nostdin -v error -y -i "$trimmed" -af "volume=${gain}dB" -ar 48000 -c:a aac -b:a 128k -movflags +faststart "$out"
len="$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$trimmed")"
echo "peak_dB=$peak trim_thr_dB=$thr in_I=$I in_TP=$TP gain_dB=$gain trimmed_s=$len -> $out"
