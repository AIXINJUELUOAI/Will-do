package com.antgskds.calendarassistant.feature.settings.developer.application

import com.antgskds.calendarassistant.feature.schedule.domain.model.EventTags
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoModeDataFactoryTest {
    private val today = LocalDate.of(2026, 9, 21)

    @Test
    fun createsEnoughReadOnlyContentForEveryDemoPage() {
        val events = DemoModeDataFactory.events(today)
        val weather = DemoModeDataFactory.weather(today)

        assertTrue(events.count { it.tag != EventTags.COURSE } >= 25)
        assertEquals(12, events.count { it.tag == EventTags.COURSE })
        assertTrue(events.all { (it.id ?: 0L) < 0L })
        assertEquals(6, DemoModeDataFactory.quickMemos(today).size)
        assertEquals(15, DemoModeDataFactory.accountingEntries(today).size)
        assertEquals(24, weather.hourlyForecast.size)
        assertEquals(7, weather.dailyForecast.size)
        assertTrue(weather.alerts.isNotEmpty())
        assertTrue(weather.riskAlerts.isNotEmpty())
    }

    @Test
    fun demoVoiceUsesOnlyASyntheticPlaybackIdentifier() {
        val memos = DemoModeDataFactory.quickMemos(today)
        val voice = memos.first { it.type == com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoType.VOICE }
        assertEquals(DemoModeDataFactory.QUICK_MEMO_AUDIO_PATH, voice.audioPath)
        assertTrue(voice.audioPath!!.startsWith("demo://"))
        assertTrue(voice.audioDurationMs > 0L)
        assertTrue(voice.bodyText.contains("演示语音"))
        assertTrue(memos.all { (it.id ?: 0L) < 0L })
    }

    @Test
    fun courseScheduleUsesTwoOrThreeClassesOnWeekdaysAndNoneOnWeekends() {
        val zone = ZoneId.systemDefault()
        val counts = DemoModeDataFactory.courses(today)
            .groupingBy {
                Instant.ofEpochSecond(it.startTS).atZone(zone).dayOfWeek
            }
            .eachCount()

        DayOfWeek.entries.take(5).forEach { day ->
            assertTrue(counts.getValue(day) in 2..3)
        }
        assertEquals(null, counts[DayOfWeek.SATURDAY])
        assertEquals(null, counts[DayOfWeek.SUNDAY])
    }
}
