package com.example.mimochat.data

import kotlinx.serialization.Serializable

@Serializable
enum class ModelId {
    MIMO_V2_5,
    MIMO_V2_5_PRO
}

@Serializable
enum class Screen {
    CHAT,
    SETTINGS,
    CONNECTION,
    ROLES,
    MEMORY
}

@Serializable
enum class VoiceModel {
    MIMO_V2_5_TTS,
    MIMO_V2_5_TTS_VOICECLONE
}

@Serializable
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

@Serializable
data class Attachment(
    val id: String,
    val name: String,
    val type: AttachmentType,
    val url: String? = null
)

@Serializable
enum class AttachmentType {
    IMAGE,
    FILE
}

@Serializable
data class Message(
    val id: String,
    val role: MessageRole,
    val text: String,
    val model: ModelId? = null,
    val attachments: List<Attachment> = emptyList()
)

@Serializable
enum class MessageRole {
    USER,
    ASSISTANT
}

@Serializable
data class Conversation(
    val id: String,
    val title: String,
    val roleId: String,
    val updated: String,
    val messages: List<Message> = emptyList()
)

@Serializable
data class MimoConnection(
    val baseUrl: String = "https://api.xiaomimimo.com/v1",
    val apiKey: String = "",
    val authMode: AuthMode = AuthMode.API_KEY
)

@Serializable
enum class AuthMode {
    API_KEY,
    BEARER
}

@Serializable
enum class ProbeStatus {
    TESTING,
    PASSED,
    REACHABLE,
    FAILED
}

@Serializable
data class ProbeResult(
    val model: String,
    val capability: String,
    val status: ProbeStatus,
    val latency: Long? = null,
    val detail: String
)

val DEFAULT_ROLES = listOf(
    Role(
        id = "mimo",
        name = "MiMo",
        description = "日常陪伴与多模态助手",
        prompt = "你是 MiMo，一个温暖、直接、可靠的私人助手。回答要自然简洁，必要时主动梳理重点。",
        capabilities = "日常对话、看图、整理信息",
        voiceModel = VoiceModel.MIMO_V2_5_TTS,
        voiceName = "Chloe",
        voicePrompt = "温暖、清晰、自然的中文声音，语速适中，像一位可靠的朋友在面对面聊天。",
        color = "#f06c3b"
    ),
    Role(
        id = "study",
        name = "知夏",
        description = "耐心的学习搭子",
        prompt = "你是一位耐心的学习搭子知夏。用启发式提问帮助用户理解，不要直接堆砌答案。",
        capabilities = "学习辅导、知识讲解、复盘",
        voiceModel = VoiceModel.MIMO_V2_5_TTS,
        voiceName = "Chloe",
        voicePrompt = "温柔、耐心、清楚的女声，语气有鼓励感，重点处稍微放慢。",
        color = "#5f7f73"
    ),
    Role(
        id = "editor",
        name = "木棉",
        description = "文字与灵感编辑",
        prompt = "你是文字编辑木棉。擅长提炼表达、改写文案和激发创意，保持克制、有品位。",
        capabilities = "写作、改写、创意构思",
        voiceModel = VoiceModel.MIMO_V2_5_TTS,
        voiceName = "Chloe",
        voicePrompt = "干净、克制、略带磁性的中性声音，句尾收得利落，像一位编辑在读稿。",
        color = "#8d6c58"
    )
)

val STARTER_CONVERSATIONS = listOf(
    Conversation(
        id = "current",
        title = "新对话",
        roleId = "mimo",
        updated = "刚刚"
    ),
    Conversation(
        id = "weekend",
        title = "周末旅行计划",
        roleId = "mimo",
        updated = "昨天",
        messages = listOf(
            Message(
                id = "w1",
                role = MessageRole.ASSISTANT,
                text = "杭州两日路线已经整理好了。",
                model = ModelId.MIMO_V2_5
            )
        )
    ),
    Conversation(
        id = "meeting",
        title = "会议录音整理",
        roleId = "study",
        updated = "周五",
        messages = listOf(
            Message(
                id = "m1",
                role = MessageRole.ASSISTANT,
                text = "录音重点已经整理为四项待办。",
                model = ModelId.MIMO_V2_5
            )
        )
    )
)