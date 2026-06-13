package com.antgskds.calendarassistant.core.quickmemo.asr

import android.content.Context
import android.util.Log
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
            val modelFiles = modelManager.ensureModelFiles()
            val decoded = audioDecoder.decodeToFloatSamples(audioPath)
            val recognizer = getRecognizer(modelFiles)
            val stream = recognizer.createStream()
            try {
                stream.acceptWaveform(decoded.samples, decoded.sampleRate)
                recognizer.decode(stream)
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
            if (cached != null && recognizerKey == key) return cached
            runCatching { recognizer?.release() }
            val config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80, dither = 0f),
                modelConfig = OfflineModelConfig(
                    paraformer = OfflineParaformerModelConfig(model = modelFiles.model.absolutePath),
                    tokens = modelFiles.tokens.absolutePath,
                    numThreads = 2,
                    debug = false,
                    provider = "cpu"
                )
            )
            return OfflineRecognizer(null, config).also {
                recognizer = it
                recognizerKey = key
            }
        }
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
