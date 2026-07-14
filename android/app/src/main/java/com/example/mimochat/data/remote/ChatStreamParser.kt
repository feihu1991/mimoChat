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
     * 解析 SSE 流式响应，逐块输出文本增量
     * 处理: 空行、心跳、[DONE]、不完整 JSON、Unicode 边界
     */
    fun parseStream(lineFlow: Flow<String>): Flow<StreamChunk> = flow {
        var buffer = ""

        lineFlow.collect { rawLine ->
            val line = rawLine.trim()

            // 跳过空行和心跳
            if (line.isEmpty()) return@collect
            if (line.startsWith(":")) return@collect // SSE comment / heartbeat

            // 处理 [DONE]
            if (line == "data: [DONE]") {
                emit(StreamChunk.Done)
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
                    // 仍然不完整，继续缓冲
                    if (buffer.length > 10000) buffer = "" // 防止无限增长
                }
            }
        }

        // 如果流结束但没有收到 [DONE]，也发出完成信号
        emit(StreamChunk.Done)
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
        val delta = choice["delta"]?.jsonObject

        if (delta != null) {
            val content = delta["content"]?.jsonPrimitive?.contentOrNull
            val role = delta["role"]?.jsonPrimitive?.contentOrNull
            if (content != null) {
                return StreamChunk.Delta(content)
            }
            if (role != null) {
                return StreamChunk.Role(role)
            }
        }

        // 非流式 fallback
        val message = choice["message"]?.jsonObject
        if (message != null) {
            val content = message["content"]?.jsonPrimitive?.contentOrNull
            if (content != null) {
                return StreamChunk.Complete(content)
            }
        }

        // 检查 finish_reason
        val finishReason = choice["finish_reason"]?.jsonPrimitive?.contentOrNull
        if (finishReason != null) {
            return StreamChunk.Finished(finishReason)
        }

        return null
    }
}

sealed class StreamChunk {
    /** 流式文本增量 */
    data class Delta(val text: String) : StreamChunk()
    /** 角色标识 */
    data class Role(val role: String) : StreamChunk()
    /** 非流式完整回复 */
    data class Complete(val text: String) : StreamChunk()
    /** 流结束原因 */
    data class Finished(val reason: String) : StreamChunk()
    /** 错误 */
    data class Error(val message: String) : StreamChunk()
    /** 流完成 */
    data object Done : StreamChunk()
}
