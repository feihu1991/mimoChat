package com.example.mimochat.data.repository

import com.example.mimochat.data.*
import com.example.mimochat.data.local.ConversationDao
import com.example.mimochat.data.local.MemoryDao
import com.example.mimochat.data.local.MessageDao
import com.example.mimochat.data.local.SettingsStorage
import com.example.mimochat.data.remote.ChatStreamParser
import com.example.mimochat.data.remote.MimoClient
import com.example.mimochat.data.remote.StreamChunk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 聊天仓库 - 处理多轮上下文构建、流式请求、消息状态
 */
class ChatRepository(
    private val messageDao: MessageDao,
    private val memoryDao: MemoryDao,
    private val settingsStorage: SettingsStorage
) {
    companion object {
        private const val MAX_CONTEXT_CHARS = 32_000 // 约 8k tokens
        private const val MAX_HISTORY_ROUNDS = 20
    }

    /**
     * 发送流式聊天请求
     * 构建完整多轮上下文 → 发送流式请求 → 逐块更新消息
     */
    fun sendStreamingMessage(
        conversationId: String,
        userMessageId: String,
        assistantMessageId: String,
        userText: String,
        systemPrompt: String,
        model: ModelId,
        images: List<String> = emptyList()
    ): Flow<StreamChunk> = flow {
        val config = settingsStorage.loadConnection()
        if (config.apiKey.isBlank()) {
            emit(StreamChunk.Error("请先在设置中配置 API Key"))
            return@flow
        }

        // 构建多轮上下文
        val contextMessages = buildContext(conversationId, systemPrompt)

        // 标记助手消息为流式中
        messageDao.updateStatus(assistantMessageId, MessageStatus.STREAMING)

        try {
            MimoClient.chatCompletionStream(
                config = config,
                model = model.apiName,
                messages = contextMessages,
                images = images
            ).collect { chunk ->
                when (chunk) {
                    is StreamChunk.Delta -> {
                        // 追加内容到数据库
                        val current = messageDao.getById(assistantMessageId)
                        val newContent = (current?.content ?: "") + chunk.text
                        messageDao.updateContent(assistantMessageId, newContent, MessageStatus.STREAMING)
                        emit(chunk)
                    }
                    is StreamChunk.Complete -> {
                        messageDao.updateContent(assistantMessageId, chunk.text, MessageStatus.SUCCESS)
                        emit(chunk)
                    }
                    is StreamChunk.Done -> {
                        val current = messageDao.getById(assistantMessageId)
                        if (current?.status == MessageStatus.STREAMING) {
                            messageDao.updateStatus(assistantMessageId, MessageStatus.SUCCESS)
                        }
                        emit(chunk)
                    }
                    is StreamChunk.Error -> {
                        messageDao.updateStatus(assistantMessageId, MessageStatus.FAILED, chunk.message)
                        emit(chunk)
                    }
                    else -> emit(chunk)
                }
            }
        } catch (e: Exception) {
            val error = MimoClient.translateError(e)
            messageDao.updateStatus(assistantMessageId, MessageStatus.FAILED, error)
            emit(StreamChunk.Error(error))
        }
    }

    /**
     * 构建多轮上下文
     * 策略: system prompt + 记忆 + 最近 N 轮对话（从旧到新裁剪）
     */
    suspend fun buildContext(conversationId: String, systemPrompt: String): List<Map<String, Any>> {
        val messages = messageDao.getByConversation(conversationId)
            .filter { it.status == MessageStatus.SUCCESS || it.status == MessageStatus.STREAMING }

        // 1. System prompt
        val context = mutableListOf<Map<String, Any>>()

        // 注入记忆
        val memories = memoryDao.getEnabled()
        val memoryText = if (memories.isNotEmpty()) {
            "\n\n以下是用户主动保存的长期偏好，仅在相关时参考：\n" +
                memories.mapIndexed { i, m -> "${i + 1}. ${m.content}" }.joinToString("\n")
        } else ""

        if (systemPrompt.isNotBlank() || memoryText.isNotBlank()) {
            context.add(mapOf("role" to "system", "content" to systemPrompt + memoryText))
        }

        // 2. 历史消息（排除正在生成和失败的）
        val validMessages = messages
            .filter { it.status == MessageStatus.SUCCESS }
            .takeLast(MAX_HISTORY_ROUNDS * 2) // 每轮 = user + assistant

        // 3. 字符数裁剪
        var totalChars = (systemPrompt + memoryText).length
        val selectedMessages = mutableListOf<MessageEntity>()
        for (msg in validMessages.reversed()) {
            if (totalChars + msg.content.length > MAX_CONTEXT_CHARS && selectedMessages.isNotEmpty()) {
                break
            }
            selectedMessages.add(msg)
            totalChars += msg.content.length
        }

        // 按时间正序
        selectedMessages.reverse()

        for (msg in selectedMessages) {
            context.add(mapOf("role" to msg.role, "content" to msg.content))
        }

        return context
    }

    /**
     * 重试失败消息 - 重新发送相同上下文
     */
    fun retryMessage(
        conversationId: String,
        assistantMessageId: String,
        systemPrompt: String,
        model: ModelId
    ): Flow<StreamChunk> = flow {
        val config = settingsStorage.loadConnection()
        if (config.apiKey.isBlank()) {
            emit(StreamChunk.Error("请先在设置中配置 API Key"))
            return@flow
        }

        // 重置状态
        messageDao.updateContent(assistantMessageId, "", MessageStatus.STREAMING)

        val contextMessages = buildContext(conversationId, systemPrompt)

        try {
            MimoClient.chatCompletionStream(
                config = config,
                model = model.apiName,
                messages = contextMessages
            ).collect { chunk ->
                when (chunk) {
                    is StreamChunk.Delta -> {
                        val current = messageDao.getById(assistantMessageId)
                        val newContent = (current?.content ?: "") + chunk.text
                        messageDao.updateContent(assistantMessageId, newContent, MessageStatus.STREAMING)
                        emit(chunk)
                    }
                    is StreamChunk.Done -> {
                        val current = messageDao.getById(assistantMessageId)
                        if (current?.status == MessageStatus.STREAMING) {
                            messageDao.updateStatus(assistantMessageId, MessageStatus.SUCCESS)
                        }
                        emit(chunk)
                    }
                    is StreamChunk.Error -> {
                        messageDao.updateStatus(assistantMessageId, MessageStatus.FAILED, chunk.message)
                        emit(chunk)
                    }
                    else -> emit(chunk)
                }
            }
        } catch (e: Exception) {
            val error = MimoClient.translateError(e)
            messageDao.updateStatus(assistantMessageId, MessageStatus.FAILED, error)
            emit(StreamChunk.Error(error))
        }
    }

    /**
     * 重新生成 - 删除原回答，创建新助手消息
     */
    fun regenerateMessage(
        conversationId: String,
        oldAssistantMessageId: String,
        newAssistantMessageId: String,
        systemPrompt: String,
        model: ModelId
    ): Flow<StreamChunk> = flow {
        // 删除旧回答
        messageDao.deleteById(oldAssistantMessageId)

        // 重新构建上下文（不含旧回答）
        val config = settingsStorage.loadConnection()
        if (config.apiKey.isBlank()) {
            emit(StreamChunk.Error("请先在设置中配置 API Key"))
            return@flow
        }

        messageDao.updateStatus(newAssistantMessageId, MessageStatus.STREAMING)
        val contextMessages = buildContext(conversationId, systemPrompt)

        try {
            MimoClient.chatCompletionStream(
                config = config,
                model = model.apiName,
                messages = contextMessages
            ).collect { chunk ->
                when (chunk) {
                    is StreamChunk.Delta -> {
                        val current = messageDao.getById(newAssistantMessageId)
                        val newContent = (current?.content ?: "") + chunk.text
                        messageDao.updateContent(newAssistantMessageId, newContent, MessageStatus.STREAMING)
                        emit(chunk)
                    }
                    is StreamChunk.Done -> {
                        messageDao.updateStatus(newAssistantMessageId, MessageStatus.SUCCESS)
                        emit(chunk)
                    }
                    is StreamChunk.Error -> {
                        messageDao.updateStatus(newAssistantMessageId, MessageStatus.FAILED, chunk.message)
                        emit(chunk)
                    }
                    else -> emit(chunk)
                }
            }
        } catch (e: Exception) {
            val error = MimoClient.translateError(e)
            messageDao.updateStatus(newAssistantMessageId, MessageStatus.FAILED, error)
            emit(StreamChunk.Error(error))
        }
    }
}
