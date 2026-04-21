package com.antgskds.calendarassistant.data.node.store

import android.util.Log
import java.io.File

internal object StoreLegacyCleanupNode {
    fun cleanupLegacyJsonIfStable(
        appFilesDir: File,
        isRoomMainEnabled: () -> Boolean,
        eventsMigrated: () -> Boolean,
        archivesMigrated: () -> Boolean,
        recurringMigrated: () -> Boolean,
        legacyJsonFiles: List<String>
    ) {
        if (!isRoomMainEnabled()) return
        if (!eventsMigrated() || !archivesMigrated() || !recurringMigrated()) {
            return
        }

        val deleted = legacyJsonFiles.count { fileName ->
            val file = File(appFilesDir, fileName)
            if (!file.exists()) {
                return@count false
            }

            if (!file.delete()) {
                Log.w("StoreNode", "删除旧 JSON 失败: $fileName")
                return@count false
            }
            true
        }

        if (deleted > 0) {
            Log.i("StoreNode", "已清理 $deleted 个旧 JSON 文件")
        }
    }
}
