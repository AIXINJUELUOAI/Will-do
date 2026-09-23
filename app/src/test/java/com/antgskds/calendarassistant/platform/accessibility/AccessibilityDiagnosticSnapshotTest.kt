package com.antgskds.calendarassistant.platform.accessibility

import android.accessibilityservice.AccessibilityServiceInfo
import org.junit.Assert.*
import org.junit.Test

class AccessibilityDiagnosticSnapshotTest {
    @Test fun firstConnectionOnlyRemovesTheManagedKeyFlag() {
        val original = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
            AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        assertEquals(original, AccessibilityDiagnosticSnapshot.connectionBaseFlags(
            null, original or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS,
        ))
    }

    @Test fun reconnectDuringDiagnosticsDoesNotPromoteTemporaryFlagsToBaseline() {
        val original = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        val diagnostic = original or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        assertEquals(original, AccessibilityDiagnosticSnapshot.connectionBaseFlags(original, diagnostic))
        assertEquals(0, AccessibilityDiagnosticSnapshot.connectionBaseFlags(0, diagnostic))
    }

    @Test fun historyOnlyIncludesDedicatedHealthRecords() {
        val before = "2026-09-23 10:00:00.000 INFO/WillDoAccessHealth: connected"
        val after = "2026-09-23 10:01:00.000 WARN/WillDoAccessHealth: configuration_unavailable"
        val mixed = listOf(before, "INFO/WillDoAccounting: business record", "stack trace", after).joinToString("\n")
        assertEquals(listOf(before, after).joinToString("\n"), AccessibilityDiagnosticSnapshot.healthHistory(mixed))
        assertEquals("", AccessibilityDiagnosticSnapshot.healthHistory(""))
    }

    @Test fun missingServiceConfigurationIsExplicitRatherThanHealthy() {
        assertFalse(AccessibilityDiagnosticSnapshot.serviceConfiguration(null).getBoolean("available"))
    }
}
