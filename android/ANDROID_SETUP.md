# FaceCam - Android Setup Guide

FaceCam is a **100% offline, completely free** camera app (Kotlin + Jetpack
Compose). It ships **three** camera families plus a full video mode:

- **Vintage** - 28 film-camera simulations with procedural grain, light leaks,
  vignette, dust, film frames, date stamps and the FaceCam branding band,
  including a decade series (50s / 60s / 70s / 80s / 90s) and a set of character
  cameras (Flash, Night, Cinematic, Light Leak, Dreamy, Old Digital, VHS,
  Kodak Warm, Cold Blue, Faded).
- **B&W** - 5 black-and-white cameras. Their colour matrices genuinely
  desaturate to monochrome (equal R/G/B luminance weights), and they run the
  same analog pipeline as the vintage cameras. The warm one adds a sepia tint.
- **Beauty** - 10 clean, iPhone-like cameras that enhance a photo (exposure,
  contrast, saturation, warm-neutral white balance, edge-aware skin smoothing,
  sharpening and a soft glow) with **no** film character at all. On-device ML
  face detection steers the skin smoothing onto the face.
- **Video** - record an MP4 (with audio), apply the selected look frame by frame
  after the fact, burn in captions, and export in slow motion (0.5x / 0.25x).
- **Subtitles** - a typed caption burned onto the clip, a sidecar `.srt`, and an
  optional **live captions** mode using Android's on-device SpeechRecognizer.
- **Intro** - a procedural 3D animated intro (rotating dotted globe, shutter
  flash, wordmark) drawn with Compose Canvas only.

There is **no monetization of any kind**: no Google Play Billing, no AdMob, no
in-app purchases, no paywall, no PRO membership and no ads. Every camera is
unlocked for everyone from first launch. The app makes **no network calls** and
declares no `INTERNET` permission.

This guide takes you from the raw project tree to a signed AAB on Google Play.

The project is a **plain Gradle project** (no version catalog). All versions are
declared inline in `build.gradle.kts` / `app/build.gradle.kts`:

| Component        | Version    |
|------------------|------------|
| Android Gradle Plugin | 8.5.2 |
| Kotlin           | 2.0.20     |
| Gradle           | 8.7        |
| compileSdk / targetSdk | 34   |
| minSdk           | 24         |
| CameraX          | 1.3.4      |
| CameraX video (`camera-video`) | 1.3.4 |
| ML Kit face detection (`com.google.mlkit:face-detection`) | 16.1.7 |
| Compose BOM      | 2024.09.02 |
| Coil             | 2.7.0      |
| ExifInterface    | 1.3.7      |
| gpuimage-plus (optional, MIT) | 3.2.0-min |

---

## 1. Open the project in Android Studio

1. Install **Android Studio** (Ladybug or newer recommended; it ships JDK 17).
2. **File > Open** and select the project **root** folder (the one containing
   `settings.gradle.kts`). Do **not** open the `app` folder on its own.
3. Let Gradle sync. On first sync Android Studio will download the Gradle 8.7
   distribution and all dependencies.
4. If you are asked to trust the project / upgrade the Gradle wrapper, decline
   the wrapper upgrade and keep Gradle 8.7 to match the pinned AGP.

### Command line

```bash
# Debug build
./gradlew assembleDebug

# Install on a connected device
./gradlew installDebug
```

> On Windows use `gradlew.bat` instead of `./gradlew`.

---

## 2. Regenerating the missing `gradle-wrapper.jar` (binary)

This archive intentionally ships **only the text files** of the Gradle wrapper:
`gradle/wrapper/gradle-wrapper.properties`. The binary `gradle-wrapper.jar` (and
the `gradlew` / `gradlew.bat` scripts) are **not** included, because a compiled
`.jar` cannot be represented as plain text and shipping one is not appropriate.

You must regenerate the wrapper before `./gradlew` will work. There are two ways:

### Option A - use a locally installed Gradle (recommended)

If you have any Gradle 8.x installed:

```bash
cd facecam-android
gradle wrapper --gradle-version 8.7 --distribution-type bin
```

This creates `gradlew`, `gradlew.bat` and `gradle/wrapper/gradle-wrapper.jar`,
all matching `gradle-wrapper.properties`.

### Option B - copy the wrapper from another project

Any modern Android/Gradle project already contains these three files. Copy them
into this project's root / `gradle/wrapper/`:

```
gradlew
gradlew.bat
gradle/wrapper/gradle-wrapper.jar
```

### Option C - let Android Studio do it

Opening the project in Android Studio and running a Gradle sync will offer to
generate the wrapper automatically. Accept it, then keep Gradle 8.7.

After regenerating, verify:

```bash
./gradlew --version
```

---

## 3. Permissions - the app is free and offline

Because FaceCam has **no ads and no billing**, there are **no AdMob ids, no
billing product ids and no API keys anywhere in the project**. The manifest
declares **no `INTERNET` permission**, and no app logic makes a network call.
There is nothing to configure here before publishing.

The permissions the app does request, all of them local:

| Permission | Why |
|------------|-----|
| `CAMERA` | Capture. |
| `RECORD_AUDIO` | Audio in video recordings, and on-device live captions (SpeechRecognizer). |
| `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` | Publishing saved photos/videos to MediaStore on API 33+. |
| `WRITE_EXTERNAL_STORAGE` (maxSdk 28) / `READ_EXTERNAL_STORAGE` (maxSdk 32) | Legacy MediaStore on older devices. |
| `POST_NOTIFICATIONS` | The "developing" progress notice. |

The manifest also declares a `<queries>` entry for
`android.speech.RecognitionService` so the on-device recognizer is visible on
API 30+ (no network permission is implied or required).

The only outbound link in the app is the optional **Privacy policy** button in
Settings, which opens a URL in the user's browser. Replace the placeholder
`https://example.com/facecam/privacy` in
`app/src/main/java/com/facecam/app/ui/screens/SettingsScreen.kt` with your hosted
policy URL (Play requires a privacy policy URL for every listing).

---

## 4. Camera presets

All cameras live as JSON in `app/src/main/assets/cameras/`. Each file has an
`id`, `name`, `tag`, `description`, a **`group`** (`"vintage"`, `"bw"` or
`"beauty"`) and a 20-float `colorMatrix`. The rest of the fields depend on the
family:

### Vintage cameras (`"group": "vintage"`) - 28 presets

`nomo_135_b`, `nomo_135_m`, `nomo_135_p`, `toy_f`, `toy_k`, `roma`, `fr2`,
`film_2007`, `eats`, `ins_2`, `swirly_2`, `range_67`, `wide_17`, the decade
series `vintage_50s`, `vintage_60s`, `vintage_70s`, `vintage_80s` and
`vintage_90s`, and the character cameras `flash_cam`, `night_film`,
`cinematic`, `light_leak`, `dreamy`, `old_digital`, `vhs`, `kodak_warm`,
`cold_blue`, `faded`.

### Black & white cameras (`"group": "bw"`) - 5 presets

`bw_classic`, `bw_high`, `bw_warm`, `bw_cool`, `bw_fade`.

Their colour matrices genuinely desaturate to grey: every output channel is the
same luminance-weighted mix of R/G/B (weights `0.2126 / 0.7152 / 0.0722`), so
colour information is fully removed. `bw_warm` uses the classic sepia matrix to
add a warm brown tint. B&W cameras use the **same** analog tuning fields as the
vintage cameras (`grain`, `leak`, `vignette`, `frame`, `dateStamp`, `instant`,
`overlay`) and run the same pipeline.

### Beauty cameras (`"group": "beauty"`) - 10 presets

`beauty_natural`, `beauty_bright`, `beauty_warm`, `beauty_portrait`, plus
`beauty_soft`, `beauty_glow`, `beauty_radiance`, `beauty_matte`, `beauty_clean`
and `beauty_rich`.

Each carries a `beauty` object with the tuning consumed by
`film/BeautyEffects.kt`:

| Field        | Range | Meaning                                        |
|--------------|-------|------------------------------------------------|
| `exposure`   | stops | exposure lift (e.g. 0.08 = +0.08 EV)           |
| `contrast`   | 0..1  | strength of the gentle S-curve                 |
| `saturation` | 0..1  | colour boost relative to natural               |
| `warmth`     | 0..1  | subtle warm-neutral white-balance shift        |
| `smooth`     | 0..1  | edge-aware skin-smoothing strength             |
| `sharpen`    | 0..1  | unsharp-mask sharpening strength               |
| `glow`       | 0..1  | soft highlight bloom strength                  |

To add a camera, drop in a new JSON with a unique `id` and the right `group`.
Nothing needs to be registered anywhere else. See `assets/cameras/_OVERLAYS.md`
for the overlay/texture notes.

---

## 5. The three rendering pipelines

The chosen camera's `group` selects the pipeline, wired in
`ui/FaceCamViewModel.kt`:

| Group     | Pipeline | Output |
|-----------|----------|--------|
| `vintage` | `film/AnalogEffects.kt` -> frame -> date stamp -> `film/BrandingRenderer.kt` | film look with grain, leaks, vignette, dust, frame, date stamp and the branding band |
| `bw`      | same as `vintage` (`film/AnalogEffects.kt` + frame + date stamp + branding) | black-and-white film look - the matrix removes all colour |
| `beauty`  | `film/BeautyEffects.kt` | clean, well-exposed phone photo - **no** grain, leaks, vignette, frame, date stamp or branding |

### BeautyEffects pipeline

`film/BeautyEffects.kt` runs, in order:

1. the preset's colour matrix (base grade),
2. a subtle warm-neutral white balance,
3. an exposure lift,
4. a gentle S-curve contrast plus natural saturation,
5. a highlight rolloff (soft shoulder so highlights never clip harshly),
6. **edge-aware skin smoothing** - a blurred copy of the image is blended back
   per pixel, weighted by a local high-frequency detail map, so flat areas
   (skin) smooth while edges and fine texture stay sharp,
7. a soft highlight glow (bloom),
8. unsharp-mask sharpening.

Everything is drawn with `android.graphics` only - no assets, no network, no
native code.

### FaceCam branding band

Every **vintage** photo gets a caption band appended **below** the photograph
(the canvas grows taller; the image itself is never covered). Implementation:
`film/BrandingRenderer.kt`, called at the very end of the vintage pipeline. The
band is about **7-8%** of the photo height, filled with the camera's frame
colour (cream / off-white), with the wordmark **FaceCam** on the left and the
camera's tag on the right.

A boolean preference `branding` (default **true**) lives in
`data/SettingsStore.kt`; toggle it from **Settings -> "FaceCam watermark"**.
Beauty photos never receive the band.

---

## 6. Manual camera mode (Blackmagic-Camera style)

The professional shooting HUD is **opt-in** and never changes the default simple
mode. Tap the **MANUAL** button in the viewfinder's top bar to reveal it. (This
mode was previously labelled "PRO"; it is **not** a paid feature - it is simply
a manual shooting mode, and every camera is free.)

New code lives under `app/src/main/java/com/facecam/app/camera/pro/`:

| File | Purpose |
|------|---------|
| `ProState.kt` | Immutable snapshot of the HUD (overlays + manual values). |
| `ProCapabilities.kt` | What the device actually supports (from `CameraCharacteristics`). |
| `ProCameraController.kt` | Real manual control via Camera2Interop / `Camera2CameraControl`. |
| `LevelSensor.kt` | Horizontal level via the accelerometer (`SensorManager`). |
| `HistogramAnalyzer.kt` | 64-bin luminance histogram from preview frames. |
| `ProFrameProcessor.kt` | `ImageAnalysis.Analyzer` producing the histogram + live overlays. |
| `FocusPeaking.kt` | Sobel focus peaking overlay. |
| `ZebraOverlay.kt` | Zebra stripes over blown highlights. |
| `FalseColorRenderer.kt` | Luminance -> false-colour ramp. |
| `ProHudOverlay.kt` | The Compose HUD + manual-control panel. |

The viewfinder additions are in `ui/screens/ViewfinderScreen.kt`.

### HUD features

- Rule-of-thirds grid overlay and a horizontal level indicator (accelerometer).
- Live histogram, top-right, computed from preview frames.
- Focus peaking and zebra stripes, both toggleable.
- False-colour mode (luminance mapped to a colour ramp), toggleable.
- A readout strip showing ISO / shutter / WB / focus.

### Real manual controls

Where the device supports them (queried from `CameraCharacteristics`), the
sliders drive genuine capture requests through the Camera2 interop layer:

| Control | CaptureRequest key |
|---------|--------------------|
| ISO | `SENSOR_SENSITIVITY` (AE off) |
| Shutter / exposure time | `SENSOR_EXPOSURE_TIME` (shown as shutter angle) |
| White balance | `COLOR_CORRECTION_GAINS` (AWB off) |
| Manual focus | `LENS_FOCUS_DISTANCE` (AF off) |

Any control the device does **not** support is greyed out automatically (its
slider and switch are disabled). Nothing here makes a network call.

---

## 7. Optional GPU filter path (android-gpuimage-plus, MIT)

FaceCam's default pipelines are pure CPU (`android.graphics`). They can
*optionally* use the MIT-licensed
[wysaid/android-gpuimage-plus](https://github.com/wysaid/android-gpuimage-plus)
library for an OpenGL-accelerated path over the live preview and stills.

> Note on coordinates: the GitHub project is `wysaid/android-gpuimage-plus`, but
> the **published Maven artifact id is `gpuimage-plus`** under group `org.wysaid`
> (hosted at `https://maven.wysaid.org/`). FaceCam uses the image-only `-min`
> variant, which needs no FFmpeg: `org.wysaid:gpuimage-plus:3.2.0-min`.

### Enabling it

The dependency is behind the Gradle property `facecam.gpuFilters` (default
`false`, set in `gradle.properties`). When it is off, the dependency is **not
resolved at all** and the app builds and runs with no GPU library present.

```bash
# gradle.properties
facecam.gpuFilters=true

# or, per build
./gradlew assembleDebug -Pfacecam.gpuFilters=true
```

When enabled:

1. `app/build.gradle.kts` adds the `org.wysaid:gpuimage-plus:3.2.0-min`
   dependency and sets `BuildConfig.GPU_FILTERS_ENABLED = true`.
2. `settings.gradle.kts` adds the `https://maven.wysaid.org/` repository.
3. `com.facecam.app.gpu.GpuFilterEngine` starts using the library through
   reflection (so the app still builds when the library is absent) and falls
   back to the CPU result whenever the GPU path is unavailable.

The library is credited, with its MIT text, in `CREDITS.md`.

---

## 7A. Video recording

A Photo / Video switch sits in the viewfinder. Video is recorded as a **plain
MP4** - the camera look is deliberately *not* baked into the live pipeline.
Instead, the clip is post-processed afterwards, frame by frame, with the same
effect code the stills use. This keeps the live preview cheap and avoids any
EGL / OpenGL surface work.

New code lives under `app/src/main/java/com/facecam/app/video/`:

| File | Purpose |
|------|---------|
| `VideoRecorder.kt` | Wraps CameraX `Recorder` / `VideoCapture`; records an MP4 into the app cache, with start / stop and an elapsed-time callback. |
| `VideoEffectProcessor.kt` | Decodes the MP4 with `MediaMetadataRetriever`, applies the selected look (via `AnalogEffects` / `BeautyEffects`), burns the caption, re-encodes to a new MP4. Returns `Result(file, lookBaked, note)` and **falls back to copying the original** if processing fails. |
| `SlowMotionExporter.kt` | Re-times the MP4 (see 7B). |
| `SubtitleOverlay.kt` | Caption state + `.srt` writer + burn-in (see 7C). |
| `LiveCaptionController.kt` | On-device SpeechRecognizer wrapper (see 7C). |
| `Mp4FrameEncoder.kt` | A small, EGL-free H.264 encoder (software ARGB -> I420 -> `MediaCodec` -> `MediaMuxer`). |

The finished clip is saved to the in-app gallery (`GalleryRepository.saveVideo`)
and published to MediaStore (`MediaStoreSaver.saveVideo`, Movies/FaceCam).

> Note on processing: frames are pulled with `MediaMetadataRetriever` and scaled
to at most 1280 px on the long edge, then encoded to H.264. This is CPU-only and
suits short clips; it is intentionally simple rather than real-time.

---

## 7B. Slow motion

`video/SlowMotionExporter.kt` re-times a clip to **0.5x** or **0.25x** by
re-encoding it with a **lower presentation-timestamp rate** - each source frame
is written with a larger PTS step, so the same frames play over a longer
time. **1x** is a straight copy of the original.

The viewfinder offers a **1x / 0.5x / 0.25x** speed control. It also queries
`CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES` (via
`Camera2CameraInfo`) for the device's highest supported fps and shows whether
**high-fps capture** is available.

---

## 7C. Subtitles and live captions

`video/SubtitleOverlay.kt` holds:

- a single **typed caption** line (burned into the video during post-processing),
- timed **cues** (typed + live), which can be written out as a sidecar `.srt`,
- incoming **live caption** lines from the on-device recognizer.

The viewfinder has a caption text field and a **"CC Live"** toggle. The toggle
starts `video/LiveCaptionController.kt`, which drives Android's
`SpeechRecognizer` with `EXTRA_PREFER_OFFLINE = true`. There is no network
permission, so captioning is strictly on-device. When no recognizer is present
the toggle simply reports that captioning is unavailable.

When a clip is finished, a `.srt` sidecar is written next to the saved video if
there are any cues.

---

## 7D. On-device ML for Beauty

The Beauty pipeline can now steer its skin smoothing with a **feathered face
mask**. Detection uses ML Kit's **bundled** face model
(`com.google.mlkit:face-detection`), which ships inside the APK - no download,
no network.

`app/src/main/java/com/facecam/app/ml/`:

| File | Purpose |
|------|---------|
| `FaceMaskProvider.kt` | Detects faces in a `Bitmap` and builds a feathered alpha mask over the face region (eyes and mouth are carved back out). Also exposes the union face bounds. |
| `FaceGuideAnalyzer.kt` | A throttled `ImageAnalysis.Analyzer` that publishes normalised face bounds for the framing guide. |

`film/BeautyEffects.kt` gained an optional `faceMask` on its `Options`. Where the
mask is opaque the smoothing runs at full strength; outside it a reduced amount
is used, so the strongest effect lands on skin while eyes, brows and lips stay
sharp (the existing edge-aware weighting protects them). **If no face is found
the pipeline falls back to its original global behaviour.**

The viewfinder also offers a **"centre on face"** framing guide that draws a box
around the detected face using the bounds hint.

---

## 7E. The modern UI

The UI was redesigned to feel like a current-generation social camera app:

- **Gradient accents** (violet -> magenta -> coral) on buttons, chips and the
  bottom bar (`ui/theme/Theme.kt`).
- **Glassmorphic rounded cards** - see `Modifier.glass(...)` in
  `ui/components/Glass.kt`.
- A **bottom navigation bar** - Camera / Gallery / Modes
  (`ui/components/BottomNav.kt`), wired in `ui/navigation/FaceCamNav.kt`.
- **Large rounded mode chips** for Photo / Video and Vintage / B&W / Beauty
  (`ModeChip` and `SegmentedTabs` in `ui/components/Glass.kt` and
  `ui/components/Common.kt`).
- A **bigger, friendlier shutter** area with a gradient ring that becomes a
  record button in video mode.
- **Smoother animated transitions** (`AnimatedVisibility` for the video panel,
  the framing guide, the recording readout and the manual HUD).
- A **refreshed Settings** screen built from titled glass groups.

The screens live in `ui/screens/` (Viewfinder, Gallery, Cameras, Settings) and
the shared atoms in `ui/components/`.

---

## 7F. The 3D animated intro

On launch the app plays a short, fully procedural intro - no external 3D
library, no assets, no network. It lives in
`ui/screens/IntroScreen.kt` and runs for about **3 seconds**, and a tap anywhere
skips it straight into the app. Three acts:

1. **Globe (0.0s - 1.2s)** - a rotating 3D sphere drawn as a dotted wireframe.
   Latitude and longitude rings are generated in 3D, spun around the vertical
   axis, tilted slightly around X so the poles are visible, and projected to 2D
   with a perspective divide (`scale = focal / (focal + z)`). Dot size and
   opacity are shaded by depth, so the far side reads as behind the near side.
2. **Shutter (1.2s - 1.9s)** - a camera-shutter flash: six aperture blades swing
   open like an iris while a bright glow blooms through, capped by a
   full-screen white flash that rises and falls.
3. **Wordmark (1.9s - 3.0s)** - the **FaceCam** wordmark fades and scales in
   with the tagline underneath.

Everything is drawn with Compose `Canvas` and `androidx.compose.animation`
only. The intro is the navigation graph's `startDestination` (`Routes.INTRO` in
`ui/navigation/FaceCamNav.kt`); when it finishes (or is skipped) it routes to
onboarding on first launch, or straight to the viewfinder afterwards.

---

## 7G. The NOMO-CAM-style viewfinder

The viewfinder (`ui/screens/ViewfinderScreen.kt`) was refined to feel like
NOMO CAM:

- a **full-bleed preview** with the live tint + vignette overlay;
- a **prominent horizontal strip of camera models** across the bottom
  (`ui/components/CameraStrip.kt`), each with a realistic-looking thumbnail and
  the camera name underneath, the active one highlighted with a bright ring;
- a **big round shutter button** centred below the strip, flanked by the
  last-shot thumbnail (left) and the lens-flip button (right);
- a **minimal top bar** (settings on the left; framing guide, MANUAL, cameras
  and double-exposure on the right);
- a **last-shot thumbnail** in the bottom-left corner (tap it to open the
  gallery);
- **family pills** (Vintage / B&W / Beauty) that switch which cameras the strip
  shows.

The strip thumbnails are drawn procedurally with Compose `Canvas` - no texture
assets and no network. The little scene is tinted by running a neutral reference
colour through the camera's own 20-float colour matrix, so each thumbnail
genuinely previews its grade (monochrome cameras read grey, the 70s camera reads
orange, the 60s camera reads cool). Analog cameras (vintage / B&W) additionally
get grain and a vignette in the thumbnail.

---

## 8. Building a signed AAB for Google Play

### 8a. Create an upload keystore (once)

```bash
keytool -genkeypair -v \
  -keystore facecam-upload.jks \
  -alias facecam \
  -keyalg RSA -keysize 2048 -validity 10000
```

Keep this keystore and its passwords safe - you cannot re-sign updates without
it (or Play App Signing).

### 8b. Configure signing

Create `keystore.properties` in the project root (and add it to `.gitignore`):

```properties
storeFile=../facecam-upload.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=facecam
keyPassword=YOUR_KEY_PASSWORD
```

Then add signing config to `app/build.gradle.kts`:

```kotlin
import java.util.Properties

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    signingConfigs {
        create("release") {
            storeFile = file(keystoreProps.getProperty("storeFile") ?: "facecam-upload.jks")
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

### 8c. Build the bundle

```bash
./gradlew bundleRelease
```

The signed AAB is written to:

```
app/build/outputs/bundle/release/app-release.aab
```

Upload that file to the Play Console. For a quick local check you can also build
an APK:

```bash
./gradlew assembleRelease
# app/build/outputs/apk/release/app-release.apk
```

### 8d. Before you ship - checklist

- [ ] Set a real privacy policy URL (Settings screen).
- [ ] Bumped `versionCode` / `versionName` in `app/build.gradle.kts`.
- [ ] Ran `./gradlew bundleRelease` with a release signing config.
- [ ] Tested on a physical device (emulators have limited camera support).
- [ ] Confirmed the app declares no `INTERNET` permission.

---

## 9. Notes and assumptions

- **Free by design.** There is no billing, no ads, no paywall and no PRO
  membership. All 43 cameras (28 vintage + 5 B&W + 10 beauty) are unlocked for
  everyone.
- **Intro by design.** The 3D intro (`ui/screens/IntroScreen.kt`) is pure Compose
  Canvas - no external 3D library, no assets and no network - and can be skipped
  with a tap.
- **Offline by design.** App logic makes no network calls and the manifest
  declares no `INTERNET` permission. The optional GPU library and the manual
  mode are 100% on-device.
- **Photos** are written to the app's private `files/gallery/` folder first, then
  optionally published to the device MediaStore (Pictures/FaceCam) on save.
- **Videos** are recorded to the app cache, post-processed (look + caption +
  slow motion), then copied into `files/gallery/<camera>/` and published to
  MediaStore (Movies/FaceCam). Any `.srt` sidecar is written next to the clip.
- **The camera look is never baked into the live video pipeline.** Recording is
  plain MP4; the look is applied afterwards, frame by frame, by
  `video/VideoEffectProcessor.kt` using the existing `AnalogEffects` /
  `BeautyEffects` code. No EGL / OpenGL surface work is used anywhere.
- **On-device ML** uses the ML Kit face-detection model **bundled in the APK**
  (no download). Face detection only ever runs locally.
- **Live captions** use Android's on-device `SpeechRecognizer`
  (`EXTRA_PREFER_OFFLINE`). No network permission exists, so captioning is
  strictly local. If no recognizer is present the toggle reports it is
  unavailable and everything else keeps working.
- **Settings** are stored in `SharedPreferences`.
- **Beauty photos** are deliberately clean: no grain, leaks, vignette, frame,
  date stamp or branding band. The date-stamp / border / watermark settings only
  affect the analog cameras (vintage **and** B&W).
- **Branding band** colour is derived from the camera's frame style (cream /
  off-white by default). The band is appended after the frame and date stamp.
- **Manual mode** is opt-in; the simple viewfinder is unchanged when it is off.
  Unsupported manual controls are greyed out based on `CameraCharacteristics`.
- **Third-party licences** are listed in `CREDITS.md`.
- **No gradle-wrapper.jar** ships in the archive - regenerate it as in section 2.
- The project is provided as complete, syntactically valid source; it has **not**
  been compiled here (no Android SDK in the generation environment). Build it in
  Android Studio or via the Gradle wrapper.
