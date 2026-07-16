package com.example.mimochat.data.remote

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * SSE parser for OpenAI-compatible text and function-tool streams.
 * Tool arguments can arrive in many fragments; the Agent repository joins them
 * by the stable tool-call index before executing anything.
 */
object ChatStreamParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parseStream(lineFlow: Flow<String>): Flow<StreamChunk> = flow {
        var receivedDone = false
        var buffer = ""

        lineFlow.collect { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith(":") || line.startsWith("event:") || line.startsWith("id:")) {
                return@collect
            }

            val data = if (line.startsWith("data:")) line.removePrefix("data:").trimStart() else line
            if (data.isBlank()) return@collect
            if (data == "[DONE]") {
                if (!receivedDone) {
                    receivedDone = true
                    emit(StreamChunk.Done)
                }
                return@collect
            }

            try {
                val jsonObj = json.parseToJsonElement(data) as? JsonObject ?: return@collect
                parseChunks(jsonObj).forEach { emit(it) }
                buffer = ""
            } catch (_: Exception) {
                buffer += data
                try {
                    val jsonObj = json.parseToJsonElement(buffer) as? JsonObject
                    if (jsonObj != null) {
                        parseChunks(jsonObj).forEach { emit(it) }
                        buffer = ""
                    }
                } catch (_: Exception) {
                    if (buffer.length > 20_000) buffer = ""
                }
            }
        }
    }

    private fun parseChunks(json: JsonObject): List<StreamChunk> {
        val error = json["error"]?.jsonObject
        if (error != null) {
            val message = error["message"]?.jsonPrimitive?.contentOrNull ?: "未知错误"
            return listOf(StreamChunk.Error(message))
        }

        val choices = json["choices"]?.jsonArray ?: return emptyList()
        if (choices.isEmpty()) return emptyList()
        val choice = choices[0].jsonObject
        val events = mutableListOf<StreamChunk>()

        val delta = choice["delta"]?.jsonObject
        if (delta != null) {
            delta["role"]?.jsonPrimitive?.contentOrNull?.let { events += StreamChunk.Role(it) }
            delta["reasoning_content"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotEmpty() }
                ?.let { events += StreamChunk.ReasoningDelta(it) }
            textContent(delta["content"])
                ?.takeIf { it.isNotEmpty() }
                ?.let { events += StreamChunk.Delta(it) }

            delta["tool_calls"]?.jsonArray?.forEach { element ->
                val tool = element.jsonObject
                val function = tool["function"]?.jsonObject
                events += StreamChunk.ToolCallDelta(
                    index = tool["index"]?.jsonPrimitive?.intOrNull ?: 0,
                    id = tool["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    name = function?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty(),
                    arguments = function?.get("arguments")?.jsonPrimitive?.contentOrNull.orEmpty()
                )
            }
        }

        val message = choice["message"]?.jsonObject
        if (message != null) {
            textContent(message["content"])
                ?.takeIf { it.isNotEmpty() }
                ?.let { events += StreamChunk.Complete(it) }
            message["tool_calls"]?.jsonArray?.forEachIndexed { index, element ->
                val tool = element.jsonObject
                val function = tool["function"]?.jsonObject
                events += StreamChunk.ToolCallDelta(
                    index = index,
                    id = tool["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    name = function?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty(),
                    arguments = function?.get("arguments")?.jsonPrimitive?.contentOrNull.orEmpty()
                )
            }
        }

        choice["finish_reason"]?.jsonPrimitive?.contentOrNull?.let {
            events += StreamChunk.Finished(it)
        }
        return events
    }

    private fun textContent(element: JsonElement?): String? = when (element) {
        null -> null
        is JsonArray -> element.mapNotNull { part ->
            val obj = part as? JsonObject
            textContent(obj?.get("text") ?: part)
        }.joinToString("").ifEmpty { null }
        is JsonPrimitive -> element.contentOrNull
        is JsonObject -> textContent(element["text"] ?: element["content"])
        else -> null
    }
}

sealed class StreamChunk {
    data class Delta(val text: String) : StreamChunk()
    data class ReasoningDelta(val text: String) : StreamChunk()
    data class Role(val role: String) : StreamChunk()
    data class Complete(val text: String) : StreamChunk()
    data class ToolCallDelta(
        val index: Int,
        val id: String,
        val name: String,
        val arguments: String
    ) : StreamChunk()
    data class Finished(val reason: String) : StreamChunk()
    data class Error(val message: String) : StreamChunk()
    data object Done : StreamChunk()
}
