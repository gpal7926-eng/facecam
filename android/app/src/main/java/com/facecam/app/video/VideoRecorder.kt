package com.facecam.app.video

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Thin wrapper around the CameraX [Recorder] / [VideoCapture] use cases.
 *
 * It records a plain MP4 into the app's cache directory - the camera look is
 * deliberately NOT baked into the live pipeline. The recorded file is
 * post-processed afterwards by [VideoEffectProcessor], which applies the
 * selected camera look frame by frame. This keeps the live preview cheap and
 * avoids any EGL / OpenGL surface work.
 *
 * 100% offline: nothing here touches the network.
 */
class VideoRecorder(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** The CameraX recorder. Bound to the lifecycle via [videoCapture]. */
    val recorder: Recorder = Recorder.Builder()
        .setQualitySelector(
            QualitySelector.from(
                Quality.HD,
                FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
            )
        )
        .build()

    /** The video capture use case; hand this to [com.facecam.app.camera.CameraController.bind]. */
    val videoCapture: VideoCapture<Recorder> = VideoCapture.withOutput(recorder)

    private var recording: Recording? = null
    private var timerJob: Job? = null
    private var startedAt = 0L

    /** True while a recording is in progress. */
    val isRecording: Boolean get() = recording != null

    /** Whether audio recording is permitted at runtime. */
    fun canRecordAudio(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Start recording into [file].
     *
     * @param onElapsed called roughly every 200 ms with the elapsed recording time in ms.
     * @param onSaved called once when the recording finishes; the file on success, null on error.
     * @param onError called with a human-readable message if recording could not start.
     */
    fun start(
        file: File,
        withAudio: Boolean = true,
        onElapsed: (Long) -> Unit = {},
        onSaved: (File?) -> Unit = {},
        onError: (String) -> Unit = {}
    ): Boolean {
        if (recording != null) {
            onError("Already recording")
            return false
        }
        return try {
            val outputOptions = FileOutputOptions.Builder(file).build()
            var pending = videoCapture.output.prepareRecording(context, outputOptions)
            if (withAudio && canRecordAudio()) {
                pending = pending.withAudioEnabled()
            }

            startedAt = SystemClock.elapsedRealtime()
            recording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Status ->
                        onElapsed(event.recordingStats.recordedDurationNanos / 1_000_000L)
                    is VideoRecordEvent.Finalize -> {
                        val ok = !event.hasError()
                        if (!ok) Log.w(TAG, "Recording finalized with error ${event.error}")
                        recording = null
                        timerJob?.cancel()
                        onElapsed(0L)
                        onSaved(if (ok) file else null)
                    }
                    else -> Unit
                }
            }

            // A light timer so the UI counter stays smooth between Status events.
            timerJob = scope.launch {
                while (recording != null) {
                    onElapsed(SystemClock.elapsedRealtime() - startedAt)
                    delay(200)
                }
            }
            true
        } catch (t: Throwable) {
            Log.e(TAG, "startRecording failed", t)
            recording = null
            timerJob?.cancel()
            onError(t.message ?: "Could not start recording")
            false
        }
    }

    /** Stop the current recording. Safe to call when not recording. */
    fun stop() {
        val active = recording ?: return
        try {
            active.stop()
        } catch (t: Throwable) {
            Log.w(TAG, "stop failed", t)
        }
        recording = null
        timerJob?.cancel()
    }

    /** Release the timer scope. Call from the screen's onDispose. */
    fun release() {
        stop()
        scope.coroutineContext[Job]?.cancel()
    }

    companion object {
        private const val TAG = "VideoRecorder"
    }
}
