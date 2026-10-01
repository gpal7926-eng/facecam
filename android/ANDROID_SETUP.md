# FaceCam - Android Setup Guide

FaceCam is a **100% offline, completely free** camera app (Kotlin + Jetpack
Compose). It ships two camera families:

- **Vintage** - 13 film-camera simulations with procedural grain, light leaks,
  vignette, dust, film frames, date stamps and the FaceCam branding band.
- **Beauty** - 4 clean, iPhone-like cameras that enhance a photo (exposure,
  contrast, saturation, warm-neutral white balance, edge-aware skin smoothing,
  sharpening and a soft glow) with **no** film character at all.

There is **no monetization of any kind**: no Google Play Billing, no AdMob, no
in-app purchases, no paywall, no PRO membership and no ads. Every camera is
unlocked for everyone from first launch.

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

## 3. No keys to paste - the app is free and offline

Because FaceCam has **no ads and no billing**, there are **no AdMob ids, no
billing product ids and no API keys anywhere in the project**. The manifest
declares no `INTERNET` permission, and no app logic makes a network call. There
is nothing to configure here before publishing.

The only outbound link in the app is the optional **Privacy policy** button in
Settings, which opens a URL in the user's browser. Replace the placeholder
`https://example.com/facecam/privacy` in
`app/src/main/java/com/facecam/app/ui/screens/SettingsScreen.kt` with your hosted
policy URL (Play requires a privacy policy URL for every listing).

---

## 4. Camera presets

All cameras live as JSON in `app/src/main/assets/cameras/`. Each file has an
`id`, `name`, `tag`, `description`, a **`group`** (`"vintage"` or `"beauty"`)
and a 20-float `colorMatrix`. The rest of the fields depend on the family:

### Vintage cameras (`"group": "vintage"`) - 13 presets

`nomo_135_b`, `nomo_135_m`, `nomo_135_p`, `toy_f`, `toy_k`, `roma`, `fr2`,
`film_2007`, `eats`, `ins_2`, `swirly_2`, `range_67`, `wide_17`.

They also carry the analog tuning consumed by `film/AnalogEffects.kt`:
`grain`, `leak`, `vignette`, `frame`, `dateStamp`, `instant` and `overlay`.

### Beauty cameras (`"group": "beauty"`) - 4 presets

`beauty_natural`, `beauty_bright`, `beauty_warm`, `beauty_portrait`.

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

## 5. The two rendering pipelines

The chosen camera's `group` selects the pipeline, wired in
`ui/FaceCamViewModel.kt`:

| Group     | Pipeline | Output |
|-----------|----------|--------|
| `vintage` | `film/AnalogEffects.kt` -> frame -> date stamp -> `film/BrandingRenderer.kt` | film look with grain, leaks, vignette, dust, frame, date stamp and the branding band |
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
  membership. All 17 cameras (13 vintage + 4 beauty) are unlocked for everyone.
- **Offline by design.** App logic makes no network calls and the manifest
  declares no `INTERNET` permission. The optional GPU library and the manual
  mode are 100% on-device.
- **Photos** are written to the app's private `files/gallery/` folder first, then
  optionally published to the device MediaStore (Pictures/FaceCam) on save.
- **Settings** are stored in `SharedPreferences`.
- **Beauty photos** are deliberately clean: no grain, leaks, vignette, frame,
  date stamp or branding band. The date-stamp / border / watermark settings only
  affect vintage cameras.
- **Branding band** colour is derived from the camera's frame style (cream /
  off-white by default). The band is appended after the frame and date stamp.
- **Manual mode** is opt-in; the simple viewfinder is unchanged when it is off.
  Unsupported manual controls are greyed out based on `CameraCharacteristics`.
- **Third-party licences** are listed in `CREDITS.md`.
- **No gradle-wrapper.jar** ships in the archive - regenerate it as in section 2.
- The project is provided as complete, syntactically valid source; it has **not**
  been compiled here (no Android SDK in the generation environment). Build it in
  Android Studio or via the Gradle wrapper.
