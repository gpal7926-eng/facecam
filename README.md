# FaceCam

**Purani film wali feel, ek click mein.**

FaceCam is an offline-first vintage film-camera app. Point it at something, press the
shutter, and it turns an ordinary phone photo into something that looks like it came off
an old 35mm roll, a toy camera, or an instant Polaroid. There are no sliders and no manual
editing — each "camera" inside the app simulates a real film camera, and the vintage look
(grain, dust, light leaks, vignette, faded colour, frames) is applied automatically and
randomly, so no two photos come out the same.

This repository contains **two complete implementations** of the same app:

| Folder | What it is |
|---|---|
| [`FaceCam-preview.html`](FaceCam-preview.html) | The whole preview as one standalone file — double-click to run. |
| [`web/`](web/) | The same app as a normal HTML / CSS / JavaScript project you can open in any browser. This is the live preview — it uses `getUserMedia` for the viewfinder, a `<canvas>` film pipeline, and browser storage (`localStorage` + `IndexedDB`). |
| [`android/`](android/) | The native Android app: Kotlin + Jetpack Compose + CameraX, 100% on-device. Open it in Android Studio. |
| [`docs/`](docs/) | The original feature spec and screenshots of the web build. |

Everything is local. There is no backend, no login, and no network call in the app logic.

---

## Features

- **13 film simulations** — 135 B / M / P, TOY F / K, ROMA, FR2, 2007, EATS, INS 2 (instant),
  SWIRLY 2, Range 67 and Wide 17 — each with its own colour curve, grain, light-leak
  behaviour, vignette and frame.
- **FaceCam watermark** — every photo gets a caption band appended *below* the image
  (the photo itself is never covered), with the **FaceCam** wordmark on the left and the
  camera name on the right. Toggle it off in Settings.
- **Realistic film pipeline** — a proper per-channel film tone curve (lift / gamma / gain +
  warmth + saturation), halation (bright-pass glow bleeding around highlights), luminance
  grain, chroma noise, light leaks, vignette, dust and scratches, frames and a date stamp.
- **PRO mode (Blackmagic-Camera-inspired)** — rule-of-thirds grid, level indicator, live
  luminance histogram, focus peaking, zebra and false colour, all computed from the real
  preview frames, plus manual ISO / shutter / white-balance / focus sliders applied to the
  actual camera where the browser exposes them (and clearly marked where it does not).
- **Randomised analog effects** applied after every shot: colour curve, film grain,
  dust specks and scratches, light leaks, vignette, sharpening/softness, frames, and an
  optional burned-in date stamp (e.g. `'26 10 01`).
- **Camera Shop** — browse and unlock individual cameras.
- **Double exposure** — take two shots and merge them.
- **Film development wait** — instant cameras show a "developing" animation before the
  photo appears.
- **PRO unlock** — every camera (including future ones), photo import from the gallery,
  no developing wait, and no ads.
- **Capture controls** — front/back switch, flash (device torch where available, otherwise a
  screen flash), self-timer, shutter, and spacebar as a shortcut.
- **Before / after** — on the result screen, tap *Original* to see the untouched capture
  next to the film version.
- **Gallery & sharing** — grouped in-app gallery, save to device, and the native share
  sheet (web: Web Share API with a download fallback).
- **Settings** — date stamp, border, shutter sound, default camera, restore purchases,
  privacy policy.

---

## Run the web preview

### Just want to look at it?

Open [`FaceCam-preview.html`](FaceCam-preview.html) — a single self-contained file with all
the HTML, CSS and JavaScript inlined. Double-click it and it runs: no server, no build step,
no dependencies. Without a webcam it starts in demo-scene mode, so every screen is still
reachable.

### Or serve the folder (recommended — this is what enables the real camera)

No build step, no dependencies:

```bash
cd web
python3 -m http.server 8000
# then open http://localhost:8000
```

A camera needs a secure context, so serve over `localhost` (or HTTPS) rather than opening
the file directly.

**No camera, or permission denied?** The viewfinder offers *Demo scene try karein* — a
built-in scene that runs through the exact same shutter → film pipeline → save → gallery
path, so you can try the whole app without a webcam. PRO users can also import a photo.

Screenshots: [`docs/screenshot-onboarding.jpg`](docs/screenshot-onboarding.jpg),
[`docs/screenshot-shop.jpg`](docs/screenshot-shop.jpg),
[`docs/screenshot-result.jpg`](docs/screenshot-result.jpg),
[`docs/screenshot-pro.jpg`](docs/screenshot-pro.jpg).

## Build the Android app

```bash
cd android
# open the folder in Android Studio, or:
gradle wrapper --gradle-version 8.7   # regenerate the missing wrapper jar
./gradlew assembleDebug
```

See [`android/ANDROID_SETUP.md`](android/ANDROID_SETUP.md) for permissions, where to paste
your real AdMob and Play Billing IDs, and how to produce a signed AAB for the Play Store.

---

## Where the data lives

| | Web build | Android build |
|---|---|---|
| Settings, owned cameras, PRO | `localStorage` (`facecam.*` keys) | `SharedPreferences` |
| Photos | `IndexedDB` (`facecam` database), with an in-memory write-through copy so the gallery is always instant | `MediaStore` + app-private folder |
| Camera presets | `web/js/cameras.js` | `android/app/src/main/assets/cameras/*.json` |

Nothing leaves the device.

---

## Publishing notes

The Android project ships with **placeholder** AdMob and Play Billing IDs — they are
clearly marked and must be replaced with your own before release. A Google Play developer
account costs a one-time US$25, and new personal accounts must run a closed test before
going to production.

## Open source

Built on:

- [`wysaid/android-gpuimage-plus`](https://github.com/wysaid/android-gpuimage-plus) (MIT) —
  optional GPU filter engine for the Android build. See `android/CREDITS.md`.
- [`wasabeef/android-gpuimage`](https://github.com/wasabeef/android-gpuimage) — the
  OpenGL filter approach that inspired the Android pipeline.

The web preview uses no third-party code at all: the whole film pipeline is plain
`<canvas>`, and PRO mode's scopes are computed by hand.

## License

MIT — see [LICENSE](LICENSE).
