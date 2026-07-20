package com.antgskds.calendarassistant.feature.quickmemo.data.asr

import android.content.Context
import android.os.Debug
import android.util.Log
import com.antgskds.calendarassistant.feature.quickmemo.domain.transcription.SpeechTranscriber
import com.antgskds.calendarassistant.feature.quickmemo.domain.transcription.TranscriptionResult
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineParaformerModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SherpaParaformerTranscriber(
    private val context: Context,
    private val audioDecoder: QuickMemoAudioDecoder = QuickMemoAudioDecoder()
) : SpeechTranscriber {
    private val modelManager = SherpaParaformerModelManager(context.applicationContext)
    private val recognizerLock = Any()
    private var recognizer: OfflineRecognizer? = null
    private var recognizerKey: String = ""

    override suspend fun transcribe(audioPath: String): TranscriptionResult = withContext(Dispatchers.IO) {
        runCatching {
            Log.i(TAG, "transcribe start audio=$audioPath exists=${File(audioPath).isFile} size=${File(audioPath).length()}")
            logMemory("before_model_resolve")
            val modelFiles = modelManager.ensureModelFiles()
            Log.i(
                TAG,
                "model files resolved model=${modelFiles.model.absolutePath} modelSize=${modelFiles.model.length()} " +
                    "tokens=${modelFiles.tokens.absolutePath} tokensSize=${modelFiles.tokens.length()}"
            )
            logMemory("before_audio_decode")
            val decoded = audioDecoder.decodeToFloatSamples(audioPath)
            Log.i(TAG, "audio decoded sampleRate=${decoded.sampleRate} samples=${decoded.samples.size}")
            logMemory("before_recognizer_get")
            val recognizer = getRecognizer(modelFiles)
            Log.i(TAG, "recognizer ready, creating stream")
            val stream = recognizer.createStream()
            try {
                stream.acceptWaveform(decoded.samples, decoded.sampleRate)
                Log.i(TAG, "waveform accepted, start decode")
                recognizer.decode(stream)
                Log.i(TAG, "decode finished, fetching result")
                recognizer.getResult(stream).text.trim()
            } finally {
                runCatching { stream.release() }
            }
        }.fold(
            onSuccess = { text ->
                if (text.isBlank()) {
                    TranscriptionResult.Failure("未识别到语音内容", retryable = true)
                } else {
                    TranscriptionResult.Success(text)
                }
            },
            onFailure = { throwable ->
                Log.e(TAG, "Sherpa Paraformer 转写失败", throwable)
                TranscriptionResult.Failure(throwable.message ?: "语音转写失败", retryable = true)
            }
        )
    }

    private fun getRecognizer(modelFiles: SherpaModelFiles): OfflineRecognizer {
        val key = "${modelFiles.model.absolutePath}|${modelFiles.tokens.absolutePath}"
        synchronized(recognizerLock) {
            val cached = recognizer
            if (cached != null && recognizerKey == key) {
                Log.i(TAG, "reuse cached recognizer key=$key")
                return cached
            }
            runCatching { recognizer?.release() }
            Log.i(TAG, "build recognizer start key=$key")
            val config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80, dither = 0f),
                modelConfig = OfflineModelConfig(
                    paraformer = OfflineParaformerModelConfig(model = modelFiles.model.absolutePath),
                    tokens = modelFiles.tokens.absolutePath,
                    numThreads = 1,
                    debug = false,
                    provider = "cpu"
                ),
                hotwordsFile = "",
                hotwordsScore = 0f
            )
            Log.i(
                TAG,
                "creating OfflineRecognizer model=${modelFiles.model.name} modelSize=${modelFiles.model.length()} " +
                    "tokensSize=${modelFiles.tokens.length()} numThreads=1 hotwordsDisabled=true"
            )
            return runCatching {
                OfflineRecognizer(null, config)
            }.onSuccess { created ->
                recognizer = created
                recognizerKey = key
                Log.i(TAG, "OfflineRecognizer created key=$key")
            }.onFailure { throwable ->
                Log.e(TAG, "OfflineRecognizer creation failed key=$key type=${throwable::class.java.name} msg=${throwable.message}", throwable)
            }.getOrThrow()
        }
    }

    private fun logMemory(stage: String) {
        val runtime = Runtime.getRuntime()
        Log.i(
            TAG,
            "memory[$stage] " +
                "javaUsed=${runtime.totalMemory() - runtime.freeMemory()} " +
                "javaFree=${runtime.freeMemory()} javaTotal=${runtime.totalMemory()} javaMax=${runtime.maxMemory()} " +
                "nativeHeap=${Debug.getNativeHeapAllocatedSize()} nativeFree=${Debug.getNativeHeapFreeSize()}"
        )
    }

    companion object {
        private const val TAG = "SherpaParaformer"
    }
}

data class SherpaModelFiles(
    val model: File,
    val tokens: File
)

class SherpaParaformerModelManager(
    private val context: Context
) {
    private val modelDir: File = File(context.filesDir, MODEL_DIR)

    fun ensureModelFiles(): SherpaModelFiles {
        copyAssetsIfPresent()
        val existing = resolveExistingModelFiles()
        if (existing != null) return existing
        modelDir.mkdirs()
        error("语音转写模型未导入，请在设置中导入 model.int8.onnx 和 tokens.txt")
    }

    private fun resolveExistingModelFiles(): SherpaModelFiles? {
        val model = QuickMemoAsrModelStore.modelFile(context)
        val tokens = QuickMemoAsrModelStore.tokensFile(context)
        return if (model != null && tokens != null) SherpaModelFiles(model, tokens) else null
    }

    private fun copyAssetsIfPresent() {
        runCatching {
            val names = context.assets.list(ASSET_MODEL_DIR)?.toSet().orEmpty()
            if (names.isEmpty()) return
            modelDir.mkdirs()
            listOf(MODEL_FILE, FALLBACK_MODEL_FILE, TOKENS_FILE).forEach { name ->
                if (name !in names) return@forEach
                val target = File(modelDir, name)
                if (target.isFile && target.length() > 0L) return@forEach
                context.assets.open("$ASSET_MODEL_DIR/$name").use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }.onFailure { Log.w(TAG, "复制 ASR 资产模型失败", it) }
    }

    companion object {
        private const val TAG = "SherpaModelManager"
        private const val MODEL_DIR = QuickMemoAsrModelStore.MODEL_DIR
        private const val ASSET_MODEL_DIR = QuickMemoAsrModelStore.ASSET_MODEL_DIR
        private const val MODEL_FILE = QuickMemoAsrModelStore.MODEL_FILE
        private const val FALLBACK_MODEL_FILE = QuickMemoAsrModelStore.FALLBACK_MODEL_FILE
        private const val TOKENS_FILE = QuickMemoAsrModelStore.TOKENS_FILE
    }
}
