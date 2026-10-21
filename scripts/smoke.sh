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
ui() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml; }
onscreen() { ui | grep -q "$1"; }
# Taps the centre of the first element whose text or description is exactly $1.
tapText() {
  local b
  b=$(ui | tr '>' '\n' | grep -E "(text|content-desc)=\"$1\"" | head -1 | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -oE '[0-9]+' | tr '\n' ' ')
  [ -z "$b" ] && return 1
  set -- $b
  adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
}
open() { adb shell am start -S -W -n $ACT --ez skip true --ei seen 999 --ez dynamic false "$@" >/dev/null; }

adb shell settings put global hide_error_dialogs 1
# The emulator's "Viewing full screen" tip has a bug that restarts System UI; mark it as already seen.
adb shell settings put secure immersive_mode_confirmations confirmed
sleep 25; nodialog
for ime in $(adb shell ime list -s | tr -d '\r'); do adb shell ime disable "$ime" >/dev/null; done

echo "== Test photos and videos =="
WA="/sdcard/Android/media/com.whatsapp/WhatsApp/Media"
adb shell mkdir -p /sdcard/DCIM/Camera /sdcard/Pictures/Screenshots /sdcard/Pictures/Telegram /sdcard/Movies/Telegram /sdcard/Download/iPhone /sdcard/Pictures/Messenger \
  "'$WA/WhatsApp Images'" "'$WA/WhatsApp Video'"
for f in fixtures/camera/*; do adb push "$f" /sdcard/DCIM/Camera/ >/dev/null; done
for f in fixtures/shots/*; do adb push "$f" /sdcard/Pictures/Screenshots/ >/dev/null; done
for f in fixtures/whatsapp/*; do adb push "$f" "$WA/WhatsApp Images/" >/dev/null; done
for f in fixtures/whatsapp_video/*; do adb push "$f" "$WA/WhatsApp Video/" >/dev/null; done
for f in fixtures/telegram/*; do adb push "$f" /sdcard/Pictures/Telegram/ >/dev/null; done
for f in fixtures/telegram_video/*; do adb push "$f" /sdcard/Movies/Telegram/ >/dev/null; done
for f in fixtures/iphone/*; do adb push "$f" /sdcard/Download/iPhone/ >/dev/null; done
for f in fixtures/messenger/*; do adb push "$f" /sdcard/Pictures/Messenger/ >/dev/null; done
adb shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1 || true
sleep 12
adb shell content query --uri content://media/external/file --projection _display_name:relative_path --where "media_type IN (1,3)" | head -40

# Puts the first crash in the log on the job summary, so it can be read without the full log.
crashnote() {
  adb logcat -d > "shots/log-$1.txt"
  if grep -q "FATAL EXCEPTION: main" "shots/log-$1.txt" && grep -q "Process: $PKG" "shots/log-$1.txt"; then
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
adb logcat -d -s ALLPHOTOS_SELFTEST:I | tee shots/selftest.txt | tail -40
line=$(adb logcat -d -s ALLPHOTOS_SELFTEST:I | grep -E "lib=.*index=" | tail -1)
echo "Self test: $line"
for part in lib db ocr textlines shader bake docs labels hash similar decode details frame trim videofilter edit trash restore rules notify index; do
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
shot 50-viewer-open 6; alive "viewer"
adb shell input tap 540 1200; shot 51-viewer-immersive 3
# Details on a photo: first through the test hook, then from the menu with real taps.
adb logcat -c
open --ei theme 1 --ez photo true --ez details true
shot 52-details 6
if ! adb logcat -d | grep -q "Details opened"; then
  echo "::error::Details panel did not open from the hook ($(adb logcat -d | grep -c 'Demo opened details') hook lines)"; fail=1
fi
adb shell input keyevent 4; sleep 2
adb logcat -c
open --ei theme 1 --ez photo true
sleep 6
tapText "More" || adb shell input tap 1030 160
sleep 1.5
tapText "Details" || true
sleep 3
if ! adb logcat -d | grep -q "Details opened"; then echo "::error::Details from the menu did not open the panel"; fail=1; fi
tapText "Show more" || true
shot 53-details-more 3
if ! adb logcat -d | grep -q "Details show more: true"; then echo "::error::Details 'Show more' did not expand"; fail=1; fi
adb shell input keyevent 4; sleep 2
alive "details"

# Swipe up for details, swipe down to close.
adb logcat -c
open --ei theme 1 --ez photo true
sleep 5
adb shell input swipe 540 1500 540 900 250
sleep 2
shot 58-swipe-up-details 1
if ! adb logcat -d | grep -q "Details opened"; then echo "::error::Swiping up did not open the details"; fail=1; fi
adb shell input keyevent 4; sleep 2
adb shell input swipe 540 1000 560 1900 300
sleep 2
shot 59-swiped-down 1
if onscreen "More"; then echo "::error::Swiping down did not close the viewer"; fail=1; fi
alive "swipes"

# Tap-to-copy text on the receipt photo.
open --ei theme 1 --ez photo true
sleep 5
tapText "Text" || adb shell input tap 540 2278
shot 62-text-mode 6
if ! onscreen "Copy"; then echo "::warning::Text mode did not show copy actions"; fi
adb shell input keyevent 4; sleep 2
alive "text mode"

open --ei theme 1 --ez video true
shot 54-video-playing 5
adb shell input tap 540 1000; shot 55-video-controls 2
alive "video"
adb logcat -c
open --ei theme 1 --ez video true --ez details true
shot 56-video-details 6
if ! adb logcat -d | grep -q "Details opened"; then echo "::error::Video details did not open"; fail=1; fi
adb shell input keyevent 4; sleep 2
open --ei theme 1 --ez video true
shot 57-video-autohide 7
open --ei theme 1 --ez edit true
shot 60-editor 7; alive "editor"
tapText "Filters" && shot 63-editor-filters 3
tapText "Vivid" && shot 64-editor-vivid 3
tapText "Adjust" && shot 65-editor-adjust 3
alive "editor filters"
open --ei theme 1 --ez trim true
shot 61-trim 7; alive "trim"

echo "== Settings, animation levels and stacks =="
open --ei theme 1 --es open settings
sleep 4
adb shell input swipe 540 1800 540 700 300
shot 66-settings-new 3
for lvl in 0 2; do
  open --ei theme 1 --ei motion $lvl --ei view 3
  sleep 5; alive "motion $lvl"
done
open --ei theme 1 --ei motion 1 --ez stacks true
shot 67-stacked 6; alive "stacks"
open --ei theme 1 --ez stacks false --ei tab 1
sleep 4
adb shell input swipe 540 1900 540 600 300
shot 68-albums-documents 3

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
if grep -v "IN SYSTEM PROCESS" shots/logcat.txt | grep -A 25 "FATAL EXCEPTION:" | grep -q "Process: $PKG"; then
  echo "::error::Crash found in log"; grep -A 40 "FATAL EXCEPTION" shots/logcat.txt | head -150; fail=1
fi
true
exit $fail
