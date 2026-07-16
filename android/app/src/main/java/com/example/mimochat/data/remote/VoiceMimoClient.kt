package com.example.mimochat.data.remote

import android.util.Base64
import com.example.mimochat.data.AuthMode
import com.example.mimochat.data.MimoConnection
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Dedicated non-streaming client for TTS, voice design and voice clone. */
object VoiceMimoClient {
    private val client = HttpClient(OkHttp) {
        expectSuccess = true
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 30_000
            requestTimeoutMillis = 180_000
            socketTimeoutMillis = 180_000
        }
        engine {
            config { retryOnConnectionFailure(true) }
        }
    }

    suspend fun synthesizeSpeech(
        config: MimoConnection,
        model: String,
        text: String,
        voiceName: String,
        voiceSample: String?,
        voicePrompt: String
    ): ByteArray {
        val audio = if (model.contains("voiceclone")) {
            val sample = voiceSample ?: throw IllegalStateException("当前角色还没有固定音色样本")
            mapOf("format" to "wav", "voice" to sample)
        } else {
            mapOf("format" to "wav", "voice" to voiceName)
        }
        val body = mapOf(
            "model" to model,
            "messages" to listOf(
                mapOf("role" to "user", "content" to voicePrompt),
                mapOf("role" to "assistant", "content" to text)
            ),
            "audio" to audio
        )
        return requestAudio(config, body)
    }

    suspend fun designVoice(
        config: MimoConnection,
        description: String,
        text: String
    ): ByteArray {
        val body = mapOf(
            "model" to "mimo-v2.5-tts-voicedesign",
            "messages" to listOf(
                mapOf("role" to "user", "content" to description),
                mapOf("role" to "assistant", "content" to text)
            ),
            "audio" to mapOf("format" to "wav")
        )
        return requestAudio(config, body)
    }

    private suspend fun requestAudio(
        config: MimoConnection,
        body: Map<String, Any>
    ): ByteArray {
        val response = client.post("${config.baseUrl.trimEnd('/')}/chat/completions") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            if (config.authMode == AuthMode.BEARER) {
                header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
            } else {
                header("api-key", config.apiKey)
            }
            setBody(body)
        }
        val data = response.body<JsonObject>()
        val audioBase64 = data["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject
            ?.get("audio")?.jsonObject
            ?.get("data")?.jsonPrimitive?.content
            ?: throw IllegalStateException("语音接口没有返回音频")
        return Base64.decode(audioBase64, Base64.DEFAULT)
    }
}
