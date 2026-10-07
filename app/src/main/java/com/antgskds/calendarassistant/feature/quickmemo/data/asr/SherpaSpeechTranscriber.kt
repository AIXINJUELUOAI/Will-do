package com.antgskds.calendarassistant.feature.quickmemo.data.asr

import android.content.Context
import com.antgskds.calendarassistant.feature.quickmemo.domain.transcription.SpeechTranscriber
import com.antgskds.calendarassistant.feature.quickmemo.domain.transcription.TranscriptionResult
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import com.antgskds.calendarassistant.shared.util.AppLogger as Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineParaformerModelConfig
import com.k2fsa.sherpa.onnx.OfflineQwen3AsrModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SherpaSpeechTranscriber(
    private val context: Context,
    private val audioDecoder: QuickMemoAudioDecoder = QuickMemoAudioDecoder(),
) : SpeechTranscriber {
    private val inferenceMutex = Mutex()
    private var recognizer: OfflineRecognizer? = null
    private var recognizerKey = ""

    override suspend fun transcribe(audioPath: String): TranscriptionResult = withContext(Dispatchers.IO) {
        inferenceMutex.withLock {
            try {
                val qwenDirectory = QuickMemoAsrModelStore.qwenDir(context)
                val qwen = QwenAsrModelFiles.missing(qwenDirectory).isEmpty()
                val config: OfflineModelConfig
                val key: String
                if (qwen) {
                    // Also clean installations that imported Qwen before automatic cleanup was added.
                    val legacy = QuickMemoAsrModelStore.modelDir(context)
                    if (legacy.exists()) {
                        runCatching { QwenAsrModelFiles.cleanupLegacyModel(context.filesDir) }
                            .onSuccess { if (!it) Log.w("SherpaSpeech", "旧语音模型清理未完成，下次转写重试") }
                            .onFailure { Log.w("SherpaSpeech", "旧语音模型清理失败，下次转写重试", it) }
                    }
                    key = "qwen:${QwenAsrModelFiles.version(qwenDirectory)}"
                    config = OfflineModelConfig(
                        qwen3Asr = OfflineQwen3AsrModelConfig(
                            convFrontend = File(qwenDirectory, "conv_frontend.onnx").absolutePath,
                            encoder = File(qwenDirectory, "encoder.int8.onnx").absolutePath,
                            decoder = File(qwenDirectory, "decoder.int8.onnx").absolutePath,
                            tokenizer = File(qwenDirectory, "tokenizer").absolutePath,
                            maxTotalLen = ConfigCatalog.QWEN_ASR_MAX_TOTAL_TOKENS,
                            maxNewTokens = ConfigCatalog.QWEN_ASR_MAX_NEW_TOKENS,
                        ),
                        numThreads = ConfigCatalog.QWEN_ASR_NUM_THREADS,
                        provider = "cpu",
                    ) // Do not force Chinese: retain code-switching and English in the original language.
                } else {
                    copyLegacyAssets()
                    val model = QuickMemoAsrModelStore.modelFile(context)
                        ?: error("请先在设置中导入 Qwen3-ASR 模型 ZIP 包")
                    val tokens = QuickMemoAsrModelStore.tokensFile(context) ?: error("原 Paraformer 缺少 tokens.txt")
                    key = "paraformer:${model.length()}:${model.lastModified()}:${tokens.length()}:${tokens.lastModified()}"
                    config = OfflineModelConfig(
                        paraformer = OfflineParaformerModelConfig(model = model.absolutePath),
                        tokens = tokens.absolutePath, numThreads = 1, provider = "cpu",
                    )
                }
                val audio = audioDecoder.decodeToFloatSamples(audioPath)
                val engine = getRecognizer(key, config, qwen)
                val ranges = if (qwen) QwenAsrAudioChunks.ranges(audio.samples, audio.sampleRate)
                    else listOf(audio.samples.indices)
                val parts = mutableListOf<String>()
                for (range in ranges) {
                    currentCoroutineContext().ensureActive()
                    val samples = if (ranges.size == 1) audio.samples else audio.samples.copyOfRange(range.first, range.last + 1)
                    if (samples.all { it == 0f }) continue
                    val stream = engine.createStream()
                    try {
                        stream.acceptWaveform(samples, audio.sampleRate)
                        engine.decode(stream)
                        currentCoroutineContext().ensureActive()
                        engine.getResult(stream).text.trim().takeIf { it.isNotEmpty() }?.let(parts::add)
                    } finally {
                        stream.release()
                    }
                }
                val text = parts.joinToString("\n")
                if (text.isBlank()) TranscriptionResult.Failure("未识别到语音内容", retryable = true)
                else TranscriptionResult.Success(text)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("SherpaSpeech", "离线语音转写失败", error)
                TranscriptionResult.Failure(error.message ?: "语音转写失败", retryable = true)
            }
        }
    }

    private fun getRecognizer(key: String, model: OfflineModelConfig, qwen: Boolean): OfflineRecognizer {
        recognizer?.takeIf { recognizerKey == key }?.let { return it }
        recognizer?.release()
        recognizer = null
        recognizerKey = ""
        return OfflineRecognizer(null, OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16000, featureDim = if (qwen) 128 else 80, dither = 0f),
            modelConfig = model,
        )).also {
            recognizer = it
            recognizerKey = key
            Log.i("SherpaSpeech", "离线识别器就绪 engine=${if (key.startsWith("qwen:")) "Qwen3-ASR" else "Paraformer"}")
        }
    }

    private fun copyLegacyAssets() {
        val directory = QuickMemoAsrModelStore.modelDir(context)
        for (name in listOf(QuickMemoAsrModelStore.MODEL_FILE, QuickMemoAsrModelStore.FALLBACK_MODEL_FILE, QuickMemoAsrModelStore.TOKENS_FILE)) {
            val target = File(directory, name)
            if (target.isFile && target.length() > 0) continue
            runCatching {
                context.assets.open("${QuickMemoAsrModelStore.ASSET_MODEL_DIR}/$name").use { input ->
                    directory.mkdirs()
                    target.outputStream().use { input.copyTo(it) }
                }
            }.onFailure { target.delete() }
        }
    }
}
