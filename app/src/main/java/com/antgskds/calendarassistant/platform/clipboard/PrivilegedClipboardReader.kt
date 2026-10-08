package com.antgskds.calendarassistant.platform.clipboard

import android.content.Context
import android.os.Build
import android.os.Process
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import com.antgskds.calendarassistant.shared.util.PrivilegeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

internal data class ClipboardProcessEvent(
    val type: String,
    val uid: Int,
    val result: String,
    val text: String? = null,
    val itemCount: Int = 0,
    val timestamp: Long = 0,
    val errorType: String? = null,
) {
    val reader: String get() = if (uid == 0) "root_process" else "shell_process"
}

internal object ClipboardProcessProtocol {
    fun parse(line: String): ClipboardProcessEvent {
        require(line.length <= ConfigCatalog.CLIPBOARD_PROCESS_MAX_TEXT_CHARS * 6 + 1024)
        val json = JSONObject(line)
        val uid = json.getInt("uid")
        require(uid == 0 || uid == 2000)
        val type = json.getString("type")
        require(type in setOf("clip", "ready", "error"))
        val result = json.getString("result")
        require(when (type) {
            "ready" -> result == "listening"
            "error" -> result == "error"
            else -> result in setOf("text", "null_clip", "no_text", "text_too_large")
        })
        val text = if (result == "text") json.getString("text") else null
        require(text == null || text.length <= ConfigCatalog.CLIPBOARD_PROCESS_MAX_TEXT_CHARS)
        return ClipboardProcessEvent(type, uid, result, text, json.optInt("item_count"),
            json.optLong("clipboard_time_ms"), json.optString("error_type").takeIf { it.matches(Regex("[A-Za-z0-9_]+")) })
    }
}

/** Reuses the existing Root/Shizuku launcher; never reads privileged output through executeShell's logger. */
internal class ClipboardProcessException(val reason: String) : IOException(reason)

internal class PrivilegedClipboardReader(private val context: Context) {
    suspend fun read(): ClipboardProcessEvent {
        var result: ClipboardProcessEvent? = null
        session("read") { result = it }
        return result ?: throw ClipboardProcessException("process_ended")
    }

    suspend fun watch(onEvent: suspend (ClipboardProcessEvent) -> Unit) = session("watch", onEvent)

    private suspend fun session(mode: String, onEvent: suspend (ClipboardProcessEvent) -> Unit) = coroutineScope {
        val userId = Process.myUid() / 100_000 // Android UID namespace: 100000 IDs per user.
        val deviceId = if (Build.VERSION.SDK_INT >= 34) context.deviceId else 0
        val process = withContext(Dispatchers.IO) {
            PrivilegeManager.startPrivilegedProcess(arrayOf(
                "/system/bin/env", "CLASSPATH=${context.applicationInfo.sourceDir}",
                "/system/bin/app_process", "/", PrivilegedClipboardProcess::class.java.name,
                mode, userId.toString(), deviceId.toString(),
            ))
        } ?: throw ClipboardProcessException("process_unavailable")
        val stdout = process.inputStream.bufferedReader()
        val stderr = process.errorStream
        val timedOut = AtomicBoolean()
        val deadline = launch {
            delay(ConfigCatalog.CLIPBOARD_PROCESS_TIMEOUT_MS.toLong())
            timedOut.set(true)
            process.destroy()
        }
        val drainErrors = launch(Dispatchers.IO) {
            runCatching { stderr.use { input -> val buffer = ByteArray(1024); while (input.read(buffer) != -1) { } } }
        }
        val reading = async(Dispatchers.IO) {
            while (true) {
                val line = readLineBounded(stdout) ?: break
                if (!line.startsWith("{")) continue // Ignore runtime startup diagnostics, never log them.
                val event = ClipboardProcessProtocol.parse(line)
                deadline.cancel()
                onEvent(event)
                if (mode == "read" || event.type == "error") break
            }
        }
        try {
            reading.await()
            if (timedOut.get()) throw ClipboardProcessException("process_timeout")
        } catch (error: IOException) {
            if (timedOut.get()) throw ClipboardProcessException("process_timeout")
            throw error
        } finally {
            process.destroy()
            runCatching { stdout.close() }
            runCatching { stderr.close() }
            deadline.cancel()
            drainErrors.cancel()
        }
    }

    private fun readLineBounded(reader: BufferedReader): String? {
        val line = StringBuilder()
        val limit = ConfigCatalog.CLIPBOARD_PROCESS_MAX_TEXT_CHARS * 6 + 1024
        while (true) {
            val character = reader.read()
            if (character == -1) return line.toString().takeIf { it.isNotEmpty() }
            if (character == '\n'.code) return line.toString()
            if (line.length >= limit) throw ClipboardProcessException("protocol_too_large")
            line.append(character.toChar())
        }
    }
}
