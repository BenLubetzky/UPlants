package com.uplants.app.data.device

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.math.roundToInt

/**
 * Reads the device's sensors for a moment and returns the averages as the post's `sensor_data`
 * (requirement 18). Sensors the device doesn't have are left out.
 */
class SensorReader(context: Context) {

    private val manager = context.getSystemService(SensorManager::class.java)

    suspend fun snapshot(durationMs: Long = 800): JsonObject = coroutineScope {
        val accelerometer = async { average(Sensor.TYPE_ACCELEROMETER, durationMs) }
        val gyroscope = async { average(Sensor.TYPE_GYROSCOPE, durationMs) }
        val light = async { average(Sensor.TYPE_LIGHT, durationMs) }
        val pressure = async { average(Sensor.TYPE_PRESSURE, durationMs) }
        buildJsonObject {
            accelerometer.await()?.let { put("accelerometer", it.toJson()) } // m/s², x y z
            gyroscope.await()?.let { put("gyroscope", it.toJson()) } // rad/s, x y z
            light.await()?.let { put("light_lux", JsonPrimitive(it[0].round2())) }
            pressure.await()?.let { put("pressure_hpa", JsonPrimitive(it[0].round2())) }
            put("device", JsonPrimitive("${Build.MANUFACTURER} ${Build.MODEL}"))
        }
    }

    private suspend fun average(type: Int, durationMs: Long): FloatArray? {
        val sensor = manager?.getDefaultSensor(type) ?: return null
        val samples = mutableListOf<FloatArray>()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                synchronized(samples) { samples += event.values.copyOf() }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        try {
            delay(durationMs)
        } finally {
            manager.unregisterListener(listener)
        }
        val taken = synchronized(samples) { samples.toList() }
        if (taken.isEmpty()) return null
        val axes = if (type == Sensor.TYPE_LIGHT || type == Sensor.TYPE_PRESSURE) 1 else 3
        return FloatArray(axes) { i -> taken.map { it.getOrElse(i) { 0f } }.average().toFloat() }
    }

    private fun FloatArray.toJson() = JsonArray(map { JsonPrimitive(it.round2()) })

    private fun Float.round2(): Double = (this * 100).roundToInt() / 100.0
}
