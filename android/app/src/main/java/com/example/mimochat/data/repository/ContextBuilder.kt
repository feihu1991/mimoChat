package com.example.mimochat.data.repository

import com.example.mimochat.data.*
import com.example.mimochat.data.local.MemoryDao
import com.example.mimochat.data.local.MessageDao

/**
 * 上下文构建器 - 按完整对话轮次裁剪
 *
 * 规则：
 * 1. system prompt 永远在最前
 * 2. 记忆注入（有独立长度限制）
 * 3. 历史按完整轮次裁剪（user+assistant 为一轮）
 * 4. 当前用户消息始终保留
 * 5. 排除 FAILED/STOPPED/PENDING/STREAMING 消息
 */
class ContextBuilder(
    private val messageDao: MessageDao,
    private val memoryDao: MemoryDao
) {
    companion object {
        private const val MAX_CONTEXT_CHARS = 30_000
        private const val MAX_MEMORY_CHARS = 2_000
        private const val MAX_HISTORY_ROUNDS = 30
    }

    /**
     * 构建对话轮次
     */
    data class ConversationTurn(
        val user: MessageEntity,
        val assistant: MessageEntity?
    )

    suspend fun build(
        conversationId: String,
        systemPrompt: String,
        currentUserMessageId: String? = null
    ): List<Map<String, Any>> {
        val context = mutableListOf<Map<String, Any>>()

        // 1. System prompt + 记忆
        val memoryText = buildMemoryText()
        val fullSystemPrompt = if (memoryText.isNotBlank()) {
            "$systemPrompt\n\n$memoryText"
        } else {
            systemPrompt
        }
        if (fullSystemPrompt.isNotBlank()) {
            context.add(mapOf("role" to "system", "content" to fullSystemPrompt))
        }

        // 2. 获取有效历史消息
        val allMessages = messageDao.getByConversation(conversationId)
        val validMessages = allMessages.filter { msg ->
            msg.status == MessageStatus.SUCCESS &&
                msg.id != currentUserMessageId
        }

        // 3. 组织为完整轮次
        val turns = buildTurns(validMessages)

        // 4. 从最近轮次向前裁剪
        var usedChars = fullSystemPrompt.length
        val selectedTurns = mutableListOf<ConversationTurn>()

        for (turn in turns.reversed()) {
            val turnChars = turn.user.content.length + (turn.assistant?.content?.length ?: 0)
            if (usedChars + turnChars > MAX_CONTEXT_CHARS && selectedTurns.isNotEmpty()) {
                break
            }
            selectedTurns.add(turn)
            usedChars += turnChars
        }
        selectedTurns.reverse()

        // 5. 展开为消息列表
        for (turn in selectedTurns) {
            context.add(mapOf("role" to "user", "content" to turn.user.content))
            if (turn.assistant != null) {
                context.add(mapOf("role" to "assistant", "content" to turn.assistant.content))
            }
        }

        // 6. 追加当前用户消息（如果有）
        if (currentUserMessageId != null) {
            val currentMsg = allMessages.find { it.id == currentUserMessageId }
            if (currentMsg != null && currentMsg.role == "user") {
                context.add(mapOf("role" to "user", "content" to currentMsg.content))
            }
        }

        return context
    }

    /**
     * 将消息列表组织为完整轮次（user + assistant 配对）
     */
    private fun buildTurns(messages: List<MessageEntity>): List<ConversationTurn> {
        val turns = mutableListOf<ConversationTurn>()
        var pendingUser: MessageEntity? = null

        for (msg in messages) {
            when (msg.role) {
                "user" -> {
                    // 如果有未配对的 user，先保存为无 assistant 的轮次
                    if (pendingUser != null) {
                        turns.add(ConversationTurn(pendingUser, null))
                    }
                    pendingUser = msg
                }
                "assistant" -> {
                    if (pendingUser != null) {
                        turns.add(ConversationTurn(pendingUser, msg))
                        pendingUser = null
                    }
                    // 如果没有对应的 user，忽略孤立的 assistant
                }
            }
        }
        // 最后的未配对 user
        if (pendingUser != null) {
            turns.add(ConversationTurn(pendingUser, null))
        }

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
