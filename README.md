# All Photos

A personal, fully offline gallery for Android, built for a Pixel 7 with Material 3 Expressive. It shows every photo and video on the phone. It has no ads, no account and no cloud, and it doesn't even have the internet permission. It replaces the default gallery, and Google Photos can keep running alongside it for backup.

**Version:** 1.2 (versionCode 3) · **Android:** 15 or newer (minSdk 35, targetSdk 36, compileSdk 37)

---

## Features

### Browsing
- **Photos, Albums, Screenshots, Cleanup** tabs in a floating bottom bar, with Search and Settings at the top of every tab
- Edge-to-edge grid with day/month headers, pinch to change columns (2–6) and a fast scroller
- Optional **stacks** for bursts of similar photos
- Plays nearly anything the phone can decode: JPEG, PNG, WebP, GIF, HEIC/HEIF, RAW (DNG), MP4, MOV and HDR video. HDR photos light up the screen in HDR mode.
- **Documents** in Albums: receipts, payment cards, IDs, tickets/events, menus, books and notes, sorted on the phone

### Viewer
- Photos grow out of the grid when opened and shrink back when closed (hero animation)
- Swipe **down** to close, swipe **up** for details. Videos work the same way.
- Top bar: date, album, screenshot source (app icon + website), HDR tag, ⭐ important, ⋮ menu (Details, Share/Save as JPG, Open website, Open with, Set as wallpaper)
- **Countdown pill** on screenshots and chat files that a clean-up rule will trash ("Moves to trash in 12 days · Keep")
- **Text from photos:** tap *Text* to outline every line, then copy, share, call a number, open a link or write an email. Works offline.
- Two-level **details**: the key facts first, then *Show more* (camera, lens, exposure, codec, bitrate, location and more)

### Video
- Shared ExoPlayer surface with no black flash when a video opens
- Skip ±10 s, step frame by frame, **save the exact frame** as a photo, rotate
- Smooth seek-bar scrubbing; double-tap left/right to skip
- **Edit video:** trim and add filters (Vivid, Dramatic, Mono, Noir…) with a live preview

### Editing
- Crop, rotate, flip
- Filters inspired by the iPhone (Vivid, Vivid Warm/Cool, Dramatic, Mono, Silvertone, Noir, Film)
- Adjustments: brightness, contrast, highlights, shadows, saturation, vibrance, warmth, tint, fade, vignette
- Live GPU preview (AGSL shader). Export uses matching CPU code, so the saved file looks like the preview.
- Save as a copy or replace the original
- HEIC → JPG conversion

### Screenshots
- Grouped by **Recent / Apps / Websites / Important**
- The source app and website are recorded by an optional accessibility service, with Usage Access as a fallback
- **Auto-trash** after N days (default 30) unless marked important
- A notification after you take screenshots offers "Mark as important". Screenshots taken within 30 seconds of each other share one notification ("Mark all 10 as important").

### Cleanup
- Storage overview by type and source
- **Similar photos** (perceptual hash), keep the best and free the rest
- **Large files** (over 10 MB): tap a thumbnail to preview or play it, tap the row to select it
- **Chat apps:** WhatsApp, Telegram, and Facebook & Messenger images and videos auto-trashed after N days, each with its own switch
- **Trash** keeps files for 30 days, and a small "Moved to trash" message confirms each move
- **Rules only count files from the install day onward.** Older screenshots and chat files are never touched.

### Smart search (on-device)
- ML Kit text recognition and image labelling, both bundled, so nothing is downloaded
- By default it indexes only while charging; there's a button to read all photos now
- Searches text in photos, things in photos ("dog", "car"), websites and file names

### Motion
- Animation level in Settings: **Minimal**, **Standard** or **Rich**
- Pages slide in from the right over the page below it, which drifts left and dims (Google Photos style). The back gesture shrinks the page as you swipe.

---

## Default settings

| Setting | Default |
|---|---|
| Theme | Follow phone |
| Wallpaper colours | On |
| Photos per row | 4 |
| Stack similar photos | Off |
| Show dates in grid | On |
| Animations | Standard |
| Clean up old screenshots | On, after 30 days |
| WhatsApp / Telegram clean-up | On, after 30 days |
| Facebook & Messenger clean-up | Off |
| Smart search only while charging | On |

---

## Install

1. Download `AllPhotos.apk` from the [`builds`](../../tree/builds) branch (or a GitHub Actions run).
2. Open it on the phone and allow installing from that source.
3. On first launch, grant:
   - **All files access**, so the app can trash, restore and edit any file without a prompt each time
   - **Media management**
   - **Notifications**, for the screenshot "Mark as important" prompt
   - Optional: **Accessibility → All Photos screenshot source** (records the app and website a screenshot came from), or **Usage access** as a lighter fallback
4. Optional: set All Photos as the default gallery when Android asks.

New versions install over old ones and keep all data (important marks, search index, sources).

---

## Build

CI does the building, but the project also builds locally with Android Studio or the command line. There is no Gradle wrapper in the repo; use Gradle 9.6 (the version CI uses).

**Requirements:** JDK 17, Android SDK with platform 37, Gradle 9.6

```bash
gradle testDebugUnitTest      # logic unit tests
gradle assembleRelease        # signed release APK → app/build/outputs/apk/release/
gradle assembleDebug          # debug build with test hooks (also minified)
```

**Toolchain:** Kotlin 2.4.20 · AGP 9.4.0 · Compose UI 1.12.1 · Material 3 1.5.0-beta01 (Expressive)

Release builds are signed with `keystore/allphotos.jks` (see `app/build.gradle.kts`). This is a personal sideload key; swap in your own before sharing builds.

---

## Continuous integration

`.github/workflows/build.yml` runs on every push to `main`:

1. **build:** unit tests, then release and debug APKs (R8 full mode)
2. **smoke:** boots an API 35 Pixel 7 emulator, loads generated test photos and videos (`scripts/fixtures.sh`), and runs `scripts/smoke.sh`, which:
   - upgrades from 1.0 to check that data survives
   - opens every tab and page in light and dark mode
   - tests the viewer gestures (swipe down/up, video tap/swipe), details, text mode, editor, trim, settings, stacks, the countdown and the Large-files preview
   - runs the on-device **self-test** (`app/src/debug/.../SelfTest.kt`): library, database, OCR, text lines, shader, bake, documents, labels, hashing, similar, decode, details, frame capture, trim, video filter, convert, edit, trash, restore, rules, notification, index
   - fails on any crash or ANR
3. **publish:** pushes the release APK, screenshots, app log and self-test results to the `builds` branch

If the build fails, the compile errors are pushed to the `buildlog` branch.

---

## Project layout

```
app/src/main/kotlin/com/ankandebnath/allphotos/
├── App.kt            app start, workers, restoring files trashed early by 1.0
├── capture/          screenshot source: accessibility service, usage stats, app icons
├── data/             MediaStore library, SQLite (FTS search, sources, documents), settings, trash ops
├── logic/            pure Kotlin, unit-tested: rules, batching, similarity, documents, smart text, filters
├── media/            OCR/labels, thumbnails, details, export, filters (GPU + CPU), video trim, HEIC convert
├── notify/           screenshot notifications and actions
├── ui/               Compose screens: tabs, viewer, video player, editor, text overlay, motion, theme
└── work/             WorkManager: auto clean-up, indexer, similar-photo data
app/src/debug/        test hooks (Demo.kt) and on-device self-test
app/src/test/         LogicTest.kt
scripts/              fixtures.sh (test media) and smoke.sh (emulator test)
```

---

## Privacy

- No internet permission (removed from the merged manifest), so nothing leaves the phone
- OCR and labelling run on-device with bundled ML Kit models
- The accessibility service only reads the current app and browser address bar when a screenshot is taken, and it's optional

---

## Changelog

**1.2**
- Countdown pill in the viewer for files a clean-up rule will trash; tap *Keep* to keep the file
- Tap a video to toggle its controls; swipe down on a video to close it
- Search moved to the top; fixed bottom tabs that always show their names
- Cleaner Cleanup and Screenshots headers
- Large files: tap a thumbnail to preview or play it; thumbnails for .MOV files with no system thumbnail
- Google Photos-style slide-in page transitions

**1.1**
- Clean-up counts only files from the install day; files 1.0 trashed early are restored
- Hero open/close, swipe down to close, Minimal/Standard/Rich animation levels
- Copy text from photos, Documents albums, new filters and video filters
- Separate WhatsApp, Telegram, and Facebook & Messenger rules
- Star and source in the viewer's top bar; simpler video controls; smoother scrubbing
- Black-and-white app icon

**1.0**
- First release
