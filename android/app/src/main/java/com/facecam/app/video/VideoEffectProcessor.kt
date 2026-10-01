package com.facecam.app.video

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import com.facecam.app.film.AnalogEffects
import com.facecam.app.film.BeautyEffects
import com.facecam.app.film.BrandingRenderer
import com.facecam.app.film.DateStampRenderer
import com.facecam.app.film.FilmPreset
import com.facecam.app.film.OverlayRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min

/**
 * Post-processes a recorded MP4 frame by frame.
 *
 * The recorded video is plain - the look is applied here, off the live camera
 * pipeline. Frames are pulled with [MediaMetadataRetriever], run through the
 * existing [AnalogEffects] / [BeautyEffects] code, the burned-in caption is
 * drawn, and the result is re-encoded to a new MP4. If anything fails the
 * original is copied through unchanged, so a video is never lost.
 *
 * 100% offline and pure CPU - no EGL / OpenGL surface work.
 */
object VideoEffectProcessor {

    /** Outcome of a post-processing pass. */
    data class Result(
        val file: File,
        val lookBaked: Boolean,
        val note: String
    )

    /**
     * Apply [preset]'s look to [input] and write the result to [output].
     *
     * @param faceMask optional feathered alpha mask (from
     *   [com.facecam.app.ml.FaceMaskProvider]) steering the Beauty skin smoothing.
     * @param caption optional caption line burned into every frame.
     */
    suspend fun process(
        input: File,
        output: File,
        preset: FilmPreset,
        faceMask: Bitmap? = null,
        caption: String? = null,
        border: Boolean = true,
        dateStamp: Boolean = true,
        branding: Boolean = true,
        maxDimension: Int = 1280,
        onProgress: (Float) -> Unit = {}
    ): Result = withContext(Dispatchers.Default) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(input.absolutePath)
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val srcW = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val srcH = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val fps = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                ?.toFloatOrNull()?.takeIf { it in 5f..120f } ?: 30f

            if (durationMs <= 0L || srcW <= 0 || srcH <= 0) {
                retriever.release()
                return@withContext fallback(input, output, "Unreadable video; original kept")
            }

            val scale = min(1f, maxDimension.toFloat() / max(srcW, srcH).toFloat())
            val outW = (srcW * scale).roundToEven()
            val outH = (srcH * scale).roundToEven()

            val frameRate = fps.toInt().coerceIn(15, 60)
            val frameStepMs = 1000L / frameRate
            val bitRate = (outW * outH * 6).coerceIn(2_000_000, 20_000_000)

            val scaledMask = scaleMask(faceMask, outW, outH)
            val dateText = if (dateStamp && !preset.isBeauty) {
                val c = Calendar.getInstance()
                DateStampRenderer.formatDate(
                    c.get(Calendar.YEAR),
                    c.get(Calendar.MONTH) + 1,
                    c.get(Calendar.DAY_OF_MONTH)
                )
            } else {
                null
            }

            val encoder = Mp4FrameEncoder(output, outW, outH, frameRate, bitRate)
            encoder.start()

            // Reuse a single caption renderer instead of allocating one per frame.
            val captionRenderer = SubtitleOverlay()
            var t = 0L
            var frameIndex = 0L
            while (t < durationMs) {
                coroutineContext.ensureActive()
                val raw = retriever.getFrameAtTime(
                    t * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST
                )
                if (raw == null) {
                    t += frameStepMs
                    continue
                }
                var frame = if (raw.width != outW || raw.height != outH) {
                    Bitmap.createScaledBitmap(raw, outW, outH, true).also { raw.recycle() }
                } else {
                    raw
                }
                val preLook = frame
                frame = applyLook(frame, preset, scaledMask, border, dateText, branding)
                if (frame !== preLook) preLook.recycle()
                if (!caption.isNullOrBlank()) {
                    frame = captionRenderer.drawCaption(frame, caption)
                }
                val ptsUs = frameIndex * (1_000_000L / frameRate)
                encoder.encodeFrame(frame, ptsUs)
                frame.recycle()

                frameIndex++
                t += frameStepMs
                if (frameIndex % 5L == 0L) {
                    onProgress((t.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f))
                }
            }
            encoder.finish()
            retriever.release()
            scaledMask?.recycle()
            onProgress(1f)
            Result(
                file = output,
                lookBaked = true,
                note = "Look applied to $frameIndex frames at ${outW}x$outH"
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Video effect processing failed", t)
            fallback(input, output, "Effect pass unavailable (${t.javaClass.simpleName}); original kept")
        }
    }

    /**
     * Run one still frame through the same pipeline the photo capture uses.
     * Shared with [SlowMotionExporter]'s sibling logic.
     */
    fun applyLook(
        frame: Bitmap,
        preset: FilmPreset,
        faceMask: Bitmap?,
        border: Boolean,
        dateText: String?,
        branding: Boolean
    ): Bitmap = if (preset.isBeauty) {
        BeautyEffects.develop(
            source = frame,
            options = BeautyEffects.Options(preset = preset, faceMask = faceMask)
        )
    } else {
        AnalogEffects.develop(
            source = frame,
            options = AnalogEffects.Options(
                preset = preset,
                border = border,
                dateStamp = dateText != null,
                branding = branding
            ),
            applyFrame = { bmp -> OverlayRenderer.drawFrame(bmp, preset.frame) },
            dateStampText = dateText
        )
    }

    private fun scaleMask(mask: Bitmap?, w: Int, h: Int): Bitmap? {
        if (mask == null) return null
        return if (mask.width == w && mask.height == h) {
            mask
        } else {
            Bitmap.createScaledBitmap(mask, w, h, true)
        }
    }

    private fun fallback(input: File, output: File, note: String): Result = try {
        input.copyTo(output, overwrite = true)
        Result(output, lookBaked = false, note = note)
    } catch (t: Throwable) {
        Log.e(TAG, "Fallback copy failed", t)
        Result(input, lookBaked = false, note = "Copy failed; source returned")
    }

    private fun Float.roundToEven(): Int {
        val v = this.toInt()
        return if (v % 2 == 0) v else v + 1
    }

    private const val TAG = "VideoEffectProcessor"
}
