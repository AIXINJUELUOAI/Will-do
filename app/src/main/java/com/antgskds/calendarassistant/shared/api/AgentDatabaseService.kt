package com.antgskds.calendarassistant.shared.api

import android.content.Context
import android.database.Cursor
import com.antgskds.calendarassistant.shared.util.AppLogger as Log
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteStatement
import com.antgskds.calendarassistant.feature.schedule.application.ScheduleFacade
import com.antgskds.calendarassistant.feature.schedule.data.db.EventsDatabase
import com.antgskds.calendarassistant.shared.operation.AgentDatabaseColumn
import com.antgskds.calendarassistant.shared.operation.AgentDatabaseDelete
import com.antgskds.calendarassistant.shared.operation.AgentDatabaseMutationResult
import com.antgskds.calendarassistant.shared.operation.AgentDatabasePage
import com.antgskds.calendarassistant.shared.operation.AgentDatabaseQuery
import com.antgskds.calendarassistant.shared.operation.AgentDatabaseTable
import com.antgskds.calendarassistant.shared.operation.AgentDatabaseUpdate
import com.antgskds.calendarassistant.shared.query.SettingsQueryApi

/** Developer-only row maintenance. Schema-changing SQL is intentionally not exposed. */
class AgentDatabaseService(
    context: Context,
    private val settingsQueryApi: SettingsQueryApi,
    private val scheduleFacade: ScheduleFacade,
) {
    private val roomDatabase = EventsDatabase.getInstance(context.applicationContext)

    fun listTables(): List<AgentDatabaseTable> = withAccess {
        val database = roomDatabase.openHelper.writableDatabase
        database.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0)
                    if (name.isAgentVisibleTable()) add(readTable(database, name))
                }
            }
        }
    }

    fun query(input: AgentDatabaseQuery): AgentDatabasePage = withAccess {
        val database = roomDatabase.openHelper.writableDatabase
        val table = requireTable(database, input.table)
        val limit = input.limit.coerceIn(1, MAX_QUERY_ROWS)
        val offset = input.offset.coerceAtLeast(0)
        val maxCellChars = input.maxCellChars.coerceIn(MIN_CELL_CHARS, MAX_CELL_CHARS)
        val columnsByName = table.columns.associateBy { it.name }
        val whereParts = mutableListOf<String>()
        val args = mutableListOf<Any?>()
        input.filters.forEach { (name, value) ->
            val column = columnsByName[name] ?: throw IllegalArgumentException("Unknown column $name")
            require(!column.isBlob()) { "BLOB columns cannot be used as filters" }
            if (value == null) {
                whereParts += "${name.quoted()} IS NULL"
            } else {
                whereParts += "${name.quoted()} = ?"
                args += column.parseValue(value)
            }
        }
        val orderColumns = table.primaryKeyColumns.ifEmpty { table.columns.take(1).map { it.name } }
        val sql = buildString {
            append("SELECT * FROM ${table.name.quoted()}")
            if (whereParts.isNotEmpty()) append(" WHERE ${whereParts.joinToString(" AND ")}")
            if (orderColumns.isNotEmpty()) append(" ORDER BY ${orderColumns.joinToString { it.quoted() }}")
            append(" LIMIT ? OFFSET ?")
        }
        args += limit + 1
        args += offset
        var truncatedCellCount = 0
        val rows = database.query(SimpleSQLiteQuery(sql, args.toTypedArray())).use { cursor ->
            buildList {
                while (cursor.moveToNext() && size <= limit) {
                    add(
                        buildMap {
                            cursor.columnNames.forEachIndexed { index, name ->
                                val cell = cursor.readCell(index, maxCellChars)
                                if (cell.truncated) truncatedCellCount += 1
                                put(name, cell.value)
                            }
                        }
                    )
                }
            }
        }
        AgentDatabasePage(
            table = table.name,
            columns = table.columns,
            primaryKeyColumns = table.primaryKeyColumns,
            rows = rows.take(limit),
            offset = offset,
            limit = limit,
            hasMore = rows.size > limit,
            truncatedCellCount = truncatedCellCount,
        )
    }

    fun update(input: AgentDatabaseUpdate): AgentDatabaseMutationResult = withAccess {
        val database = roomDatabase.openHelper.writableDatabase
        val table = requireTable(database, input.table)
        val columnsByName = table.columns.associateBy { it.name }
        validateKey(table, input.key)
        require(input.values.isNotEmpty()) { "No values were supplied" }
        input.values.keys.forEach { name ->
            val column = columnsByName[name] ?: throw IllegalArgumentException("Unknown column $name")
            require(name !in table.primaryKeyColumns) { "Primary key columns cannot be updated" }
            require(!column.isBlob()) { "BLOB columns cannot be updated through Agent API" }
        }
        val assignments = input.values.keys.joinToString { "${it.quoted()} = ?" }
        val where = table.primaryKeyColumns.joinToString(" AND ") { "${it.quoted()} = ?" }
        val statement = database.compileStatement(
            "UPDATE ${table.name.quoted()} SET $assignments WHERE $where"
        )
        var bindIndex = 1
        input.values.forEach { (name, value) ->
            bindIndex = statement.bind(columnsByName.getValue(name), value, bindIndex)
        }
        table.primaryKeyColumns.forEach { name ->
            bindIndex = statement.bind(columnsByName.getValue(name), input.key.getValue(name), bindIndex)
        }
        val affected = executeMutation(database, statement)
        Log.w(TAG, "Agent updated table=${table.name} rows=$affected")
        if (affected > 0) scheduleFacade.refreshAll()
        AgentDatabaseMutationResult(table.name, affected)
    }

    fun delete(input: AgentDatabaseDelete): AgentDatabaseMutationResult = withAccess {
        require(input.keys.isNotEmpty()) { "No row keys were supplied" }
        require(input.keys.size <= MAX_MUTATION_ROWS) { "At most $MAX_MUTATION_ROWS rows can be deleted at once" }
        val database = roomDatabase.openHelper.writableDatabase
        val table = requireTable(database, input.table)
        val columnsByName = table.columns.associateBy { it.name }
        input.keys.forEach { validateKey(table, it) }
        val where = table.primaryKeyColumns.joinToString(" AND ") { "${it.quoted()} = ?" }
        var affected = 0
        database.beginTransaction()
        try {
            input.keys.forEach { key ->
                val statement = database.compileStatement("DELETE FROM ${table.name.quoted()} WHERE $where")
                var bindIndex = 1
                table.primaryKeyColumns.forEach { name ->
                    bindIndex = statement.bind(columnsByName.getValue(name), key.getValue(name), bindIndex)
                }
                affected += statement.executeUpdateDelete()
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        Log.w(TAG, "Agent deleted table=${table.name} rows=$affected")
        if (affected > 0) scheduleFacade.refreshAll()
        AgentDatabaseMutationResult(table.name, affected)
    }

    private fun requireTable(database: SupportSQLiteDatabase, name: String): AgentDatabaseTable {
        require(name.isAgentVisibleTable()) { "Table $name is not available" }
        val exists = database.query(
            SimpleSQLiteQuery(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1",
                arrayOf(name),
            )
        ).use { it.moveToFirst() }
        require(exists) { "Table $name does not exist" }
        return readTable(database, name)
    }

    private fun readTable(database: SupportSQLiteDatabase, name: String): AgentDatabaseTable {
        val columns = database.query("PRAGMA table_info(${name.quoted()})").use { cursor ->
            buildList {
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                val typeIndex = cursor.getColumnIndexOrThrow("type")
                val notNullIndex = cursor.getColumnIndexOrThrow("notnull")
                val pkIndex = cursor.getColumnIndexOrThrow("pk")
                while (cursor.moveToNext()) {
                    add(
                        AgentDatabaseColumn(
                            name = cursor.getString(nameIndex),
                            type = cursor.getString(typeIndex).orEmpty(),
                            notNull = cursor.getInt(notNullIndex) != 0,
                            primaryKeyPosition = cursor.getInt(pkIndex),
                        )
                    )
                }
            }
        }
        val rowCount = database.query("SELECT COUNT(*) FROM ${name.quoted()}").use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }
        return AgentDatabaseTable(
            name = name,
            columns = columns,
            primaryKeyColumns = columns.filter { it.primaryKeyPosition > 0 }
                .sortedBy { it.primaryKeyPosition }
                .map { it.name },
            rowCount = rowCount,
        )
    }

    private fun validateKey(table: AgentDatabaseTable, key: Map<String, String>) {
        require(table.primaryKeyColumns.isNotEmpty()) { "Table ${table.name} has no primary key" }
        require(key.keys == table.primaryKeyColumns.toSet()) {
            "Row key must contain exactly: ${table.primaryKeyColumns.joinToString()}"
        }
    }

    private fun executeMutation(database: SupportSQLiteDatabase, statement: SupportSQLiteStatement): Int {
        database.beginTransaction()
        return try {
            val affected = statement.executeUpdateDelete()
            database.setTransactionSuccessful()
            affected
        } finally {
            database.endTransaction()
        }
    }

    private fun SupportSQLiteStatement.bind(
        column: AgentDatabaseColumn,
        rawValue: String?,
        index: Int,
    ): Int {
        if (rawValue == null) {
            require(!column.notNull) { "${column.name} cannot be null" }
            bindNull(index)
            return index + 1
        }
        when {
            column.isInteger() -> bindLong(
                index,
                rawValue.toLongOrNull() ?: throw IllegalArgumentException("${column.name} requires an integer"),
            )
            column.isReal() -> bindDouble(
                index,
                rawValue.toDoubleOrNull() ?: throw IllegalArgumentException("${column.name} requires a number"),
            )
            column.isBlob() -> throw IllegalArgumentException("BLOB values are not supported")
            else -> bindString(index, rawValue)
        }
        return index + 1
    }

    private fun AgentDatabaseColumn.parseValue(value: String): Any = when {
        isInteger() -> value.toLongOrNull() ?: throw IllegalArgumentException("$name requires an integer")
        isReal() -> value.toDoubleOrNull() ?: throw IllegalArgumentException("$name requires a number")
        else -> value
    }

    private fun AgentDatabaseColumn.isInteger(): Boolean = type.uppercase().contains("INT")
    private fun AgentDatabaseColumn.isReal(): Boolean {
        val normalized = type.uppercase()
        return normalized.contains("REAL") || normalized.contains("FLOA") || normalized.contains("DOUB")
    }
    private fun AgentDatabaseColumn.isBlob(): Boolean = type.uppercase().contains("BLOB")

    private fun Cursor.readCell(index: Int, maxChars: Int): CellValue = when (getType(index)) {
        Cursor.FIELD_TYPE_NULL -> CellValue(null, false)
        Cursor.FIELD_TYPE_INTEGER -> CellValue(getLong(index).toString(), false)
        Cursor.FIELD_TYPE_FLOAT -> CellValue(getDouble(index).toString(), false)
        Cursor.FIELD_TYPE_BLOB -> CellValue("<BLOB:${getBlob(index).size} bytes>", false)
        else -> {
            val value = getString(index).orEmpty()
            if (value.length <= maxChars) CellValue(value, false)
            else CellValue(value.take(maxChars), true)
        }
    }

    private inline fun <T> withAccess(block: () -> T): T {
        val settings = settingsQueryApi.settings.value
        check(settings.agentApiEnabled) { "Agent API is disabled" }
        if (!settings.agentDatabaseOperationsEnabled) {
            throw SecurityException("Agent database operations are disabled")
        }
        return block()
    }

    private fun String.isAgentVisibleTable(): Boolean =
        isNotBlank() && !startsWith("sqlite_") && this != "room_master_table" && this != "android_metadata"

    private fun String.quoted(): String = "\"${replace("\"", "\"\"")}\""

    private data class CellValue(val value: String?, val truncated: Boolean)

    private companion object {
        const val TAG = "WillDoAgentDatabase"
        const val MAX_QUERY_ROWS = 100
        const val MAX_MUTATION_ROWS = 200
        const val MIN_CELL_CHARS = 256
        const val MAX_CELL_CHARS = 100_000
    }
}
