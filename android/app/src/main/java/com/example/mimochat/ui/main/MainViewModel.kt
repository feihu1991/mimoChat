package com.example.mimochat.ui.main

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.mimochat.core.agent.ApprovalManager
import com.example.mimochat.core.workspace.GitHubWorkspace
import com.example.mimochat.core.workspace.GitHubWorkspaceConfig
import com.example.mimochat.core.workspace.WorkspaceSyncState
import com.example.mimochat.data.*
import com.example.mimochat.data.local.AppDatabase
import com.example.mimochat.data.local.SettingsStorage
import com.example.mimochat.data.remote.StreamChunk
import com.example.mimochat.data.repository.AgentRepository
import com.example.mimochat.data.repository.ContextBuilder
import com.example.mimochat.data.repository.ConversationRepository
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val settingsStorage = SettingsStorage(application)
    private val contextBuilder = ContextBuilder(db.messageDao(), db.memoryDao())
    private val conversationRepo = ConversationRepository(db.conversationDao(), db.messageDao())
    private val approvalManager = ApprovalManager()
    private val workspace = GitHubWorkspace(application, settingsStorage)
    private val chatRepo = AgentRepository(
        db.messageDao(),
        contextBuilder,
        settingsStorage,
        workspace,
        approvalManager
    )
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    companion object {
        private const val MAX_USER_MESSAGE_CHARS = 28_000
    }

    data class GenerationTask(
        val token: String,
        val conversationId: String,
        val assistantMessageId: String,
        val userMessageId: String,
        val job: Job
    )

    private var generationTask: GenerationTask? = null
    private val generationMutex = Mutex()

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

    private val _toast = MutableStateFlow("")
    val toast: StateFlow<String> = _toast.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    val pendingApproval = approvalManager.pending

    private val _workspaceConfig = MutableStateFlow(settingsStorage.loadWorkspaceConfig())
    val workspaceConfig: StateFlow<GitHubWorkspaceConfig> = _workspaceConfig.asStateFlow()

    private val _workspaceSyncState = MutableStateFlow<WorkspaceSyncState>(WorkspaceSyncState.Idle)
    val workspaceSyncState: StateFlow<WorkspaceSyncState> = _workspaceSyncState.asStateFlow()

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
            return roleFor(conv?.roleId)
        }

    val currentModel: ModelId
        get() {
            val conv = conversations.value.find { it.id == _conversationId.value }
            return conv?.model?.let { ModelId.fromApiName(it) } ?: ModelId.MIMO_V2_5
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

    private fun loadTheme(): ThemeMode = when (settingsStorage.theme) {
        "light" -> ThemeMode.LIGHT
        "dark" -> ThemeMode.DARK
        else -> ThemeMode.SYSTEM
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

    private fun loadRoles(): List<Role> {
        val rolesJson = settingsStorage.rolesJson
        if (rolesJson.isBlank()) return DEFAULT_ROLES
        return try {
            json.decodeFromString<List<Role>>(rolesJson)
        } catch (_: Exception) {
            DEFAULT_ROLES
        }
    }

    private fun roleFor(roleId: String?): Role =
        _roles.value.find { it.id == roleId }
            ?: _roles.value.firstOrNull()
            ?: DEFAULT_ROLES[0]

    fun setRoles(roles: List<Role>) {
        _roles.value = roles
        settingsStorage.rolesJson = json.encodeToString(roles)
    }

    fun setDefaultRoleId(id: String) {
        _defaultRoleId.value = id
        settingsStorage.defaultRoleId = id
    }

    fun setScreen(screen: Screen) { _screen.value = screen }
    fun setDrawerOpen(open: Boolean) { _drawerOpen.value = open }
    fun setModelOpen(open: Boolean) { _modelOpen.value = open }

    fun selectConversation(id: String) {
        _conversationId.value = id
    }

    fun startNewConversation() {
        viewModelScope.launch {
            val id = conversationRepo.createConversation(roleId = _defaultRoleId.value)
            _conversationId.value = id
            _drawerOpen.value = false
            _screen.value = Screen.CHAT
        }
    }

    fun renameConversation(id: String, newTitle: String) {
        viewModelScope.launch { conversationRepo.updateTitle(id, newTitle) }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            val wasCurrent = _conversationId.value == id
            cancelGeneration(id)
            conversationRepo.deleteConversation(id)
            if (wasCurrent) {
                val remaining = conversations.value.firstOrNull { it.id != id }
                if (remaining != null) _conversationId.value = remaining.id
                else startNewConversation()
            }
        }
    }

    fun deleteAllConversations() {
        viewModelScope.launch {
            cancelGeneration()
            conversationRepo.deleteAllConversations()
            startNewConversation()
        }
    }

    fun setModel(model: ModelId) {
        val convId = _conversationId.value ?: return
        viewModelScope.launch {
            val conv = conversationRepo.getConversation(convId)
            if (conv != null) {
                db.conversationDao().update(conv.copy(model = model.apiName))
            }
        }
    }

    fun sendMessage(text: String) {
        val clean = validateMessage(text) ?: return
        val convId = _conversationId.value ?: return

        viewModelScope.launch {
            generationMutex.withLock {
                if (hasActiveGenerationLocked()) return@withLock

                val conv = conversationRepo.getConversation(convId) ?: return@withLock
                val role = roleFor(conv.roleId)
                val model = ModelId.fromApiName(conv.model)
                val userMsg = MessageEntity(
                    conversationId = convId,
                    role = "user",
                    content = clean,
                    status = MessageStatus.SUCCESS
                )
                val assistantMsg = MessageEntity(
                    conversationId = convId,
                    role = "assistant",
                    content = "",
                    status = MessageStatus.PENDING,
                    model = model.apiName
                )

                db.withTransaction {
                    conversationRepo.insertMessage(userMsg)
                    if (conv.title == "新对话" || conv.title.isBlank()) {
                        conversationRepo.updateTitle(convId, clean.take(14))
                    }
                    conversationRepo.insertMessage(assistantMsg)
                    touchConversation(convId)
                }

                startGenerationLocked(
                    conversationId = convId,
                    userMessageId = userMsg.id,
                    assistantMessageId = assistantMsg.id,
                    systemPrompt = role.prompt,
                    model = model
                )
            }
        }
    }

    private fun validateMessage(text: String): String? {
        val clean = text.trim()
        if (clean.isBlank()) return null
        if (clean.length > MAX_USER_MESSAGE_CHARS) {
            showToast("消息过长，请控制在 $MAX_USER_MESSAGE_CHARS 个字符以内")
            return null
        }
        return clean
    }

    private fun hasActiveGenerationLocked(): Boolean {
        val active = generationTask?.job?.isActive == true
        if (active) showToast("请等待当前回答完成，或先停止生成")
        return active
    }

    private fun startGenerationLocked(
        conversationId: String,
        userMessageId: String,
        assistantMessageId: String,
        systemPrompt: String,
        model: ModelId
    ) {
        check(generationTask?.job?.isActive != true) { "generation already active" }

        val token = UUID.randomUUID().toString()
        _isStreaming.value = true

        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                chatRepo.executeGeneration(
                    conversationId = conversationId,
                    assistantMessageId = assistantMessageId,
                    userMessageId = userMessageId,
                    systemPrompt = systemPrompt,
                    model = model
                ).collect { chunk ->
                    if (chunk is StreamChunk.Error) showToast(chunk.message)
                }
            } finally {
                generationMutex.withLock {
                    if (generationTask?.token == token) {
                        generationTask = null
                        _isStreaming.value = false
                    }
                }
                touchConversation(conversationId)
            }
        }

        generationTask = GenerationTask(
            token = token,
            conversationId = conversationId,
            assistantMessageId = assistantMessageId,
            userMessageId = userMessageId,
            job = job
        )
        job.start()
    }

    fun stopGeneration() {
        approvalManager.cancelPending()
        viewModelScope.launch {
            val task = generationMutex.withLock {
                val current = generationTask ?: return@withLock null
                generationTask = null
                _isStreaming.value = false
                current
            } ?: return@launch

            task.job.cancelAndJoin()
            val msg = conversationRepo.getMessage(task.assistantMessageId)
            if (msg?.status == MessageStatus.STREAMING || msg?.status == MessageStatus.PENDING) {
                conversationRepo.updateMessageContent(
                    task.assistantMessageId,
                    msg.content,
                    MessageStatus.STOPPED
                )
            }
        }
    }

    private suspend fun cancelGeneration(conversationId: String? = null) {
        approvalManager.cancelPending()
        val task = generationMutex.withLock {
            val current = generationTask ?: return@withLock null
            if (conversationId != null && current.conversationId != conversationId) return@withLock null
            generationTask = null
            _isStreaming.value = false
            current
        } ?: return
        task.job.cancelAndJoin()
    }

    fun retryMessage(messageId: String) {
        val convId = _conversationId.value ?: return

        viewModelScope.launch {
            generationMutex.withLock {
                if (hasActiveGenerationLocked()) return@withLock

                val msg = conversationRepo.getMessage(messageId) ?: return@withLock
                if (msg.conversationId != convId || msg.role != "assistant") return@withLock

                val conv = conversationRepo.getConversation(convId) ?: return@withLock
                val role = roleFor(conv.roleId)
                val model = ModelId.fromApiName(conv.model)
                val messages = conversationRepo.getMessages(convId)
                val targetIndex = messages.indexOfFirst { it.id == messageId }
                val userMsg = messages.getOrNull(targetIndex - 1)?.takeIf { it.role == "user" }
                if (targetIndex < 0 || userMsg == null) {
                    showToast("找不到对应的问题")
                    return@withLock
                }

                val newAssistant = MessageEntity(
                    conversationId = convId,
                    role = "assistant",
                    content = "",
                    status = MessageStatus.PENDING,
                    model = model.apiName
                )

                db.withTransaction {
                    for (i in targetIndex until messages.size) {
                        conversationRepo.deleteMessage(messages[i].id)
                    }
                    conversationRepo.insertMessage(newAssistant)
                    touchConversation(convId)
                }

                startGenerationLocked(convId, userMsg.id, newAssistant.id, role.prompt, model)
            }
        }
    }

    fun regenerateMessage(messageId: String) {
        val convId = _conversationId.value ?: return

        viewModelScope.launch {
            generationMutex.withLock {
                if (hasActiveGenerationLocked()) return@withLock

                val conv = conversationRepo.getConversation(convId) ?: return@withLock
                val role = roleFor(conv.roleId)
                val model = ModelId.fromApiName(conv.model)
                val messages = conversationRepo.getMessages(convId)
                val targetIndex = messages.indexOfFirst { it.id == messageId }
                val target = messages.getOrNull(targetIndex)
                val userMsg = messages.getOrNull(targetIndex - 1)?.takeIf { it.role == "user" }
                if (target?.role != "assistant" || userMsg == null) {
                    showToast("找不到对应的问题")
                    return@withLock
                }

                val newAssistant = MessageEntity(
                    conversationId = convId,
                    role = "assistant",
                    content = "",
                    status = MessageStatus.PENDING,
                    model = model.apiName
                )

                db.withTransaction {
                    for (i in targetIndex until messages.size) {
                        conversationRepo.deleteMessage(messages[i].id)
                    }
                    conversationRepo.insertMessage(newAssistant)
                    touchConversation(convId)
                }

                startGenerationLocked(convId, userMsg.id, newAssistant.id, role.prompt, model)
            }
        }
    }

    fun editAndResend(messageId: String, newText: String) {
        val convId = _conversationId.value ?: return
        val cleanText = validateMessage(newText) ?: return

        viewModelScope.launch {
            generationMutex.withLock {
                if (hasActiveGenerationLocked()) return@withLock

                val conv = conversationRepo.getConversation(convId) ?: return@withLock
                val role = roleFor(conv.roleId)
                val model = ModelId.fromApiName(conv.model)
                val messages = conversationRepo.getMessages(convId)
                val targetIndex = messages.indexOfFirst { it.id == messageId }
                if (messages.getOrNull(targetIndex)?.role != "user") return@withLock

                val newAssistant = MessageEntity(
                    conversationId = convId,
                    role = "assistant",
                    content = "",
                    status = MessageStatus.PENDING,
                    model = model.apiName
                )

                db.withTransaction {
                    for (i in targetIndex + 1 until messages.size) {
                        conversationRepo.deleteMessage(messages[i].id)
                    }
                    conversationRepo.updateMessageContent(messageId, cleanText, MessageStatus.SUCCESS)
                    conversationRepo.insertMessage(newAssistant)
                    touchConversation(convId)
                }

                startGenerationLocked(convId, messageId, newAssistant.id, role.prompt, model)
            }
        }
    }

    fun copyMessage(text: String) {
        val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE)
            as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("mimo_chat", text))
        showToast("已复制到剪贴板")
    }

    private suspend fun touchConversation(conversationId: String) {
        val conv = conversationRepo.getConversation(conversationId) ?: return
        db.conversationDao().update(conv.copy(updatedAt = System.currentTimeMillis()))
    }

    private val _connectionPhase = MutableStateFlow(ConnectionPhase.IDLE)
    val connectionPhase: StateFlow<ConnectionPhase> = _connectionPhase.asStateFlow()

    private val _connectionError = MutableStateFlow("")
    val connectionError: StateFlow<String> = _connectionError.asStateFlow()

    private val _probeResults = MutableStateFlow<List<ProbeResult>>(emptyList())
    val probeResults: StateFlow<List<ProbeResult>> = _probeResults.asStateFlow()

    fun loadConnection(): MimoConnection = settingsStorage.loadConnection()
    fun saveConnection(connection: MimoConnection) { settingsStorage.saveConnection(connection) }
    fun clearApiKey() { settingsStorage.clearApiKey() }

    fun saveWorkspaceConfig(config: GitHubWorkspaceConfig) {
        settingsStorage.saveWorkspaceConfig(config)
        _workspaceConfig.value = config
    }

    fun syncWorkspace(config: GitHubWorkspaceConfig) {
        saveWorkspaceConfig(config)
        if (!config.isConfigured) {
            _workspaceSyncState.value = WorkspaceSyncState.Error("请填写 owner/repository、基础分支和 GitHub Token")
            return
        }
        _workspaceSyncState.value = WorkspaceSyncState.Syncing
        viewModelScope.launch {
            try {
                val ready = workspace.sync(config)
                _workspaceConfig.value = settingsStorage.loadWorkspaceConfig()
                _workspaceSyncState.value = ready
                showToast("工作区已同步 ${ready.files} 个文件")
            } catch (e: Exception) {
                val message = e.message ?: "同步工作区失败"
                _workspaceSyncState.value = WorkspaceSyncState.Error(message)
                showToast(message)
            }
        }
    }

    fun clearGitHubToken() {
        settingsStorage.clearGitHubToken()
        _workspaceConfig.value = settingsStorage.loadWorkspaceConfig()
    }

    fun approveAgentAction() = approvalManager.approve()
    fun denyAgentAction() = approvalManager.deny()

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
                val models = com.example.mimochat.data.remote.MimoClient.loadModels(config)
                if (models.isEmpty()) throw Exception("没有加载到模型")
                _connectionPhase.value = ConnectionPhase.TESTING
                _probeResults.value = models.map { m ->
                    ProbeResult(model = m, capability = "识别中", status = ProbeStatus.TESTING, detail = "正在验证能力")
                }
                val results = mutableListOf<ProbeResult>()
                for (m in models) {
                    results.add(com.example.mimochat.data.remote.MimoClient.probeModel(config, m))
                    _probeResults.value = results.toList()
                }
                _connectionPhase.value = ConnectionPhase.DONE
            } catch (e: Exception) {
                _connectionError.value = com.example.mimochat.data.remote.MimoClient.translateError(e)
                _connectionPhase.value = ConnectionPhase.IDLE
            }
        }
    }

    fun showToast(message: String) {
        _toast.value = message.take(80)
        viewModelScope.launch {
            delay(2500)
            if (_toast.value == message.take(80)) _toast.value = ""
        }
    }

    fun getMemories() = db.memoryDao().getAllFlow()
    fun addMemory(content: String) { viewModelScope.launch { db.memoryDao().upsert(MemoryEntity(content = content)) } }
    fun deleteMemory(id: String) { viewModelScope.launch { db.memoryDao().deleteById(id) } }
    fun toggleMemory(id: String, enabled: Boolean) { viewModelScope.launch { db.memoryDao().setEnabled(id, enabled) } }

    override fun onCleared() {
        approvalManager.cancelPending()
        super.onCleared()
    }
}
