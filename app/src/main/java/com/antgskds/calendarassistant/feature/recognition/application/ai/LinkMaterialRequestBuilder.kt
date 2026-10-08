package com.antgskds.calendarassistant.feature.recognition.application.ai
import kotlinx.serialization.json.*

object LinkMaterialRequestBuilder {
    fun openAi(prompt: String, materials: List<Pair<String,String>>, model: String, disableThinking: Boolean = false) = buildJsonObject {
        put("model",model)
        putJsonArray("messages") {
            add(buildJsonObject {
                put("role","user")
                putJsonArray("content") {
                    add(buildJsonObject { put("type","text"); put("text",prompt) })
                    materials.forEach { (mime,data) -> add(buildJsonObject {
                        if (mime.startsWith("image/")) {
                            put("type","image_url"); putJsonObject("image_url") { put("url","data:$mime;base64,$data") }
                        } else {
                            require(mime in setOf("audio/wav","audio/mpeg")) { "当前音频请求仅支持 WAV/MP3" }
                            put("type","input_audio"); putJsonObject("input_audio") { put("data",data); put("format",if (mime=="audio/wav") "wav" else "mp3") }
                        }
                    }) }
                }
            })
        }
        if (disableThinking) putJsonObject("thinking") { put("type","disabled") }
    }
}
