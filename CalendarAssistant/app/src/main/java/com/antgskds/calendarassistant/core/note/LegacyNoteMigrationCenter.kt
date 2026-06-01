package com.antgskds.calendarassistant.core.note

import android.content.Context
import com.antgskds.calendarassistant.calendar.data.EventsDatabase
import com.antgskds.calendarassistant.calendar.models.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LegacyNoteMigrationResult(
    val scanned: Int,
    val migrated: Int,
    val skipped: Int,
    val cleaned: Int = 0
)

class LegacyNoteMigrationCenter(
    context: Context,
    private val noteRepository: NoteRepository
) {
    private val appContext = context.applicationContext
    private val db = EventsDatabase.getInstance(appContext)
    private val prefs = appContext.getSharedPreferences("note_migration", Context.MODE_PRIVATE)

    suspend fun runAutoMigrationIfNeeded(): LegacyNoteMigrationResult = withContext(Dispatchers.IO) {
        if (prefs.getBoolean(KEY_AUTO_DONE, false)) {
            return@withContext LegacyNoteMigrationResult(0, 0, 0)
        }
        migrateLegacyNotes(force = false).also {
            prefs.edit().putBoolean(KEY_AUTO_DONE, true).apply()
        }
    }

    suspend fun migrateLegacyNotes(force: Boolean): LegacyNoteMigrationResult = withContext(Dispatchers.IO) {
        val events = db.eventsDao().getRetiredNoteEvents()
        val existingKeys = db.notesDao().getAllNotes().mapNotNull { it.legacyMigrationKey() }.toMutableSet()
        var migrated = 0
        var skipped = 0
        events.forEach { event ->
            val key = event.legacyMigrationKey()
            if (!force && key in existingKeys) {
                skipped++
                return@forEach
            }
            if (force && db.notesDao().getAllNotes().any { it.legacyMigrationKey() == key }) {
                skipped++
                return@forEach
            }
            val document = NoteDocument.fromPlainText(event.description.trimEnd()).withLegacyMigrationKey(key)
            noteRepository.saveNote(
                id = null,
                title = event.title.trim().ifBlank { "无标题" },
                document = document,
                createdAt = event.lastUpdated.takeIf { it > 0L }?.times(1000L)
            )
            existingKeys += key
            migrated++
        }
        LegacyNoteMigrationResult(events.size, migrated, skipped)
    }

    suspend fun cleanLegacyNoteEvents(): LegacyNoteMigrationResult = withContext(Dispatchers.IO) {
        val scanned = db.eventsDao().getRetiredNoteEvents().size
        val cleaned = db.eventsDao().deleteRetiredNoteEvents()
        LegacyNoteMigrationResult(scanned = scanned, migrated = 0, skipped = 0, cleaned = cleaned)
    }

    private fun Event.legacyMigrationKey(): String = "legacy-event:${id ?: "no-id"}:${title}:${lastUpdated}"

    private fun NoteEntity.legacyMigrationKey(): String? {
        return document().legacyMigrationKey.takeIf { it.isNotBlank() }
    }

    private fun NoteDocument.withLegacyMigrationKey(key: String): NoteDocument = copy(
        legacyMigrationKey = key
    )

    companion object {
        private const val KEY_AUTO_DONE = "legacy_note_auto_done"
    }
}
