package com.example.mimochat.data.repository

import com.example.mimochat.data.*
import com.example.mimochat.data.local.ConversationDao
import com.example.mimochat.data.local.MessageDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 会话仓库 - 会话 CRUD + 消息管理
 */
class ConversationRepository(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) {
    fun getAllConversationsFlow(): Flow<List<ConversationEntity>> = conversationDao.getAllFlow()

    fun getMessagesFlow(conversationId: String): Flow<List<Message>> {
        return messageDao.getByConversationFlow(conversationId).map { entities ->
            entities.map { it.toUiModel() }
        }
    }

    suspend fun createConversation(title: String = "新对话", roleId: String = "mimo", model: ModelId = ModelId.MIMO_V2_5): String {
        val entity = ConversationEntity(
            title = title,
            roleId = roleId,
            model = model.apiName
        )
        conversationDao.upsert(entity)
        return entity.id
    }

    suspend fun updateTitle(conversationId: String, title: String) {
        val conv = conversationDao.getById(conversationId) ?: return
        conversationDao.update(conv.copy(title = title, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteConversation(conversationId: String) {
        conversationDao.deleteById(conversationId)
    }

    suspend fun deleteAllConversations() {
        conversationDao.deleteAll()
    }

    suspend fun searchConversations(query: String): List<ConversationEntity> {
        return conversationDao.search(query)
    }

    suspend fun getConversation(conversationId: String): ConversationEntity? {
        return conversationDao.getById(conversationId)
    }

    // ── Messages ──

    suspend fun insertMessage(message: MessageEntity) {
        messageDao.upsert(message)
    }

    suspend fun updateMessageContent(messageId: String, content: String, status: MessageStatus) {
        messageDao.updateContent(messageId, content, status)
    }

    suspend fun updateMessageStatus(messageId: String, status: MessageStatus, error: String? = null) {
        messageDao.updateStatus(messageId, status, error)
    }

    suspend fun getMessage(messageId: String): MessageEntity? {
        return messageDao.getById(messageId)
    }

    suspend fun deleteMessage(messageId: String) {
        messageDao.deleteById(messageId)
    }

    suspend fun getLastUserMessage(conversationId: String): MessageEntity? {
        return messageDao.getLastUserMessage(conversationId)
    }

    suspend fun getMessages(conversationId: String): List<MessageEntity> {
        return messageDao.getByConversation(conversationId)
    }

    suspend fun generateTitle(conversationId: String) {
        val messages = messageDao.getByConversation(conversationId)
        val firstUserMessage = messages.firstOrNull { it.role == "user" }
        if (firstUserMessage != null) {
            val title = firstUserMessage.content.take(14).ifEmpty { "新对话" }
            updateTitle(conversationId, title)
        }
    }
}

// ── Extensions ──

fun MessageEntity.toUiModel(): Message {
    return Message(
        id = id,
        role = when (role) {
            "user" -> MessageRole.USER
            "assistant" -> MessageRole.ASSISTANT
            "system" -> MessageRole.SYSTEM
            else -> MessageRole.USER
        },
        text = content,
        status = status,
        errorMessage = errorMessage,
        model = model?.let { ModelId.fromApiName(it) }
    )
}

fun Message.toEntity(conversationId: String): MessageEntity {
    return MessageEntity(
        id = id,
        conversationId = conversationId,
        role = when (role) {
            MessageRole.USER -> "user"
            MessageRole.ASSISTANT -> "assistant"
            MessageRole.SYSTEM -> "system"
        },
        content = text,
        status = status,
        errorMessage = errorMessage,
        model = model?.apiName
    )
}
