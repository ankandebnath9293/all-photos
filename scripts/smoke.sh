#!/usr/bin/env bash
# Installs the app in an emulator with test photos, opens every screen, takes screenshots,
# runs the on-device self test and fails on any crash.
set -u
PKG=com.ankandebnath.allphotos
ACT=$PKG/.ui.MainActivity
mkdir -p shots
fail=0

nodialog() {
  if adb shell dumpsys window | grep -q "Application Not Responding"; then
    echo "(dismissing a system 'not responding' box)"
    adb shell input keyevent 66; sleep 1
  fi
}
shot() { sleep "${2:-5}"; nodialog; adb exec-out screencap -p > "shots/$1.png"; }
alive() {
  if ! adb shell pidof $PKG >/dev/null; then echo "::error::App is not running after: $1"; fail=1; fi
}
open() { adb shell am start -S -W -n $ACT --ez skip true --ei seen 1 --ez dynamic false "$@" >/dev/null; }

adb shell settings put global hide_error_dialogs 1
sleep 25; nodialog
for ime in $(adb shell ime list -s | tr -d '\r'); do adb shell ime disable "$ime" >/dev/null; done

echo "== Test photos and videos =="
WA="/sdcard/Android/media/com.whatsapp/WhatsApp/Media"
adb shell mkdir -p /sdcard/DCIM/Camera /sdcard/Pictures/Screenshots /sdcard/Pictures/Telegram /sdcard/Movies/Telegram /sdcard/Download/iPhone \
  "'$WA/WhatsApp Images'" "'$WA/WhatsApp Video'"
for f in fixtures/camera/*; do adb push "$f" /sdcard/DCIM/Camera/ >/dev/null; done
for f in fixtures/shots/*; do adb push "$f" /sdcard/Pictures/Screenshots/ >/dev/null; done
for f in fixtures/whatsapp/*; do adb push "$f" "$WA/WhatsApp Images/" >/dev/null; done
for f in fixtures/whatsapp_video/*; do adb push "$f" "$WA/WhatsApp Video/" >/dev/null; done
for f in fixtures/telegram/*; do adb push "$f" /sdcard/Pictures/Telegram/ >/dev/null; done
for f in fixtures/telegram_video/*; do adb push "$f" /sdcard/Movies/Telegram/ >/dev/null; done
for f in fixtures/iphone/*; do adb push "$f" /sdcard/Download/iPhone/ >/dev/null; done
adb shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1 || true
sleep 12
adb shell content query --uri content://media/external/file --projection _display_name:relative_path --where "media_type IN (1,3)" | head -40

# Puts the first crash in the log on the job summary, so it can be read without the full log.
crashnote() {
  adb logcat -d > "shots/log-$1.txt"
  if grep -q "FATAL EXCEPTION" "shots/log-$1.txt"; then
    echo "::error::Crash during $1: $(grep -A 14 'FATAL EXCEPTION' "shots/log-$1.txt" | sed 's/^.*AndroidRuntime: //' | head -15 | tr '\n' '|' | cut -c1-1800)"
  fi
}

echo "== Release build: first launch =="
adb logcat -c
adb install -r apk/AllPhotos.apk
adb shell am start -W -n $ACT >/dev/null
shot 00-welcome 8
alive "release first launch"
adb shell input tap 900 2250; shot 01-storage-step 3
alive "onboarding step"
crashnote release
adb uninstall $PKG >/dev/null

echo "== Debug build with access granted =="
adb install -r apk/AllPhotos-debug.apk
adb shell appops set --uid $PKG MANAGE_EXTERNAL_STORAGE allow
adb shell appops set --uid $PKG MANAGE_MEDIA allow
adb shell appops set $PKG GET_USAGE_STATS allow
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS
adb shell pm grant $PKG android.permission.READ_MEDIA_IMAGES
adb shell pm grant $PKG android.permission.READ_MEDIA_VIDEO
adb shell settings put secure enabled_accessibility_services $PKG/$PKG.capture.WatchService
adb shell settings put secure accessibility_enabled 1
adb logcat -c

# Let the emulator compile the app before timing anything.
open; sleep 25
adb logcat -d > shots/logcat-warmup.txt; adb logcat -c

echo "== Self test =="
open --ez selftest true
for i in $(seq 1 60); do
  if adb logcat -d -s ALLPHOTOS_SELFTEST:I | grep -q "index="; then break; fi
  sleep 3
done
adb logcat -d -s ALLPHOTOS_SELFTEST:I | tail -40
line=$(adb logcat -d -s ALLPHOTOS_SELFTEST:I | grep -E "lib=.*index=" | tail -1)
echo "Self test: $line"
for part in lib db ocr labels hash similar decode details frame trim edit trash rules notify index; do
  if ! echo "$line" | grep -qE "(^| )$part=OK"; then echo "::error::Self test part '$part' did not pass"; fail=1; fi
done
if ! echo "$line" | grep -qE "convert=(OK|SKIP)"; then echo "::error::Self test part 'convert' failed"; fail=1; fi
alive "self test"

echo "== Every tab, light and dark =="
for tab in 0 1 2 3; do
  open --ei tab $tab --ei theme 1
  shot "1$tab-tab$tab" 6; alive "tab $tab light"
  adb shell input swipe 540 1800 540 700 300
  shot "1$tab-tab$tab-scrolled" 2
  open --ei tab $tab --ei theme 2
  shot "2$tab-tab$tab-dark" 6; alive "tab $tab dark"
done
open --ei tab 0 --ei theme 1 --ei cols 3
shot 15-photos-3cols 5
open --ei tab 0 --ei theme 1 --ei cols 6
shot 16-photos-6cols 5
open --ei cols 4 --ei theme 1
sleep 2
open --ei tab 0 --ei theme 1 --ez dynamic true
shot 17-wallpaper-colours 5; alive "dynamic colour"

echo "== Screenshots tab sections =="
open --ei tab 2 --ei theme 1
sleep 5
adb shell input tap 400 520; shot 30-shots-apps 3
adb shell input tap 660 520; shot 31-shots-websites 3
alive "screenshot sections"

echo "== Pages =="
for p in similar large trash settings search; do
  open --ei theme 1 --es open $p
  shot "4-$p" 7; alive "page $p"
done
open --ei theme 2 --es open settings
adb shell input swipe 540 1900 540 500 300
shot 4-settings-dark-scrolled 3

echo "== Viewer, details, video, editor, trim =="
open --ei theme 1 --ei view 2
shot 50-viewer-photo 6; alive "viewer"
adb shell input tap 540 1200; shot 51-viewer-immersive 3
open --ei theme 1 --ei view 2 --ez details true
shot 52-details 6
adb shell input tap 540 1400; shot 53-details-more 3
alive "details"
open --ei theme 1 --ez video true
shot 54-video-playing 5
adb shell input tap 540 1000; shot 55-video-controls 2
alive "video"
open --ei theme 1 --ez video true --ez details true
shot 56-video-details 6
adb shell input tap 540 1400; shot 57-video-details-more 3
open --ei theme 1 --ez edit true
shot 60-editor 7; alive "editor"
open --ei theme 1 --ez trim true
shot 61-trim 7; alive "trim"

echo "== A new screenshot gets its notification =="
open --ei tab 2 --ei theme 1
sleep 6
for f in fixtures/new_shots/*; do adb push "$f" /sdcard/Pictures/Screenshots/ >/dev/null; sleep 2; done
adb shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1 || true
sleep 10
adb shell dumpsys notification --noredact | grep -E "android.title=.*[Ss]creenshot" | head -5
if ! adb shell dumpsys notification --noredact | grep -qE "android.title=.*[Ss]creenshot"; then
  echo "::warning::No screenshot notification was seen"
fi
adb shell cmd statusbar expand-notifications; shot 70-notification 3; adb shell cmd statusbar collapse
alive "new screenshot"

echo "== Background jobs =="
adb shell cmd jobscheduler run -f $PKG 0 >/dev/null 2>&1 || true
sleep 5; alive "background jobs"

adb logcat -d > shots/logcat-main.txt
cat shots/logcat-warmup.txt shots/logcat-main.txt > shots/logcat.txt
if grep -q "ANR in $PKG" shots/logcat-main.txt; then
  echo "::error::The app stopped responding"; grep -B 6 -A 12 "ANR in $PKG" shots/logcat-main.txt | head -80; fail=1
fi
rm -f shots/logcat-warmup.txt shots/logcat-main.txt
crashnote debug
if grep -A 25 "FATAL EXCEPTION" shots/logcat.txt | grep -q "$PKG"; then
  echo "::error::Crash found in log"; grep -A 40 "FATAL EXCEPTION" shots/logcat.txt | head -150; fail=1
fi
grep "ALLPHOTOS_SELFTEST" shots/logcat.txt > shots/selftest.txt || true
exit $fail
