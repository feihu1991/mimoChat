package com.example.mimochat.data.repository

import com.example.mimochat.data.*
import com.example.mimochat.data.local.MessageDao
import com.example.mimochat.data.local.SettingsStorage
import com.example.mimochat.data.remote.MimoClient
import com.example.mimochat.data.remote.StreamChunk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * 聊天仓库 - 流式生成 + DB 写入节流
 */
class ChatRepository(
    private val messageDao: MessageDao,
    private val contextBuilder: ContextBuilder,
    private val settingsStorage: SettingsStorage
) {
    companion object {
        private const val DB_WRITE_INTERVAL_MS = 200L
        private const val DB_WRITE_CHAR_THRESHOLD = 50
    }

    /**
     * 执行流式生成 - 供所有生成入口调用
     *
     * @param conversationId 会话 ID
     * @param assistantMessageId 助手消息 ID
     * @param userMessageId 用户消息 ID（用于上下文构建）
     * @param systemPrompt 系统提示词
     * @param model 模型
     */
    fun executeGeneration(
        conversationId: String,
        assistantMessageId: String,
        userMessageId: String,
        systemPrompt: String,
        model: ModelId
    ): Flow<StreamChunk> = flow {
        val config = settingsStorage.loadConnection()

        // 预检查 API Key
        if (config.apiKey.isBlank()) {
            withContext(Dispatchers.IO) {
                messageDao.updateStatus(assistantMessageId, MessageStatus.FAILED, "请先在设置中配置 API Key")
            }
            emit(StreamChunk.Error("请先在设置中配置 API Key"))
            return@flow
        }

        // 标记开始
        withContext(Dispatchers.IO) {
            messageDao.updateStatus(assistantMessageId, MessageStatus.STREAMING)
        }

        // 构建上下文
        val contextMessages = withContext(Dispatchers.IO) {
            contextBuilder.build(conversationId, systemPrompt, userMessageId)
        }

        // 流式内容缓冲
        val contentBuffer = StringBuilder()
        var lastDbWriteTime = System.currentTimeMillis()
        var lastDbWriteLength = 0
        var receivedDone = false

        try {
            MimoClient.chatCompletionStream(
                config = config,
                model = model.apiName,
                messages = contextMessages
            ).collect { chunk ->
                when (chunk) {
                    is StreamChunk.Delta -> {
                        contentBuffer.append(chunk.text)
                        emit(chunk)

                        // 节流写入 DB
                        val now = System.currentTimeMillis()
                        val charsSinceWrite = contentBuffer.length - lastDbWriteLength
                        if (now - lastDbWriteTime >= DB_WRITE_INTERVAL_MS ||
                            charsSinceWrite >= DB_WRITE_CHAR_THRESHOLD
                        ) {
                            withContext(Dispatchers.IO) {
                                messageDao.updateContent(
                                    assistantMessageId,
                                    contentBuffer.toString(),
                                    MessageStatus.STREAMING
                                )
                            }
                            lastDbWriteTime = now
                            lastDbWriteLength = contentBuffer.length
                        }
                    }
                    is StreamChunk.Complete -> {
                        contentBuffer.clear()
                        contentBuffer.append(chunk.text)
                        withContext(Dispatchers.IO) {
                            messageDao.updateContent(assistantMessageId, chunk.text, MessageStatus.SUCCESS)
                        }
                        emit(chunk)
                    }
                    is StreamChunk.Done -> {
                        if (!receivedDone) {
                            receivedDone = true
                            // 确保最终内容写入 DB
                            withContext(Dispatchers.IO) {
                                val current = messageDao.getById(assistantMessageId)
                                if (current?.status == MessageStatus.STREAMING) {
                                    messageDao.updateContent(
                                        assistantMessageId,
                                        contentBuffer.toString(),
                                        MessageStatus.SUCCESS
                                    )
                                }
                            }
                            emit(chunk)
                        }
                        // 后续的重复 Done 忽略
                    }
                    is StreamChunk.Error -> {
                        withContext(Dispatchers.IO) {
                            messageDao.updateStatus(assistantMessageId, MessageStatus.FAILED, chunk.message)
                        }
                        emit(chunk)
                    }
                    is StreamChunk.Role -> emit(chunk)
                    is StreamChunk.Finished -> emit(chunk)
                }
            }

            // 流结束但没有收到 [DONE] — 标记为可能不完整
            if (!receivedDone) {
                withContext(Dispatchers.IO) {
                    val current = messageDao.getById(assistantMessageId)
                    if (current?.status == MessageStatus.STREAMING) {
                        if (contentBuffer.isNotEmpty()) {
                            messageDao.updateContent(
                                assistantMessageId,
                                contentBuffer.toString(),
                                MessageStatus.SUCCESS
                            )
                        } else {
                            messageDao.updateStatus(
                                assistantMessageId,
                                MessageStatus.FAILED,
                                "回答可能不完整：连接已断开"
                            )
                        }
                    }
                }
                emit(StreamChunk.Done)
            }
        } catch (e: CancellationException) {
            // 用户取消 — 保留已生成内容
            withContext(NonCancellable + Dispatchers.IO) {
                val current = messageDao.getById(assistantMessageId)
                if (current?.status == MessageStatus.STREAMING) {
                    messageDao.updateContent(
                        assistantMessageId,
                        contentBuffer.toString(),
                        MessageStatus.STOPPED
                    )
                }
            }
            throw e
        } catch (e: Exception) {
            val error = MimoClient.translateError(e)
            withContext(Dispatchers.IO) {
                messageDao.updateStatus(assistantMessageId, MessageStatus.FAILED, error)
            }
            emit(StreamChunk.Error(error))
        }
    }
}
