#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VIDEO="$ROOT_DIR/play-store/video/exports/pixelpals-play-store-preview.mp4"

if [[ ! -f "$VIDEO" ]]; then
  echo "Missing approved Play preview: $VIDEO" >&2
  exit 1
fi

video_stream="$(ffprobe -v error -select_streams v:0 \
  -show_entries stream=codec_name,width,height,pix_fmt,r_frame_rate \
  -of default=noprint_wrappers=1 "$VIDEO")"
audio_stream="$(ffprobe -v error -select_streams a:0 \
  -show_entries stream=codec_name,sample_rate,channels \
  -of default=noprint_wrappers=1 "$VIDEO")"
duration="$(ffprobe -v error -show_entries format=duration \
  -of default=noprint_wrappers=1:nokey=1 "$VIDEO")"

grep -qx 'codec_name=h264' <<<"$video_stream"
grep -qx 'width=720' <<<"$video_stream"
grep -qx 'height=1280' <<<"$video_stream"
grep -qx 'pix_fmt=yuv420p' <<<"$video_stream"
grep -qx 'codec_name=aac' <<<"$audio_stream"
grep -qx 'sample_rate=48000' <<<"$audio_stream"
grep -qx 'channels=2' <<<"$audio_stream"
awk -v duration="$duration" 'BEGIN { exit !(duration >= 9.9 && duration <= 10.1) }'

ffmpeg -v error -i "$VIDEO" -f null -

printf '%s\n' "$video_stream"
printf '%s\n' "$audio_stream"
printf 'duration=%s\n' "$duration"
printf 'Approved Play preview is valid: %s\n' "$VIDEO"
