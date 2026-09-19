package com.antgskds.calendarassistant.feature.cloudsync.data

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.util.UUID

class SyncV2DeviceIdentityStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "webdav_sync_v2_device_id.txt"))

    fun getOrCreate(): String {
        if (file.baseFile.exists()) {
            runCatching { file.openRead().use { it.readBytes().toString(Charsets.UTF_8).trim() } }
                .getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        val value = UUID.randomUUID().toString()
        val output = file.startWrite()
        try {
            output.write(value.toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (error: Throwable) {
            file.failWrite(output)
            throw error
        }
        return value
    }
}
