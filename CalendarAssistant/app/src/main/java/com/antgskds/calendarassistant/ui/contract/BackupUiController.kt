package com.antgskds.calendarassistant.ui.contract

import android.net.Uri
import com.antgskds.calendarassistant.core.ai.AiPrompts
import com.antgskds.calendarassistant.core.center.ImportMode
import com.antgskds.calendarassistant.core.center.ParsedCourseImport
import com.antgskds.calendarassistant.data.model.AppBackupImportResult
import com.antgskds.calendarassistant.data.model.AppBackupOptions
import com.antgskds.calendarassistant.data.model.MySettings
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface BackupUiController {
    val settings: StateFlow<MySettings>
    val promptLocalVersion: StateFlow<Int>
    val promptSource: StateFlow<AiPrompts.PromptSource>
    val promptCheckInProgress: StateFlow<Boolean>
    val promptCheckFeedback: SharedFlow<PromptCheckFeedback>
    fun getAttachmentCount(): Int
    fun estimateAttachmentBytes(): Long
    fun getCoursesCount(): Int
    suspend fun exportCoursesData(): String
    suspend fun importCoursesData(content: String): Result<Unit>
    suspend fun parseExternalCourseImport(content: String): Result<ParsedCourseImport>
    suspend fun fetchWakeUpShareImport(text: String): Result<ParsedCourseImport>
    fun importParsedCourseImport(parsed: ParsedCourseImport, mode: ImportMode, importSettings: Boolean, callback: suspend (Result<Int>) -> Unit)
    suspend fun exportBackupData(options: AppBackupOptions): String
    suspend fun exportBackupZip(uri: Uri, options: AppBackupOptions)
    suspend fun importBackupJson(content: String, options: AppBackupOptions): Result<AppBackupImportResult>
    suspend fun importBackupZip(uri: Uri, options: AppBackupOptions): Result<AppBackupImportResult>
    fun checkPromptUpdatesManually()
    fun refreshPromptInfo()
}
