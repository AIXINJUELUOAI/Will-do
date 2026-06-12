package com.antgskds.calendarassistant.core.quickmemo.asr

sealed class TranscriptionResult {
    data class Success(val text: String) : TranscriptionResult()
    data class Failure(val message: String, val retryable: Boolean = true) : TranscriptionResult()
}

interface SpeechTranscriber {
    suspend fun transcribe(audioPath: String): TranscriptionResult
}

class NoopSpeechTranscriber : SpeechTranscriber {
    override suspend fun transcribe(audioPath: String): TranscriptionResult {
        return TranscriptionResult.Failure("转写模型未接入", retryable = true)
    }
}
