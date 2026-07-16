package com.example.mimochat.data.remote

import com.example.mimochat.data.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.*

object MimoClient {
    private var client: HttpClient? = null

    private fun getClient(config: MimoConnection): HttpClient {
        return client ?: HttpClient(OkHttp) {
            // 所有非 2xx 响应直接抛出异常，避免把 401/403/5xx 误判为可达。
            expectSuccess = false
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            install(HttpTimeout) {
                connectTimeoutMillis = 30_000
                requestTimeoutMillis = 120_000
                socketTimeoutMillis = 120_000
            }
            engine {
                config {
                    retryOnConnectionFailure(true)
                }
            }
        }.also { client = it }
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

    private fun normalizeBaseUrl(url: String): String = url.trimEnd('/')

    // ── Models ──
    suspend fun loadModels(config: MimoConnection): List<String> {
        val response = getClient(config).get("${normalizeBaseUrl(config.baseUrl)}/models") {
            headers(config).forEach { (key, value) -> header(key, value) }
        }
        requireSuccess(response)
        val data = response.body<JsonObject>()
        val list = data["data"]?.jsonArray ?: data["models"]?.jsonArray ?: emptyList()
        return list.mapNotNull { element ->
            try {
                if (element is JsonObject) element["id"]?.jsonPrimitive?.content
                else element.jsonPrimitive.content
            } catch (_: Exception) {
                null
            }
        }.sorted()
    }

    // ── Streaming Chat ──
    fun chatCompletionStream(
        config: MimoConnection,
        model: String,
        messages: List<Map<String, Any>>,
        images: List<String> = emptyList()
    ): Flow<StreamChunk> = flow {
        val content: Any = if (images.isNotEmpty()) {
            buildList {
                val lastUserText = messages.lastOrNull { it["role"] == "user" }
                    ?.get("content") as? String ?: ""
                add(mapOf("type" to "text", "text" to lastUserText))
                images.forEach { url ->
                    add(mapOf("type" to "image_url", "image_url" to mapOf("url" to url)))
                }
            }
        } else {
            messages.lastOrNull { it["role"] == "user" }?.get("content") as? String ?: ""
        }

        val apiMessages = messages.toMutableList()
        if (images.isNotEmpty()) {
            val lastIndex = apiMessages.indexOfLast { it["role"] == "user" }
            if (lastIndex >= 0) {
                apiMessages[lastIndex] = apiMessages[lastIndex].toMutableMap().apply {
                    put("content", content)
                }
            }
        }

        val body = mapOf(
            "model" to model,
            "messages" to apiMessages,
            "stream" to true
        )

        val response = getClient(config).post("${normalizeBaseUrl(config.baseUrl)}/chat/completions") {
            headers(config).forEach { (key, value) -> header(key, value) }
            setBody(toJsonObject(body))
            timeout { requestTimeoutMillis = 120_000 }
        }
        requireSuccess(response)

        val channel = response.bodyAsChannel()
        val lineFlow = flow {
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                emit(line)
            }
        }

        ChatStreamParser.parseStream(lineFlow).collect { chunk -> emit(chunk) }
    }.flowOn(Dispatchers.IO)

    // ── Non-streaming Chat ──
    suspend fun chatCompletion(
        config: MimoConnection,
        model: String,
        prompt: String,
        images: List<String> = emptyList()
    ): String {
        val content: Any = if (images.isNotEmpty()) {
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

        val response = getClient(config).post("${normalizeBaseUrl(config.baseUrl)}/chat/completions") {
            headers(config).forEach { (key, value) -> header(key, value) }
            setBody(toJsonObject(body))
        }
        requireSuccess(response)

        val data = response.body<JsonObject>()
        return data["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
            ?: data["output_text"]?.jsonPrimitive?.content
            ?: throw Exception("模型没有返回文本内容")
    }

    // ── ASR ──
    suspend fun speechRecognition(config: MimoConnection, audioDataUrl: String): String {
        val body = mapOf(
            "model" to "mimo-v2.5-asr",
            "messages" to listOf(
                mapOf(
                    "role" to "user",
                    "content" to listOf(
                        mapOf("type" to "input_audio", "input_audio" to mapOf("data" to audioDataUrl))
                    )
                )
            ),
            "asr_options" to mapOf("language" to "auto")
        )

        val response = getClient(config).post("${normalizeBaseUrl(config.baseUrl)}/chat/completions") {
            headers(config).forEach { (key, value) -> header(key, value) }
            setBody(toJsonObject(body))
        }
        requireSuccess(response)

        val data = response.body<JsonObject>()
        return data["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
            ?: throw Exception("没有识别到语音内容")
    }

    // ── TTS ──
    suspend fun synthesizeSpeech(
        config: MimoConnection,
        model: String,
        text: String,
        voiceName: String = "Chloe",
        voiceSample: String? = null,
        voicePrompt: String = "自然、清晰、像面对面聊天一样回应。"
    ): String {
        val audio = when {
            model.contains("voiceclone") -> {
                if (voiceSample.isNullOrBlank()) throw Exception("当前角色还没有录入克隆音色")
                mapOf("format" to "wav", "voice" to voiceSample)
            }
            model.contains("voicedesign") -> {
                mapOf("format" to "wav", "optimize_text_preview" to true)
            }
            else -> {
                mapOf("format" to "wav", "voice" to voiceName)
            }
        }

        val body = mapOf(
            "model" to model,
            "messages" to listOf(
                mapOf("role" to "user", "content" to voicePrompt),
                mapOf("role" to "assistant", "content" to text)
            ),
            "audio" to audio
        )

        val response = getClient(config).post("${normalizeBaseUrl(config.baseUrl)}/chat/completions") {
            headers(config).forEach { (key, value) -> header(key, value) }
            setBody(toJsonObject(body))
        }
        requireSuccess(response)

        val data = response.body<JsonObject>()
        val audioBase64 = data["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject?.get("audio")?.jsonObject?.get("data")?.jsonPrimitive?.content
            ?: throw Exception("语音合成没有返回音频")

        return "data:audio/wav;base64,$audioBase64"
    }

    // ── Probe ──
    suspend fun probeModel(config: MimoConnection, model: String): ProbeResult {
        val capability = capabilityFor(model)
        val started = System.currentTimeMillis()

        return try {
            // 克隆模型必须带真实的音频样本，不能用空音频探测；模型列表已经证明接口可达。
            if (capability == "声音克隆") {
                return ProbeResult(model, capability, ProbeStatus.REACHABLE, 0, "模型已发现，录入音频样本后可试听")
            }
            val body = probeBody(model)
            val response = getClient(config).post("${normalizeBaseUrl(config.baseUrl)}/chat/completions") {
                headers(config).forEach { (key, value) -> header(key, value) }
                setBody(toJsonObject(body))
            }
            requireSuccess(response)
            val data = response.body<JsonObject>()
            val latency = System.currentTimeMillis() - started
            val hasOutput = data["choices"]?.jsonArray?.isNotEmpty() == true ||
                data.containsKey("output") || data.containsKey("id")
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
                detail = translateError(e)
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
            "声音设计" -> mapOf(
                "model" to model,
                "messages" to listOf(
                    mapOf("role" to "user", "content" to "清晰、自然、语速适中的中文女声。"),
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
                            mapOf(
                                "type" to "input_audio",
                                "input_audio" to mapOf(
                                    "data" to "data:audio/wav;base64,UklGRiQAAABXQVZFZm10IBAAAAABAAEARKwAAIhYAQACABAAZGF0YQAAAAA="
                                )
                            )
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
                            mapOf("type" to "text", "text" to "只回答 OK。"),
                            mapOf(
                                "type" to "image_url",
                                "image_url" to mapOf(
                                    "url" to "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
                                )
                            )
                        )
                    )
                ),
                "max_completion_tokens" to 12
            )
            "深度推理" -> mapOf(
                "model" to model,
                "messages" to listOf(mapOf("role" to "user", "content" to "计算 17×19，只回答结果。")),
                "max_completion_tokens" to 16
            )
            else -> mapOf(
                "model" to model,
                "messages" to listOf(mapOf("role" to "user", "content" to "只回答 OK。")),
                "max_completion_tokens" to 16
            )
        }
    }

    /** Ktor 的默认序列化器无法直接处理 Map<String, Any> 中的异构嵌套集合。 */
    private fun toJsonObject(body: Map<String, Any?>): JsonObject =
        JsonObject(body.mapValues { (_, value) -> toJsonElement(value) })

    private suspend fun requireSuccess(response: HttpResponse) {
        if (response.status.isSuccess()) return
        val raw = runCatching { response.bodyAsText() }.getOrNull().orEmpty()
        val detail = runCatching {
            Json.parseToJsonElement(raw).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
        }.getOrNull()
            ?: raw.take(400).ifBlank { response.status.description }
        throw IllegalStateException("HTTP ${response.status.value}: $detail")
    }

    private fun toJsonElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is Map<*, *> -> JsonObject(value.entries.associate { (key, item) ->
            key.toString() to toJsonElement(item)
        })
        is Iterable<*> -> JsonArray(value.map(::toJsonElement))
        is Array<*> -> JsonArray(value.map(::toJsonElement))
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        else -> JsonPrimitive(value.toString())
    }

    fun translateError(error: Throwable): String {
        val msg = error.message ?: return "未知错误"
        return when {
            msg.contains("401") || msg.contains("Unauthorized") -> "API Key 无效或已过期"
            msg.contains("403") -> "没有权限访问该模型"
            msg.contains("404") -> "模型不存在或接口地址错误"
            msg.contains("429") -> "请求频率过高，请稍后再试"
            msg.contains("500") || msg.contains("502") || msg.contains("503") -> "服务端异常，请稍后再试"
            msg.contains("timeout", true) -> "请求超时，请检查网络"
            msg.contains("connect", true) -> "网络连接失败，请检查网络"
            msg.contains("UnknownHost", true) -> "无法解析服务器地址，请检查 API 地址"
            msg.contains("Socket") -> "网络连接中断"
            msg.contains("Canceled") -> "请求已取消"
            msg.contains("内容过长") -> "内容过长，请缩短输入"
            else -> msg.take(100)
        }
    }
}
