package com.antgskds.calendarassistant.feature.home.domain

import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class HomeCalendarViewPolicyTest {
    @Test fun oldSettingsWithoutViewFieldsUseSafeDefaults() {
        val settings = Json.decodeFromString<MySettings>("{}")
        assertTrue(settings.rememberCalendarViewMode)
        assertEquals("TODAY", settings.homeCalendarViewMode)
    }

    @Test fun manualViewSurvivesSettingsSerialization() {
        val settings = MySettings(homeCalendarViewMode = "MONTH", rememberCalendarViewMode = false)
        val restored = Json.decodeFromString<MySettings>(Json.encodeToString(MySettings.serializer(), settings))
        assertEquals("MONTH", restored.homeCalendarViewMode)
        assertFalse(restored.rememberCalendarViewMode)
    }

    @Test fun layoutAndModuleFallbackDoNotDestroyThePreference() {
        val saved = "COURSE"
        assertEquals("COURSE", HomeCalendarViewPolicy.resolve(saved, true, true))
        assertEquals("WEEK", HomeCalendarViewPolicy.resolve(saved, true, false))
        assertEquals("TODAY", HomeCalendarViewPolicy.resolve(saved, false, true))
        assertEquals("COURSE", HomeCalendarViewPolicy.resolve(saved, true, true))
        assertEquals("WEEK", HomeCalendarViewPolicy.resolve("AGENDA", true, true))
        assertEquals("TODAY", HomeCalendarViewPolicy.resolve("unknown", false, false))
    }
}
