package com.antgskds.calendarassistant.data.node.store

import com.antgskds.calendarassistant.core.importer.ImportMode
import com.antgskds.calendarassistant.data.model.Course
import com.antgskds.calendarassistant.data.model.ImportResult
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.data.model.MySettings

internal class StoreBackupWorkflowNode(
    private val backupNode: StoreBackupNode,
    private val currentCourses: () -> List<Course>,
    private val activeEvents: () -> List<MyEvent>,
    private val archivedEvents: () -> List<MyEvent>,
    private val currentSettings: () -> MySettings,
    private val ensureArchivesLoaded: suspend () -> Unit,
    private val updateSettings: (MySettings) -> Unit,
    private val saveCourses: suspend (List<Course>) -> Unit,
    private val loadCurrentActiveMutableList: () -> MutableList<MyEvent>,
    private val updateEvents: suspend (List<MyEvent>) -> Unit,
    private val scheduleRemindersIfNeeded: (MyEvent) -> Unit,
    private val loadCurrentArchivedMutableList: suspend () -> MutableList<MyEvent>,
    private val updateArchivedEvents: suspend (List<MyEvent>) -> Unit,
    private val upsertRecurringEvents: suspend (List<MyEvent>) -> Unit,
    private val archiveEvent: suspend (String) -> Unit,
    private val restoreEvent: suspend (String) -> Unit,
    private val triggerAutoSync: suspend () -> Unit
) {
    suspend fun exportCoursesData(): String {
        return backupNode.exportCoursesData(courses = currentCourses(), settings = currentSettings())
    }

    suspend fun importCoursesData(jsonString: String): Result<Unit> {
        return backupNode.importCoursesData(
            jsonString = jsonString,
            saveCourses = { saveCourses(it) },
            applyImportedSemesterSettings = { semesterStartDate, totalWeeks ->
                applyImportedSemesterSettings(semesterStartDate, totalWeeks)
            },
            applyImportedCourseBackupSettings = { data ->
                applyImportedCourseBackupSettings(data)
            }
        )
    }

    suspend fun importWakeUpFile(content: String, mode: ImportMode, importSettings: Boolean): Result<Int> {
        return backupNode.importWakeUpFile(
            content = content,
            mode = mode,
            importSettings = importSettings,
            currentCourses = { currentCourses() },
            saveCourses = { saveCourses(it) },
            applyImportedSemesterSettings = { semesterStartDate, totalWeeks ->
                applyImportedSemesterSettings(semesterStartDate, totalWeeks)
            }
        )
    }

    suspend fun exportEventsData(): String {
        ensureArchivesLoaded()
        return backupNode.exportEventsData(activeEvents(), archivedEvents())
    }

    suspend fun importEventsData(
        jsonString: String,
        preserveArchivedStatus: Boolean = true
    ): Result<ImportResult> {
        ensureArchivesLoaded()
        return backupNode.importEventsData(
            jsonString = jsonString,
            preserveArchivedStatus = preserveArchivedStatus,
            existingActiveEvents = { activeEvents() },
            existingArchivedEvents = { archivedEvents() },
            appendImportedActiveEvents = { appendImportedActiveEvents(it) },
            appendImportedArchivedEvents = { appendImportedArchivedEvents(it) },
            upsertRecurringEvents = { upsertRecurringEvents(it) },
            archiveEvent = { id -> archiveEvent(id) },
            restoreEvent = { id -> restoreEvent(id) },
            triggerAutoSync = { triggerAutoSync() }
        )
    }

    private fun applyImportedSemesterSettings(semesterStartDate: String?, totalWeeks: Int?): String? {
        if (semesterStartDate == null && totalWeeks == null) return null
        val (newSettings, normalizedDate) = backupNode.buildImportedSemesterSettings(
            currentSettings = currentSettings(),
            semesterStartDate = semesterStartDate,
            totalWeeks = totalWeeks
        )
        updateSettings(newSettings)
        return normalizedDate
    }

    private fun applyImportedCourseBackupSettings(data: CoursesBackupData) {
        val newSettings = backupNode.buildImportedCourseBackupSettings(
            currentSettings = currentSettings(),
            data = data
        )
        updateSettings(newSettings)
    }

    private suspend fun appendImportedActiveEvents(newActiveEvents: List<MyEvent>) {
        if (newActiveEvents.isEmpty()) return
        val currentActive = loadCurrentActiveMutableList()
        val uniqueNewActive = backupNode.appendImportedActiveEvents(newActiveEvents, currentActive)
        updateEvents(currentActive)
        uniqueNewActive.forEach(scheduleRemindersIfNeeded)
    }

    private suspend fun appendImportedArchivedEvents(newArchivedEvents: List<MyEvent>) {
        if (newArchivedEvents.isEmpty()) return
        val currentArchived = loadCurrentArchivedMutableList()
        backupNode.appendImportedArchivedEvents(newArchivedEvents, currentArchived)
        updateArchivedEvents(currentArchived)
    }
}
