package com.antgskds.calendarassistant.aiengine

object AiEngineProtocol {
    const val MSG_GENERATE = 1
    const val MSG_GENERATE_RESULT = 2
    const val MSG_GENERATE_ERROR = 3
    const val MSG_MODEL_LOADED = 4

    const val KEY_REQUEST_ID = "request_id"
    const val KEY_MODEL_PATH = "model_path"
    const val KEY_PROMPT = "prompt"
    const val KEY_IMAGE_PATH = "image_path"
    const val KEY_MAX_TOKENS = "max_tokens"
    const val KEY_MAX_NUM_IMAGES = "max_num_images"
    const val KEY_BACKEND = "backend"
    const val KEY_TEMPERATURE = "temperature"
    const val KEY_TOP_K = "top_k"
    const val KEY_TOP_P = "top_p"
    const val KEY_ENABLE_VISION = "enable_vision"
    const val KEY_TIMEOUT_MILLIS = "timeout_millis"

    const val KEY_TEXT = "text"
    const val KEY_LOAD_ELAPSED = "load_elapsed"
    const val KEY_INFERENCE_ELAPSED = "inference_elapsed"
    const val KEY_TOTAL_ELAPSED = "total_elapsed"
    const val KEY_ERROR = "error"
    const val KEY_STATUS = "status"
}
