package com.example.mimochat.data.remote

import com.example.mimochat.data.AuthMode
import com.example.mimochat.data.MimoConnection
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** OpenAI-compatible MiMo client dedicated to coding-agent tool calls. */
object AgentMimoClient {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = HttpClient(OkHttp) {
        expectSuccess = true
        install(ContentNegotiation) { json(AgentMimoClient.json) }
        install(HttpTimeout) {
            connectTimeoutMillis = 30_000
            requestTimeoutMillis = 180_000
            socketTimeoutMillis = 180_000
        }
        engine { config { retryOnConnectionFailure(true) } }
    }

    fun stream(
        config: MimoConnection,
        model: String,
        messages: List<JsonObject>,
        tools: List<JsonObject>
    ): Flow<StreamChunk> = flow {
        val body = buildJsonObject {
            put("model", model)
            put("messages", JsonArray(messages))
            put("stream", true)
            put("tools", JsonArray(tools))
            put("tool_choice", "auto")
        }

        val response = client.post("${config.baseUrl.trimEnd('/')}/chat/completions") {
            header("Content-Type", "application/json")
            if (config.authMode == AuthMode.BEARER) {
                header("Authorization", "Bearer ${config.apiKey}")
            } else {
                header("api-key", config.apiKey)
            }
            header("X-Mimo-Source", "mimo-chat-android-agent")
            setBody(body)
            timeout { requestTimeoutMillis = 180_000 }
        }

        val channel = response.bodyAsChannel()
        val lines = flow {
            while (!channel.isClosedForRead) {
                emit(channel.readUTF8Line() ?: break)
            }
        }
        ChatStreamParser.parseStream(lines).collect { emit(it) }
    }.flowOn(Dispatchers.IO)

    fun textMessage(role: String, content: String): JsonObject = buildJsonObject {
        put("role", role)
        put("content", content)
    }

    fun toolResultMessage(callId: String, content: String): JsonObject = buildJsonObject {
        put("role", "tool")
        put("tool_call_id", callId)
        put("content", content)
    }

    fun assistantToolMessage(text: String, calls: List<Triple<String, String, String>>): JsonObject = buildJsonObject {
        put("role", "assistant")
        put("content", JsonPrimitive(text))
        put("tool_calls", JsonArray(calls.map { (id, name, arguments) ->
            buildJsonObject {
                put("id", id)
                put("type", "function")
                put("function", buildJsonObject {
                    put("name", name)
                    put("arguments", arguments)
                })
            }
        }))
    }
}
