package com.antgskds.calendarassistant.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LocationFixTest {
    @Test
    fun acceptsBoundaryCoordinates() {
        val fix = LocationFix(
            latitude = 90.0,
            longitude = -180.0,
            accuracyMeters = 10f,
            timestampMillis = 1L,
            provider = "test",
            source = LocationSource.LIVE
        )

        assertEquals(90.0, fix.latitude, 0.0)
        assertEquals(-180.0, fix.longitude, 0.0)
    }

    @Test
    fun rejectsLatitudeOutsideEarth() {
        assertThrows(IllegalArgumentException::class.java) {
            LocationFix(
                latitude = 90.1,
                longitude = 0.0,
                accuracyMeters = 10f,
                timestampMillis = 1L,
                provider = "test",
                source = LocationSource.LAST_KNOWN
            )
        }
    }
}
