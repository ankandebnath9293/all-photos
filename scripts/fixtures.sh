#!/usr/bin/env bash
# Makes the test photos and videos the emulator test copies onto the phone.
set -eu
F=fixtures
rm -rf "$F" && mkdir -p "$F/camera" "$F/shots" "$F/whatsapp" "$F/whatsapp_video" "$F/telegram" "$F/telegram_video" "$F/new_shots" "$F/iphone" "$F/messenger"

# A burst: four near-identical shots of the same scene, plus a different photo.
convert -size 1600x1200 -seed 7 plasma:skyblue-forestgreen -blur 0x2 \
  -fill white -stroke black -strokewidth 3 -draw "circle 800,600 800,380" \
  -fill '#ffcc33' -draw "rectangle 200,200 500,420" "$F/base.png"
for i in 1 2 3 4; do
  convert "$F/base.png" -modulate $((99 + i)) -attenuate 0.15 +noise Gaussian -quality 92 "$F/camera/PXL_20261020_1200${i}0.jpg"
done
convert "$F/base.png" -blur 0x9 -quality 92 "$F/camera/PXL_20261020_120050.jpg"
convert -size 1500x1000 -seed 42 plasma:orange-purple -quality 90 "$F/camera/PXL_20261019_090000.jpg"
convert -size 1080x1440 -seed 3 plasma:pink-teal \
  -fill black -pointsize 90 -annotate +80+700 'Receipt 1234' -quality 90 "$F/camera/PXL_20261018_183000.jpg"

# Screenshots, one of a "browser" with an address bar.
mk_shot() {
  convert -size 1080x2400 xc:white \
    -fill '#eeeeee' -draw "rectangle 0,0 1080,220" \
    -fill black -pointsize 46 -annotate +60+170 "$2" \
    -fill '#333333' -pointsize 64 -annotate +60+520 "$3" \
    -pointsize 44 -annotate +60+640 "Screenshot test page" "$1"
}
mk_shot "$F/shots/Screenshot_20261020-101500_Chrome.png" "motionblurmedia.agency/work" "Our latest work"
mk_shot "$F/shots/Screenshot_20261019-221000.png" "Messenger" "See you tomorrow"
mk_shot "$F/shots/Screenshot_20260801-080000.png" "Settings" "Display and brightness"
mk_shot "$F/new_shots/Screenshot_20261021-200000.png" "example.org/new" "Fresh screenshot one"
mk_shot "$F/new_shots/Screenshot_20261021-200005.png" "example.org/new2" "Fresh screenshot two"

# WhatsApp and Telegram media.
convert -size 900x1200 -seed 11 plasma:yellow-blue -quality 80 "$F/whatsapp/IMG-20260901-WA0001.jpg"
convert -size 1200x900 -seed 12 plasma:red-green -quality 80 "$F/whatsapp/IMG-20260902-WA0002.jpg"
convert -size 800x800 -seed 13 plasma:white-black -quality 80 "$F/telegram/photo_2026-09-01_10-00-00.jpg"
convert -size 960x720 -seed 14 plasma:orange-teal -quality 80 "$F/messenger/received_1234567890.jpeg"
# A receipt and a ticket for the Documents section.
convert -size 900x1400 xc:white -fill black -pointsize 52 \
  -annotate +60+150 'STAR KABAB' -annotate +60+300 'Chicken Biryani  350.00' -annotate +60+400 'Borhani x2  120.00' \
  -annotate +60+550 'Sub Total  470.00' -annotate +60+650 'VAT 5%  23.50' -annotate +60+750 'Grand Total  493.50' \
  -annotate +60+850 'Cash  500.00' -annotate +60+950 'Change  6.50' -quality 92 "$F/camera/PXL_20261017_201500.jpg"

# Videos: a camera MP4 with sound, an iPhone-style MOV, chat videos.
ffmpeg -loglevel error -f lavfi -i testsrc2=size=1280x720:rate=30 -f lavfi -i sine=frequency=440:sample_rate=48000 \
  -t 4 -c:v libx264 -pix_fmt yuv420p -profile:v high -c:a aac -b:a 128k -shortest "$F/camera/PXL_20261020_130000.mp4"
ffmpeg -loglevel error -f lavfi -i testsrc=size=1920x1080:rate=24 -f lavfi -i sine=frequency=220 \
  -t 3 -c:v libx264 -pix_fmt yuv420p -c:a aac -shortest "$F/iphone/IMG_0042.MOV"
ffmpeg -loglevel error -f lavfi -i testsrc=size=640x360:rate=30 -t 2 -c:v libx264 -pix_fmt yuv420p "$F/whatsapp_video/VID-20260901-WA0003.mp4"
# A big one (over 10 MB) for the Large files page.
ffmpeg -loglevel error -f lavfi -i testsrc2=size=1920x1080:rate=30 -t 4 -vf "noise=alls=40:allf=t" -c:v libx264 -preset veryfast -pix_fmt yuv420p -b:v 28M -maxrate 28M -bufsize 28M -x264-params nal-hrd=cbr "$F/camera/PXL_20261018_090000.mp4"
ffmpeg -loglevel error -f lavfi -i testsrc=size=640x360:rate=25 -t 2 -c:v libx264 -pix_fmt yuv420p "$F/telegram_video/video_2026-09-01.mp4"

# An iPhone-style HEIC photo, when the encoder is available.
if command -v heif-enc >/dev/null 2>&1; then
  heif-enc -q 80 -o "$F/iphone/IMG_0041.HEIC" "$F/camera/PXL_20261019_090000.jpg" || true
fi
rm -f "$F/base.png"
find "$F" -type f | sort
