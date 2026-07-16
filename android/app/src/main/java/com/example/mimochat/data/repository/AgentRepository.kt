package com.example.mimochat.data.repository

import com.example.mimochat.core.agent.AgentToolCall
import com.example.mimochat.core.agent.AgentToolExecutor
import com.example.mimochat.core.agent.ApprovalManager
import com.example.mimochat.core.workspace.GitHubWorkspace
import com.example.mimochat.data.MessageStatus
import com.example.mimochat.data.ModelId
import com.example.mimochat.data.local.MessageDao
import com.example.mimochat.data.local.SettingsStorage
import com.example.mimochat.data.remote.AgentMimoClient
import com.example.mimochat.data.remote.StreamChunk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private class AgentStreamException(message: String) : RuntimeException(message)

/**
 * Multi-step coding agent loop:
 * model response -> local tool execution -> tool result -> next model response.
 */
class AgentRepository(
    private val messageDao: MessageDao,
    private val contextBuilder: ContextBuilder,
    private val settingsStorage: SettingsStorage,
    workspace: GitHubWorkspace,
    approvals: ApprovalManager
) {
    companion object {
        private const val MAX_AGENT_STEPS = 20
        private const val DB_WRITE_INTERVAL_MS = 180L
        private const val DB_WRITE_CHAR_THRESHOLD = 60
    }

    private val tools = AgentToolExecutor(workspace, approvals)

    fun executeGeneration(
        conversationId: String,
        assistantMessageId: String,
        userMessageId: String,
        systemPrompt: String,
        model: ModelId
    ): Flow<StreamChunk> = flow {
        val config = settingsStorage.loadConnection()
        if (config.apiKey.isBlank()) {
            withContext(Dispatchers.IO) {
                messageDao.updateStatus(assistantMessageId, MessageStatus.FAILED, "请先在设置中配置 API Key")
            }
            emit(StreamChunk.Error("请先在设置中配置 API Key"))
            return@flow
        }

        withContext(Dispatchers.IO) {
            messageDao.updateStatus(assistantMessageId, MessageStatus.STREAMING)
        }

        val context = withContext(Dispatchers.IO) {
            contextBuilder.build(conversationId, systemPrompt, userMessageId)
        }
        val messages = context.map(::toJsonMessage).toMutableList()
        val agentInstructions = """
                You are MiMo, a conversational assistant running inside an Android application. Only when the user
                explicitly asks for GitHub/codebase work may you inspect and change the configured app-private project
                workspace through the provided structured tools. Never claim that you read,
                changed, committed, pushed, or opened a pull request unless the corresponding tool result confirms it.
                Prefer list_files/grep_files/read_file before editing. Use edit_file for precise changes and write_file
                for new files or complete rewrites. File writes, deletes, branches, pushes, and pull requests require
                explicit user approval in the Android UI. Do not request build, packaging, deployment, arbitrary shell,
                or binary execution. For normal questions, answer conversationally without calling tools.
                When a coding task is complete, summarize files changed and Git state.
                """.trimIndent()
        val systemIndex = messages.indexOfFirst {
            it["role"]?.jsonPrimitive?.contentOrNull == "system"
        }
        if (systemIndex >= 0) {
            val existing = messages[systemIndex]["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
            messages[systemIndex] = AgentMimoClient.textMessage(
                "system",
                "$agentInstructions\n\n$existing"
            )
        } else {
            messages.add(0, AgentMimoClient.textMessage("system", agentInstructions))
        }

        val visible = StringBuilder()
        var lastWriteTime = 0L
        var lastWriteLength = 0

        suspend fun persist(status: MessageStatus = MessageStatus.STREAMING, force: Boolean = false) {
            val now = System.currentTimeMillis()
            if (!force && now - lastWriteTime < DB_WRITE_INTERVAL_MS && visible.length - lastWriteLength < DB_WRITE_CHAR_THRESHOLD) {
                return
            }
            withContext(Dispatchers.IO) {
                messageDao.updateContent(assistantMessageId, visible.toString(), status)
            }
            lastWriteTime = now
            lastWriteLength = visible.length
        }

        try {
            var completed = false
            repeat(MAX_AGENT_STEPS) { step ->
                if (completed) return@repeat

                val stepText = StringBuilder()
                val calls = sortedMapOf<Int, MutableToolCall>()
                var streamError: String? = null

                AgentMimoClient.stream(
                    config = config,
                    model = model.apiName,
                    messages = messages,
                    tools = tools.definitions()
                ).collect { chunk ->
                    when (chunk) {
                        is StreamChunk.Delta -> {
                            stepText.append(chunk.text)
                            visible.append(chunk.text)
                            emit(chunk)
                            persist()
                        }
                        is StreamChunk.Complete -> {
                            stepText.append(chunk.text)
                            visible.append(chunk.text)
                            emit(StreamChunk.Delta(chunk.text))
                            persist()
                        }
                        is StreamChunk.ReasoningDelta -> Unit
                        is StreamChunk.ToolCallDelta -> {
                            val current = calls.getOrPut(chunk.index) { MutableToolCall(chunk.index) }
                            if (chunk.id.isNotBlank()) current.id = chunk.id
                            if (chunk.name.isNotBlank()) current.name = chunk.name
                            current.arguments.append(chunk.arguments)
                        }
                        is StreamChunk.Error -> streamError = chunk.message
                        is StreamChunk.Role,
                        is StreamChunk.Finished,
                        StreamChunk.Done -> Unit
                    }
                }

                streamError?.let { throw AgentStreamException(it) }
                val finalizedCalls = calls.values.map { it.finalize() }
                if (finalizedCalls.isEmpty()) {
                    if (stepText.isBlank() && visible.isBlank()) {
                        throw AgentStreamException("模型没有返回文本或工具调用")
                    }
                    completed = true
                    persist(MessageStatus.SUCCESS, force = true)
                    emit(StreamChunk.Done)
                    return@repeat
                }

                val toolSummary = StringBuilder("工具执行结果（请基于这些结果继续回答；不要假设未返回的内容）：\n")
                for (call in finalizedCalls) {
                    val started = "\n\n> 🔧 ${call.name}"
                    visible.append(started)
                    emit(StreamChunk.Delta(started))
                    persist(force = true)

                    val result = tools.execute(call)
                    val marker = if (result.failed) "⚠" else "✓"
                    val displayLine = "\n> $marker ${result.display}"
                    visible.append(displayLine)
                    emit(StreamChunk.Delta(displayLine))
                    persist(force = true)
                    toolSummary.append("\n- ${call.name}: ")
                        .append(result.content.take(80_000))
                }
                // MiMo API 对 assistant.tool_calls + role=tool 的多轮历史校验较严格。
                // 用普通 user 消息承载本地工具结果，保持 Android Agent 在多轮执行时兼容。
                messages += AgentMimoClient.textMessage("user", toolSummary.toString())

                if (step == MAX_AGENT_STEPS - 1) {
                    throw AgentStreamException("Agent 达到最大执行步数，已停止以避免循环")
                }
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) {
                messageDao.updateContent(assistantMessageId, visible.toString(), MessageStatus.STOPPED)
            }
            throw e
        } catch (e: Exception) {
            val message = e.message ?: "Agent 执行失败"
            withContext(Dispatchers.IO) {
                messageDao.updateStatus(assistantMessageId, MessageStatus.FAILED, message)
                if (visible.isNotBlank()) {
                    messageDao.updateContent(assistantMessageId, visible.toString(), MessageStatus.FAILED)
                }
            }
            emit(StreamChunk.Error(message))
        }
    }.flowOn(Dispatchers.IO)

    private fun toJsonMessage(message: Map<String, Any>): JsonObject = buildJsonObject {
        put("role", message["role"]?.toString().orEmpty())
        put("content", message["content"]?.toString().orEmpty())
    }

    private data class MutableToolCall(
        val index: Int,
        var id: String = "",
        var name: String = "",
        val arguments: StringBuilder = StringBuilder()
    ) {
        fun finalize(): AgentToolCall {
            val finalId = id.ifBlank { "call-$index-${System.nanoTime()}" }
            require(name.isNotBlank()) { "模型返回了缺少名称的工具调用" }
            return AgentToolCall(index, finalId, name, arguments.toString().ifBlank { "{}" })
        }
    }
}
