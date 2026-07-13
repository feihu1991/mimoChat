package com.example.mimochat.data

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object MimoClient {
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    private fun normalizeBaseUrl(url: String): String {
        return url.trimEnd('/')
    }

    private fun headers(config: MimoConnection): Map<String, String> {
        return buildMap {
            put("Content-Type", "application/json")
            if (config.authMode == AuthMode.BEARER) {
                put("Authorization", "Bearer ${config.apiKey}")
            } else {
                put("api-key", config.apiKey)
            }
        }
    }

    suspend fun loadModels(config: MimoConnection): List<String> {
        val response = client.get("${normalizeBaseUrl(config.baseUrl)}/models") {
            headers(config).forEach { (key, value) -> header(key, value) }
        }
        val data = response.body<JsonObject>()
        val list = data["data"]?.jsonArray ?: data["models"]?.jsonArray ?: emptyList()
        return list.mapNotNull { element ->
            try {
                if (element is JsonObject) {
                    element["id"]?.jsonPrimitive?.content
                } else {
                    element.jsonPrimitive.content
                }
            } catch (e: Exception) {
                null
            }
        }.sorted()
    }

    suspend fun chatCompletion(
        config: MimoConnection,
        model: String,
        prompt: String,
        images: List<String> = emptyList()
    ): String {
        val content = if (images.isNotEmpty()) {
            buildList {
                add(mapOf("type" to "text", "text" to prompt))
                images.forEach { url ->
                    add(mapOf("type" to "image_url", "image_url" to mapOf("url" to url)))
                }
            }
        } else {
            prompt
        }

        val body = mapOf(
            "model" to model,
            "messages" to listOf(mapOf("role" to "user", "content" to content)),
            "stream" to false
        )

        val response = client.post("${normalizeBaseUrl(config.baseUrl)}/chat/completions") {
            headers(config).forEach { (key, value) -> header(key, value) }
            setBody(body)
        }

        val data = response.body<JsonObject>()
        val choices = data["choices"]?.jsonArray
        val outputText = data["output_text"]?.jsonPrimitive?.content

        return choices?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
            ?: outputText
            ?: throw Exception("模型没有返回文本内容")
    }

    suspend fun speechRecognition(config: MimoConnection, audioDataUrl: String): String {
        val body = mapOf(
            "model" to "mimo-v2.5-asr",
            "messages" to listOf(
                mapOf(
                    "role" to "user",
                    "content" to listOf(
                        mapOf(
                            "type" to "input_audio",
                            "input_audio" to mapOf("data" to audioDataUrl)
                        )
                    )
                )
            ),
            "asr_options" to mapOf("language" to "auto")
        )

        val response = client.post("${normalizeBaseUrl(config.baseUrl)}/chat/completions") {
            headers(config).forEach { (key, value) -> header(key, value) }
            setBody(body)
        }

        val data = response.body<JsonObject>()
        return data["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
            ?: throw Exception("没有识别到语音内容")
    }

    suspend fun synthesizeSpeech(
        config: MimoConnection,
        model: VoiceModel,
        text: String,
        voiceName: String = "Chloe",
        voiceSample: String? = null,
        voicePrompt: String = "自然、清晰、像面对面聊天一样回应。"
    ): String {
        val audio = if (model == VoiceModel.MIMO_V2_5_TTS_VOICECLONE) {
            if (voiceSample == null) throw Exception("当前角色还没有录入克隆音色")
            mapOf("format" to "wav", "voice" to voiceSample)
        } else {
            mapOf("format" to "wav", "voice" to voiceName)
        }

        val body = mapOf(
            "model" to if (model == VoiceModel.MIMO_V2_5_TTS_VOICECLONE) "mimo-v2.5-tts-voiceclone" else "mimo-v2.5-tts",
            "messages" to listOf(
                mapOf("role" to "user", "content" to voicePrompt),
                mapOf("role" to "assistant", "content" to text)
            ),
            "audio" to audio
        )

        val response = client.post("${normalizeBaseUrl(config.baseUrl)}/chat/completions") {
            headers(config).forEach { (key, value) -> header(key, value) }
            setBody(body)
        }

        val data = response.body<JsonObject>()
        val audioBase64 = data["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("audio")?.jsonObject?.get("data")?.jsonPrimitive?.content
            ?: throw Exception("语音合成没有返回音频")

        return "data:audio/wav;base64,$audioBase64"
    }

    suspend fun probeModel(config: MimoConnection, model: String): ProbeResult {
        val capability = capabilityFor(model)
        val started = System.currentTimeMillis()

        return try {
            val body = probeBody(model)
            val response = client.post("${normalizeBaseUrl(config.baseUrl)}/chat/completions") {
                headers(config).forEach { (key, value) -> header(key, value) }
                setBody(body)
            }

            val data = response.body<JsonObject>()
            val latency = System.currentTimeMillis() - started
            val hasOutput = data.containsKey("choices") || data.containsKey("output") || data.containsKey("id")

            ProbeResult(
                model = model,
                capability = capability,
                status = if (hasOutput) ProbeStatus.PASSED else ProbeStatus.REACHABLE,
                latency = latency,
                detail = if (hasOutput) "功能响应正常" else "接口可达，响应结构非标准"
            )
        } catch (e: Exception) {
            ProbeResult(
                model = model,
                capability = capability,
                status = ProbeStatus.FAILED,
                latency = System.currentTimeMillis() - started,
                detail = e.message ?: "未知错误"
            )
        }
    }

    private fun capabilityFor(model: String): String {
        val id = model.lowercase()
        return when {
            id.contains("voiceclone") -> "声音克隆"
            id.contains("voicedesign") -> "声音设计"
            id.contains("tts") -> "语音合成"
            id.contains("asr") || id.contains("whisper") -> "语音识别"
            id.contains("omni") || id == "mimo-v2.5" || id.contains("vision") -> "多模态理解"
            id.contains("pro") || id.contains("reason") -> "深度推理"
            else -> "文本对话"
        }
    }

    private fun probeBody(model: String): Map<String, Any> {
        val capability = capabilityFor(model)
        return when (capability) {
            "声音克隆" -> mapOf(
                "model" to model,
                "messages" to listOf(
                    mapOf("role" to "user", "content" to "自然、清晰地说话。"),
                    mapOf("role" to "assistant", "content" to "你好，这是声音克隆能力检测。")
                ),
                "audio" to mapOf("format" to "wav", "voice" to testWavDataUrl())
            )
            "声音设计" -> mapOf(
                "model" to model,
                "messages" to listOf(
                    mapOf("role" to "user", "content" to "温暖、沉稳、自然的青年中文声音。"),
                    mapOf("role" to "assistant", "content" to "你好，这是声音设计能力检测。")
                ),
                "audio" to mapOf("format" to "wav", "optimize_text_preview" to true)
            )
            "语音合成" -> mapOf(
                "model" to model,
                "messages" to listOf(
                    mapOf("role" to "user", "content" to "用轻快自然的语气。"),
                    mapOf("role" to "assistant", "content" to "你好，这是语音合成能力检测。")
                ),
                "audio" to mapOf("format" to "wav", "voice" to "Chloe")
            )
            "语音识别" -> mapOf(
                "model" to model,
                "messages" to listOf(
                    mapOf(
                        "role" to "user",
                        "content" to listOf(
                            mapOf("type" to "input_audio", "input_audio" to mapOf("data" to testWavDataUrl()))
                        )
                    )
                ),
                "asr_options" to mapOf("language" to "auto")
            )
            "多模态理解" -> mapOf(
                "model" to model,
                "messages" to listOf(
                    mapOf(
                        "role" to "user",
                        "content" to listOf(
                            mapOf("type" to "text", "text" to "只回答图像是否成功读取。"),
                            mapOf("type" to "image_url", "image_url" to mapOf("url" to "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="))
                        )
                    )
                ),
                "max_tokens" to 12
            )
            "深度推理" -> mapOf(
                "model" to model,
                "messages" to listOf(mapOf("role" to "user", "content" to "计算 17×19，只回答结果。")),
                "max_tokens" to 16
            )
            else -> mapOf(
                "model" to model,
                "messages" to listOf(mapOf("role" to "user", "content" to "只回答 OK。")),
                "max_tokens" to 16
            )
        }
    }

    private fun testWavDataUrl(): String {
        // This is a simplified version - in a real app, you'd generate a proper WAV file
        return "data:audio/wav;base64,UklGRiQAAABXQVZFZm10IBAAAAABAAEARKwAAIhYAQACABAAZGF0YQAAAAA="
    }
}