package com.antgskds.calendarassistant

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.antgskds.calendarassistant.feature.schedule.data.db.EventsDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** 使用独立测试数据库，验证覆盖升级、数据保留与第二次打开；不操作应用的 events.db。 */
@RunWith(AndroidJUnit4::class)
class Database17CompatibilityTest {
    @Test
    fun version15CreatesEmptyAccountingTableAndKeepsNotes() = verifyUpgrade(15)

    @Test
    fun version16KeepsAccountingRowsAndNotes() = verifyUpgrade(16)

    @Test
    fun version17AddsDraftsAndKeepsAccountingRows() = verifyUpgrade(17)

    @Test
    fun version18PreservesDraftsAndMarksOldTransactionTypesUnknown() = verifyUpgrade(18)

    @Test
    fun version19AddsNullableScreenshotsWithoutChangingBillsOrDrafts() = verifyUpgrade(19)

    private fun verifyUpgrade(oldVersion: Int) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "database17-test-${UUID.randomUUID()}.db"
        fun openDatabase(withMigrations: Boolean): EventsDatabase {
            val builder = Room.databaseBuilder(context, EventsDatabase::class.java, name)
                .allowMainThreadQueries()
            if (withMigrations) {
                builder.addMigrations(EventsDatabase.MIGRATION_15_17, EventsDatabase.MIGRATION_16_17, EventsDatabase.MIGRATION_17_18, EventsDatabase.MIGRATION_18_19, EventsDatabase.MIGRATION_19_20)
            }
            return builder.build()
        }

        try {
            val fixture = openDatabase(withMigrations = false)
            try {
                val db = fixture.openHelper.writableDatabase
                db.execSQL("DROP TABLE accounting_drafts")
                db.execSQL("DROP TABLE accounting_entries")
                db.execSQL("""INSERT INTO notes (id, title, plain_text, document_json, created_at, updated_at)
                    VALUES (1, 'keep-note', 'keep-body', '{}', 1, 1)""".trimIndent())
                if (oldVersion >= 16) {
                    // 重新建立真实旧结构，避免当前版本新增列混入旧版本测试夹具。
                    EventsDatabase.MIGRATION_15_17.migrate(db)
                    db.execSQL("""INSERT INTO accounting_entries
                        (id, amountMinor, direction, currency, merchant, category, note, occurredAt,
                         zoneId, source, channel, transactionId, status, ruleId, createdAt, updatedAt)
                        VALUES ('keep-entry', 1234, 'EXPENSE', 'CNY', 'test', 'other', '', 1,
                                'Asia/Shanghai', 'MANUAL', '', '', 'CONFIRMED', '', 1, 1)""".trimIndent())
                }
                if (oldVersion >= 18) {
                    EventsDatabase.MIGRATION_17_18.migrate(db)
                    db.execSQL("""INSERT INTO accounting_drafts
                        (id, amount, direction, currency, merchant, category, note, occurredAt, zoneId,
                         channel, transactionId, paymentStatus, sourceType, sourceId, createdAt)
                        VALUES ('keep-draft', '35.00', 'EXPENSE', 'CNY', '午饭', '', '', '', '',
                                '微信支付', 'old-id', 'COMPLETED', 'image', 'test', 1)""".trimIndent())
                }
                if (oldVersion >= 19) EventsDatabase.MIGRATION_18_19.migrate(db)
                db.execSQL("UPDATE room_master_table SET identity_hash = 'old-version-fixture' WHERE id = 42")
                db.version = oldVersion
            } finally {
                fixture.close()
            }

            // 第二次打开也必须成功，确认 Room 已写回正确的版本与结构标识。
            repeat(2) {
                val upgraded = openDatabase(withMigrations = true)
                try {
                    val db = upgraded.openHelper.writableDatabase
                    assertEquals(20, db.version)
                    db.query("SELECT COUNT(*) FROM accounting_drafts").use { cursor ->
                        assertTrue(cursor.moveToFirst()); assertEquals(if (oldVersion >= 18) 1 else 0, cursor.getInt(0))
                    }
                    db.query("SELECT title, plain_text FROM notes WHERE id = 1").use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertEquals("keep-note", cursor.getString(0))
                        assertEquals("keep-body", cursor.getString(1))
                    }
                    db.query("SELECT COUNT(*) FROM accounting_entries").use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertEquals(if (oldVersion >= 16) 1 else 0, cursor.getInt(0))
                    }
                    if (oldVersion >= 16) {
                        db.query("SELECT amountMinor, transactionIdType, sourceImagePath FROM accounting_entries WHERE id = 'keep-entry'").use { cursor ->
                            assertTrue(cursor.moveToFirst())
                            assertEquals(1234L, cursor.getLong(0))
                            assertEquals("UNKNOWN", cursor.getString(1))
                            assertTrue(cursor.isNull(2))
                        }
                    }
                    if (oldVersion >= 18) {
                        db.query("SELECT transactionId, transactionIdType, sourceImagePath FROM accounting_drafts WHERE id = 'keep-draft'").use { cursor ->
                            assertTrue(cursor.moveToFirst())
                            assertEquals("old-id", cursor.getString(0))
                            assertEquals("UNKNOWN", cursor.getString(1))
                            assertTrue(cursor.isNull(2))
                        }
                    }
                } finally {
                    upgraded.close()
                }
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
