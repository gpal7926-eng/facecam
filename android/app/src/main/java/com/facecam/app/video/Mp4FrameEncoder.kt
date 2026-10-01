package com.facecam.app.video

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * A small, EGL-free H.264 MP4 encoder.
 *
 * Frames are handed in as [Bitmap]s, converted to planar YUV420 (I420) in
 * software and queued to a [MediaCodec] byte-buffer encoder, then muxed with
 * [MediaMuxer]. Deliberately no Surface / OpenGL path is used - the caller
 * draws everything it needs (camera look, burned-in caption) into the bitmap
 * before handing it over.
 *
 * Used by [VideoEffectProcessor] and [SlowMotionExporter].
 */
internal class Mp4FrameEncoder(
    private val output: File,
    private val width: Int,
    private val height: Int,
    private val frameRate: Int,
    private val bitRate: Int
) {

    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var trackIndex = -1
    private var muxerStarted = false

    private val yuv: ByteArray = ByteArray(width * height * 3 / 2)

    fun start() {
        val format = MediaFormat.createVideoFormat(MIME, width, height).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            )
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(MIME)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        this.codec = codec
        muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    /** Encode one frame whose presentation time is [ptsUs] microseconds. */
    fun encodeFrame(bitmap: Bitmap, ptsUs: Long) {
        val codec = codec ?: return
        val frame = if (bitmap.width == width && bitmap.height == height) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        }
        toI420(frame)
        if (frame !== bitmap) frame.recycle()

        drain(false)
        val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
        if (inIndex >= 0) {
            val buffer: ByteBuffer = codec.getInputBuffer(inIndex) ?: return
            buffer.clear()
            buffer.put(yuv)
            codec.queueInputBuffer(inIndex, 0, yuv.size, ptsUs, 0)
        }
        drain(false)
    }

    /** Flush and release. Always call this once, even after an error. */
    fun finish() {
        val codec = codec ?: return
        try {
            val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
            if (inIndex >= 0) {
                codec.queueInputBuffer(
                    inIndex,
                    0,
                    0,
                    0L,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                )
            }
            drain(true)
        } catch (_: Throwable) {
            // best effort
        } finally {
            try {
                codec.stop()
            } catch (_: Throwable) {
            }
            try {
                codec.release()
            } catch (_: Throwable) {
            }
            this.codec = null
            try {
                if (muxerStarted) muxer?.stop()
            } catch (_: Throwable) {
            }
            try {
                muxer?.release()
            } catch (_: Throwable) {
            }
            muxer = null
            muxerStarted = false
        }
    }

    private fun drain(endOfStream: Boolean) {
        val codec = codec ?: return
        val muxer = muxer ?: return
        val info = MediaCodec.BufferInfo()
        var spins = 0
        while (true) {
            val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            when {
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (endOfStream && spins++ < 5) continue
                    return
                }
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxerStarted) {
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                }
                outIndex >= 0 -> {
                    val outBuf: ByteBuffer = codec.getOutputBuffer(outIndex) ?: continue
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        info.size = 0
                    }
                    if (info.size > 0 && muxerStarted) {
                        outBuf.position(info.offset)
                        outBuf.limit(info.offset + info.size)
                        muxer.writeSampleData(trackIndex, outBuf, info)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    /** Software ARGB -> I420 conversion (Y plane, then quarter-size U and V). */
    private fun toI420(bitmap: Bitmap) {
        val w = width
        val h = height
        val argb = IntArray(w * h)
        bitmap.getPixels(argb, 0, w, 0, 0, w, h)
        val frameSize = w * h
        var yIndex = 0
        var uIndex = frameSize
        var vIndex = frameSize + frameSize / 4
        for (j in 0 until h) {
            for (i in 0 until w) {
                val c = argb[j * w + i]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val y = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(0, 255)
                yuv[yIndex++] = y.toByte()
                if (j % 2 == 0 && i % 2 == 0) {
                    val u = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(0, 255)
                    val v = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(0, 255)
                    yuv[uIndex++] = u.toByte()
                    yuv[vIndex++] = v.toByte()
                }
            }
        }
    }

    companion object {
        private const val MIME = "video/avc"
        private const val TIMEOUT_US = 10_000L
    }
}
