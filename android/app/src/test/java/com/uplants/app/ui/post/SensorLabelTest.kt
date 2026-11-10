package com.uplants.app.ui.post

import org.junit.Assert.assertEquals
import org.junit.Test

class SensorLabelTest {
    @Test
    fun `sensor keys get readable names and units`() {
        assertEquals("Light (lux)", sensorLabel("light_lux"))
        assertEquals("Pressure (hPa)", sensorLabel("pressure_hpa"))
        assertEquals("Accelerometer (m/s²)", sensorLabel("accelerometer"))
        assertEquals("Device", sensorLabel("device"))
        assertEquals("Soil moisture", sensorLabel("soil_moisture"))
    }
}
