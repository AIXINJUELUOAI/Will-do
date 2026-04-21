package com.antgskds.calendarassistant.core.center

import com.antgskds.calendarassistant.core.importer.ImportMode
import com.antgskds.calendarassistant.core.operation.BackupOperationApi
import com.antgskds.calendarassistant.data.model.ImportResult

class BackupCenter(
    private val backupOperationApi: BackupOperationApi
) {
    suspend fun exportCoursesData(): String {
        return backupOperationApi.exportCoursesData()
    }

    suspend fun importCoursesData(jsonString: String): Result<Unit> {
        return backupOperationApi.importCoursesData(jsonString)
    }

    suspend fun exportEventsData(): String {
        return backupOperationApi.exportEventsData()
    }

    suspend fun importEventsData(jsonString: String): Result<ImportResult> {
        return backupOperationApi.importEventsData(jsonString)
    }

    suspend fun importWakeUpFile(
        content: String,
        mode: ImportMode,
        importSettings: Boolean
    ): Result<Int> {
        return backupOperationApi.importWakeUpFile(content, mode, importSettings)
    }
}
