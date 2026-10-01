package com.facecam.app.camera.pro

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Horizontal level indicator driven by the accelerometer ([SensorManager]).
 *
 * [roll] is the tilt of the phone left/right in degrees (-180..180); the PRO HUD
 * turns green when it is within a small dead-zone of 0. [pitch] is the front/
 * back tilt, useful for showing how flat the camera is held.
 */
class LevelSensor(context: Context) {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _roll = MutableStateFlow(0f)
    val roll: StateFlow<Float> = _roll.asStateFlow()

    private val _pitch = MutableStateFlow(0f)
    val pitch: StateFlow<Float> = _pitch.asStateFlow()

    /** False on devices with no accelerometer - the HUD hides the bubble then. */
    val available: Boolean get() = accelerometer != null

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            // Roll: rotation about the viewing axis (left/right tilt).
            val rollRad = atan2(x.toDouble(), y.toDouble())
            _roll.value = Math.toDegrees(rollRad).toFloat()
            // Pitch: front/back tilt.
            val pitchRad = atan2(-z.toDouble(), sqrt((x * x + y * y).toDouble()))
            _pitch.value = Math.toDegrees(pitchRad).toFloat()
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start() {
        val manager = sensorManager ?: return
        val sensor = accelerometer ?: return
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        sensorManager?.unregisterListener(listener)
    }
}
