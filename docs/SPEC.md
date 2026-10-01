# APP SPEC 1 — VINTAGE FILM CAMERA
### (Full feature parity with the Chinese app "NOMO CAM")

> **How to use:** Hand this whole file to any coding AI or Android developer. It lists every feature the original NOMO CAM has, plus the screen flow, data, tech stack, monetization, and publishing steps. The app is **offline-first** — no server, no backend, no monthly cost.

---

## 1. CONCEPT
A point-and-shoot camera that makes normal phone photos look like they were shot on old film / retro / instant cameras. The magic is that the user does **nothing** — no sliders, no editing. Each "camera" inside the app simulates a real film camera, and the vintage look (grain, dust, light leaks, faded colours, frames) is added automatically, randomly, so every photo feels unique.

**Name suggestions:** RetroCam, VintageCam, FilmRoll, OldRoll, Purani Yaadein.
**Tagline:** "Purani film wali feel, ek click mein."

---

## 2. FULL FEATURE LIST (match the original NOMO CAM)

### 2.1 Multiple "cameras" (film simulations) — CORE
The app is built around the idea of owning different **camera models**, each with its own distinct look. Examples from the original:
- **NOMO 135 B** / **NOMO 135 M** (classic 35mm)
- **NOMO 135P** (double-exposure + ultra-wide/tele markings)
- **NOMO TOY F** / **NOMO TOY K** (toy camera look)
- **NOMO ROMA**, **NOMO FR2**, **NOMO 2007**, **NOMO EATS** (food camera)
- **NOMO INS 2** (instant/Polaroid, with a "developing" wait)
- **NOMO SWIRLY 2** (swirly lens bokeh)
- **NOMO Range 67** (rangefinder, exposure dial)
- **Wide 17** (wide lens)

Each camera defines its own: colour curve, grain amount, light-leak behaviour, vignette, frame/border, and special effects.

### 2.2 Automatic random analog presets — CORE
After a photo is taken, the app applies **random** film effects, exactly like real film:
- Colour curves
- Film grain / noise
- Dust specks and scratches
- Light leaks (random coloured streaks at edges)
- Vignette (dark corners)
- Sharpening / softness
- Frames / borders
- Date stamp option (burn a date like "'98 10 15" into the photo)

Because they are randomised, no two photos look identical.

### 2.3 Camera Shop
A store screen inside the app where the user browses, downloads, and **buys individual cameras**. New cameras are added over time.

### 2.4 Double exposure
A dedicated button: take two photos, and the app merges them into one image (the classic film "forgot to advance the roll" effect).

### 2.5 Film development time
Instant (INS) cameras show a **"developing" wait** after shooting — you see the photo slowly appear, mimicking a real Polaroid. (PRO users can turn this wait off.)

### 2.6 PRO membership
- Unlimited use of **all** cameras, including future releases.
- **Membership-only exclusive cameras**.
- **Pro tools:** importing external photos, turning off film development time, and other extras.

### 2.7 Import photos (PRO)
Pick an existing photo from the gallery and run it through a camera's film look.

### 2.8 Capture controls
Front/back camera switch, flash toggle, self-timer, and a shutter button.

### 2.9 Gallery & sharing
An in-app gallery of photos (grouped by camera), tap to view full-screen, save to device, and share via the standard Android share sheet (WhatsApp, Instagram, etc.).

### 2.10 Settings
Date stamp on/off, border on/off, shutter sound on/off, default camera, restore purchases, privacy policy link.

---

## 3. FEATURE PARITY TABLE

| Original NOMO CAM feature | In our app |
|---|---|
| Multiple authentic cameras | Multiple film-simulation "cameras" |
| Camera Shop (buy/download) | In-app shop with Play Billing |
| Random analog presets | Random grain/dust/leak/vignette/curves/frames |
| Double exposure | Double exposure mode |
| Film development time | "Developing" animation for instant cameras |
| NOMO PRO (all cameras + exclusive) | PRO membership |
| Pro tools: import photos, disable dev time | Same |
| Date stamp / frames | Date stamp + frame options |
| Save / share | Save to gallery + Android share sheet |

---

## 4. SCREENS & FLOW
1. **Splash** — logo, 1–2 sec.
2. **Onboarding (1 screen)** — quick intro + camera permission request.
3. **Camera viewfinder (main)** — full-screen preview with the selected camera's look applied live; shutter button; icons for camera switch, flash, timer, camera picker, shop, gallery.
4. **Camera picker (bottom sheet)** — swipe/list of owned + locked cameras with live thumbnails; locked ones show a price.
5. **Camera shop** — grid of cameras with preview images, prices, "Owned / Buy" buttons; PRO banner.
6. **Developing screen** (instant cameras) — animated wait, then the finished photo.
7. **Result preview** — the finished photo with Save / Retake / Share; small banner ad at bottom.
8. **Gallery** — grid of saved photos; tap to view, delete, share.
9. **PRO paywall** — benefits list + subscribe button.
10. **Settings** — toggles as listed above.

---

## 5. DATA & STORAGE (all on-device)
- **Camera presets:** JSON files in `assets/cameras/` — each defines colour-matrix values, grain strength, leak behaviour, vignette, frame, date-stamp flag, and whether it is free or paid.
- **Overlay textures:** grain, dust, light-leak images in `res/drawable` or `assets/`.
- **Photos:** saved to the device Pictures folder via MediaStore, and/or the app's private folder.
- **Purchases & settings:** SharedPreferences + Google Play Billing (Play handles purchase records, so no server needed).

---

## 6. TECH STACK
- **Language:** Kotlin. **UI:** Jetpack Compose (or XML).
- **Camera:** CameraX (camera2 under the hood).
- **Image processing:** a GPU shader library such as **GPUImage for Android** (`jp.co.cyberagent.android:gpuimage`) OR custom OpenGL ES shaders, OR a `ColorMatrix` + overlay-compositing approach. All processing happens **on-device**.
- **Overlays:** composited with `Canvas` / PorterDuff blending.
- **Date stamp:** drawn with `Canvas.drawText`.
- **Billing:** Google Play Billing for individual cameras and PRO.
- **Min SDK 24, target latest.** **No network calls anywhere.**

---

## 7. MONETIZATION
- **Individual camera purchases** (one-time) via Google Play Billing.
- **PRO membership** (subscription or one-time unlock) — all cameras + pro tools.
- **Optional ads:** a banner on the Gallery and Result-preview screens, and a capped interstitial after every 3rd save. Keep the viewfinder ad-free.
- **Remove-ads** option as part of PRO.

---

## 8. PERMISSIONS
- **Camera** (required).
- **Storage / Media** (to save photos).
- **Notifications** (optional, for new-camera announcements).

---

## 9. BUILD & PUBLISH STEPS
1. Install **Android Studio** (free); create a new Empty Compose project.
2. Add dependencies: CameraX, GPUImage (or shader lib), Play Billing.
3. Build the features above; test on a real phone.
4. Create a **Google Play developer account** — one-time **US$25**.
5. Prepare listing: name, description, icon (512×512), feature graphic, ≥4 screenshots, **privacy policy URL** ("no data collected, all on device"), Data Safety form, Content Rating.
6. Generate a **signed App Bundle (.aab)**; upload; test track first, then production.
7. (New personal accounts) run a closed test before going live.

---

## 10. READY-TO-PASTE PROMPT FOR AN AI CODER

> "Build a complete, publishable Android app in Kotlin using Jetpack Compose. It must be 100% offline — no backend, no login, no network calls. Min SDK 24, target latest.
>
> It is a vintage film-camera app (NOMO CAM style). Requirements:
> 1. A live camera viewfinder (CameraX) that applies the selected film look in real time.
> 2. At least 12 'cameras' (film simulations), each with its own colour curve, grain, light-leak, vignette, and frame. Store each as a JSON preset in assets. Some free, some paid.
> 3. After each shot, apply randomised analog effects (grain, dust, scratches, light leaks, vignette, sharpening, frames) plus an optional date stamp.
> 4. A Camera Shop screen where users buy/download cameras (Google Play Billing).
> 5. A double-exposure mode (two shots merged).
> 6. A 'developing' wait animation for instant-style cameras.
> 7. A PRO unlock: all cameras + import photos from gallery + disable developing wait + remove ads.
> 8. In-app gallery, save to device, and Android share sheet.
> 9. Front/back camera, flash, self-timer, settings screen.
> 10. Google AdMob (banner on gallery/preview, capped interstitial) with clearly-marked placeholder IDs.
>
> Provide full project structure, Gradle dependencies, all Kotlin files, JSON preset format, and setup instructions."
