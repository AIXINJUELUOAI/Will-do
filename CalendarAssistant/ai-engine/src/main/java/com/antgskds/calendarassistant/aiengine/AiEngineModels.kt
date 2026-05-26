package com.antgskds.calendarassistant.aiengine

enum class AiEngineBackend {
    DEFAULT,
    CPU,
    GPU
}

enum class AiEngineStatus {
    SUCCESS,
    EMPTY_EVENTS,
    INVALID_JSON,
    INVALID_SCHEMA,
    TIMEOUT_LOADING,
    TIMEOUT_GENERATING,
    ENGINE_DISCONNECTED,
    ENGINE_KILLED_LOW_MEMORY,
    ENGINE_NATIVE_CRASH,
    ENGINE_JAVA_CRASH,
    MODEL_FILE_MISSING,
    MODEL_LOAD_FAILED,
    MODEL_UNSUPPORTED,
    USER_CANCELLED,
    FOREGROUND_START_FAILED,
    UNKNOWN_ERROR
}

class AiEngineException(
    val status: AiEngineStatus,
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)

data class AiEngineRequest(
    val modelPath: String,
    val prompt: String,
    val imagePath: String? = null,
    val maxTokens: Int = 512,
    val maxNumImages: Int = 0,
    val backend: AiEngineBackend = AiEngineBackend.DEFAULT,
    val temperature: Float = 0.2f,
    val topK: Int = 40,
    val topP: Float = 0.9f,
    val enableVision: Boolean = false,
    val timeoutMillis: Long = 180_000L
)

data class AiEngineResult(
    val text: String,
    val loadElapsedMillis: Long,
    val inferenceElapsedMillis: Long,
    val totalElapsedMillis: Long,
    val status: AiEngineStatus = AiEngineStatus.SUCCESS
)
