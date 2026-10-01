# FaceCam - Android Setup Guide

FaceCam is a 100% offline vintage film-camera app (Kotlin + Jetpack Compose).
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
| Play Billing     | 6.2.1      |
| AdMob (play-services-ads) | 22.6.0 |
| Coil             | 2.7.0      |
| ExifInterface    | 1.3.7      |

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

## 3. Where to paste your real IDs (placeholders ship in the code)

The project ships with **clearly marked placeholder** AdMob and Play Billing
IDs. Replace every placeholder below before publishing. **Never commit real
keys** - keep them in your own private config or inject them at build time.

### 3a. AdMob

| What | File | Placeholder |
|------|------|-------------|
| App ID | `app/src/main/AndroidManifest.xml` (`com.google.android.gms.ads.APPLICATION_ID`) | `ca-app-pub-0000000000000000~0000000000` |
| Banner unit | `app/src/main/java/com/facecam/app/ads/AdManager.kt` (`BANNER_UNIT_ID`) | `ca-app-pub-0000000000000000/0000000000` |
| Interstitial unit | `app/src/main/java/com/facecam/app/ads/AdManager.kt` (`INTERSTITIAL_UNIT_ID`) | `ca-app-pub-0000000000000000/1111111111` |

The same placeholder unit ids also appear in
`app/src/main/res/values/strings.xml` (`admob_banner_unit_id`,
`admob_interstitial_unit_id`) for reference.

Ad placement recap:
- **Banner**: Gallery screen + result preview.
- **Interstitial**: capped, shown after every **3rd** save only.
- **Viewfinder**: always ad-free.
- Ads are fully disabled once the user unlocks PRO.

### 3b. Google Play Billing

Create these products in **Play Console > Monetize > Products > In-app products**,
then make sure the ids match `app/src/main/java/com/facecam/app/billing/ProductIds.kt`:

- One non-consumable: `facecam_pro_unlock` (PRO).
- One non-consumable per paid camera, prefixed `cam_`, e.g. `cam_roma`,
  `cam_ins_2`. The suffix must equal the camera id in
  `app/src/main/assets/cameras/*.json`.

To use different product ids, edit `ProductIds.PRO` and
`ProductIds.CAMERA_PREFIX`, and the `PAID_CAMERAS` list.

> Billing requires the app to be signed and uploaded to a Play track (internal
> testing is fine) before purchases resolve. On a plain debug build you will get
> "item unavailable" - that is expected.

### 3c. Privacy policy URL

`app/src/main/java/com/facecam/app/ui/screens/SettingsScreen.kt` links to a
placeholder `https://example.com/facecam/privacy`. Replace it with your hosted
policy URL (required by Play for an app with ads).

---

## 4. Camera presets

The 13 film cameras live as JSON in `app/src/main/assets/cameras/`. Each file
has a 20-float `colorMatrix` plus `grain`, `leak`, `vignette`, `frame`,
`dateStamp`, `free` and `instant` fields. To add a camera, drop in a new JSON
with a unique `id` and add the id to `ProductIds.PAID_CAMERAS` if it is paid.
See `assets/cameras/_OVERLAYS.md` for the overlay/texture notes.

---

## 5. Building a signed AAB for Google Play

### 5a. Create an upload keystore (once)

```bash
keytool -genkeypair -v \
  -keystore facecam-upload.jks \
  -alias facecam \
  -keyalg RSA -keysize 2048 -validity 10000
```

Keep this keystore and its passwords safe - you cannot re-sign updates without
it (or Play App Signing).

### 5b. Configure signing

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

### 5c. Build the bundle

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

### 5d. Before you ship - checklist

- [ ] Replaced all AdMob app id + unit id placeholders.
- [ ] Created Play Billing products and matched the ids in `ProductIds.kt`.
- [ ] Set a real privacy policy URL.
- [ ] Bumped `versionCode` / `versionName` in `app/build.gradle.kts`.
- [ ] Ran `./gradlew bundleRelease` with a release signing config.
- [ ] Tested on a physical device (emulators have limited camera support).

---

## 6. Notes and assumptions

- **Offline by design.** App logic makes no network calls. The only outbound
  traffic is the AdMob SDK, which is disabled for PRO users.
- **Photos** are written to the app's private `files/gallery/` folder first, then
  optionally published to the device MediaStore (Pictures/FaceCam) on save.
- **Purchases & settings** are stored in `SharedPreferences`. "Restore purchases"
  re-validates against Google Play Billing.
- **No gradle-wrapper.jar** ships in the archive - regenerate it as in section 2.
- The project is provided as complete, syntactically valid source; it has **not**
  been compiled here (no Android SDK in the generation environment). Build it in
  Android Studio or via the Gradle wrapper.
