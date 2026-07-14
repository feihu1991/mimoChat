package com.example.mimochat.data.remote

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * SSE 流式响应解析器
 * 处理 OpenAI 兼容格式的流式 JSON
 */
object ChatStreamParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * 解析 SSE 流式响应
     * 修复：不再在流结束时无条件发 Done，只在明确收到 [DONE] 时发
     */
    fun parseStream(lineFlow: Flow<String>): Flow<StreamChunk> = flow {
        var receivedDone = false
        var buffer = ""

        lineFlow.collect { rawLine ->
            val line = rawLine.trim()

            // 跳过空行和心跳
            if (line.isEmpty()) return@collect
            if (line.startsWith(":")) return@collect

            // 处理 [DONE]
            if (line == "data: [DONE]") {
                if (!receivedDone) {
                    receivedDone = true
                    emit(StreamChunk.Done)
                }
                return@collect
            }

            // 提取 JSON 数据
            val data = if (line.startsWith("data: ")) {
                line.removePrefix("data: ")
            } else {
                line
            }

            if (data.isBlank()) return@collect

            try {
                val jsonObj = json.parseToJsonElement(data) as? JsonObject ?: return@collect
                val chunk = parseChunk(jsonObj)
                if (chunk != null) emit(chunk)
            } catch (_: Exception) {
                // 不完整 JSON，尝试缓冲
                buffer += data
                try {
                    val jsonObj = json.parseToJsonElement(buffer) as? JsonObject
                    if (jsonObj != null) {
                        val chunk = parseChunk(jsonObj)
                        if (chunk != null) emit(chunk)
                        buffer = ""
                    }
                } catch (_: Exception) {
                    if (buffer.length > 10_000) buffer = ""
                }
            }
        }

        // 流结束 — 不再无条件发 Done
        // 如果没有收到 [DONE]，由调用方判断是否需要标记完成
    }

    private fun parseChunk(json: JsonObject): StreamChunk? {
        // 检查错误
        val error = json["error"]?.jsonObject
        if (error != null) {
            val message = error["message"]?.jsonPrimitive?.contentOrNull ?: "未知错误"
            return StreamChunk.Error(message)
        }

        // 解析 choices
        val choices = json["choices"]?.jsonArray ?: return null
        if (choices.isEmpty()) return null

        val choice = choices[0].jsonObject

        // 检查 finish_reason
        val finishReason = choice["finish_reason"]?.jsonPrimitive?.contentOrNull
        if (finishReason != null) {
            return StreamChunk.Finished(finishReason)
        }

        val delta = choice["delta"]?.jsonObject
        if (delta != null) {
            val content = delta["content"]?.jsonPrimitive?.contentOrNull
            val role = delta["role"]?.jsonPrimitive?.contentOrNull
            if (content != null) return StreamChunk.Delta(content)
            if (role != null) return StreamChunk.Role(role)
        }

        // 非流式 fallback
        val message = choice["message"]?.jsonObject
        if (message != null) {
            val content = message["content"]?.jsonPrimitive?.contentOrNull
            if (content != null) return StreamChunk.Complete(content)
        }

        return null
    }
}

sealed class StreamChunk {
    data class Delta(val text: String) : StreamChunk()
    data class Role(val role: String) : StreamChunk()
    data class Complete(val text: String) : StreamChunk()
    data class Finished(val reason: String) : StreamChunk()
    data class Error(val message: String) : StreamChunk()
    data object Done : StreamChunk()
}
