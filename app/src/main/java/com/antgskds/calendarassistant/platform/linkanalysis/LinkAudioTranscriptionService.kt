package com.antgskds.calendarassistant.platform.linkanalysis

import android.app.Service
import android.content.*
import android.os.*
import com.antgskds.calendarassistant.feature.quickmemo.data.asr.SherpaSpeechTranscriber
import com.antgskds.calendarassistant.feature.quickmemo.domain.transcription.TranscriptionResult
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog as Limits
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/** Bound from the Worker; native inference runs in its own process and does not write application data. */
class LinkAudioTranscriptionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private companion object {
        val inferenceMutex = Mutex()
        var sharedTranscriber: SherpaSpeechTranscriber? = null
    }
    private val transcriber get() = sharedTranscriber ?: SherpaSpeechTranscriber(applicationContext).also { sharedTranscriber = it }
    private val messenger by lazy {
        Messenger(Handler(Looper.getMainLooper()) { message ->
            val reply = message.replyTo
            val path = message.data.getString("path").orEmpty()
            scope.launch {
                val result = try {
                    val file = File(path).canonicalFile
                    val root = File(cacheDir,"link-analysis").canonicalFile
                    require(file.toPath().startsWith(root.toPath()) && file.isFile && file.length() <= Limits.LINK_AI_INPUT_BYTES)
                    inferenceMutex.withLock { transcriber.transcribe(file.absolutePath) }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { TranscriptionResult.Failure("本地转写失败") }
                val data = Bundle()
                when (result) {
                    is TranscriptionResult.Success -> data.putString("text",result.text.take(Limits.LINK_TEXT_MAX_CHARS))
                    is TranscriptionResult.Failure -> data.putString("error",result.message.take(512))
                }
                runCatching { reply?.send(Message.obtain().apply { this.data = data }) }
            }
            true
        })
    }
    override fun onBind(intent: Intent): IBinder = messenger.binder
    override fun onUnbind(intent: Intent?): Boolean { scope.cancel(); stopSelf(); return false }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}

class LinkAudioTranscriptionClient(private val context: Context) {
    suspend fun transcribe(file: File): String = withTimeout(Limits.LINK_TASK_TIMEOUT_MS.toLong()) {
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                var bound = false
                lateinit var connection: ServiceConnection
                fun release() { if (bound) { bound=false; runCatching { context.unbindService(connection) } } }
                val reply = Messenger(Handler(Looper.getMainLooper()) { message ->
                    if (continuation.isActive) {
                        val error = message.data.getString("error")
                        val text = message.data.getString("text").orEmpty().trim()
                        release()
                        if (error != null || text.isBlank()) continuation.resumeWith(Result.failure(IllegalStateException(error ?: "未识别到语音内容")))
                        else continuation.resumeWith(Result.success(text))
                    }
                    true
                })
                connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                        try {
                            Messenger(binder).send(Message.obtain().apply { replyTo=reply; data=Bundle().apply { putString("path",file.absolutePath) } })
                        } catch (_: Exception) {
                            release()
                            if (continuation.isActive) continuation.resumeWith(Result.failure(IllegalStateException("无法连接本地转写服务")))
                        }
                    }
                    override fun onServiceDisconnected(name: ComponentName) {
                        release()
                        if (continuation.isActive) continuation.resumeWith(Result.failure(IllegalStateException("本地转写进程已退出，可重试")))
                    }
                    override fun onBindingDied(name: ComponentName) = onServiceDisconnected(name)
                    override fun onNullBinding(name: ComponentName) = onServiceDisconnected(name)
                }
                bound = context.bindService(Intent(context,LinkAudioTranscriptionService::class.java),connection,Context.BIND_AUTO_CREATE)
                if (!bound) continuation.resumeWith(Result.failure(IllegalStateException("无法启动本地转写服务")))
                continuation.invokeOnCancellation { Handler(Looper.getMainLooper()).post { release() } }
            }
        }
    }
}
