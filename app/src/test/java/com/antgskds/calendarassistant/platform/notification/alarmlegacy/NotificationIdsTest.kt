package com.antgskds.calendarassistant.platform.notification.alarmlegacy

import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Modifier

class NotificationIdsTest {
    @Test fun fixedNotificationIdsDoNotReplaceEachOther() {
        val ids = NotificationIds::class.java.fields
            .filter { it.type == Int::class.javaPrimitiveType && Modifier.isStatic(it.modifiers) }
            .map { it.getInt(null) }
        assertEquals("Different notification purposes must have distinct IDs", ids.size, ids.toSet().size)
    }
}
