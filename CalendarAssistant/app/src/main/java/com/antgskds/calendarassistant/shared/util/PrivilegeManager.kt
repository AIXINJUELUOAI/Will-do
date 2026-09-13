package com.antgskds.calendarassistant.shared.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.os.Handler
import android.os.Looper
import com.antgskds.calendarassistant.shared.util.AppLogger as Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import moe.shizuku.server.IShizukuService
import moe.shizuku.server.IRemoteProcess
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

object PrivilegeManager {
    private const val TAG = "PrivilegeManager"

    enum class PrivilegeType { NONE, SHIZUKU, ROOT }

    private val _privilegeTypeFlow = MutableStateFlow(PrivilegeType.NONE)
    val privilegeTypeFlow: StateFlow<PrivilegeType> = _privilegeTypeFlow.asStateFlow()

    var privilegeType: PrivilegeType
        get() = _privilegeTypeFlow.value
        private set(value) {
            if (_privilegeTypeFlow.value == value) return
            Log.i(TAG, "Privilege type changed: ${_privilegeTypeFlow.value} -> $value")
            _privilegeTypeFlow.value = value
        }

    val hasPrivilege: Boolean
        get() = privilegeType != PrivilegeType.NONE

    private var isInitialized = false
    private var pendingShizukuResult: ((Boolean) -> Unit)? = null
    private val rootRequestMutex = Mutex()
    private val privilegeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.d(TAG, "Shizuku binder received")
        refreshShizukuPermission()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.w(TAG, "Shizuku binder dead")
        if (privilegeType == PrivilegeType.SHIZUKU) {
            privilegeType = PrivilegeType.NONE
        }
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        val granted = grantResult == PackageManager.PERMISSION_GRANTED
        if (granted) {
            if (privilegeType != PrivilegeType.ROOT) {
                privilegeType = PrivilegeType.SHIZUKU
            }
            Log.d(TAG, "Shizuku permission granted")
        } else {
            Log.w(TAG, "Shizuku permission denied")
        }
        val callback = pendingShizukuResult
        pendingShizukuResult = null
        if (callback != null) {
            Handler(Looper.getMainLooper()).post { callback(granted) }
        }
    }

    fun initCheck(context: Context? = null) {
        if (isInitialized) {
            Log.d(TAG, "Already initialized, skipping")
            return
        }
        isInitialized = true

        Log.d(TAG, "Starting privilege check...")

        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            
            if (Shizuku.pingBinder()) {
                Log.d(TAG, "Shizuku binder available")
                refreshShizukuPermission()
            } else {
                Log.d(TAG, "Shizuku binder not available")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku check failed", e)
        }

        val appContext = context?.applicationContext
        if (appContext != null && wasRootPreviouslyGranted(appContext)) {
            privilegeScope.launch {
                requestRootAccess(appContext)
            }
        }

        Log.d(TAG, "Privilege check completed: $privilegeType")
    }

    fun refreshPrivilege(): PrivilegeType {
        if (privilegeType == PrivilegeType.ROOT || privilegeType == PrivilegeType.SHIZUKU) {
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

    suspend fun requestRootAccess(context: Context? = null): Boolean = withContext(Dispatchers.IO) {
        rootRequestMutex.withLock {
            if (privilegeType == PrivilegeType.ROOT) return@withLock true
            val granted = checkRoot()
            if (granted) {
                privilegeType = PrivilegeType.ROOT
                context?.applicationContext
                    ?.getSharedPreferences(PRIVILEGE_PREFS, Context.MODE_PRIVATE)
                    ?.edit()
                    ?.putBoolean(ROOT_PREVIOUSLY_GRANTED, true)
                    ?.apply()
                Log.d(TAG, "Root privilege acquired")
            } else {
                context?.applicationContext
                    ?.getSharedPreferences(PRIVILEGE_PREFS, Context.MODE_PRIVATE)
                    ?.edit()
                    ?.putBoolean(ROOT_PREVIOUSLY_GRANTED, false)
                    ?.apply()
            }
            granted
        }
    }

    fun wasRootPreviouslyGranted(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PRIVILEGE_PREFS, Context.MODE_PRIVATE)
            .getBoolean(ROOT_PREVIOUSLY_GRANTED, false)

    fun hasRootBinary(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "command -v su"))
            process.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }

    fun requestShizukuAccess(onResult: (Boolean) -> Unit = {}) {
        try {
            if (!Shizuku.pingBinder()) {
                onResult(false)
                return
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                if (privilegeType != PrivilegeType.ROOT) {
                    privilegeType = PrivilegeType.SHIZUKU
                }
                onResult(true)
            } else {
                pendingShizukuResult = onResult
                Shizuku.addRequestPermissionResultListener(permissionResultListener)
                Shizuku.requestPermission(0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku permission request failed", e)
            pendingShizukuResult = null
            onResult(false)
        }
    }

    private fun refreshShizukuPermission() {
        try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                if (privilegeType != PrivilegeType.ROOT) {
                    privilegeType = PrivilegeType.SHIZUKU
                }
                Log.d(TAG, "Shizuku permission already granted")
            } else {
                Log.d(TAG, "Shizuku permission not granted")
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

    fun startPrivilegedProcess(command: Array<String>): ProcessHandle? {
        if (!hasPrivilege) {
            refreshPrivilege()
        }
        if (!hasPrivilege) return null
        return try {
            when (privilegeType) {
                PrivilegeType.ROOT -> {
                    val process = Runtime.getRuntime().exec("su")
                    val os = DataOutputStream(process.outputStream)
                    os.writeBytes(command.joinToString(" ") { shellQuote(it) })
                    os.writeBytes("\n")
                    os.flush()
                    ProcessHandle.Local(process, os)
                }
                PrivilegeType.SHIZUKU -> {
                    if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                        return null
                    }
                    val binder = Shizuku.getBinder() ?: return null
                    val service = IShizukuService.Stub.asInterface(binder) ?: return null
                    val remote = service.newProcess(command, null, null)
                    ProcessHandle.Remote(remote)
                }
                PrivilegeType.NONE -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Start privileged process failed", e)
            null
        }
    }

    sealed class ProcessHandle {
        abstract val inputStream: java.io.InputStream
        abstract val errorStream: java.io.InputStream
        abstract fun destroy()

        class Local(
            private val process: Process,
            private val commandOutput: DataOutputStream
        ) : ProcessHandle() {
            override val inputStream: java.io.InputStream get() = process.inputStream
            override val errorStream: java.io.InputStream get() = process.errorStream
            override fun destroy() {
                runCatching { commandOutput.writeBytes("exit\n") }
                runCatching { commandOutput.flush() }
                runCatching { commandOutput.close() }
                runCatching { process.destroy() }
            }
        }

        class Remote(private val process: IRemoteProcess) : ProcessHandle() {
            override val inputStream: java.io.InputStream
                get() = ParcelFileDescriptor.AutoCloseInputStream(process.inputStream)
            override val errorStream: java.io.InputStream
                get() = ParcelFileDescriptor.AutoCloseInputStream(process.errorStream)
            override fun destroy() {
                runCatching { process.destroy() }
            }
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

    private fun shellQuote(value: String): String {
        if (value.matches(Regex("[A-Za-z0-9_@%+=:,./-]+"))) return value
        return "'" + value.replace("'", "'\\''") + "'"
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

    private const val PRIVILEGE_PREFS = "privilege_manager"
    private const val ROOT_PREVIOUSLY_GRANTED = "root_previously_granted"
}
