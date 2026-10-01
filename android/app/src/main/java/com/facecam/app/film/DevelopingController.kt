package com.facecam.app.film

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controls the "developing" wait animation shown for instant cameras.
 *
 * Instant cameras need a few seconds to "develop" the print. The delay is
 * purely cosmetic.
 */
class DevelopingController {

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _developing = MutableStateFlow(false)
    val developing: StateFlow<Boolean> = _developing.asStateFlow()

    /** Total wait in milliseconds for an instant camera. */
    var durationMillis: Long = 3200L

    /**
     * Run the developing animation. [onTick] receives progress 0..1; the caller
     * suspends until the wait finishes.
     */
    suspend fun develop(skip: Boolean, onTick: (Float) -> Unit = {}) {
        if (skip) {
            _progress.value = 1f
            onTick(1f)
            _developing.value = false
            return
        }
        _developing.value = true
        _progress.value = 0f
        val steps = 32
        val stepDelay = (durationMillis / steps).coerceAtLeast(16L)
        for (i in 1..steps) {
            val p = i.toFloat() / steps
            _progress.value = p
            onTick(p)
            delay(stepDelay)
        }
        _developing.value = false
    }
}
