package com.antgskds.calendarassistant.core.util

import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.util.Log
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

object PrivilegeManager {
    private const val TAG = "PrivilegeManager"

    var privilegeType = PrivilegeType.NONE
        private set

    enum class PrivilegeType { NONE, SHIZUKU, ROOT }

    val hasPrivilege: Boolean
        get() = privilegeType != PrivilegeType.NONE

    private var isInitialized = false

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.d(TAG, "Shizuku binder received")
        checkShizukuPermission()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.w(TAG, "Shizuku binder dead")
        if (privilegeType == PrivilegeType.SHIZUKU) {
            privilegeType = PrivilegeType.NONE
        }
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            privilegeType = PrivilegeType.SHIZUKU
            Log.d(TAG, "Shizuku permission granted")
        } else {
            Log.w(TAG, "Shizuku permission denied")
        }
    }

    fun initCheck() {
        if (isInitialized) {
            Log.d(TAG, "Already initialized, skipping")
            return
        }
        isInitialized = true

        Log.d(TAG, "Starting privilege check...")

        if (checkRoot()) {
            privilegeType = PrivilegeType.ROOT
            Log.d(TAG, "Root privilege acquired")
            return
        }

        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            
            if (Shizuku.pingBinder()) {
                Log.d(TAG, "Shizuku binder available")
                checkShizukuPermission()
            } else {
                Log.d(TAG, "Shizuku binder not available")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku check failed", e)
        }

        Log.d(TAG, "Privilege check completed: $privilegeType")
    }

    fun refreshPrivilege(): PrivilegeType {
        if (privilegeType == PrivilegeType.ROOT || privilegeType == PrivilegeType.SHIZUKU) {
            return privilegeType
        }
        if (checkRoot()) {
            privilegeType = PrivilegeType.ROOT
            return privilegeType
        }
        try {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                privilegeType = PrivilegeType.SHIZUKU
            }
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku refresh failed", e)
        }
        return privilegeType
    }

    private fun checkShizukuPermission() {
        try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                privilegeType = PrivilegeType.SHIZUKU
                Log.d(TAG, "Shizuku permission already granted")
            } else {
                Log.d(TAG, "Requesting Shizuku permission...")
                Shizuku.addRequestPermissionResultListener(permissionResultListener)
                Shizuku.requestPermission(0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku permission check failed", e)
        }
    }

    suspend fun executeShell(command: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (!hasPrivilege) {
            refreshPrivilege()
        }
        if (!hasPrivilege) {
            Log.w(TAG, "No privilege, cannot execute: $command")
            return@withContext Pair(false, "No Privilege")
        }

        Log.d(TAG, "Executing shell command (via $privilegeType): $command")

        try {
            val result = when (privilegeType) {
                PrivilegeType.ROOT -> executeWithSu(command)
                PrivilegeType.SHIZUKU -> executeWithShizuku(command)
                PrivilegeType.NONE -> Pair(false, "No Privilege")
            }
            Log.d(TAG, "Shell result: success=${result.first}, output=${result.second.take(100)}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Shell execution failed", e)
            Pair(false, e.message ?: "Unknown Error")
        }
    }

    private fun executeWithSu(command: String): Pair<Boolean, String> {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()
            os.close()
            val exitCode = process.waitFor()
            val output = readStream(process.inputStream)
            val error = readStream(process.errorStream)
            val fullOutput = if (output.isNotEmpty()) output else error
            Pair(exitCode == 0, fullOutput)
        } catch (e: Exception) {
            Log.e(TAG, "SU execution failed", e)
            Pair(false, e.message ?: "SU Error")
        }
    }

    private fun executeWithShizuku(command: String): Pair<Boolean, String> {
        return try {
            if (!Shizuku.pingBinder()) {
                return Pair(false, "Shizuku binder not available")
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                return Pair(false, "Shizuku permission denied")
            }
            val binder = Shizuku.getBinder() ?: return Pair(false, "Shizuku binder unavailable")
            val service = IShizukuService.Stub.asInterface(binder)
                ?: return Pair(false, "Shizuku service unavailable")
            val remote = service.newProcess(arrayOf("sh", "-c", command), null, null)
            val output = ParcelFileDescriptor.AutoCloseInputStream(remote.inputStream)
                .use { readStream(it) }
            val error = ParcelFileDescriptor.AutoCloseInputStream(remote.errorStream)
                .use { readStream(it) }
            val exitCode = remote.waitFor()
            val fullOutput = if (output.isNotEmpty()) output else error
            Pair(exitCode == 0, fullOutput)
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku execution failed", e)
            Pair(false, e.message ?: "Shizuku Error")
        }
    }

    private fun readStream(inputStream: java.io.InputStream): String {
        val reader = BufferedReader(InputStreamReader(inputStream))
        val sb = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            sb.append(line).append("\n")
        }
        reader.close()
        return sb.toString().trim()
    }

    private fun checkRoot(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val exitCode = process.waitFor()
            val isRoot = exitCode == 0
            Log.d(TAG, "Root check result: $isRoot")
            isRoot
        } catch (e: Exception) {
            Log.d(TAG, "Root check failed: ${e.message}")
            false
        }
    }
}
