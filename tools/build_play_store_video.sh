#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VIDEO_DIR="$ROOT_DIR/play-store/video"
SOURCE_DIR="$VIDEO_DIR/source"
WORK_DIR="$VIDEO_DIR/work"
EXPORT_DIR="$VIDEO_DIR/exports"
FONT_REGULAR="/usr/share/fonts/truetype/noto/NotoSans-Regular.ttf"
FONT_BOLD="/usr/share/fonts/truetype/noto/NotoSans-Bold.ttf"
FEATURE_ART="$ROOT_DIR/play-store/source/aso-feature-base-source.png"
PARTY_ART="$VIDEO_DIR/art/pixelpals-all-pets-party.png"

mkdir -p "$WORK_DIR" "$EXPORT_DIR"

for required in \
  "$SOURCE_DIR/ginger-home.mp4" \
  "$SOURCE_DIR/ginger-home-en.mp4" \
  "$SOURCE_DIR/ginger-care-en.mp4" \
  "$SOURCE_DIR/bloop-care.mp4" \
  "$SOURCE_DIR/bloop-overlay.mp4" \
  "$SOURCE_DIR/adventures.mp4" \
  "$SOURCE_DIR/adventures-en.mp4" \
  "$FEATURE_ART" \
  "$PARTY_ART" \
  "$FONT_REGULAR" \
  "$FONT_BOLD"; do
  if [[ ! -f "$required" ]]; then
    echo "Missing required source: $required" >&2
    exit 1
  fi
done

render_live_segment() {
  local input="$1"
  local start="$2"
  local duration="$3"
  local title_file="$4"
  local output="$5"

  ffmpeg -hide_banner -loglevel error -y \
    -ss "$start" -t "$duration" -i "$input" \
    -filter_complex "
      [0:v]fps=30,split=2[back][front];
      [back]scale=1080:2412:force_original_aspect_ratio=increase,
        crop=1080:1920,gblur=sigma=34,eq=brightness=-0.14:saturation=0.72[blur];
      [front]scale=-2:1920[phone];
      [blur][phone]overlay=(W-w)/2:0,
        drawbox=x=0:y=0:w=1080:h=82:color=0x183B38@1.0:t=fill,
        drawbox=x=54:y=92:w=972:h=156:color=0x183B38@0.88:t=fill,
        drawtext=fontfile='$FONT_BOLD':textfile='$title_file':
          fontcolor=0xFFF9F2:fontsize=45:x=(w-text_w)/2:y=142:
          shadowcolor=black@0.28:shadowx=0:shadowy=3,
        fade=t=in:st=0:d=0.18,fade=t=out:st=$(awk -v d="$duration" 'BEGIN { printf "%.3f", d-0.18 }'):d=0.18,
        format=yuv420p[v]" \
    -map "[v]" -an -c:v libx264 -preset medium -crf 18 \
    -profile:v high -level 4.1 -movflags +faststart "$output"
}

render_feature_segment() {
  local input="$1"
  local duration="$2"
  local headline_file="$3"
  local subtitle_file="$4"
  local output="$5"

  ffmpeg -hide_banner -loglevel error -y \
    -loop 1 -t "$duration" -i "$input" \
    -filter_complex "
      [0:v]fps=30,split=2[back][card];
      [back]scale=1080:1920:force_original_aspect_ratio=increase,
        crop=1080:1920,gblur=sigma=42,eq=brightness=-0.19:saturation=0.82[blur];
      [card]scale=1000:-2[art];
      [blur][art]overlay=40:(H-h)/2,
        drawbox=x=54:y=250:w=972:h=320:color=0x183B38@0.86:t=fill,
        drawtext=fontfile='$FONT_BOLD':text=PixelPals:
          fontcolor=0xFFF9F2:fontsize=90:x=(w-text_w)/2:y=300:
          shadowcolor=black@0.28:shadowx=0:shadowy=4,
        drawtext=fontfile='$FONT_BOLD':textfile='$headline_file':
          fontcolor=0xF4A52C:fontsize=49:x=(w-text_w)/2:y=420,
        drawtext=fontfile='$FONT_REGULAR':textfile='$subtitle_file':
          fontcolor=0xFFF9F2:fontsize=31:x=(w-text_w)/2:y=495,
        fade=t=in:st=0:d=0.25,fade=t=out:st=$(awk -v d="$duration" 'BEGIN { printf "%.3f", d-0.22 }'):d=0.22,
        format=yuv420p[v]" \
    -map "[v]" -an -c:v libx264 -preset medium -crf 18 \
    -profile:v high -level 4.1 -movflags +faststart "$output"
}

render_memory_segment() {
  local input="$1"
  local duration="$2"
  local title_file="$3"
  local output="$4"

  ffmpeg -hide_banner -loglevel error -y \
    -loop 1 -t "$duration" -i "$input" \
    -filter_complex "
      [0:v]fps=30,scale=1080:2412:force_original_aspect_ratio=increase,
        crop=1080:1920,
        drawbox=x=0:y=0:w=1080:h=82:color=0x183B38@1.0:t=fill,
        drawbox=x=54:y=92:w=972:h=156:color=0x183B38@0.88:t=fill,
        drawtext=fontfile='$FONT_BOLD':textfile='$title_file':
          fontcolor=0xFFF9F2:fontsize=45:x=(w-text_w)/2:y=142:
          shadowcolor=black@0.28:shadowx=0:shadowy=3,
        fade=t=in:st=0:d=0.18,fade=t=out:st=$(awk -v d="$duration" 'BEGIN { printf "%.3f", d-0.18 }'):d=0.18,
        format=yuv420p[v]" \
    -map "[v]" -an -c:v libx264 -preset medium -crf 18 \
    -profile:v high -level 4.1 -movflags +faststart "$output"
}

render_party_segment() {
  local input="$1"
  local duration="$2"
  local title_file="$3"
  local output="$4"

  ffmpeg -hide_banner -loglevel error -y \
    -loop 1 -t "$duration" -i "$input" \
    -filter_complex "
      [0:v]fps=30,split=2[back][card];
      [back]scale=1080:1920:force_original_aspect_ratio=increase,
        crop=1080:1920,gblur=sigma=38,eq=brightness=-0.16:saturation=0.82[blur];
      [card]scale=1020:-2[art];
      [blur][art]overlay=30:(H-h)/2,
        drawbox=x=54:y=92:w=972:h=156:color=0x183B38@0.88:t=fill,
        drawtext=fontfile='$FONT_BOLD':textfile='$title_file':
          fontcolor=0xFFF9F2:fontsize=43:x=(w-text_w)/2:y=143:
          shadowcolor=black@0.28:shadowx=0:shadowy=3,
        fade=t=in:st=0:d=0.18,fade=t=out:st=$(awk -v d="$duration" 'BEGIN { printf "%.3f", d-0.18 }'):d=0.18,
        format=yuv420p[v]" \
    -map "[v]" -an -c:v libx264 -preset medium -crf 18 \
    -profile:v high -level 4.1 -movflags +faststart "$output"
}

write_copy() {
  local locale="$1"
  local copy_dir="$WORK_DIR/copy-$locale"
  mkdir -p "$copy_dir"
  if [[ "$locale" == "es" ]]; then
    printf '%s' 'Un compañero que se siente vivo' > "$copy_dir/intro.txt"
    printf '%s' '15 mascotas · hogar · aventuras' > "$copy_dir/intro-subtitle.txt"
    printf '%s' 'Ginger sueña en su propio hogar' > "$copy_dir/ginger.txt"
    printf '%s' 'Cuidados únicos para cada especie' > "$copy_dir/care.txt"
    printf '%s' 'Contigo, también sobre otras apps' > "$copy_dir/overlay.txt"
    printf '%s' 'Aventuras, tesoros y recuerdos' > "$copy_dir/adventures.txt"
    printf '%s' 'Un vínculo que crece cada día' > "$copy_dir/memory.txt"
    printf '%s' 'Una gran familia, 15 vidas únicas' > "$copy_dir/party.txt"
    printf '%s' 'Adopta a tu compañero' > "$copy_dir/outro.txt"
    printf '%s' 'Descubre quién está esperando conocerte' > "$copy_dir/outro-subtitle.txt"
  else
    printf '%s' 'A companion that feels alive' > "$copy_dir/intro.txt"
    printf '%s' '15 pets · homes · adventures' > "$copy_dir/intro-subtitle.txt"
    printf '%s' 'Ginger dreams in a home of her own' > "$copy_dir/ginger.txt"
    printf '%s' 'Care made for every species' > "$copy_dir/care.txt"
    printf '%s' 'With you, even over other apps' > "$copy_dir/overlay.txt"
    printf '%s' 'Adventures, treasures and memories' > "$copy_dir/adventures.txt"
    printf '%s' 'A bond that grows every day' > "$copy_dir/memory.txt"
    printf '%s' 'One big family, 15 unique lives' > "$copy_dir/party.txt"
    printf '%s' 'Meet your new companion' > "$copy_dir/outro.txt"
    printf '%s' 'Discover who is waiting to meet you' > "$copy_dir/outro-subtitle.txt"
  fi
}

render_locale() {
  local locale="$1"
  local feature="$2"
  local screenshots="$ROOT_DIR/screenshots-editor/public/screenshots/android/phone/$locale"
  local copy_dir="$WORK_DIR/copy-$locale"
  local locale_work="$WORK_DIR/$locale"
  local home_source="$SOURCE_DIR/ginger-home.mp4"
  local care_source="$SOURCE_DIR/bloop-care.mp4"
  local adventures_source="$SOURCE_DIR/adventures.mp4"
  if [[ "$locale" == "en" ]]; then
    home_source="$SOURCE_DIR/ginger-home-en.mp4"
    care_source="$SOURCE_DIR/ginger-care-en.mp4"
    adventures_source="$SOURCE_DIR/adventures-en.mp4"
  fi
  mkdir -p "$locale_work"
  rm -f "$locale_work"/0*.mp4 "$locale_work/concat.txt" "$locale_work/silent.mp4"
  write_copy "$locale"

  render_feature_segment "$feature" 1.5 "$copy_dir/intro.txt" "$copy_dir/intro-subtitle.txt" "$locale_work/01-intro.mp4"
  render_live_segment "$home_source" 0.5 4.5 "$copy_dir/ginger.txt" "$locale_work/02-ginger.mp4"
  render_live_segment "$care_source" 4.4 6.0 "$copy_dir/care.txt" "$locale_work/03-care.mp4"
  render_live_segment "$SOURCE_DIR/bloop-overlay.mp4" 1.0 5.0 "$copy_dir/overlay.txt" "$locale_work/04-overlay.mp4"
  render_live_segment "$adventures_source" 3.6 5.0 "$copy_dir/adventures.txt" "$locale_work/05-adventures.mp4"
  render_memory_segment "$screenshots/07.png" 2.5 "$copy_dir/memory.txt" "$locale_work/06-memory.mp4"
  render_party_segment "$PARTY_ART" 2.0 "$copy_dir/party.txt" "$locale_work/07-party.mp4"
  render_feature_segment "$feature" 1.5 "$copy_dir/outro.txt" "$copy_dir/outro-subtitle.txt" "$locale_work/08-outro.mp4"

  : > "$locale_work/concat.txt"
  for segment in "$locale_work"/0*.mp4; do
    printf "file '%s'\n" "$segment" >> "$locale_work/concat.txt"
  done
  ffmpeg -hide_banner -loglevel error -y -f concat -safe 0 \
    -i "$locale_work/concat.txt" -c copy "$locale_work/silent.mp4"

  ffmpeg -hide_banner -loglevel error -y \
    -i "$locale_work/silent.mp4" -i "$WORK_DIR/pixelpals-original-soundtrack.wav" \
    -filter_complex "[1:a]loudnorm=I=-16:TP=-1.5:LRA=7[a]" \
    -map 0:v:0 -map "[a]" -shortest -c:v copy -c:a aac -b:a 192k \
    -ar 48000 \
    -movflags +faststart "$EXPORT_DIR/pixelpals-play-store-preview-$locale.mp4"
}

python3 "$ROOT_DIR/tools/generate_promo_soundtrack.py" "$WORK_DIR/pixelpals-original-soundtrack.wav"
render_locale es "$FEATURE_ART"
render_locale en "$FEATURE_ART"

for video in "$EXPORT_DIR"/pixelpals-play-store-preview-*.mp4; do
  ffprobe -v error -show_entries format=filename,duration,size \
    -show_entries stream=codec_name,width,height,pix_fmt,sample_rate,channels \
    -of compact=p=0 "$video"
done
