package com.antgskds.calendarassistant.aiengine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.PowerManager
import android.os.RemoteException
import android.util.Log
import org.json.JSONObject
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
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

class AiEngineService : Service() {
    private val runtime = LiteRtRuntime()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val messenger by lazy { Messenger(IncomingHandler()) }
    private val activeReplyTos = ConcurrentHashMap<Long, Messenger>()
    private val activeJobs = ConcurrentHashMap<Long, Job>()
    private var idleStopJob: Job? = null
    private val watchdogExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "AiEngineWatchdog").apply { isDaemon = true }
    }
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile
    private var activeRequests = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        AiEngineLog.write(this, "INFO", TAG, "service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                AiEngineLog.write(this, "WARN", TAG, "cancel requested")
                cancelActiveRequests()
                stopForegroundCompat(removeNotification = true)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START_FOREGROUND, ACTION_BIND, null -> startAsForeground()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        startAsForeground()
        return messenger.binder
    }

    override fun onDestroy() {
        AiEngineLog.write(this, "INFO", TAG, "service destroyed")
        runtime.close()
        releaseWakeLock()
        watchdogExecutor.shutdownNow()
        serviceScope.cancel()
        super.onDestroy()
    }

    private inner class IncomingHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MSG_GENERATE -> handleGenerate(msg)
                MSG_PREPARE_MODEL -> handlePrepareModel(msg)
                else -> super.handleMessage(msg)
            }
        }
    }

    private fun handlePrepareModel(msg: Message) {
        val replyTo = msg.replyTo ?: return
        val data = msg.data
        val requestId = data.getLong(KEY_REQUEST_ID)
        val request = data.toRequest()
        idleStopJob?.cancel()
        activeRequests += 1
        startAsForeground()
        acquireWakeLock(request.timeoutMillis + 30_000L)
        AiEngineLog.write(
            this,
            "INFO",
            TAG,
            "request#$requestId prepare mode=${if (request.enableVision) "vision" else "text"} runtime=LITERT_LM backend=${request.backend} model=${File(request.modelPath).name} maxTokens=${request.maxTokens} timeout=${request.timeoutMillis}"
        )
        activeReplyTos[requestId] = replyTo
        val job = serviceScope.launch {
            try {
                val loadElapsed = runtime.prepare(applicationContext, request)
                AiEngineLog.write(applicationContext, "INFO", TAG, "request#$requestId prepared load=${loadElapsed}ms")
                replyTo.sendPrepareResult(requestId, loadElapsed)
            } catch (e: Exception) {
                val status = e.toStatus()
                val error = e.message ?: "AI 引擎模型加载失败"
                Log.e(TAG, "LiteRT prepare failed", e)
                AiEngineLog.write(applicationContext, "ERROR", TAG, "request#$requestId prepare failed status=$status error=$error")
                replyTo.sendError(requestId, status, error)
            } finally {
                activeReplyTos.remove(requestId)
                activeJobs.remove(requestId)
                activeRequests = (activeRequests - 1).coerceAtLeast(0)
                if (activeRequests == 0) {
                    releaseWakeLock()
                    scheduleIdleStop()
                }
            }
        }
        activeJobs[requestId] = job
        launchRequestWatchdog(requestId, request.timeoutMillis)
    }

    private fun handleGenerate(msg: Message) {
        val replyTo = msg.replyTo ?: return
        val data = msg.data
        val requestId = data.getLong(KEY_REQUEST_ID)
        val request = data.toRequest()
        idleStopJob?.cancel()
        activeRequests += 1
        startAsForeground()
        acquireWakeLock(request.timeoutMillis + 30_000L)
        AiEngineLog.write(
            this,
            "INFO",
            TAG,
            "request#$requestId start mode=${if (request.enableVision) "vision" else "text"} runtime=LITERT_LM backend=${request.backend} model=${File(request.modelPath).name} maxTokens=${request.maxTokens} timeout=${request.timeoutMillis} image=${request.imagePath != null}"
        )
        AiEngineLog.write(this, "DEBUG", TAG, "request#$requestId prompt\n${request.prompt}")
        request.imagePath?.let { AiEngineLog.write(this, "DEBUG", TAG, "request#$requestId image=$it") }

        activeReplyTos[requestId] = replyTo
        val job = serviceScope.launch {
            try {
                val result = runtime.generate(applicationContext, request) {
                    replyTo.sendModelLoaded(requestId)
                }
                AiEngineLog.write(
                    applicationContext,
                    "INFO",
                    TAG,
                    "request#$requestId success load=${result.loadElapsedMillis}ms inference=${result.inferenceElapsedMillis}ms total=${result.totalElapsedMillis}ms outputChars=${result.text.length}"
                )
                AiEngineLog.write(applicationContext, "DEBUG", TAG, "request#$requestId output\n${result.text}")
                replyTo.sendResult(requestId, result)
            } catch (e: Exception) {
                val status = e.toStatus()
                val error = e.message ?: "AI 引擎推理失败"
                Log.e(TAG, "LiteRT request failed", e)
                AiEngineLog.write(applicationContext, "ERROR", TAG, "request#$requestId failed status=$status error=$error")
                AiEngineLog.write(applicationContext, "DEBUG", TAG, Log.getStackTraceString(e))
                replyTo.sendError(requestId, status, error)
            } finally {
                activeReplyTos.remove(requestId)
                activeJobs.remove(requestId)
                activeRequests = (activeRequests - 1).coerceAtLeast(0)
                runtime.close()
                if (activeRequests == 0) {
                    releaseWakeLock()
                    stopForegroundCompat(removeNotification = true)
                    stopSelf()
                }
            }
        }
        activeJobs[requestId] = job
        launchRequestWatchdog(requestId, request.timeoutMillis)
    }

    private fun launchRequestWatchdog(requestId: Long, timeoutMillis: Long) {
        watchdogExecutor.schedule({
            val replyTo = activeReplyTos.remove(requestId)
            if (replyTo != null) {
                activeJobs.remove(requestId)?.cancel()
                AiEngineLog.write(applicationContext, "ERROR", TAG, "request#$requestId watchdog timeout after ${timeoutMillis}ms")
                replyTo.sendError(requestId, AiEngineStatus.TIMEOUT_LOADING, "模型加载或生成超时")
                activeRequests = (activeRequests - 1).coerceAtLeast(0)
                runtime.close()
                releaseWakeLock()
                stopForegroundCompat(removeNotification = true)
                stopSelf()
            }
        }, timeoutMillis, TimeUnit.MILLISECONDS)
    }

    private fun cancelActiveRequests() {
        idleStopJob?.cancel()
        activeReplyTos.forEach { (requestId, replyTo) ->
            replyTo.sendError(requestId, AiEngineStatus.USER_CANCELLED, "用户取消本地模型识别")
        }
        activeJobs.values.forEach { it.cancel() }
        activeReplyTos.clear()
        activeJobs.clear()
        activeRequests = 0
        runtime.close()
        releaseWakeLock()
    }

    private fun scheduleIdleStop() {
        idleStopJob?.cancel()
        idleStopJob = serviceScope.launch {
            delay(IDLE_STOP_DELAY_MS)
            if (activeRequests == 0) {
                AiEngineLog.write(applicationContext, "INFO", TAG, "idle stop after prepare")
                runtime.close()
                stopForegroundCompat(removeNotification = true)
                stopSelf()
            }
        }
    }

    private fun acquireWakeLock(timeoutMillis: Long) {
        val current = wakeLock
        if (current?.isHeld == true) return
        val powerManager = getSystemService(PowerManager::class.java) ?: return
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:AiEngineInference").apply {
            setReferenceCounted(false)
            acquire(timeoutMillis)
        }
        AiEngineLog.write(this, "INFO", TAG, "partial wake lock acquired timeout=${timeoutMillis}ms")
    }

    private fun releaseWakeLock() {
        val current = wakeLock ?: return
        if (current.isHeld) {
            runCatching { current.release() }
            AiEngineLog.write(this, "INFO", TAG, "partial wake lock released")
            wakeLock = null
        }
    }

    private fun Bundle.toRequest(): AiEngineRequest {
        val backendName = getString(KEY_BACKEND).orEmpty()
        return AiEngineRequest(
            modelPath = getString(KEY_MODEL_PATH).orEmpty(),
            prompt = getString(KEY_PROMPT).orEmpty(),
            imagePath = getString(KEY_IMAGE_PATH),
            maxTokens = getInt(KEY_MAX_TOKENS, 512),
            maxNumImages = getInt(KEY_MAX_NUM_IMAGES, 0),
            backend = runCatching { AiEngineBackend.valueOf(backendName) }.getOrDefault(AiEngineBackend.DEFAULT),
            temperature = getFloat(KEY_TEMPERATURE, 0.2f),
            topK = getInt(KEY_TOP_K, 40),
            topP = getFloat(KEY_TOP_P, 0.9f),
            enableVision = getBoolean(KEY_ENABLE_VISION, false),
            timeoutMillis = getLong(KEY_TIMEOUT_MILLIS, 180_000L)
        )
    }

    private fun Messenger.sendResult(requestId: Long, result: AiEngineResult) {
        val message = Message.obtain(null, MSG_GENERATE_RESULT).apply {
            data = Bundle().apply {
                putLong(KEY_REQUEST_ID, requestId)
                putString(KEY_TEXT, result.text)
                putLong(KEY_LOAD_ELAPSED, result.loadElapsedMillis)
                putLong(KEY_INFERENCE_ELAPSED, result.inferenceElapsedMillis)
                putLong(KEY_TOTAL_ELAPSED, result.totalElapsedMillis)
                putString(KEY_STATUS, result.status.name)
            }
        }
        trySend(message)
    }

    private fun Messenger.sendPrepareResult(requestId: Long, loadElapsed: Long) {
        val message = Message.obtain(null, MSG_PREPARE_RESULT).apply {
            data = Bundle().apply {
                putLong(KEY_REQUEST_ID, requestId)
                putLong(KEY_LOAD_ELAPSED, loadElapsed)
                putString(KEY_STATUS, AiEngineStatus.SUCCESS.name)
            }
        }
        trySend(message)
    }

    private fun Messenger.sendError(requestId: Long, status: AiEngineStatus, error: String) {
        val message = Message.obtain(null, MSG_GENERATE_ERROR).apply {
            data = Bundle().apply {
                putLong(KEY_REQUEST_ID, requestId)
                putString(KEY_STATUS, status.name)
                putString(KEY_ERROR, error)
            }
        }
        trySend(message)
    }

    private fun Messenger.sendModelLoaded(requestId: Long) {
        val message = Message.obtain(null, MSG_MODEL_LOADED).apply {
            data = Bundle().apply { putLong(KEY_REQUEST_ID, requestId) }
        }
        trySend(message)
    }

    private fun Messenger.trySend(message: Message) {
        try {
            send(message)
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to send AI engine response", e)
            AiEngineLog.write(this@AiEngineService, "ERROR", TAG, "failed to send response: ${e.message}")
        }
    }

    private fun startAsForeground() {
        try {
            val notification = buildForegroundNotification("本地模型加载中", "正在准备端侧模型")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(FOREGROUND_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(FOREGROUND_NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
            AiEngineLog.write(this, "ERROR", TAG, "startForeground failed: ${e.message}")
        }
    }

    private fun buildForegroundNotification(
        title: String,
        text: String
    ): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val cancelIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AiEngineService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val useLiveCapsule = isLiveCapsuleEnabled()
        val channelId = if (useLiveCapsule) CHANNEL_ID_LIVE else CHANNEL_ID_ENGINE
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            Notification.Builder(this)
        }
        contentIntent?.let { builder.setContentIntent(it) }
        builder
            .setSmallIcon(resolveModelLoadingIcon())
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setStyle(Notification.BigTextStyle().setBigContentTitle(title).bigText(text))
            .addAction(0, "取消", cancelIntent)
        if (useLiveCapsule) {
            builder.setShowWhen(false)
        }
        if (useLiveCapsule && Build.VERSION.SDK_INT >= 35) {
            val extras = Bundle().apply { putBoolean("android.requestPromotedOngoing", true) }
            builder.addExtras(extras)
        }
        if (useLiveCapsule && Build.VERSION.SDK_INT >= 36) {
            builder.setShortCriticalText("模型加载中")
        }
        return builder.build()
    }

    private fun isLiveCapsuleEnabled(): Boolean {
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val json = prefs.getString("settings_json", null)
        if (!json.isNullOrBlank()) {
            return runCatching { JSONObject(json).optBoolean("isLiveCapsuleEnabled", false) }.getOrDefault(false)
        }
        return prefs.getBoolean("live_capsule_enabled", false)
    }

    private fun resolveModelLoadingIcon(): Int {
        val iconId = resources.getIdentifier("ic_model_loading", "drawable", packageName)
        return if (iconId != 0) iconId else applicationInfo.icon
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID_LIVE) == null) {
            val liveChannel = NotificationChannel(CHANNEL_ID_LIVE, "实况胶囊", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "进行中任务的实况胶囊"
                setSound(null, null)
                setShowBadge(false)
            }
            manager.createNotificationChannel(liveChannel)
        }
        if (manager.getNotificationChannel(CHANNEL_ID_ENGINE) == null) {
            val engineChannel = NotificationChannel(CHANNEL_ID_ENGINE, "本地模型", NotificationManager.IMPORTANCE_LOW).apply {
                description = "本地模型加载和推理进度"
                setSound(null, null)
                setShowBadge(false)
            }
            manager.createNotificationChannel(engineChannel)
        }
    }

    private fun stopForegroundCompat(removeNotification: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(if (removeNotification) STOP_FOREGROUND_REMOVE else STOP_FOREGROUND_DETACH)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(removeNotification)
        }
    }

    private class LiteRtRuntime {
        private var loadedKey: RuntimeKey? = null
        private var liteRtEngine: Engine? = null

        suspend fun generate(
            context: android.content.Context,
            request: AiEngineRequest,
            onModelLoaded: () -> Unit
        ): AiEngineResult {
            if (!File(request.modelPath).exists()) {
                throw AiEngineException(AiEngineStatus.MODEL_FILE_MISSING, "模型文件不存在：${request.modelPath}")
            }
            request.imagePath?.let {
                if (!File(it).exists()) throw AiEngineException(AiEngineStatus.MODEL_FILE_MISSING, "图片文件不存在：$it")
            }

            return withContext(Dispatchers.IO) {
                val totalStart = System.currentTimeMillis()
                val loadElapsed = try {
                    withTimeout(request.timeoutMillis) {
                        ensureLoaded(context, request)
                    }
                } catch (e: TimeoutCancellationException) {
                    throw AiEngineException(AiEngineStatus.TIMEOUT_LOADING, "模型加载超时", e)
                } catch (e: AiEngineException) {
                    throw e
                } catch (e: Exception) {
                    throw AiEngineException(AiEngineStatus.MODEL_LOAD_FAILED, e.message ?: "模型加载失败", e)
                }
                onModelLoaded()

                var response = ""
                val inferenceElapsed = try {
                    withTimeout(request.timeoutMillis) {
                        measureTimeMillis {
                            response = runSession(request)
                        }
                    }
                } catch (e: TimeoutCancellationException) {
                    throw AiEngineException(AiEngineStatus.TIMEOUT_GENERATING, "模型生成超时", e)
                }

                AiEngineResult(
                    text = response,
                    loadElapsedMillis = loadElapsed,
                    inferenceElapsedMillis = inferenceElapsed,
                    totalElapsedMillis = System.currentTimeMillis() - totalStart,
                    status = AiEngineStatus.SUCCESS
                )
            }
        }

        suspend fun prepare(
            context: android.content.Context,
            request: AiEngineRequest
        ): Long {
            if (!File(request.modelPath).exists()) {
                throw AiEngineException(AiEngineStatus.MODEL_FILE_MISSING, "模型文件不存在：${request.modelPath}")
            }
            request.imagePath?.let {
                if (!File(it).exists()) throw AiEngineException(AiEngineStatus.MODEL_FILE_MISSING, "图片文件不存在：$it")
            }
            return withContext(Dispatchers.IO) {
                try {
                    withTimeout(request.timeoutMillis) {
                        ensureLoaded(context, request)
                    }
                } catch (e: TimeoutCancellationException) {
                    throw AiEngineException(AiEngineStatus.TIMEOUT_LOADING, "模型加载超时", e)
                } catch (e: AiEngineException) {
                    throw e
                } catch (e: Exception) {
                    throw AiEngineException(AiEngineStatus.MODEL_LOAD_FAILED, e.message ?: "模型加载失败", e)
                }
            }
        }

        private fun ensureLoaded(context: android.content.Context, request: AiEngineRequest): Long {
            val requestedMaxTokens = request.maxTokens.coerceAtLeast(1)
            val requestedMaxImages = if (request.enableVision) request.maxNumImages.coerceAtLeast(1) else 0
            val key = RuntimeKey(request.modelPath, requestedMaxTokens, requestedMaxImages, request.backend)
            if (loadedKey == key && liteRtEngine != null) {
                AiEngineLog.write(context, "INFO", TAG, "MODEL_CACHE_HIT runtime=LITERT_LM model=${File(request.modelPath).name}")
                return 0L
            }
            AiEngineLog.write(context, "INFO", TAG, "MODEL_CACHE_MISS runtime=LITERT_LM model=${File(request.modelPath).name}")
            close()
            val elapsed = measureTimeMillis {
                liteRtEngine = createLiteRtEngine(context, request, requestedMaxTokens)
                loadedKey = key
            }
            Log.d(TAG, "LITERT_LM model loaded path=${request.modelPath}, maxTokens=$requestedMaxTokens, maxImages=$requestedMaxImages, elapsed=${elapsed}ms")
            return elapsed
        }

        private fun runSession(request: AiEngineRequest): String {
            return runLiteRtSession(request)
        }

        @OptIn(ExperimentalApi::class)
        private fun createLiteRtEngine(
            context: android.content.Context,
            request: AiEngineRequest,
            requestedMaxTokens: Int
        ): Engine {
            val maxImages = if (request.enableVision) request.maxNumImages.coerceAtLeast(1) else null
            val backend = request.backend.toLiteRtBackend()
            val engine = Engine(
                EngineConfig(
                    modelPath = request.modelPath,
                    backend = backend,
                    visionBackend = com.google.ai.edge.litertlm.Backend.GPU(),
                    audioBackend = com.google.ai.edge.litertlm.Backend.CPU(),
                    maxNumTokens = requestedMaxTokens,
                    maxNumImages = maxImages,
                    cacheDir = File(context.cacheDir, "litert_lm").also { it.mkdirs() }.absolutePath
                )
            )
            engine.initialize()
            return engine
        }

        private fun runLiteRtSession(request: AiEngineRequest): String {
            val engine = liteRtEngine ?: error("模型尚未加载")
            val conversationConfig = ConversationConfig(
                samplerConfig = SamplerConfig(
                    topK = request.topK,
                    topP = request.topP.toDouble(),
                    temperature = request.temperature.toDouble(),
                    seed = 0
                )
            )
            engine.createConversation(conversationConfig).use { conversation ->
                val contents = if (request.enableVision) {
                    val imagePath = request.imagePath ?: error("视觉推理缺少图片")
                    Contents.of(Content.ImageFile(imagePath), Content.Text(request.prompt))
                } else {
                    Contents.of(Content.Text(request.prompt))
                }
                val response = conversation.sendMessage(contents)
                return response.contents.contents
                    .filterIsInstance<Content.Text>()
                    .joinToString(separator = "\n") { it.text }
            }
        }

        fun close() {
            runCatching { liteRtEngine?.close() }
            liteRtEngine = null
            loadedKey = null
        }

        fun isLoaded(): Boolean = liteRtEngine != null

        private fun AiEngineBackend.toLiteRtBackend(): com.google.ai.edge.litertlm.Backend {
            return when (this) {
                AiEngineBackend.CPU -> com.google.ai.edge.litertlm.Backend.CPU()
                AiEngineBackend.GPU,
                AiEngineBackend.DEFAULT -> com.google.ai.edge.litertlm.Backend.GPU()
            }
        }

        private data class RuntimeKey(
            val modelPath: String,
            val maxTokens: Int,
            val maxImages: Int,
            val backend: AiEngineBackend
        )
    }

    companion object {
        private const val TAG = "AiEngineService"
        private const val CHANNEL_ID_LIVE = "calendar_assistant_live_channel_v3"
        private const val CHANNEL_ID_ENGINE = "calendar_assistant_ai_engine_channel_v1"
        private const val FOREGROUND_NOTIFICATION_ID = 0x4CA11
        private const val IDLE_STOP_DELAY_MS = 60_000L
        const val ACTION_BIND = "com.antgskds.calendarassistant.aiengine.BIND"
        const val ACTION_START_FOREGROUND = "com.antgskds.calendarassistant.aiengine.START_FOREGROUND"
        const val ACTION_CANCEL = "com.antgskds.calendarassistant.aiengine.CANCEL"
    }
}

private fun Throwable.toStatus(): AiEngineStatus {
    if (this is AiEngineException) return status
    val text = listOfNotNull(message, cause?.message).joinToString(" ").lowercase()
    return when {
        this is TimeoutCancellationException -> AiEngineStatus.TIMEOUT_GENERATING
        this is RemoteException -> AiEngineStatus.ENGINE_DISCONNECTED
        "model" in text && ("not exist" in text || "不存在" in text) -> AiEngineStatus.MODEL_FILE_MISSING
        "native" in text || "signal" in text || "segmentation" in text -> AiEngineStatus.ENGINE_NATIVE_CRASH
        else -> AiEngineStatus.ENGINE_JAVA_CRASH
    }
}
