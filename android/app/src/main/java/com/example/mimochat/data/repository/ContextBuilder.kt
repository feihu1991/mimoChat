package com.example.mimochat.data.repository

import com.example.mimochat.data.*
import com.example.mimochat.data.local.MemoryDao
import com.example.mimochat.data.local.MessageDao

/**
 * 上下文构建器 - 按完整对话轮次裁剪。
 *
 * 规则：
 * 1. 当前用户消息优先保留，超过硬上限时做防御性截断
 * 2. system prompt 与记忆占用剩余预算
 * 3. 历史按完整轮次从新到旧加入
 * 4. 最多保留 30 轮历史
 * 5. 排除 FAILED/STOPPED/PENDING/STREAMING 消息
 */
class ContextBuilder(
    private val messageDao: MessageDao,
    private val memoryDao: MemoryDao
) {
    companion object {
        const val MAX_CONTEXT_CHARS = 30_000
        private const val MAX_MEMORY_CHARS = 2_000
        private const val MAX_HISTORY_ROUNDS = 30
    }

    data class ConversationTurn(
        val user: MessageEntity,
        val assistant: MessageEntity?
    )

    suspend fun build(
        conversationId: String,
        systemPrompt: String,
        currentUserMessageId: String? = null
    ): List<Map<String, Any>> {
        val allMessages = messageDao.getByConversation(conversationId)
        val currentMessage = currentUserMessageId?.let { id ->
            allMessages.find { it.id == id && it.role == "user" }
        }
        val currentContent = currentMessage?.content?.take(MAX_CONTEXT_CHARS).orEmpty()

        val memoryText = buildMemoryText()
        val fullSystemPrompt = if (memoryText.isNotBlank()) {
            "$systemPrompt\n\n$memoryText"
        } else {
            systemPrompt
        }
        val systemBudget = (MAX_CONTEXT_CHARS - currentContent.length).coerceAtLeast(0)
        val boundedSystemPrompt = fullSystemPrompt.take(systemBudget)

        val context = mutableListOf<Map<String, Any>>()
        if (boundedSystemPrompt.isNotBlank()) {
            context.add(mapOf("role" to "system", "content" to boundedSystemPrompt))
        }

        val validMessages = allMessages.filter { msg ->
            msg.status == MessageStatus.SUCCESS && msg.id != currentUserMessageId
        }
        val turns = buildTurns(validMessages)

        var usedChars = boundedSystemPrompt.length + currentContent.length
        val selectedTurns = mutableListOf<ConversationTurn>()
        for (turn in turns.reversed()) {
            if (selectedTurns.size >= MAX_HISTORY_ROUNDS) break
            val turnChars = turn.user.content.length + (turn.assistant?.content?.length ?: 0)
            if (usedChars + turnChars > MAX_CONTEXT_CHARS) break
            selectedTurns.add(turn)
            usedChars += turnChars
        }
        selectedTurns.reverse()

        for (turn in selectedTurns) {
            context.add(mapOf("role" to "user", "content" to turn.user.content))
            turn.assistant?.let {
                context.add(mapOf("role" to "assistant", "content" to it.content))
            }
        }

        if (currentMessage != null) {
            context.add(mapOf("role" to "user", "content" to currentContent))
        }

        return context
    }

    private fun buildTurns(messages: List<MessageEntity>): List<ConversationTurn> {
        val turns = mutableListOf<ConversationTurn>()
        var pendingUser: MessageEntity? = null

        for (msg in messages) {
            when (msg.role) {
                "user" -> {
                    pendingUser?.let { turns.add(ConversationTurn(it, null)) }
                    pendingUser = msg
                }
                "assistant" -> {
                    pendingUser?.let {
                        turns.add(ConversationTurn(it, msg))
                        pendingUser = null
                    }
                }
            }
        }
        pendingUser?.let { turns.add(ConversationTurn(it, null)) }
        return turns
    }

    private suspend fun buildMemoryText(): String {
        val memories = memoryDao.getEnabled()
        if (memories.isEmpty()) return ""

        val sb = StringBuilder("以下是用户主动保存的长期偏好，仅在相关时参考：\n")
        for ((i, m) in memories.withIndex()) {
            val line = "${i + 1}. ${m.content}\n"
            if (sb.length + line.length > MAX_MEMORY_CHARS) break
            sb.append(line)
        }
        return sb.toString().trim()
    }
}
