# FaceCam

**Purani film wali feel — aur ek clean beauty shot. Dono ek app mein.**

FaceCam is an offline camera app with two personalities. Pick a **Vintage** camera and it
turns an ordinary phone photo into something that looks like it came off an old 35mm roll, a
toy camera or an instant Polaroid. Pick a **Beauty** look and it produces a clean, natural,
brightly-lit photo — the kind of result you expect straight out of a modern phone camera.

There are no sliders and no manual editing. Every camera does its own thing, and the vintage
looks are randomised, so no two photos come out the same.

It is **completely free**: every camera is unlocked, there is no paywall, no subscription and
no ads. There is no backend, no login and no network call anywhere in the app.

This repository contains **two complete implementations** of the same app:

| Path | What it is |
|---|---|
| [`FaceCam-preview.html`](FaceCam-preview.html) | The whole web preview as one standalone file — double-click to run. |
| [`web/`](web/) | The same app as a normal HTML / CSS / JavaScript project. Uses `getUserMedia` for the viewfinder, `<canvas>` for the image pipelines, and browser storage. |
| [`android/`](android/) | The native Android app: Kotlin + Jetpack Compose + CameraX, 100% on-device. |
| [`docs/`](docs/) | The original feature spec and screenshots. |

---

## Cameras — 46 looks in three families

**Vintage — 31 looks.** The decades: **50s Kodachrome, 60s Ektachrome, 70s Faded, 80s Punch,
90s Disposable**. The film simulations: FaceCam 135 B / M / P, TOY F / K, ROMA, FR2,
2007, EATS, INS 2 (instant), SWIRLY 2, Range 67 and Wide 17. The character cameras:
**Flash Camera, Night Film, Cinematic Film, Light Leak Film, Dreamy Film, Old Digital
Camera, VHS / Retro, Kodak Warm, Cold Blue Film and Faded Film**. And three movie stocks
that shine in video: **8mm Film, Super 8 and MiniDV**. Each has its own colour curve, grain,
light-leak behaviour, vignette and frame.

**Black & White — 5 looks.** Classic silver-grey, high-contrast deep blacks, a warm
sepia tone, a cool blue-toned mono and a faded low-contrast mono. These genuinely
desaturate, and keep the silver grain and vignette.

**Beauty — 10 looks.** Natural, Bright, Warm, Portrait, Soft, Glow, Radiance, Matte,
Clean and Rich.
These apply an iPhone-like enhance instead of a film look: exposure lift, a gentle contrast
curve, natural saturation, highlight rolloff, edge-aware skin smoothing (flat skin softens
while edges stay crisp), a soft highlight glow, and unsharp-mask sharpening. No grain, no
leaks, no vignette, no frame — a clean photo.

---

## Features

- **Photo editor** — a 12-slider editor on any captured photo (or a gallery shot): exposure,
  brightness, contrast, saturation, temperature, tint, fade, grain, vignette, blur, sharpen
  and light-leak, with live preview, reset and save.
- **Capture controls** — front/back, flash OFF / ON / AUTO, 3s / 10s self-timer, 1x / 2x / 3x
  zoom, exposure compensation, and tap-to-focus with a focus ring.
- **Timestamp options** — retro orange date, date-only (YYYY/MM/DD or DD/MM/YYYY), date + time,
  or your own custom text.
- **Double exposure** — two shots merged with adjustable opacity and a blend mode
  (screen / lighten / overlay / soft light).
- **Export** — save or share as JPG or PNG, and export the original or the edited frame.
- **Film grain** — per-camera grain plus a Fine / Medium / Heavy global setting.
- **3D intro animation** — the app opens on a spinning dotted 3D globe, a camera-shutter
  flash, then the FaceCam wordmark. Tap to skip.
- **Look intensity** — one slider scales the whole look (grain, leak, vignette, colour,
  smoothing) from subtle to full-on, for both photos and video.
- **Photo and Video modes** — flip between a still camera and a video camera with one chip.
- **Video recording with the look baked in** — the same camera presets run live on video,
  with the effects *animated* frame by frame: moving grain, drifting dust, random scratches,
  flicker, animated light leaks, film burn, frame jitter, focus breathing, VHS scanlines and
  tracking, chromatic aberration, digital glitches and a burned-in retro timestamp. Records
  the microphone, and bakes your caption/subtitle onto every frame.
- **Video capture controls** — resolution (auto / 720p / 1080p / 4K) and frame rate
  (24 / 30 / 60), a front-camera mirror, pause / resume mid-recording, and optional
  vintage-mic / VHS / cassette audio treatment (original audio stays clean by default).
- **Photo-consistent bake** — optionally re-render a clip through the exact photo pipeline so
  a video matches the still photo of the same camera.
- **Slow motion** — record, then bake a 0.5x or 0.25x slow-motion version, or change playback
  speed while viewing. The slow-mo export is a real re-timed file, not just a preview.
- **Subtitles** — type a caption line and it is burned onto the video; optional live
  captions use the browser's on-device speech recognition where available.
- **Two families, one app** — a Vintage / Beauty pill switch with a horizontal camera strip.
- **Randomised analog effects** on vintage shots: colour curve, film grain, dust and
  scratches, light leaks, vignette, frames, and an optional burned-in date stamp.
- **Realistic film pipeline** — a proper per-channel tone curve (lift / gamma / gain + warmth
  + saturation), halation (a warm glow bleeding around highlights), luminance grain and
  chroma noise.
- **FaceCam watermark** on vintage photos — a caption band appended *below* the image (the
  photo itself is never covered), with the **FaceCam** wordmark and the camera name. Beauty
  shots stay clean with no band. Toggle it off in Settings.
- **Manual camera mode** — rule-of-thirds grid, level indicator, live luminance histogram,
  focus peaking, zebra and false colour, all computed from the real preview frames, plus
  manual ISO / shutter / white-balance / focus sliders applied to the actual camera where
  the browser exposes them.
- **Double exposure** — take two shots and merge them.
- **Film development wait** — instant cameras show a "developing" animation.
- **Capture controls** — front/back switch, flash (device torch where available, otherwise a
  screen flash), self-timer, shutter, and spacebar as a shortcut.
- **Before / after** — on the result screen, tap *Original* to see the untouched capture.
- **Gallery & sharing** — grouped in-app gallery, save to device, and the native share sheet.
- **Settings** — date stamp, border, shutter sound, FaceCam watermark, default camera, reset.

---

## Run the web preview

### Just want to look at it?

Open [`FaceCam-preview.html`](FaceCam-preview.html) — a single self-contained file with all
the HTML, CSS and JavaScript inlined. Double-click it and it runs: no server, no build step,
no dependencies. Without a webcam it starts in demo-scene mode, so every screen is still
reachable.

### Or serve the folder (this is what enables the real camera)

No build step, no dependencies:

```bash
cd web
python3 -m http.server 8000
# then open http://localhost:8000
```

A camera needs a secure context, so serve over `localhost` (or HTTPS) rather than opening the
file directly.

**No camera, or permission denied?** The viewfinder offers *Demo scene try karein* — a
built-in scene that runs through the exact same shutter → pipeline → save → gallery path.

Screenshots: [`docs/screenshot-onboarding.jpg`](docs/screenshot-onboarding.jpg),
[`docs/screenshot-cameras.jpg`](docs/screenshot-cameras.jpg),
[`docs/screenshot-beauty.jpg`](docs/screenshot-beauty.jpg),
[`docs/screenshot-video.jpg`](docs/screenshot-video.jpg),
[`docs/screenshot-result.jpg`](docs/screenshot-result.jpg),
[`docs/screenshot-pro.jpg`](docs/screenshot-pro.jpg).

### Camera permissions

A camera (and, for video audio, a microphone) needs a **secure context**. `localhost` and
`https://` count; opening the file directly from disk does **not**, and the browser will
block the camera entirely. The app detects this and tells you so, with a link to the live
version.

## Build the Android app

```bash
cd android
# open the folder in Android Studio, or:
gradle wrapper --gradle-version 8.7   # regenerate the missing wrapper jar
./gradlew assembleDebug
```

See [`android/ANDROID_SETUP.md`](android/ANDROID_SETUP.md) for permissions and how to build a
signed AAB for the Play Store.

---

## Where the data lives

| | Web build | Android build |
|---|---|---|
| Settings | `localStorage` (`facecam.*` keys) | `SharedPreferences` |
| Photos | `IndexedDB`, with an in-memory write-through copy so the gallery is always instant | `MediaStore` + app-private folder |
| Cameras | `web/js/cameras.js` | `android/app/src/main/assets/cameras/*.json` |

Nothing leaves the device.

---

## App icon

The launcher icon is the FaceCam lens mark: a navy rounded tile with a camera lens, a
glowing blue/purple rim, a face-detection frame, a recording dot and the FACE CAM wordmark.

| File | What it is |
|---|---|
| [`docs/icon-512.png`](docs/icon-512.png) | 512×512 master (Play Store / store listing) |
| `android/app/src/main/res/mipmap-*/ic_launcher.png` | legacy square icon, all five densities |
| `android/app/src/main/res/mipmap-*/ic_launcher_round.png` | legacy round icon |
| `android/app/src/main/res/mipmap-*/ic_launcher_foreground.png` | adaptive foreground (transparent, mask-safe centred) |
| `android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | adaptive icon (navy background + foreground) |
| `android/app/src/main/res/drawable/ic_launcher_background.xml` | navy background |
| `web/icon-256.png` | web favicon / apple-touch-icon |
| `web/icon-128.png` | onboarding logo (also inlined into the single-file preview) |
| `tools/facecam_icon.png` | the square master artwork |

Android can't use SVG in `res/`, so the master is a raster and every density is generated
from it. Regenerate all of the above with `python3 tools/make_icons.py` (needs Pillow).

---

## Open source

Built on:

- [`wysaid/android-gpuimage-plus`](https://github.com/wysaid/android-gpuimage-plus) (MIT) —
  optional GPU filter engine for the Android build. See `android/CREDITS.md`.
- [`wasabeef/android-gpuimage`](https://github.com/wasabeef/android-gpuimage) — the OpenGL
  filter approach that inspired the Android pipeline.

The web preview uses no third-party code at all: the whole pipeline is plain `<canvas>`, and
the manual-mode scopes are computed by hand.

## License

MIT — see [LICENSE](LICENSE).
