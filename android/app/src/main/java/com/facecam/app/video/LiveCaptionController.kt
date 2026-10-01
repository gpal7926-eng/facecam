package com.facecam.app.video

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * Live captions via Android's on-device [SpeechRecognizer].
 *
 * The recognizer is asked to prefer offline processing and the app declares no
 * `INTERNET` permission, so nothing leaves the device. Recognised lines are
 * pushed out through [onLine]; partial results arrive with `isFinal = false`.
 *
 * This is best-effort: when no on-device recognizer is present the controller
 * reports an error through [onError] and the UI simply shows nothing.
 */
class LiveCaptionController(private val context: Context) {

    /** Called with each recognised line and whether it is a final result. */
    var onLine: ((text: String, isFinal: Boolean) -> Unit)? = null

    /** Called with a human-readable message when captioning cannot run. */
    var onError: ((message: String) -> Unit)? = null

    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var intent: Intent? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start() {
        if (listening) return
        if (!isAvailable()) {
            onError?.invoke("On-device speech recognition unavailable")
            return
        }
        try {
            val r = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = r
            r.setRecognitionListener(listener)

            intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            listening = true
            r.startListening(intent)
        } catch (t: Throwable) {
            Log.e(TAG, "Could not start live captions", t)
            listening = false
            onError?.invoke("Could not start live captions")
        }
    }

    fun stop() {
        listening = false
        runCatching { recognizer?.stopListening() }
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun restart() {
        if (!listening) return
        val r = recognizer ?: return
        runCatching { r.startListening(intent) }
    }

    private fun firstResult(bundle: Bundle?): String? {
        val list = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        return list?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> restart()
                else -> {
                    onError?.invoke("Live captions stopped")
                    listening = false
                }
            }
        }

        override fun onResults(results: Bundle?) {
            firstResult(results)?.let { onLine?.invoke(it, true) }
            restart()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            firstResult(partialResults)?.let { onLine?.invoke(it, false) }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    companion object {
        private const val TAG = "LiveCaptionController"
    }
}
