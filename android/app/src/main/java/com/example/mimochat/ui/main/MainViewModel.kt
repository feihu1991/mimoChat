package com.example.mimochat.ui.main

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mimochat.data.*
import com.example.mimochat.data.local.AppDatabase
import com.example.mimochat.data.local.SettingsStorage
import com.example.mimochat.data.remote.MimoClient
import com.example.mimochat.data.remote.StreamChunk
import com.example.mimochat.data.repository.ChatRepository
import com.example.mimochat.data.repository.ConversationRepository
import com.example.mimochat.data.repository.toEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val settingsStorage = SettingsStorage(application)
    private val conversationRepo = ConversationRepository(db.conversationDao(), db.messageDao())
    private val chatRepo = ChatRepository(db.messageDao(), db.memoryDao(), settingsStorage)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ── UI State ──
    private val _screen = MutableStateFlow(Screen.CHAT)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _drawerOpen = MutableStateFlow(false)
    val drawerOpen: StateFlow<Boolean> = _drawerOpen.asStateFlow()

    private val _modelOpen = MutableStateFlow(false)
    val modelOpen: StateFlow<Boolean> = _modelOpen.asStateFlow()

    private val _theme = MutableStateFlow(loadTheme())
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    private val _roles = MutableStateFlow(loadRoles())
    val roles: StateFlow<List<Role>> = _roles.asStateFlow()

    private val _defaultRoleId = MutableStateFlow(settingsStorage.defaultRoleId)
    val defaultRoleId: StateFlow<String> = _defaultRoleId.asStateFlow()

    private val _conversationId = MutableStateFlow<String?>(null)
    val conversationId: StateFlow<String?> = _conversationId.asStateFlow()

    private val _model = MutableStateFlow(ModelId.MIMO_V2_5)
    val model: StateFlow<ModelId> = _model.asStateFlow()

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _toast = MutableStateFlow("")
    val toast: StateFlow<String> = _toast.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    // ── Streaming Job ──
    private var streamingJob: Job? = null

    // ── Data Flows ──
    val conversations: StateFlow<List<ConversationEntity>> =
        conversationRepo.getAllConversationsFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentMessages: StateFlow<List<Message>> =
        _conversationId.filterNotNull().flatMapLatest { id ->
            conversationRepo.getMessagesFlow(id)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeRole: Role
        get() {
            val conv = conversations.value.find { it.id == _conversationId.value }
            return _roles.value.find { it.id == conv?.roleId } ?: _roles.value.firstOrNull() ?: DEFAULT_ROLES[0]
        }

    val currentConversation: ConversationEntity?
        get() = conversations.value.find { it.id == _conversationId.value }

    val resolvedTheme: ThemeMode
        get() {
            val t = _theme.value
            return if (t == ThemeMode.DARK || (t == ThemeMode.SYSTEM && isSystemDarkTheme())) ThemeMode.DARK
            else ThemeMode.LIGHT
        }

    val hasApiKey: Boolean get() = settingsStorage.hasApiKey()

    init {
        // 初始化：加载或创建默认会话
        viewModelScope.launch {
            val convs = conversationRepo.getAllConversationsFlow().first()
            if (convs.isEmpty()) {
                val id = conversationRepo.createConversation(roleId = _defaultRoleId.value)
                _conversationId.value = id
            } else {
                _conversationId.value = convs.first().id
            }
        }
    }

    // ── Theme ──

    private fun loadTheme(): ThemeMode {
        return when (settingsStorage.theme) {
            "light" -> ThemeMode.LIGHT
            "dark" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    private fun isSystemDarkTheme(): Boolean {
        val uiMode = getApplication<Application>().resources.configuration.uiMode
        return (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    fun setTheme(theme: ThemeMode) {
        _theme.value = theme
        settingsStorage.theme = when (theme) {
            ThemeMode.LIGHT -> "light"
            ThemeMode.DARK -> "dark"
            ThemeMode.SYSTEM -> "system"
        }
    }

    // ── Roles ──

    private fun loadRoles(): List<Role> {
        val rolesJson = settingsStorage.rolesJson
        if (rolesJson.isBlank()) return DEFAULT_ROLES
        return try {
            json.decodeFromString<List<Role>>(rolesJson)
        } catch (_: Exception) { DEFAULT_ROLES }
    }

    fun setRoles(roles: List<Role>) {
        _roles.value = roles
        settingsStorage.rolesJson = json.encodeToString(roles)
    }

    fun setDefaultRoleId(id: String) {
        _defaultRoleId.value = id
        settingsStorage.defaultRoleId = id
    }

    // ── Navigation ──

    fun setScreen(screen: Screen) { _screen.value = screen }
    fun setDrawerOpen(open: Boolean) { _drawerOpen.value = open }
    fun setModelOpen(open: Boolean) { _modelOpen.value = open }
    fun setInput(input: String) { _input.value = input }
    fun setModel(model: ModelId) { _model.value = model }

    // ── Conversation Management ──

    fun selectConversation(id: String) {
        _conversationId.value = id
        val conv = conversations.value.find { it.id == id }
        if (conv != null) {
            _model.value = ModelId.fromApiName(conv.model)
        }
    }

    fun startNewConversation() {
        viewModelScope.launch {
            val id = conversationRepo.createConversation(
                roleId = _defaultRoleId.value,
                model = _model.value
            )
            _conversationId.value = id
            _model.value = ModelId.MIMO_V2_5
            _drawerOpen.value = false
            _screen.value = Screen.CHAT
        }
    }

    fun renameConversation(id: String, newTitle: String) {
        viewModelScope.launch {
            conversationRepo.updateTitle(id, newTitle)
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            val wasCurrent = _conversationId.value == id
            conversationRepo.deleteConversation(id)
            if (wasCurrent) {
                val remaining = conversations.value.firstOrNull { it.id != id }
                if (remaining != null) {
                    _conversationId.value = remaining.id
                } else {
                    startNewConversation()
                }
            }
        }
    }

    fun deleteAllConversations() {
        viewModelScope.launch {
            conversationRepo.deleteAllConversations()
            startNewConversation()
        }
    }

    suspend fun searchConversations(query: String): List<ConversationEntity> {
        return conversationRepo.searchConversations(query)
    }

    // ── Chat: Send Message ──

    fun sendMessage(text: String = _input.value.trim()) {
        if (text.isBlank() || _isStreaming.value) return
        val convId = _conversationId.value ?: return

        _input.value = ""

        viewModelScope.launch {
            // 插入用户消息
            val userMsg = MessageEntity(
                conversationId = convId,
                role = "user",
                content = text,
                status = MessageStatus.SUCCESS
            )
            conversationRepo.insertMessage(userMsg)

            // 自动生成标题
            val conv = conversationRepo.getConversation(convId)
            if (conv != null && (conv.title == "新对话" || conv.title.isBlank())) {
                conversationRepo.updateTitle(convId, text.take(14))
            }

            // 插入助手占位消息
            val assistantMsg = MessageEntity(
                conversationId = convId,
                role = "assistant",
                content = "",
                status = MessageStatus.PENDING,
                model = _model.value.apiName
            )
            conversationRepo.insertMessage(assistantMsg)

            // 发送流式请求
            _isStreaming.value = true
            streamingJob = viewModelScope.launch {
                chatRepo.sendStreamingMessage(
                    conversationId = convId,
                    userMessageId = userMsg.id,
                    assistantMessageId = assistantMsg.id,
                    userText = text,
                    systemPrompt = activeRole.prompt,
                    model = _model.value
                ).collect { chunk ->
                    when (chunk) {
                        is StreamChunk.Error -> {
                            showToast(chunk.message)
                        }
                        else -> { /* UI 通过 Flow 自动更新 */ }
                    }
                }
                _isStreaming.value = false
            }
        }
    }

    // ── Stop Generation ──

    fun stopGeneration() {
        streamingJob?.cancel()
        streamingJob = null
        _isStreaming.value = false

        // 标记当前流式消息为 STOPPED
        viewModelScope.launch {
            val convId = _conversationId.value ?: return@launch
            val messages = conversationRepo.getMessages(convId)
            val streamingMsg = messages.lastOrNull {
                it.role == "assistant" && it.status == MessageStatus.STREAMING
            }
            if (streamingMsg != null) {
                conversationRepo.updateMessageStatus(streamingMsg.id, MessageStatus.STOPPED)
            }
        }
    }

    // ── Retry ──

    fun retryMessage(messageId: String) {
        val convId = _conversationId.value ?: return
        val conv = currentConversation ?: return

        _isStreaming.value = true
        streamingJob = viewModelScope.launch {
            conversationRepo.updateMessageContent(messageId, "", MessageStatus.STREAMING)

            chatRepo.retryMessage(
                conversationId = convId,
                assistantMessageId = messageId,
                systemPrompt = activeRole.prompt,
                model = _model.value
            ).collect { chunk ->
                if (chunk is StreamChunk.Error) {
                    showToast(chunk.message)
                }
            }
            _isStreaming.value = false
        }
    }

    // ── Regenerate ──

    fun regenerateMessage(messageId: String) {
        val convId = _conversationId.value ?: return

        _isStreaming.value = true
        val newAssistantMsg = MessageEntity(
            conversationId = convId,
            role = "assistant",
            content = "",
            status = MessageStatus.PENDING,
            model = _model.value.apiName
        )

        streamingJob = viewModelScope.launch {
            conversationRepo.insertMessage(newAssistantMsg)

            chatRepo.regenerateMessage(
                conversationId = convId,
                oldAssistantMessageId = messageId,
                newAssistantMessageId = newAssistantMsg.id,
                systemPrompt = activeRole.prompt,
                model = _model.value
            ).collect { chunk ->
                if (chunk is StreamChunk.Error) {
                    showToast(chunk.message)
                }
            }
            _isStreaming.value = false
        }
    }

    // ── Edit & Resend ──

    fun editAndResend(messageId: String, newText: String) {
        val convId = _conversationId.value ?: return

        viewModelScope.launch {
            // 删除该消息之后的所有消息
            val messages = conversationRepo.getMessages(convId)
            val targetIndex = messages.indexOfFirst { it.id == messageId }
            if (targetIndex < 0) return@launch

            // 删除后续消息
            for (i in targetIndex + 1 until messages.size) {
                conversationRepo.deleteMessage(messages[i].id)
            }

            // 更新用户消息
            conversationRepo.updateMessageContent(messageId, newText, MessageStatus.SUCCESS)

            // 重新发送
            _input.value = ""
            sendMessage(newText)
        }
    }

    // ── Copy Message ──

    fun copyMessage(text: String) {
        val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE)
            as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("mimo_chat", text))
        showToast("已复制到剪贴板")
    }

    // ── Connection ──

    private val _connectionPhase = MutableStateFlow(ConnectionPhase.IDLE)
    val connectionPhase: StateFlow<ConnectionPhase> = _connectionPhase.asStateFlow()

    private val _connectionError = MutableStateFlow("")
    val connectionError: StateFlow<String> = _connectionError.asStateFlow()

    private val _probeResults = MutableStateFlow<List<ProbeResult>>(emptyList())
    val probeResults: StateFlow<List<ProbeResult>> = _probeResults.asStateFlow()

    fun loadConnection(): MimoConnection = settingsStorage.loadConnection()

    fun saveConnection(connection: MimoConnection) {
        settingsStorage.saveConnection(connection)
    }

    fun clearApiKey() {
        settingsStorage.clearApiKey()
    }

    fun connect() {
        val config = settingsStorage.loadConnection()
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

                _connectionPhase.value = ConnectionPhase.TESTING
                _probeResults.value = models.map { model ->
                    ProbeResult(model = model, capability = "识别中", status = ProbeStatus.TESTING, detail = "正在验证能力")
                }

                val results = mutableListOf<ProbeResult>()
                for (model in models) {
                    val result = MimoClient.probeModel(config, model)
                    results.add(result)
                    _probeResults.value = results.toList()
                }

                _connectionPhase.value = ConnectionPhase.DONE
            } catch (e: Exception) {
                _connectionError.value = MimoClient.translateError(e)
                _connectionPhase.value = ConnectionPhase.IDLE
            }
        }
    }

    // ── Toast ──

    fun showToast(message: String) {
        _toast.value = message.take(80)
        viewModelScope.launch {
            delay(2500)
            if (_toast.value == message.take(80)) _toast.value = ""
        }
    }

    fun clearToast() { _toast.value = "" }

    // ── Memory ──

    fun getMemories() = db.memoryDao().getAllFlow()

    fun addMemory(content: String) {
        viewModelScope.launch {
            db.memoryDao().upsert(MemoryEntity(content = content))
        }
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch {
            db.memoryDao().deleteById(id)
        }
    }

    fun toggleMemory(id: String, enabled: Boolean) {
        viewModelScope.launch {
            db.memoryDao().setEnabled(id, enabled)
        }
    }
}
