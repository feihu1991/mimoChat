package com.example.mimochat.ui.main

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mimochat.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val sharedPreferences = application.getSharedPreferences("mimo_chat", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _screen = MutableStateFlow(Screen.CHAT)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _drawerOpen = MutableStateFlow(false)
    val drawerOpen: StateFlow<Boolean> = _drawerOpen.asStateFlow()

    private val _attachmentOpen = MutableStateFlow(false)
    val attachmentOpen: StateFlow<Boolean> = _attachmentOpen.asStateFlow()

    private val _modelOpen = MutableStateFlow(false)
    val modelOpen: StateFlow<Boolean> = _modelOpen.asStateFlow()

    private val _theme = MutableStateFlow(loadTheme())
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    private val _roles = MutableStateFlow(loadRoles())
    val roles: StateFlow<List<Role>> = _roles.asStateFlow()

    private val _defaultRoleId = MutableStateFlow(sharedPreferences.getString("mimo-default-role", "mimo") ?: "mimo")
    val defaultRoleId: StateFlow<String> = _defaultRoleId.asStateFlow()

    private val _conversations = MutableStateFlow(loadConversations())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _conversationId = MutableStateFlow("current")
    val conversationId: StateFlow<String> = _conversationId.asStateFlow()

    private val _model = MutableStateFlow(ModelId.MIMO_V2_5)
    val model: StateFlow<ModelId> = _model.asStateFlow()

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _attachments = MutableStateFlow<List<Attachment>>(emptyList())
    val attachments: StateFlow<List<Attachment>> = _attachments.asStateFlow()

    private val _thinking = MutableStateFlow(false)
    val thinking: StateFlow<Boolean> = _thinking.asStateFlow()

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.asStateFlow()

    private val _voiceStatus = MutableStateFlow("")
    val voiceStatus: StateFlow<String> = _voiceStatus.asStateFlow()

    private val _toast = MutableStateFlow("")
    val toast: StateFlow<String> = _toast.asStateFlow()

    private val _playingMessageId = MutableStateFlow<String?>(null)
    val playingMessageId: StateFlow<String?> = _playingMessageId.asStateFlow()

    private val _connection = MutableStateFlow(loadConnection())
    val connection: StateFlow<MimoConnection> = _connection.asStateFlow()

    private val _probeResults = MutableStateFlow<List<ProbeResult>>(emptyList())
    val probeResults: StateFlow<List<ProbeResult>> = _probeResults.asStateFlow()

    private val _connectionPhase = MutableStateFlow(ConnectionPhase.IDLE)
    val connectionPhase: StateFlow<ConnectionPhase> = _connectionPhase.asStateFlow()

    private val _connectionError = MutableStateFlow("")
    val connectionError: StateFlow<String> = _connectionError.asStateFlow()

    val currentConversation: Conversation
        get() = _conversations.value.find { it.id == _conversationId.value } ?: _conversations.value.first()

    val activeRole: Role
        get() = _roles.value.find { it.id == currentConversation.roleId } ?: _roles.value.first()

    val resolvedTheme: ThemeMode
        get() {
            val theme = _theme.value
            return if (theme == ThemeMode.DARK || (theme == ThemeMode.SYSTEM && isSystemDarkTheme())) {
                ThemeMode.DARK
            } else {
                ThemeMode.LIGHT
            }
        }

    private fun isSystemDarkTheme(): Boolean {
        val uiMode = getApplication<Application>().resources.configuration.uiMode
        return (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun loadTheme(): ThemeMode {
        val themeStr = sharedPreferences.getString("mimo-theme", "system") ?: "system"
        return when (themeStr) {
            "light" -> ThemeMode.LIGHT
            "dark" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    private fun loadRoles(): List<Role> {
        val rolesJson = sharedPreferences.getString("mimo-roles", null)
        return if (rolesJson != null) {
            try {
                json.decodeFromString(rolesJson)
            } catch (e: Exception) {
                DEFAULT_ROLES
            }
        } else {
            DEFAULT_ROLES
        }
    }

    private fun loadConversations(): List<Conversation> {
        val conversationsJson = sharedPreferences.getString("mimo-conversations", null)
        return if (conversationsJson != null) {
            try {
                json.decodeFromString(conversationsJson)
            } catch (e: Exception) {
                STARTER_CONVERSATIONS
            }
        } else {
            STARTER_CONVERSATIONS
        }
    }

    private fun loadConnection(): MimoConnection {
        val connectionJson = sharedPreferences.getString("mimo-connection", null)
        return if (connectionJson != null) {
            try {
                json.decodeFromString(connectionJson)
            } catch (e: Exception) {
                MimoConnection()
            }
        } else {
            MimoConnection()
        }
    }

    fun setScreen(screen: Screen) {
        _screen.value = screen
    }

    fun setDrawerOpen(open: Boolean) {
        _drawerOpen.value = open
    }

    fun setAttachmentOpen(open: Boolean) {
        _attachmentOpen.value = open
    }

    fun setModelOpen(open: Boolean) {
        _modelOpen.value = open
    }

    fun setTheme(theme: ThemeMode) {
        _theme.value = theme
        sharedPreferences.edit().putString("mimo-theme", when (theme) {
            ThemeMode.LIGHT -> "light"
            ThemeMode.DARK -> "dark"
            ThemeMode.SYSTEM -> "system"
        }).apply()
    }

    fun setRoles(roles: List<Role>) {
        _roles.value = roles
        sharedPreferences.edit().putString("mimo-roles", json.encodeToString(roles)).apply()
    }

    fun setDefaultRoleId(id: String) {
        _defaultRoleId.value = id
        sharedPreferences.edit().putString("mimo-default-role", id).apply()
    }

    fun setConversationId(id: String) {
        _conversationId.value = id
    }

    fun setModel(model: ModelId) {
        _model.value = model
    }

    fun setInput(input: String) {
        _input.value = input
    }

    fun setAttachments(attachments: List<Attachment>) {
        _attachments.value = attachments
    }

    fun addAttachment(attachment: Attachment) {
        _attachments.value = _attachments.value + attachment
    }

    fun removeAttachment(id: String) {
        _attachments.value = _attachments.value.filter { it.id != id }
    }

    fun startNewConversation() {
        val id = "chat-${System.currentTimeMillis()}"
        val newConversation = Conversation(
            id = id,
            title = "新对话",
            roleId = _defaultRoleId.value,
            updated = "刚刚"
        )
        _conversations.value = listOf(newConversation) + _conversations.value
        _conversationId.value = id
        _model.value = ModelId.MIMO_V2_5
        _attachments.value = emptyList()
        _drawerOpen.value = false
        _screen.value = Screen.CHAT
        saveConversations()
    }

    fun sendPrompt(text: String, fromVoice: Boolean = false) {
        val clean = text.trim()
        if ((clean.isEmpty() && _attachments.value.isEmpty()) || _thinking.value) return

        val userMessage = Message(
            id = "user-${System.currentTimeMillis()}",
            role = MessageRole.USER,
            text = clean.ifEmpty { "请分析这些附件" },
            attachments = _attachments.value
        )

        val outgoingAttachments = _attachments.value
        updateConversation { conversation ->
            conversation.copy(
                title = if (conversation.messages.isEmpty()) clean.take(14).ifEmpty { "图片对话" } else conversation.title,
                updated = "刚刚",
                messages = conversation.messages + userMessage
            )
        }

        _input.value = ""
        _attachments.value = emptyList()
        _thinking.value = true

        if (fromVoice) {
            _voiceStatus.value = "${if (_model.value == ModelId.MIMO_V2_5_PRO) "Pro" else "v2.5"} 正在回答"
        }

        viewModelScope.launch {
            try {
                val config = _connection.value
                val routedModel = if (outgoingAttachments.any { it.type == AttachmentType.IMAGE }) {
                    ModelId.MIMO_V2_5
                } else {
                    _model.value
                }

                val imageUrls = outgoingAttachments.filter { it.type == AttachmentType.IMAGE && it.url != null }.map { it.url!! }

                val reply = if (config.apiKey.isNotEmpty()) {
                    MimoClient.chatCompletion(
                        config,
                        if (routedModel == ModelId.MIMO_V2_5_PRO) "mimo-v2.5-pro" else "mimo-v2.5",
                        "${activeRole.prompt}\n\n用户：${clean.ifEmpty { "请分析附件" }}",
                        imageUrls
                    )
                } else {
                    demoReply(clean, activeRole, routedModel, imageUrls.isNotEmpty())
                }

                val assistant = Message(
                    id = "assistant-${System.currentTimeMillis()}",
                    role = MessageRole.ASSISTANT,
                    text = reply,
                    model = routedModel
                )

                updateConversation { conversation ->
                    conversation.copy(messages = conversation.messages + assistant)
                }

                if (fromVoice) {
                    _voiceStatus.value = "正在生成角色声音"
                    // TODO: Implement voice playback
                }
            } catch (e: Exception) {
                showToast(e.message ?: "请求失败")
            } finally {
                _thinking.value = false
                _voiceStatus.value = ""
            }
        }
    }

    private fun updateConversation(updater: (Conversation) -> Conversation) {
        _conversations.value = _conversations.value.map { conversation ->
            if (conversation.id == _conversationId.value) {
                updater(conversation)
            } else {
                conversation
            }
        }
        saveConversations()
    }

    private fun saveConversations() {
        sharedPreferences.edit().putString("mimo-conversations", json.encodeToString(_conversations.value)).apply()
    }

    fun showToast(message: String) {
        _toast.value = message.take(80)
        // In a real app, you'd use a timer to clear the toast
    }

    fun setConnection(connection: MimoConnection) {
        _connection.value = connection
        sharedPreferences.edit().putString("mimo-connection", json.encodeToString(connection)).apply()
    }

    fun connect() {
        val config = _connection.value
        if (config.baseUrl.isBlank() || config.apiKey.isBlank()) {
            _connectionError.value = "请填写模型地址和 API Key"
            return
        }

        _connectionError.value = ""
        _probeResults.value = emptyList()
        _connectionPhase.value = ConnectionPhase.LOADING

        viewModelScope.launch {
            try {
                val models = MimoClient.loadModels(config)
                if (models.isEmpty()) throw Exception("没有加载到模型")

                sharedPreferences.edit().putInt("mimo-model-count", models.size).apply()
                sharedPreferences.edit().putString("mimo-model-list", json.encodeToString(models)).apply()

                _connectionPhase.value = ConnectionPhase.TESTING
                _probeResults.value = models.map { model ->
                    ProbeResult(
                        model = model,
                        capability = "识别中",
                        status = ProbeStatus.TESTING,
                        detail = "正在验证能力"
                    )
                }

                val results = mutableListOf<ProbeResult>()
                for (model in models) {
                    val result = MimoClient.probeModel(config, model)
                    results.add(result)
                    _probeResults.value = results.toList()
                }

                _connectionPhase.value = ConnectionPhase.DONE
            } catch (e: Exception) {
                _connectionError.value = e.message ?: "连接失败"
                _connectionPhase.value = ConnectionPhase.IDLE
            }
        }
    }

    fun playRoleVoice(text: String, messageId: String? = null) {
        // TODO: Implement voice playback
    }

    fun toggleRecording() {
        // TODO: Implement recording
    }

    private fun demoReply(text: String, role: Role, model: ModelId, hasImage: Boolean): String {
        return when {
            hasImage -> "我已经看到了这张图片。作为${role.name}，我会先从画面主体、细节和你的目标三个方面来分析。你可以再告诉我最想关注哪一部分。"
            model == ModelId.MIMO_V2_5_PRO -> "我会先把\"${text.ifEmpty { "这个问题" }}\"拆成目标、约束和行动三部分，再给你一个可以直接执行的方案。"
            else -> "明白了。关于\"${text.ifEmpty { "这件事" }}\"，我建议先抓住最重要的一步开始。如果你愿意，我可以继续帮你整理成清单。"
        }
    }
}

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

enum class ConnectionPhase {
    IDLE,
    LOADING,
    TESTING,
    DONE
}