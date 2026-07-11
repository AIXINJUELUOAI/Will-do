package com.antgskds.calendarassistant.ui.contract

import android.net.Uri
import com.antgskds.calendarassistant.core.center.ImportMode
import com.antgskds.calendarassistant.core.center.ParsedCourseImport
import com.antgskds.calendarassistant.data.model.AppBackupImportResult
import com.antgskds.calendarassistant.data.model.AppBackupOptions
import com.antgskds.calendarassistant.ui.viewmodel.MainViewModel
import com.antgskds.calendarassistant.ui.viewmodel.SettingsViewModel

class BackupUiController(
    private val settingsViewModel: SettingsViewModel,
    private val mainViewModel: MainViewModel
) {
    val settings = settingsViewModel.settings
    val promptLocalVersion = mainViewModel.promptLocalVersion
    val promptSource = mainViewModel.promptSource
    val promptCheckInProgress = mainViewModel.promptCheckInProgress
    val promptCheckFeedback = mainViewModel.promptCheckFeedback

    fun getAttachmentCount() = settingsViewModel.getAttachmentCount()
    fun estimateAttachmentBytes() = settingsViewModel.estimateAttachmentBytes()
    fun getCoursesCount() = settingsViewModel.getCoursesCount()
    suspend fun exportCoursesData() = settingsViewModel.exportCoursesData()
    suspend fun importCoursesData(content: String) = settingsViewModel.importCoursesData(content)
    suspend fun parseExternalCourseImport(content: String) = settingsViewModel.parseExternalCourseImport(content)
    suspend fun fetchWakeUpShareImport(text: String) = settingsViewModel.fetchWakeUpShareImport(text)
    fun importParsedCourseImport(parsed: ParsedCourseImport, mode: ImportMode, importSettings: Boolean, callback: suspend (Result<Int>) -> Unit) =
        settingsViewModel.importParsedCourseImport(parsed, mode, importSettings, callback)
    suspend fun exportBackupData(options: AppBackupOptions) = settingsViewModel.exportBackupData(options)
    suspend fun exportBackupZip(uri: Uri, options: AppBackupOptions) = settingsViewModel.exportBackupZip(uri, options)
    suspend fun importBackupJson(content: String, options: AppBackupOptions): Result<AppBackupImportResult> = settingsViewModel.importBackupJson(content, options)
    suspend fun importBackupZip(uri: Uri, options: AppBackupOptions): Result<AppBackupImportResult> = settingsViewModel.importBackupZip(uri, options)
    fun checkPromptUpdatesManually() = mainViewModel.checkPromptUpdatesManually()
    fun refreshPromptInfo() = mainViewModel.refreshPromptInfo()
}
