package com.antgskds.calendarassistant.feature.backup.courseimport

import org.junit.Assert.*
import org.junit.Test

class WakeUpShareResponseTest {
    @Test fun newVersionRejectionExplainsFileImportInsteadOfUpgradingWillDo() {
        val error = assertThrows(IllegalStateException::class.java) {
            CourseImportParser.extractWakeUpShareData("""{"status":5000004,"message":"当前版本过低，请升级","data":{}}""")
        }
        assertTrue(error.message!!.contains("从文件"))
        assertFalse(error.message!!.contains("请升级"))
    }

    @Test fun legacySuccessAndExpiredShareMessagesArePreserved() {
        assertEquals("fixture", CourseImportParser.extractWakeUpShareData("""{"status":1,"data":"fixture"}"""))
        val expired = assertThrows(IllegalStateException::class.java) {
            CourseImportParser.extractWakeUpShareData("""{"status":0,"message":"分享口令已失效"}""")
        }
        assertEquals("分享口令已失效", expired.message)
    }

    @Test fun emptySuccessfulResponsesAreNotImported() {
        assertThrows(IllegalStateException::class.java) {
            CourseImportParser.extractWakeUpShareData("""{"status":1,"data":""}""")
        }
    }
}
