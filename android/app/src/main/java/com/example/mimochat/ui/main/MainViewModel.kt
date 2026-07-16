package com.example.mimochat.ui.main

import android.app.Application
import android.content.Context
import android.media.MediaPlayer
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
import com.example.mimochat.data.audio.VoiceRecorder
import com.example.mimochat.data.audio.VoiceSampleStore
import com.example.mimochat.data.remote.MimoClient
import com.example.mimochat.data.remote.StreamChunk
import com.example.mimochat.data.repository.AgentRepository
import com.example.mimochat.data.repository.ChatRepository
import com.example.mimochat.data.repository.ContextBuilder
import com.example.mimochat.data.repository.ConversationRepository
import java.util.UUID
import android.util.Base64
import java.io.File
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
    private val agentRepo = AgentRepository(
        db.messageDao(),
        contextBuilder,
        settingsStorage,
        workspace,
        approvalManager
    )
    private val chatRepo = ChatRepository(db.messageDao(), contextBuilder, settingsStorage)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val voiceRecorder = VoiceRecorder(application)
    private val voiceSampleStore = VoiceSampleStore(application)
    private var mediaPlayer: MediaPlayer? = null
    private var mediaFile: File? = null

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
    private val screenBackStack = ArrayDeque<Screen>()

    private val _drawerOpen = MutableStateFlow(false)
    val drawerOpen: StateFlow<Boolean> = _drawerOpen.asStateFlow()

    private val _modelOpen = MutableStateFlow(false)
    val modelOpen: StateFlow<Boolean> = _modelOpen.asStateFlow()

    private val _roleOpen = MutableStateFlow(false)
    val roleOpen: StateFlow<Boolean> = _roleOpen.asStateFlow()

    private val _voiceState = MutableStateFlow(VoiceChatState.IDLE)
    val voiceState: StateFlow<VoiceChatState> = _voiceState.asStateFlow()

    private val _isGeneratingVoice = MutableStateFlow(false)
    val isGeneratingVoice: StateFlow<Boolean> = _isGeneratingVoice.asStateFlow()

    private val _speakingId = MutableStateFlow<String?>(null)
    val speakingId: StateFlow<String?> = _speakingId.asStateFlow()

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

    fun generateRoleVoice(role: Role) {
        if (settingsStorage.loadConnection().apiKey.isBlank()) {
            showToast("请先配置 API Key")
            return
        }
        viewModelScope.launch {
            try {
                _isGeneratingVoice.value = true
                _voiceState.value = VoiceChatState.THINKING
                val dataUrl = withContext(Dispatchers.IO) {
                    MimoClient.synthesizeSpeech(
                        settingsStorage.loadConnection(), "mimo-v2.5-tts-voicedesign",
                        "你好，我是${role.name}。${role.description}", role.voiceName, null,
                        role.voicePrompt ?: "自然、清晰、像面对面聊天一样回应。"
                    )
                }
                // MiMo 声音设计没有可复用的 voiceId；保存生成音频作为克隆样本，
                // 后续试听和聊天都使用同一份样本，避免每次重新设计出不同声音。
                val sampleReference = withContext(Dispatchers.IO) {
                    voiceSampleStore.save(role.id, dataUrl)
                }
                val saved = role.copy(
                    voiceModel = VoiceModel.MIMO_V2_5_TTS_VOICEDESIGN,
                    voiceSample = sampleReference,
                    voiceGenerated = true
                )
                setRoles(_roles.value.map { if (it.id == role.id) saved else it })
                showToast("音色已生成并保存，聊天将使用该音色")
                playDataUrl("role-preview", dataUrl)
            } catch (e: Exception) {
                _voiceState.value = VoiceChatState.ERROR
                showToast(MimoClient.translateError(e))
                delay(1200)
                _voiceState.value = VoiceChatState.IDLE
            } finally {
                _isGeneratingVoice.value = false
            }
        }
    }

    fun previewRoleVoice(role: Role) {
        if (role.voiceModel == VoiceModel.MIMO_V2_5_TTS_VOICEDESIGN && role.voiceSample.isNullOrBlank()) {
            showToast("请先生成并保存音色")
            return
        }
        speakText("role-preview", "你好，我是${role.name}。${role.description}", role)
    }

    fun speakMessage(messageId: String, text: String) {
        if (text.isBlank()) return
        if (_speakingId.value == messageId && mediaPlayer != null) {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                    _voiceState.value = VoiceChatState.IDLE
                } else {
                    player.start()
                    _voiceState.value = VoiceChatState.SPEAKING
                }
            }
            return
        }
        speakText(messageId, text, activeRole)
    }

    private fun speakText(id: String, text: String, role: Role) {
        if (settingsStorage.loadConnection().apiKey.isBlank()) {
            showToast("请先配置 API Key")
            return
        }
        viewModelScope.launch {
            try {
                _voiceState.value = VoiceChatState.THINKING
                val voiceSample = withContext(Dispatchers.IO) {
                    voiceSampleStore.resolve(role.voiceSample)
                }
                val voiceApiModel = if (role.voiceModel == VoiceModel.MIMO_V2_5_TTS_VOICEDESIGN && !voiceSample.isNullOrBlank()) {
                    VoiceModel.MIMO_V2_5_TTS_VOICECLONE.apiName
                } else role.voiceModel.apiName
                val dataUrl = withContext(Dispatchers.IO) {
                    MimoClient.synthesizeSpeech(
                        settingsStorage.loadConnection(), voiceApiModel, text,
                        role.voiceName, voiceSample, role.voicePrompt ?: "自然、清晰、像面对面聊天一样回应。"
                    )
                }
                playDataUrl(id, dataUrl)
            } catch (e: Exception) {
                _speakingId.value = null
                _voiceState.value = VoiceChatState.ERROR
                showToast(MimoClient.translateError(e))
                delay(1200)
                _voiceState.value = VoiceChatState.IDLE
            }
        }
    }

    private suspend fun playDataUrl(id: String, dataUrl: String) {
        val encoded = dataUrl.substringAfter(',', "")
        val file = File(getApplication<Application>().cacheDir, "speech-${System.currentTimeMillis()}.wav")
        withContext(Dispatchers.IO) {
            file.writeBytes(Base64.decode(encoded, Base64.DEFAULT))
        }
        withContext(Dispatchers.Main) {
            mediaPlayer?.release()
            mediaFile?.delete()
            mediaFile = file
            mediaPlayer = MediaPlayer().apply {
                val player = this
                setDataSource(file.absolutePath)
                setOnPreparedListener {
                    _speakingId.value = id
                    _voiceState.value = VoiceChatState.SPEAKING
                    start()
                }
                setOnCompletionListener {
                    _speakingId.value = null
                    _voiceState.value = VoiceChatState.IDLE
                    release()
                    file.delete()
                    if (mediaPlayer === player) {
                        mediaFile = null
                        mediaPlayer = null
                    }
                }
                setOnErrorListener { _, _, _ ->
                    _speakingId.value = null
                    _voiceState.value = VoiceChatState.ERROR
                    file.delete()
                    if (mediaPlayer === player) {
                        mediaFile = null
                        mediaPlayer = null
                    }
                    release()
                    true
                }
                prepareAsync()
            }
        }
    }

    fun startVoiceRecording() {
        if (voiceState.value != VoiceChatState.IDLE) return
        try {
            voiceRecorder.start()
            _voiceState.value = VoiceChatState.LISTENING
        } catch (e: Exception) {
            voiceRecorder.cancel()
            showToast("无法开始录音：${e.message ?: "请检查麦克风权限"}")
        }
    }

    fun stopVoiceRecording() {
        if (voiceState.value != VoiceChatState.LISTENING) return
        val file = voiceRecorder.stop() ?: run { _voiceState.value = VoiceChatState.IDLE; return }
        viewModelScope.launch {
            try {
                _voiceState.value = VoiceChatState.TRANSCRIBING
                val audio = withContext(Dispatchers.IO) {
                    "data:audio/mp4;base64," + Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                }
                val transcript = withContext(Dispatchers.IO) { MimoClient.speechRecognition(settingsStorage.loadConnection(), audio) }
                _voiceState.value = VoiceChatState.THINKING
                sendMessage(transcript)
            } catch (e: Exception) {
                _voiceState.value = VoiceChatState.ERROR
                showToast(MimoClient.translateError(e))
                delay(1200)
            } finally {
                file.delete()
                _voiceState.value = VoiceChatState.IDLE
            }
        }
    }

    fun cancelVoiceRecording() {
        voiceRecorder.cancel()
        _voiceState.value = VoiceChatState.IDLE
    }

    // ── Chat: Send Message ──
    fun sendMessage(text: String) {
        val clean = validateMessage(text) ?: return
        val convId = _conversationId.value ?: return

        viewModelScope.launch {
            generationMutex.withLock {
                if (hasActiveGenerationLocked()) return@withLock

                val conv = conversationRepo.getConversation(convId) ?: return@withLock
                val role = roleFor(conv.roleId)
                val model = ModelId.fromApiName(conv.model)
                if (_availableModels.value.isNotEmpty() && model.apiName !in _availableModels.value) {
                    showToast("当前 API Key 未开放 ${model.displayName}，请切换模型或重新连接")
                    return@withLock
                }
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
                    model = model,
                    useAgent = shouldUseAgent(clean),
                    userText = clean
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

    private fun shouldUseAgent(text: String): Boolean {
        val lower = text.lowercase()
        val explicitWorkspaceTerms = listOf(
            "github", "git hub", "仓库", "代码库", "工作区", "项目文件", "pull request", "pr ",
            "commit", "push", "branch", "提交代码", "推送代码", "读取文件", "修改文件", "编辑文件",
            "改文件", "删除文件", "读取代码", "修改代码", "编辑代码", "写代码", "同步代码", "改代码", "修复代码"
        )
        return explicitWorkspaceTerms.any { lower.contains(it) }
    }

    private suspend fun prepareWorkspaceForAgent(text: String) {
        val current = settingsStorage.loadWorkspaceConfig()
        if (current.token.isBlank()) {
            throw IllegalStateException("如需处理 GitHub 代码，请先在设置中配置 GitHub Token")
        }

        val repository = Regex("github\\.com/([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+)", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?: Regex("(?<![\\w.-])([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+)(?![\\w.-])")
                .find(text)
                ?.groupValues
                ?.getOrNull(1)
            ?: current.repository
        if (repository.isNullOrBlank()) {
            throw IllegalStateException("请在消息中说明 GitHub 仓库，例如 owner/repository")
        }

        val config = current.copy(repository = repository)
        settingsStorage.saveWorkspaceConfig(config)
        _workspaceConfig.value = config
        if (!workspace.isReadyFor(config)) {
            showToast("正在准备 GitHub 代码工作区…")
            _workspaceSyncState.value = WorkspaceSyncState.Syncing
            val ready = workspace.sync(config)
            _workspaceSyncState.value = ready
        }
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
        model: ModelId,
        useAgent: Boolean,
        userText: String
    ) {
        check(generationTask?.job?.isActive != true) { "generation already active" }

        val token = UUID.randomUUID().toString()
        _isStreaming.value = true

        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                if (useAgent) prepareWorkspaceForAgent(userText)
                val generation = if (useAgent) {
                    agentRepo.executeGeneration(
                        conversationId = conversationId,
                        assistantMessageId = assistantMessageId,
                        userMessageId = userMessageId,
                        systemPrompt = systemPrompt,
                        model = model
                    )
                } else {
                    chatRepo.executeGeneration(
                        conversationId = conversationId,
                        assistantMessageId = assistantMessageId,
                        userMessageId = userMessageId,
                        systemPrompt = systemPrompt,
                        model = model
                    )
                }
                generation.collect { chunk ->
                    if (chunk is StreamChunk.Error) showToast(chunk.message)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = MimoClient.translateError(e)
                conversationRepo.updateMessageStatus(assistantMessageId, MessageStatus.FAILED, message)
                showToast(message)
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

                startGenerationLocked(
                    convId,
                    userMsg.id,
                    newAssistant.id,
                    role.prompt,
                    model,
                    useAgent = shouldUseAgent(userMsg.content),
                    userText = userMsg.content
                )
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

                startGenerationLocked(
                    convId,
                    userMsg.id,
                    newAssistant.id,
                    role.prompt,
                    model,
                    useAgent = shouldUseAgent(userMsg.content),
                    userText = userMsg.content
                )
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

                startGenerationLocked(
                    convId,
                    messageId,
                    newAssistant.id,
                    role.prompt,
                    model,
                    useAgent = shouldUseAgent(cleanText),
                    userText = cleanText
                )
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
    fun saveConnection(connection: MimoConnection) {
        settingsStorage.saveConnection(connection)
        _availableModels.value = emptySet()
    }
    fun clearApiKey() { settingsStorage.clearApiKey() }

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
                _availableModels.value = models.toSet()
                _connectionPhase.value = ConnectionPhase.TESTING
                _probeResults.value = models.map { m ->
                    ProbeResult(model = m, capability = "识别中", status = ProbeStatus.TESTING, detail = "正在验证能力")
                }
                val results = mutableListOf<ProbeResult>()
                for (m in models) {
                    results.add(com.example.mimochat.data.remote.MimoClient.probeModel(config, m))
                    _probeResults.value = results.toList()
                }
                _availableModels.value = results
                    .filter { it.status == ProbeStatus.PASSED || it.status == ProbeStatus.REACHABLE }
                    .map { it.model }
                    .toSet()
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
        voiceRecorder.cancel()
        mediaPlayer?.release()
        mediaFile?.delete()
        mediaPlayer = null
        super.onCleared()
    }
}
