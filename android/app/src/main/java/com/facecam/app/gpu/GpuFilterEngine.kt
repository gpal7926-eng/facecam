package com.facecam.app.gpu

import android.graphics.Bitmap
import android.util.Log
import com.facecam.app.BuildConfig

/**
 * OPTIONAL GPU filter path.
 *
 * FaceCam's default film pipeline runs entirely on the CPU with
 * [android.graphics] (see `film/AnalogEffects`). When the app is built with the
 * GPU feature flag enabled, this engine can additionally hand a still (or a
 * preview frame) to the MIT-licensed library
 * [wysaid/android-gpuimage-plus](https://github.com/wysaid/android-gpuimage-plus)
 * for OpenGL-accelerated filtering.
 *
 * The dependency is wired in `app/build.gradle.kts` behind the Gradle property
 * `facecam.gpuFilters` (see ANDROID_SETUP.md). Because the library is optional,
 * this class reaches it through **reflection** rather than a compile-time import:
 * the app therefore builds and runs perfectly well with the flag OFF and the
 * library absent. When the flag is on and the classes are present, the GPU path
 * is used; otherwise every call falls back to the supplied CPU implementation.
 *
 * 100% offline: this engine never performs any network access.
 */
object GpuFilterEngine {

    private const val TAG = "GpuFilterEngine"

    /** Maven coordinates of the optional library (see CREDITS.md). */
    const val LIBRARY_COORDINATES = "org.wysaid:gpuimage-plus"

    /** Class that exposes the native filter entry points in gpuimage-plus. */
    private const val NATIVE_LIBRARY_CLASS = "org.wysaid.nativePort.CGENativeLibrary"

    /** Whether the feature flag was enabled at build time. */
    val enabledByFlag: Boolean get() = BuildConfig.GPU_FILTERS_ENABLED

    /** True only when the flag is on AND the library classes are actually present. */
    val available: Boolean by lazy {
        if (!enabledByFlag) {
            false
        } else {
            runCatching { Class.forName(NATIVE_LIBRARY_CLASS) }.isSuccess
        }
    }

    /** Human-readable status for the settings/about screen. */
    fun statusLabel(): String = when {
        !enabledByFlag -> "CPU (GPU filters not enabled at build time)"
        !available -> "CPU (GPU library not bundled)"
        else -> "GPU (android-gpuimage-plus, MIT)"
    }

    /**
     * Apply a gpuimage-plus filter config (e.g.
     * `"@adjust saturation 0.8 @curve R(0,0)(255,255)"`) to [bitmap].
     *
     * When the GPU path is unavailable this returns `fallback(bitmap)` unchanged,
     * so callers can always supply the CPU result they already computed.
     */
    fun applyFilter(
        bitmap: Bitmap,
        filterConfig: String,
        intensity: Float = 1f,
        fallback: (Bitmap) -> Bitmap
    ): Bitmap {
        if (!available) return fallback(bitmap)
        return try {
            val clazz = Class.forName(NATIVE_LIBRARY_CLASS)
            // Try the (Bitmap, String) overload first, then (Bitmap, String, float).
            val method = clazz.methods.firstOrNull {
                it.name == "filterImage" &&
                    it.parameterTypes.size >= 2 &&
                    it.parameterTypes[0] == Bitmap::class.java &&
                    it.parameterTypes[1] == String::class.java
            } ?: return fallback(bitmap)

            val result = if (method.parameterTypes.size == 3) {
                method.invoke(null, bitmap, filterConfig, intensity)
            } else {
                method.invoke(null, bitmap, filterConfig)
            }
            (result as? Bitmap) ?: fallback(bitmap)
        } catch (t: Throwable) {
            Log.w(TAG, "GPU filter failed, falling back to CPU", t)
            fallback(bitmap)
        }
    }

    /**
     * Whether the library can drive the *live* preview (it ships an OpenGL
     * surface view). The app currently uses this only as a hint; the CPU overlay
     * path in `camera/pro` remains the default.
     */
    val supportsLivePreview: Boolean
        get() = available && runCatching {
            Class.forName("org.wysaid.view.ImageGLSurfaceView")
        }.isSuccess
}
