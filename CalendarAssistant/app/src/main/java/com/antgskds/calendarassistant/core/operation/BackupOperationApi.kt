package com.antgskds.calendarassistant.core.operation

import com.antgskds.calendarassistant.core.importer.ImportMode
import com.antgskds.calendarassistant.data.model.ImportResult

interface BackupOperationApi {
    suspend fun exportCoursesData(): String
    suspend fun importCoursesData(jsonString: String): Result<Unit>
    suspend fun exportEventsData(): String
    suspend fun importEventsData(jsonString: String): Result<ImportResult>
    suspend fun importWakeUpFile(content: String, mode: ImportMode, importSettings: Boolean): Result<Int>
}
