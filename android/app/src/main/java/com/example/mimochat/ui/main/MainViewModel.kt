package com.example.mimochat.ui.main

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mimochat.core.agent.ApprovalManager
import com.example.mimochat.core.workspace.GitHubWorkspace
import com.example.mimochat.core.workspace.GitHubWorkspaceConfig
import com.example.mimochat.core.workspace.WorkspaceSyncState
import com.example.mimochat.data.*
import com.example.mimochat.data.audio.VoiceRecorder
import com.example.mimochat.data.audio.VoiceSampleStore
import com.example.mimochat.data.local.AppDatabase
import com.example.mimochat.data.local.SettingsStorage
import com.example.mimochat.data.repository.AgentRepository
import com.example.mimochat.data.repository.ChatRepository
import com.example.mimochat.data.repository.ContextBuilder
import com.example.mimochat.data.repository.ConversationRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 应用级 ViewModel：负责导航、主题/角色、会话与记忆等状态，
 * 并将语音、连接探测、聊天生成等内聚职责委托给对应的控制器。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    // ── Dependencies ──

    private val db = AppDatabase.getInstance(application)
    private val settingsStorage = SettingsStorage(application)
    private val contextBuilder = ContextBuilder(db.messageDao(), db.memoryDao())
    private val conversationRepo = ConversationRepository(db.conversationDao(), db.messageDao())
    private val approvalManager = ApprovalManager()
    private val workspace = GitHubWorkspace(application, settingsStorage)
    private val agentRepo = AgentRepository(
        db.messageDao(),
        contextBuilder,
        settingsStorage,
        workspace,
        approvalManager
    )
    private val chatRepo = ChatRepository(db.messageDao(), contextBuilder, settingsStorage)
    private val voiceRecorder = VoiceRecorder(application)
    private val voiceSampleStore = VoiceSampleStore(application)

    // ── Controllers ──

    private val voiceController = VoiceController(
        context = application,
        settingsStorage = settingsStorage,
        voiceRecorder = voiceRecorder,
        voiceSampleStore = voiceSampleStore,
        scope = viewModelScope,
        showToast = { showToast(it) },
        rolesProvider = { _roles.value },
        saveRoles = { setRoles(it) },
        roleProvider = { activeRole }
    )

    private val connectionController = ConnectionController(
        settingsStorage = settingsStorage,
        scope = viewModelScope,
        onModelsLoaded = { _availableModels.value = it }
    )

    private val generationController = ChatGenerationController(
        db = db,
        conversationRepo = conversationRepo,
        chatRepo = chatRepo,
        agentRepo = agentRepo,
        settingsStorage = settingsStorage,
        workspace = workspace,
        scope = viewModelScope,
        conversationIdProvider = { _conversationId.value },
        availableModelsProvider = { _availableModels.value },
        rolesProvider = { _roles.value },
        approvalManager = approvalManager,
        isStreamingSetter = { _isStreaming.value = it },
        workspaceConfigSetter = { _workspaceConfig.value = it },
        workspaceSyncStateSetter = { _workspaceSyncState.value = it },
        showToast = { showToast(it) }
    )

    // ── Navigation / UI state ──

    private val _screen = MutableStateFlow(Screen.CHAT)
    val screen: StateFlow<Screen> = _screen.asStateFlow()
    private val screenBackStack = ArrayDeque<Screen>()

    private val _drawerOpen = MutableStateFlow(false)
    val drawerOpen: StateFlow<Boolean> = _drawerOpen.asStateFlow()

    private val _modelOpen = MutableStateFlow(false)
    val modelOpen: StateFlow<Boolean> = _modelOpen.asStateFlow()

    private val _roleOpen = MutableStateFlow(false)
    val roleOpen: StateFlow<Boolean> = _roleOpen.asStateFlow()

    // ── Theme / roles ──

    private val _theme = MutableStateFlow(loadTheme())
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    private val _roles = MutableStateFlow(loadRoles())
    val roles: StateFlow<List<Role>> = _roles.asStateFlow()

    private val _defaultRoleId = MutableStateFlow(settingsStorage.defaultRoleId)
    val defaultRoleId: StateFlow<String> = _defaultRoleId.asStateFlow()

    // ── Conversation state ──

    private val _conversationId = MutableStateFlow<String?>(null)
    val conversationId: StateFlow<String?> = _conversationId.asStateFlow()

    private val _toast = MutableStateFlow("")
    val toast: StateFlow<String> = _toast.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _availableModels = MutableStateFlow<Set<String>>(emptySet())
    val availableModels: StateFlow<Set<String>> = _availableModels.asStateFlow()

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

    // ── Voice state (delegated) ──

    val voiceState: StateFlow<VoiceChatState> = voiceController.voiceState
    val isGeneratingVoice: StateFlow<Boolean> = voiceController.isGeneratingVoice
    val speakingId: StateFlow<String?> = voiceController.speakingId

    // ── Connection state (delegated) ──

    val connectionPhase: StateFlow<ConnectionPhase> = connectionController.connectionPhase
    val connectionError: StateFlow<String> = connectionController.connectionError
    val probeResults: StateFlow<List<ProbeResult>> = connectionController.probeResults

    // ── Derived values ──

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

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

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

    // ── Navigation ──

    fun setScreen(screen: Screen) {
        if (_screen.value == screen) return
        if (screen == Screen.CHAT) {
            screenBackStack.clear()
        } else {
            screenBackStack.addLast(_screen.value)
        }
        _screen.value = screen
    }

    fun goBack(): Boolean {
        val previous = screenBackStack.removeLastOrNull() ?: return false
        _screen.value = previous
        return true
    }

    fun setDrawerOpen(open: Boolean) { _drawerOpen.value = open }
    fun setModelOpen(open: Boolean) { _modelOpen.value = open }
    fun setRoleOpen(open: Boolean) { _roleOpen.value = open }

    // ── Conversations ──

    fun selectConversation(id: String) {
        _conversationId.value = id
    }

    fun startNewConversation() {
        viewModelScope.launch {
            val id = conversationRepo.createConversation(roleId = _defaultRoleId.value)
            _conversationId.value = id
            _drawerOpen.value = false
            screenBackStack.clear()
            _screen.value = Screen.CHAT
        }
    }

    fun renameConversation(id: String, newTitle: String) {
        viewModelScope.launch { conversationRepo.updateTitle(id, newTitle) }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            val wasCurrent = _conversationId.value == id
            generationController.cancelGeneration(id)
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
            generationController.cancelGeneration()
            conversationRepo.deleteAllConversations()
            startNewConversation()
        }
    }

    fun setModel(model: ModelId) {
        if (_availableModels.value.isNotEmpty() && model.apiName !in _availableModels.value) {
            showToast("当前 API Key 未开放 ${model.displayName}")
            return
        }
        val convId = _conversationId.value ?: return
        viewModelScope.launch {
            val conv = conversationRepo.getConversation(convId)
            if (conv != null) {
                db.conversationDao().update(conv.copy(model = model.apiName))
            }
        }
    }

    fun setCurrentRole(roleId: String) {
        val convId = _conversationId.value ?: return
        viewModelScope.launch {
            conversationRepo.getConversation(convId)?.let { conv ->
                db.conversationDao().update(conv.copy(roleId = roleId, updatedAt = System.currentTimeMillis()))
            }
        }
    }

    // ── Voice (delegated) ──

    fun generateRoleVoice(role: Role) = voiceController.generateRoleVoice(role)
    fun previewRoleVoice(role: Role) = voiceController.previewRoleVoice(role)
    fun speakMessage(messageId: String, text: String) = voiceController.speakMessage(messageId, text)
    fun startVoiceRecording() = voiceController.startVoiceRecording()
    fun stopVoiceRecording() = voiceController.stopVoiceRecording { transcript -> sendMessage(transcript) }
    fun cancelVoiceRecording() = voiceController.cancelVoiceRecording()

    // ── Chat generation (delegated) ──

    fun sendMessage(text: String) = generationController.sendMessage(text)
    fun stopGeneration() = generationController.stopGeneration()
    fun retryMessage(messageId: String) = generationController.retryMessage(messageId)
    fun regenerateMessage(messageId: String) = generationController.regenerateMessage(messageId)
    fun editAndResend(messageId: String, newText: String) = generationController.editAndResend(messageId, newText)

    fun copyMessage(text: String) {
        val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE)
            as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("mimo_chat", text))
        showToast("已复制到剪贴板")
    }

    // ── Connection (delegated) ──

    fun loadConnection(): MimoConnection = connectionController.loadConnection()
    fun saveConnection(connection: MimoConnection) = connectionController.saveConnection(connection)
    fun clearApiKey() = connectionController.clearApiKey()
    fun connect() = connectionController.connect()

    // ── Workspace / Agent ──

    fun saveWorkspaceConfig(config: GitHubWorkspaceConfig) {
        settingsStorage.saveWorkspaceConfig(config)
        _workspaceConfig.value = config
    }

    fun saveGitHubToken(token: String) {
        saveWorkspaceConfig(settingsStorage.loadWorkspaceConfig().copy(token = token.trim()))
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

    // ── Toast ──

    fun showToast(message: String) {
        _toast.value = message.take(80)
        viewModelScope.launch {
            delay(2500)
            if (_toast.value == message.take(80)) _toast.value = ""
        }
    }

    // ── Memory ──

    fun getMemories() = db.memoryDao().getAllFlow()
    fun addMemory(content: String) { viewModelScope.launch { db.memoryDao().upsert(MemoryEntity(content = content)) } }
    fun deleteMemory(id: String) { viewModelScope.launch { db.memoryDao().deleteById(id) } }
    fun toggleMemory(id: String, enabled: Boolean) { viewModelScope.launch { db.memoryDao().setEnabled(id, enabled) } }

    override fun onCleared() {
        approvalManager.cancelPending()
        voiceController.release()
        voiceRecorder.cancel()
        super.onCleared()
    }
}
