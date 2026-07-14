package com.antgskds.calendarassistant.feature.backup.ui.connector

import android.net.Uri
import com.antgskds.calendarassistant.feature.backup.courseimport.ImportMode
import com.antgskds.calendarassistant.feature.backup.courseimport.ParsedCourseImport
import com.antgskds.calendarassistant.data.model.AppBackupImportResult
import com.antgskds.calendarassistant.data.model.AppBackupOptions
import com.antgskds.calendarassistant.feature.backup.ui.contract.BackupUiController
import com.antgskds.calendarassistant.ui.viewmodel.MainViewModel
import com.antgskds.calendarassistant.ui.viewmodel.SettingsViewModel

class BackupUiControllerAdapter(
    private val settingsViewModel: SettingsViewModel,
    private val mainViewModel: MainViewModel
) : BackupUiController {
    override val settings = settingsViewModel.settings
    override val promptLocalVersion = mainViewModel.promptLocalVersion
    override val promptSource = mainViewModel.promptSource
    override val promptCheckInProgress = mainViewModel.promptCheckInProgress
    override val promptCheckFeedback = mainViewModel.promptCheckFeedback
    override fun getAttachmentCount() = settingsViewModel.getAttachmentCount()
    override fun estimateAttachmentBytes() = settingsViewModel.estimateAttachmentBytes()
    override fun getCoursesCount() = settingsViewModel.getCoursesCount()
    override suspend fun exportCoursesData() = settingsViewModel.exportCoursesData()
    override suspend fun importCoursesData(content: String) = settingsViewModel.importCoursesData(content)
    override suspend fun parseExternalCourseImport(content: String) = settingsViewModel.parseExternalCourseImport(content)
    override suspend fun fetchWakeUpShareImport(text: String) = settingsViewModel.fetchWakeUpShareImport(text)
    override fun importParsedCourseImport(parsed: ParsedCourseImport, mode: ImportMode, importSettings: Boolean, callback: suspend (Result<Int>) -> Unit) = settingsViewModel.importParsedCourseImport(parsed, mode, importSettings, callback)
    override suspend fun exportBackupData(options: AppBackupOptions) = settingsViewModel.exportBackupData(options)
    override suspend fun exportBackupZip(uri: Uri, options: AppBackupOptions) = settingsViewModel.exportBackupZip(uri, options)
    override suspend fun importBackupJson(content: String, options: AppBackupOptions): Result<AppBackupImportResult> = settingsViewModel.importBackupJson(content, options)
    override suspend fun importBackupZip(uri: Uri, options: AppBackupOptions): Result<AppBackupImportResult> = settingsViewModel.importBackupZip(uri, options)
    override fun checkPromptUpdatesManually() = mainViewModel.checkPromptUpdatesManually()
    override fun refreshPromptInfo() = mainViewModel.refreshPromptInfo()
}
