package com.antgskds.calendarassistant.shared.util

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PrivilegeManagerTest {
    @Test fun rootRequestTakesPriorityOverShizuku() = runBlocking {
        var shizukuRequested = false
        val result = PrivilegeManager.requestPreferredAccess(
            rootAvailable = true,
            requestRoot = { true },
            requestShizuku = { shizukuRequested = true; PrivilegeManager.AccessRequestResult.SHIZUKU_GRANTED },
        )
        assertEquals(PrivilegeManager.AccessRequestResult.ROOT_GRANTED, result)
        assertFalse(shizukuRequested)
    }

    @Test fun rootDenialDoesNotRequestAnotherPrivilege() = runBlocking {
        var shizukuRequested = false
        val result = PrivilegeManager.requestPreferredAccess(
            rootAvailable = true,
            requestRoot = { false },
            requestShizuku = { shizukuRequested = true; PrivilegeManager.AccessRequestResult.SHIZUKU_GRANTED },
        )
        assertEquals(PrivilegeManager.AccessRequestResult.ROOT_DENIED, result)
        assertFalse(shizukuRequested)
    }

    @Test fun unrootedDevicePreservesShizukuGrantDenialAndUnavailableResults() = runBlocking {
        for (shizukuResult in listOf(
            PrivilegeManager.AccessRequestResult.SHIZUKU_GRANTED,
            PrivilegeManager.AccessRequestResult.SHIZUKU_DENIED,
            PrivilegeManager.AccessRequestResult.SHIZUKU_UNAVAILABLE,
        )) {
            val result = PrivilegeManager.requestPreferredAccess(
                rootAvailable = false,
                requestRoot = { error("Unrooted device must not invoke su") },
                requestShizuku = { shizukuResult },
            )
            assertEquals(shizukuResult, result)
        }
    }
}
