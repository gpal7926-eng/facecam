# FaceCam camera overlays

FaceCam draws every film frame, light leak, dust and date stamp procedurally with
`android.graphics` (Canvas + Paint + PorterDuff), so no bitmap texture files are
strictly required. If you want to drop in your own PNG texture overlays, place
them in `app/src/main/assets/overlays/` and reference the file name from a
preset's `overlay` field.

## Overlay slots

| Slot        | Drawn by            | Notes                                        |
|-------------|---------------------|----------------------------------------------|
| frame       | OverlayRenderer.kt  | Border drawn per the preset `frame` style.   |
| light leak  | AnalogEffects.kt    | Randomised warm gradient, blended with PLUS. |
| grain       | AnalogEffects.kt    | Random per-pixel noise, density from `grain`.|
| vignette    | AnalogEffects.kt    | Radial darkening, strength from `vignette`.  |
| dust        | AnalogEffects.kt    | Random specks + hairline scratches.          |
| date stamp  | DateStampRenderer.kt| Burned-in amber text when `dateStamp` true.  |

## Optional custom textures

If you add textures, name them after the preset id, e.g. `nomo_135_b_leak.png`,
and set `"overlay": "nomo_135_b_leak.png"` in the preset JSON. The renderer
falls back to procedural effects whenever the asset is missing, so the app keeps
working with zero texture files.
