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

private class StreamTerminatedException : RuntimeException()

/** Legacy plain-chat repository retained for non-agent callers. */
class ChatRepository(
    private val messageDao: MessageDao,
    private val contextBuilder: ContextBuilder,
    private val settingsStorage: SettingsStorage
) {
    companion object {
        private const val DB_WRITE_INTERVAL_MS = 200L
        private const val DB_WRITE_CHAR_THRESHOLD = 50
    }

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

        val contextMessages = withContext(Dispatchers.IO) {
            contextBuilder.build(conversationId, systemPrompt, userMessageId)
        }

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
                    }
                    is StreamChunk.Error -> {
                        withContext(Dispatchers.IO) {
                            messageDao.updateStatus(assistantMessageId, MessageStatus.FAILED, chunk.message)
                        }
                        emit(chunk)
                        throw StreamTerminatedException()
                    }
                    is StreamChunk.Role -> emit(chunk)
                    is StreamChunk.Finished -> emit(chunk)
                    is StreamChunk.ReasoningDelta -> Unit
                    is StreamChunk.ToolCallDelta -> Unit
                }
            }

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
        } catch (_: StreamTerminatedException) {
            Unit
        } catch (e: CancellationException) {
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
    }.flowOn(Dispatchers.IO)
}
