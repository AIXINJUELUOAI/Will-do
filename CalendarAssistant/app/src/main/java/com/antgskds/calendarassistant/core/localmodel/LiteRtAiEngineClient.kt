package com.antgskds.calendarassistant.core.localmodel

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import com.antgskds.calendarassistant.aiengine.AiEngineBackend
import com.antgskds.calendarassistant.aiengine.AiEngineException
import com.antgskds.calendarassistant.aiengine.AiEngineLog
import com.antgskds.calendarassistant.aiengine.AiEngineProtocol.KEY_BACKEND
import com.antgskds.calendarassistant.aiengine.AiEngineProtocol.KEY_ENABLE_VISION
import com.antgskds.calendarassistant.aiengine.AiEngineProtocol.KEY_ERROR
import com.antgskds.calendarassistant.aiengine.AiEngineProtocol.KEY_IMAGE_PATH
import com.antgskds.calendarassistant.aiengine.AiEngineProtocol.KEY_INFERENCE_ELAPSED
import com.antgskds.calendarassistant.aiengine.AiEngineProtocol.KEY_LOAD_ELAPSED
import com.antgskds.calendarassistant.aiengine.AiEngineProtocol.KEY_MAX_NUM_IMAGES
import com.antgskds.calendarassistant.aiengine.AiEngineProtocol.KEY_MAX_TOKENS
import com.antgskds.calendarassistant.aiengine.AiEngineProtocol.KEY_MODEL_PATH
import com.antgskds.calendarassistant.aiengine.AiEngineProtocol.KEY_PROMPT
import com.antgskds.calendarassistant.aiengine.AiEngineProtocol.KEY_REQUEST_ID
import com.antgskds.calendarassistant.aiengine.AiEngineProtocol.KEY_STATUS
import com.antgskds.calendarassistant.aiengine.AiEngineProtocol.KEY_TEMPERATURE
import com.antgskds.calendarassistant.aiengine.AiEngineProtocol.KEY_TEXT
import com.antgskds.calendarassistant.aiengine.AiEngineProtocol.KEY_TIMEOUT_MILLIS
import com.antgskds.calendarassistant.aiengine.AiEngineProtocol.KEY_TOP_K
import com.antgskds.calendarassistant.aiengine.AiEngineProtocol.KEY_TOP_P
import com.antgskds.calendarassistant.aiengine.AiEngineProtocol.KEY_TOTAL_ELAPSED
import com.antgskds.calendarassistant.aiengine.AiEngineProtocol.MSG_GENERATE
import com.antgskds.calendarassistant.aiengine.AiEngineProtocol.MSG_GENERATE_ERROR
import com.antgskds.calendarassistant.aiengine.AiEngineProtocol.MSG_GENERATE_RESULT
import com.antgskds.calendarassistant.aiengine.AiEngineProtocol.MSG_MODEL_LOADED
import com.antgskds.calendarassistant.aiengine.AiEngineProtocol.MSG_PREPARE_MODEL
import com.antgskds.calendarassistant.aiengine.AiEngineProtocol.MSG_PREPARE_RESULT
import com.antgskds.calendarassistant.aiengine.AiEngineRequest
import com.antgskds.calendarassistant.aiengine.AiEngineResult
import com.antgskds.calendarassistant.aiengine.AiEngineService
import com.antgskds.calendarassistant.aiengine.AiEngineStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LiteRtAiEngineClient(context: Context) {
    private val appContext = context.applicationContext
    private val serviceRef = AtomicReference<Messenger?>()
    private val requestIds = AtomicLong(1L)
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<AiEngineResult>>()
    private val pendingPrepare = ConcurrentHashMap<Long, CompletableDeferred<Long>>()
    private val modelLoadedCallbacks = ConcurrentHashMap<Long, () -> Unit>()
    private val incomingMessenger = Messenger(IncomingHandler())
    private val bindMutex = Mutex()
    private var bindDeferred: CompletableDeferred<Messenger>? = null
    private var serviceConnection: ServiceConnection? = null

    suspend fun generate(
        model: LocalModelInfo,
        prompt: String,
        imagePath: String? = null,
        enableVision: Boolean = false,
        maxTokens: Int = DEFAULT_MAX_TOKENS,
        onModelLoaded: (() -> Unit)? = null
    ): AiEngineResult {
        require(model.runtime == LocalModelRuntime.LITERT_LM) { "不是 LiteRT-LM 模型" }
        val requestId = requestIds.getAndIncrement()
        AiEngineLog.write(appContext, "INFO", TAG, "request#$requestId client start model=${model.fileName} vision=$enableVision")
        val service = bindService()
        val deferred = CompletableDeferred<AiEngineResult>()
        pending[requestId] = deferred
        onModelLoaded?.let { modelLoadedCallbacks[requestId] = it }

        val request = AiEngineRequest(
            modelPath = model.path,
            prompt = prompt,
            imagePath = imagePath,
            maxTokens = maxTokens,
            maxNumImages = if (enableVision) 1 else 0,
            backend = if (model.fileName.endsWith(".litertlm", ignoreCase = true)) AiEngineBackend.GPU else AiEngineBackend.DEFAULT,
            temperature = 0.2f,
            topK = 40,
            topP = 0.95f,
            enableVision = enableVision,
            timeoutMillis = DEFAULT_TIMEOUT_MILLIS
        )
        val message = Message.obtain(null, MSG_GENERATE).apply {
            replyTo = incomingMessenger
            data = request.toBundle(requestId)
        }

        try {
            service.send(message)
        } catch (e: RemoteException) {
            pending.remove(requestId)
            modelLoadedCallbacks.remove(requestId)
            serviceRef.set(null)
            val status = classifyLastEngineExit()
            AiEngineLog.write(appContext, "ERROR", TAG, "request#$requestId send failed status=$status error=${e.message}")
            throw AiEngineException(status, "AI 引擎通信失败：${e.message}", e)
        }

        return try {
            deferred.await()
        } finally {
            pending.remove(requestId)
            modelLoadedCallbacks.remove(requestId)
            AiEngineLog.write(appContext, "INFO", TAG, "request#$requestId client finished")
        }
    }

    suspend fun prepare(
        model: LocalModelInfo,
        enableVision: Boolean = false,
        maxTokens: Int = DEFAULT_MAX_TOKENS
    ): Long {
        require(model.runtime == LocalModelRuntime.LITERT_LM) { "不是 LiteRT-LM 模型" }
        val requestId = requestIds.getAndIncrement()
        AiEngineLog.write(appContext, "INFO", TAG, "request#$requestId client prepare model=${model.fileName} vision=$enableVision")
        val service = bindService()
        val deferred = CompletableDeferred<Long>()
        pendingPrepare[requestId] = deferred

        val request = AiEngineRequest(
            modelPath = model.path,
            prompt = "",
            maxTokens = maxTokens,
            maxNumImages = if (enableVision) 1 else 0,
            backend = if (model.fileName.endsWith(".litertlm", ignoreCase = true)) AiEngineBackend.GPU else AiEngineBackend.DEFAULT,
            temperature = 0.2f,
            topK = 40,
            topP = 0.95f,
            enableVision = enableVision,
            timeoutMillis = DEFAULT_TIMEOUT_MILLIS
        )
        val message = Message.obtain(null, MSG_PREPARE_MODEL).apply {
            replyTo = incomingMessenger
            data = request.toBundle(requestId)
        }

        try {
            service.send(message)
        } catch (e: RemoteException) {
            pendingPrepare.remove(requestId)
            serviceRef.set(null)
            val status = classifyLastEngineExit()
            AiEngineLog.write(appContext, "ERROR", TAG, "request#$requestId prepare send failed status=$status error=${e.message}")
            throw AiEngineException(status, "AI 引擎通信失败：${e.message}", e)
        }

        return try {
            deferred.await()
        } finally {
            pendingPrepare.remove(requestId)
            AiEngineLog.write(appContext, "INFO", TAG, "request#$requestId client prepare finished")
        }
    }

    private inner class IncomingHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            val data = msg.data
            val requestId = data.getLong(KEY_REQUEST_ID)
            when (msg.what) {
                MSG_MODEL_LOADED -> {
                    modelLoadedCallbacks.remove(requestId)?.invoke()
                }
                MSG_GENERATE_RESULT -> {
                    val deferred = pending.remove(requestId) ?: return
                    modelLoadedCallbacks.remove(requestId)
                    val status = parseStatus(data.getString(KEY_STATUS), AiEngineStatus.SUCCESS)
                    val result = AiEngineResult(
                        text = data.getString(KEY_TEXT).orEmpty(),
                        loadElapsedMillis = data.getLong(KEY_LOAD_ELAPSED),
                        inferenceElapsedMillis = data.getLong(KEY_INFERENCE_ELAPSED),
                        totalElapsedMillis = data.getLong(KEY_TOTAL_ELAPSED),
                        status = status
                    )
                    AiEngineLog.write(appContext, "INFO", TAG, "request#$requestId result status=$status outputChars=${result.text.length}")
                    deferred.complete(result)
                }
                MSG_GENERATE_ERROR -> {
                    val deferred = pending.remove(requestId)
                    val prepareDeferred = pendingPrepare.remove(requestId)
                    modelLoadedCallbacks.remove(requestId)
                    val status = parseStatus(data.getString(KEY_STATUS), AiEngineStatus.UNKNOWN_ERROR)
                    val error = data.getString(KEY_ERROR).orEmpty().ifBlank { "AI 引擎推理失败" }
                    AiEngineLog.write(appContext, "ERROR", TAG, "request#$requestId error status=$status error=$error")
                    val exception = AiEngineException(status, error)
                    deferred?.completeExceptionally(exception)
                    prepareDeferred?.completeExceptionally(exception)
                }
                MSG_PREPARE_RESULT -> {
                    val deferred = pendingPrepare.remove(requestId) ?: return
                    val status = parseStatus(data.getString(KEY_STATUS), AiEngineStatus.SUCCESS)
                    if (status == AiEngineStatus.SUCCESS) {
                        deferred.complete(data.getLong(KEY_LOAD_ELAPSED))
                    } else {
                        deferred.completeExceptionally(AiEngineException(status, "模型加载失败"))
                    }
                }
                else -> super.handleMessage(msg)
            }
        }
    }

    private fun AiEngineRequest.toBundle(requestId: Long): Bundle {
        return Bundle().apply {
            putLong(KEY_REQUEST_ID, requestId)
            putString(KEY_MODEL_PATH, modelPath)
            putString(KEY_PROMPT, prompt)
            putString(KEY_IMAGE_PATH, imagePath)
            putInt(KEY_MAX_TOKENS, maxTokens)
            putInt(KEY_MAX_NUM_IMAGES, maxNumImages)
            putString(KEY_BACKEND, backend.name)
            putFloat(KEY_TEMPERATURE, temperature)
            putInt(KEY_TOP_K, topK)
            putFloat(KEY_TOP_P, topP)
            putBoolean(KEY_ENABLE_VISION, enableVision)
            putLong(KEY_TIMEOUT_MILLIS, timeoutMillis)
        }
    }

    private suspend fun bindService(): Messenger {
        serviceRef.get()?.let { return it }

        val deferred = bindMutex.withLock {
            serviceRef.get()?.let { return it }
            bindDeferred?.takeIf { it.isActive } ?: CompletableDeferred<Messenger>().also { created ->
                bindDeferred = created
                startAndBind(created)
            }
        }
        return deferred.await()
    }

    private fun startAndBind(deferred: CompletableDeferred<Messenger>) {
        val intent = Intent(appContext, AiEngineService::class.java).apply {
            action = AiEngineService.ACTION_START_FOREGROUND
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
        }.onFailure { e ->
            bindDeferred = null
            val error = AiEngineException(AiEngineStatus.FOREGROUND_START_FAILED, "AI 引擎前台服务启动失败：${e.message}", e)
            deferred.completeExceptionally(error)
            AiEngineLog.write(appContext, "ERROR", TAG, "startForegroundService failed: ${e.message}")
            return
        }

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val messenger = service?.let { Messenger(it) }
                if (messenger == null) {
                    deferred.completeExceptionally(AiEngineException(AiEngineStatus.ENGINE_DISCONNECTED, "AI 引擎服务绑定失败"))
                } else {
                    serviceRef.set(messenger)
                    deferred.complete(messenger)
                    AiEngineLog.write(appContext, "INFO", TAG, "service connected")
                }
                bindDeferred = null
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                handleDisconnected("onServiceDisconnected")
            }

            override fun onBindingDied(name: ComponentName?) {
                handleDisconnected("onBindingDied")
            }

            override fun onNullBinding(name: ComponentName?) {
                handleDisconnected("onNullBinding")
            }
        }
        serviceConnection = connection
        val bindIntent = Intent(appContext, AiEngineService::class.java).apply {
            action = AiEngineService.ACTION_BIND
        }
        val bound = appContext.bindService(bindIntent, connection, Context.BIND_AUTO_CREATE)
        if (!bound) {
            bindDeferred = null
            serviceConnection = null
            deferred.completeExceptionally(AiEngineException(AiEngineStatus.ENGINE_DISCONNECTED, "AI 引擎服务启动失败"))
            AiEngineLog.write(appContext, "ERROR", TAG, "bindService returned false")
        }
    }

    private fun handleDisconnected(source: String) {
        serviceRef.set(null)
        bindDeferred?.completeExceptionally(AiEngineException(AiEngineStatus.ENGINE_DISCONNECTED, "AI 引擎进程已断开"))
        bindDeferred = null
        val status = classifyLastEngineExit()
        val error = AiEngineException(status, "AI 引擎进程已断开")
        pending.values.forEach { it.completeExceptionally(error) }
        pending.clear()
        pendingPrepare.values.forEach { it.completeExceptionally(error) }
        pendingPrepare.clear()
        modelLoadedCallbacks.clear()
        AiEngineLog.write(appContext, "ERROR", TAG, "$source status=$status")
    }

    private fun classifyLastEngineExit(): AiEngineStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return AiEngineStatus.ENGINE_DISCONNECTED
        val manager = appContext.getSystemService(ActivityManager::class.java) ?: return AiEngineStatus.ENGINE_DISCONNECTED
        return runCatching {
            val info = manager.getHistoricalProcessExitReasons(appContext.packageName, 0, 10)
                .firstOrNull { it.processName == "${appContext.packageName}:ai_engine" }
                ?: return@runCatching AiEngineStatus.ENGINE_DISCONNECTED
            AiEngineLog.write(
                appContext,
                "ERROR",
                TAG,
                "last ai_engine exit reason=${info.reason} status=${info.status} importance=${info.importance} pss=${info.pss} rss=${info.rss} desc=${info.description}"
            )
            when (info.reason) {
                ApplicationExitInfo.REASON_LOW_MEMORY -> AiEngineStatus.ENGINE_KILLED_LOW_MEMORY
                ApplicationExitInfo.REASON_CRASH_NATIVE -> AiEngineStatus.ENGINE_NATIVE_CRASH
                ApplicationExitInfo.REASON_CRASH -> AiEngineStatus.ENGINE_JAVA_CRASH
                else -> AiEngineStatus.ENGINE_DISCONNECTED
            }
        }.getOrDefault(AiEngineStatus.ENGINE_DISCONNECTED)
    }

    private fun parseStatus(raw: String?, fallback: AiEngineStatus): AiEngineStatus {
        return runCatching { AiEngineStatus.valueOf(raw.orEmpty()) }.getOrDefault(fallback)
    }

    companion object {
        private const val TAG = "LiteRtAiEngineClient"
        private const val DEFAULT_MAX_TOKENS = 1024
        private const val DEFAULT_TIMEOUT_MILLIS = 180_000L
    }
}
