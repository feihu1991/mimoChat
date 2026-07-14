package com.example.mimochat.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.UUID

// ── Message Status ──

enum class MessageStatus {
    PENDING,
    STREAMING,
    SUCCESS,
    FAILED,
    STOPPED
}

// ── Room Entities ──

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String = "新对话",
    val roleId: String = "mimo",
    val model: String = "mimo-v2.5",
    val systemPrompt: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(
        entity = ConversationEntity::class,
        parentColumns = ["id"],
        childColumns = ["conversationId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("conversationId")]
)
data class MessageEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val conversationId: String,
    val role: String, // "user" | "assistant" | "system"
    val content: String = "",
    val status: MessageStatus = MessageStatus.SUCCESS,
    val errorMessage: String? = null,
    val model: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val content: String,
    val enabled: Boolean = true,
    val category: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

// ── UI Models (non-persistent) ──

enum class ModelId(val displayName: String, val apiName: String) {
    MIMO_V2_5("MiMo 2.5", "mimo-v2.5"),
    MIMO_V2_5_PRO("MiMo 2.5 Pro", "mimo-v2.5-pro");

    companion object {
        fun fromApiName(name: String): ModelId =
            entries.firstOrNull { it.apiName == name } ?: MIMO_V2_5
    }
}

enum class Screen {
    CHAT, SETTINGS, CONNECTION, ROLES, MEMORY
}

enum class VoiceModel(val apiName: String) {
    MIMO_V2_5_TTS("mimo-v2.5-tts"),
    MIMO_V2_5_TTS_VOICECLONE("mimo-v2.5-tts-voiceclone")
}

data class Role(
    val id: String,
    val name: String,
    val description: String,
    val prompt: String,
    val capabilities: String,
    val voiceModel: VoiceModel,
    val voiceName: String,
    val voicePrompt: String? = null,
    val voiceSample: String? = null,
    val color: String
)

data class Attachment(
    val id: String,
    val name: String,
    val type: AttachmentType,
    val url: String? = null
)

enum class AttachmentType { IMAGE, FILE }

data class Message(
    val id: String,
    val role: MessageRole,
    val text: String,
    val status: MessageStatus = MessageStatus.SUCCESS,
    val errorMessage: String? = null,
    val model: ModelId? = null,
    val attachments: List<Attachment> = emptyList()
)

enum class MessageRole { USER, ASSISTANT, SYSTEM }

data class Conversation(
    val id: String,
    val title: String,
    val roleId: String,
    val updated: String,
    val model: ModelId = ModelId.MIMO_V2_5,
    val messages: List<Message> = emptyList()
)

// ── Connection ──

@Serializable
data class MimoConnection(
    val baseUrl: String = "https://api.xiaomimimo.com/v1",
    val apiKey: String = "",
    val authMode: AuthMode = AuthMode.API_KEY
)

@Serializable
enum class AuthMode { API_KEY, BEARER }

enum class ProbeStatus { TESTING, PASSED, REACHABLE, FAILED }

data class ProbeResult(
    val model: String,
    val capability: String,
    val status: ProbeStatus,
    val latency: Long? = null,
    val detail: String
)

enum class ThemeMode { LIGHT, DARK, SYSTEM }

enum class ConnectionPhase { IDLE, LOADING, TESTING, DONE }

// ── Voice Chat State Machine ──

enum class VoiceChatState {
    IDLE, LISTENING, TRANSCRIBING, THINKING, SPEAKING, ERROR
}

// ── Defaults ──

val DEFAULT_ROLES = listOf(
    Role(
        id = "mimo", name = "MiMo", description = "日常陪伴与多模态助手",
        prompt = "你是 MiMo，一个温暖、直接、可靠的私人助手。回答要自然简洁，必要时主动梳理重点。",
        capabilities = "日常对话、看图、整理信息",
        voiceModel = VoiceModel.MIMO_V2_5_TTS, voiceName = "Chloe",
        voicePrompt = "温暖、清晰、自然的中文声音，语速适中，像一位可靠的朋友在面对面聊天。",
        color = "#f06c3b"
    ),
    Role(
        id = "study", name = "知夏", description = "耐心的学习搭子",
        prompt = "你是一位耐心的学习搭子知夏。用启发式提问帮助用户理解，不要直接堆砌答案。",
        capabilities = "学习辅导、知识讲解、复盘",
        voiceModel = VoiceModel.MIMO_V2_5_TTS, voiceName = "Chloe",
        voicePrompt = "温柔、耐心、清楚的女声，语气有鼓励感，重点处稍微放慢。",
        color = "#5f7f73"
    ),
    Role(
        id = "editor", name = "木棉", description = "文字与灵感编辑",
        prompt = "你是文字编辑木棉。擅长提炼表达、改写文案和激发创意，保持克制、有品位。",
        capabilities = "写作、改写、创意构思",
        voiceModel = VoiceModel.MIMO_V2_5_TTS, voiceName = "Chloe",
        voicePrompt = "干净、克制、略带磁性的中性声音，句尾收得利落，像一位编辑在读稿。",
        color = "#8d6c58"
    )
)
