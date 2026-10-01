# FaceCam - Credits & Third-Party Notices

FaceCam bundles, or can optionally bundle, the following open-source software.
All components are used under their respective licences. Nothing here is a
network service; every library runs entirely on-device.

## Optional GPU filter engine

### wysaid/android-gpuimage-plus

- **Project:** https://github.com/wysaid/android-gpuimage-plus
- **Maven coordinates:** `org.wysaid:gpuimage-plus` (published to
  https://maven.wysaid.org/). The GitHub project is named
  `android-gpuimage-plus`; the published **artifact id is `gpuimage-plus`**.
  FaceCam uses the image-only `-min` variant (no FFmpeg), e.g.
  `org.wysaid:gpuimage-plus:3.2.0-min`.
- **Licence:** MIT
- **Used for:** the optional, feature-flagged GPU path for live-preview and
  still-image filtering (`com.facecam.app.gpu.GpuFilterEngine`). The library is
  disabled by default; the app's normal pipelines are pure CPU
  (`film/AnalogEffects` and `film/BeautyEffects`).

MIT Licence text (android-gpuimage-plus):

```
MIT License

Copyright (c) wysaid

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Platform libraries

- **AndroidX / Jetpack Compose / CameraX** - Apache License 2.0 (Google).
- **Coil** - Apache License 2.0.
- **AndroidX ExifInterface** - Apache License 2.0.

FaceCam does not bundle any advertising or billing SDK.

## Fonts & assets

- All frames, grain, vignette, light leaks, dust, date stamps, the FaceCam
  branding band and the Beauty enhance are drawn procedurally with
  `android.graphics`. No third-party image or font assets are bundled.
