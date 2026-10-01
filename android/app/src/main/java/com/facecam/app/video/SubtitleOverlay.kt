package com.facecam.app.video

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Locale

/**
 * Holds the subtitle state for the video recorder:
 *
 *  - a single typed caption line (the one burned into the video by
 *    [VideoEffectProcessor]),
 *  - a list of timed [Cue]s (typed + live), which can be written out as a
 *    sidecar `.srt`,
 *  - live caption lines fed from Android's on-device SpeechRecognizer.
 *
 * All processing is local - the SpeechRecognizer runs on-device and this class
 * makes no network calls.
 */
class SubtitleOverlay {

    /** One timed caption cue. */
    data class Cue(val startMs: Long, val endMs: Long, val text: String)

    /** The caption line the user typed, burned onto the video during processing. */
    @Volatile
    var typedCaption: String = ""
        private set

    private val _liveCaption = MutableStateFlow("")

    /** The most recent live caption line, for the viewfinder overlay. */
    val liveCaption: StateFlow<String> = _liveCaption.asStateFlow()

    private val cues = mutableListOf<Cue>()
    private var openCue = -1

    /** Set the typed caption (trimmed). */
    fun setTypedCaption(text: String) {
        typedCaption = text.trim()
    }

    /** True when there is something to burn in. */
    fun hasTypedCaption(): Boolean = typedCaption.isNotBlank()

    /**
     * Append (or extend) a live caption line starting at [atMs]. Partial results
     * keep extending the same cue; call [commitLiveCaption] when the recognizer
     * finalises a phrase.
     */
    fun addLiveCaption(text: String, atMs: Long) {
        val clean = text.trim()
        _liveCaption.value = clean
        if (clean.isEmpty()) return
        if (openCue in cues.indices) {
            val existing = cues[openCue]
            cues[openCue] = existing.copy(endMs = atMs + LIVE_HOLD_MS, text = clean)
        } else {
            cues.add(Cue(atMs, atMs + LIVE_HOLD_MS, clean))
            openCue = cues.size - 1
        }
    }

    /** Close the current live cue so the next phrase starts a fresh one. */
    fun commitLiveCaption(atMs: Long) {
        if (openCue in cues.indices) {
            val existing = cues[openCue]
            cues[openCue] = existing.copy(endMs = atMs)
        }
        openCue = -1
    }

    /** Record an explicit cue (used for typed captions with a known window). */
    fun recordCue(startMs: Long, endMs: Long, text: String) {
        if (text.isBlank()) return
        cues.add(Cue(startMs, endMs, text.trim()))
    }

    /** The cue active at [ms], or null. */
    fun cueAt(ms: Long): String? =
        cues.firstOrNull { ms in it.startMs until it.endMs }?.text

    /** All cues, ordered by start time. */
    fun allCues(): List<Cue> = cues.sortedBy { it.startMs }

    /** Reset everything. */
    fun clear() {
        cues.clear()
        openCue = -1
        _liveCaption.value = ""
    }

    // ------------------------------------------------------------------
    // SRT sidecar
    // ------------------------------------------------------------------
    /** Build the `.srt` body for the recorded cues. */
    fun buildSrt(): String {
        val ordered = allCues()
        val sb = StringBuilder()
        ordered.forEachIndexed { index, cue ->
            sb.append(index + 1).append('\n')
            sb.append(srtTimestamp(cue.startMs))
                .append(" --> ")
                .append(srtTimestamp(cue.endMs))
                .append('\n')
            sb.append(cue.text).append('\n')
            sb.append('\n')
        }
        return sb.toString()
    }

    /** Write the `.srt` sidecar next to the video. Returns true on success. */
    fun writeSrt(file: File): Boolean = try {
        file.writeText(buildSrt())
        true
    } catch (_: Throwable) {
        false
    }

    /** Format milliseconds as an SRT timestamp: HH:MM:SS,mmm. */
    fun srtTimestamp(ms: Long): String {
        val total = ms.coerceAtLeast(0L)
        val hours = total / 3_600_000L
        val minutes = (total % 3_600_000L) / 60_000L
        val seconds = (total % 60_000L) / 1_000L
        val millis = total % 1_000L
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }

    // ------------------------------------------------------------------
    // Burn-in
    // ------------------------------------------------------------------
    /**
     * Draw [text] as a burned-in caption near the bottom of [src] and return a
     * NEW bitmap. The input is recycled to match the other pipeline stages.
     */
    fun drawCaption(src: Bitmap, text: String): Bitmap {
        if (text.isBlank()) return src
        val w = src.width
        val h = src.height
        val out = src.copy(Bitmap.Config.ARGB_8888, true)

        val margin = (w * 0.05f).coerceAtLeast(12f)
        val textSize = (h * 0.042f).coerceAtLeast(18f)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            this.textSize = textSize
            letterSpacing = 0.02f
        }
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(150, 0, 0, 0)
        }

        val textWidth = textPaint.measureText(text)
        val boxPad = textSize * 0.6f
        val boxH = textSize + boxPad * 1.4f
        val boxW = (textWidth + boxPad * 2f).coerceAtMost(w - margin * 2f)
        val boxLeft = (w - boxW) / 2f
        val boxBottom = h - margin
        val boxTop = boxBottom - boxH
        val radius = boxH / 2f

        val canvas = Canvas(out)
        canvas.drawRoundRect(
            RectF(boxLeft, boxTop, boxLeft + boxW, boxBottom),
            radius,
            radius,
            boxPaint
        )
        val fm = textPaint.fontMetrics
        val baseline = boxTop + boxH / 2f - (fm.ascent + fm.descent) / 2f
        val textX = (w - textWidth) / 2f
        canvas.drawText(text, textX, baseline, textPaint)

        src.recycle()
        return out
    }

    companion object {
        /** How long a live cue stays on screen if no further result arrives. */
        private const val LIVE_HOLD_MS = 2_500L
    }
}
