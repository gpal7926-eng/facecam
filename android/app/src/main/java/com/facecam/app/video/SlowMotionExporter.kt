package com.facecam.app.video

import android.media.MediaMetadataRetriever
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min

/** Playback speeds offered in the video UI. */
enum class VideoSpeed(val factor: Float, val label: String) {
    X1(1f, "1x"),
    X05(0.5f, "0.5x"),
    X025(0.25f, "0.25x");

    val isSlow: Boolean get() = factor < 1f
}

/**
 * Re-times a recorded MP4 for slow motion.
 *
 * The clip is re-encoded with a **lower presentation-timestamp rate**: each
 * source frame is written with a larger PTS step, so the same frames play back
 * over a longer wall-clock time (0.5x = twice as long, 0.25x = four times).
 * 1x is a straight copy of the original.
 *
 * 100% offline, pure CPU, no EGL / OpenGL surface work.
 */
object SlowMotionExporter {

    /** Outcome of a slow-motion pass. */
    data class Result(val file: File, val note: String)

    suspend fun export(
        input: File,
        output: File,
        speed: VideoSpeed,
        maxDimension: Int = 1280,
        onProgress: (Float) -> Unit = {}
    ): Result = withContext(Dispatchers.Default) {
        if (speed == VideoSpeed.X1) {
            return@withContext try {
                input.copyTo(output, overwrite = true)
                onProgress(1f)
                Result(output, "1x - original copied")
            } catch (t: Throwable) {
                Log.e(TAG, "1x copy failed", t)
                Result(input, "1x copy failed; source returned")
            }
        }

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
                input.copyTo(output, overwrite = true)
                return@withContext Result(output, "Unreadable video; original copied")
            }

            val scale = min(1f, maxDimension.toFloat() / max(srcW, srcH).toFloat())
            val outW = even(srcW * scale)
            val outH = even(srcH * scale)
            val frameRate = fps.toInt().coerceIn(15, 60)
            val bitRate = (outW * outH * 6).coerceIn(2_000_000, 20_000_000)
            val frameStepMs = 1000L / frameRate

            // A larger PTS step per frame => slower playback.
            val outStepUs = ((1_000_000.0 / frameRate) / speed.factor).toLong()

            val encoder = Mp4FrameEncoder(output, outW, outH, frameRate, bitRate)
            encoder.start()

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
                val frame = if (raw.width != outW || raw.height != outH) {
                    android.graphics.Bitmap.createScaledBitmap(raw, outW, outH, true)
                        .also { raw.recycle() }
                } else {
                    raw
                }
                encoder.encodeFrame(frame, frameIndex * outStepUs)
                frame.recycle()
                frameIndex++
                t += frameStepMs
                if (frameIndex % 5L == 0L) {
                    onProgress((t.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f))
                }
            }
            encoder.finish()
            retriever.release()
            onProgress(1f)
            Result(
                output,
                "${speed.label} slow motion - $frameIndex frames re-timed"
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Slow-motion export failed", t)
            try {
                input.copyTo(output, overwrite = true)
                Result(output, "Slow motion unavailable; original copied")
            } catch (copy: Throwable) {
                Result(input, "Slow motion failed; source returned")
            }
        }
    }

    private fun even(v: Float): Int {
        val i = v.toInt()
        return if (i % 2 == 0) i else i + 1
    }

    private const val TAG = "SlowMotionExporter"
}
