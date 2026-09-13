package com.antgskds.calendarassistant.shared.util

import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import java.io.File
import java.io.RandomAccessFile
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppLoggerTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun appendsAllSourcesToSameDayAndRetainsThreeCalendarDays() {
        val dir = temp.newFolder()
        for (day in 10..13) {
            AppLogger.appendRecord(dir, LocalDate.of(2026, 9, day), "INFO recognition\n".toByteArray(), true)
            AppLogger.appendRecord(dir, LocalDate.of(2026, 9, day), "ERROR crash\n".toByteArray(), true)
        }
        assertEquals(listOf("log-2026-09-11.log", "log-2026-09-12.log", "log-2026-09-13.log"), dir.list()!!.sorted())
        assertEquals("INFO recognition\nERROR crash\n", File(dir, "log-2026-09-13.log").readText())
    }

    @Test fun disabledRecordingCleansExpiredButPreservesExistingAndExports() {
        val dir = temp.newFolder()
        File(dir, "log-2026-08-29.log").writeText("expired")
        File(dir, "log-2026-08-31.log").writeText("keep")
        File(dir, "willdo_log_export.txt").writeText("user export")
        AppLogger.appendRecord(dir, LocalDate.of(2026, 9, 1), "ignored".toByteArray(), false)
        assertFalse(File(dir, "log-2026-08-29.log").exists())
        assertEquals("keep", File(dir, "log-2026-08-31.log").readText())
        assertFalse(File(dir, "log-2026-09-01.log").exists())
        assertEquals("user export", File(dir, "willdo_log_export.txt").readText())
        AppLogger.appendRecord(dir, LocalDate.of(2026, 9, 1), "resumed".toByteArray(), true)
        assertEquals("resumed", File(dir, "log-2026-09-01.log").readText())
    }

    @Test fun sizeLimitDoesNotEraseEarlierRecordsAndNextDayStillWrites() {
        val dir = temp.newFolder()
        val file = File(dir, "log-2026-09-13.log")
        RandomAccessFile(file, "rw").use {
            it.writeUTF("original")
            it.setLength(ConfigCatalog.LOG_MAX_BYTES)
        }
        AppLogger.appendRecord(dir, LocalDate.of(2026, 9, 13), "overflow".toByteArray(), true)
        assertEquals(ConfigCatalog.LOG_MAX_BYTES, file.length())
        RandomAccessFile(file, "r").use { assertEquals("original", it.readUTF()) }
        AppLogger.appendRecord(dir, LocalDate.of(2026, 9, 14), "new day".toByteArray(), true)
        assertEquals("new day", File(dir, "log-2026-09-14.log").readText())
    }

    @Test fun longAbsenceDeletesAllExpiredDaysWithoutCreatingEmptyInterveningFiles() {
        val dir = temp.newFolder()
        File(dir, "log-2025-12-31.log").writeText("old")
        AppLogger.appendRecord(dir, LocalDate.of(2026, 1, 10), "today".toByteArray(), true)
        assertEquals(listOf("log-2026-01-10.log"), dir.list()!!.toList())
    }
}
