package com.example.mimochat.ui.main

import androidx.room.withTransaction
import com.example.mimochat.core.agent.ApprovalManager
import com.example.mimochat.core.workspace.GitHubWorkspace
import com.example.mimochat.core.workspace.GitHubWorkspaceConfig
import com.example.mimochat.core.workspace.WorkspaceSyncState
import com.example.mimochat.data.DEFAULT_ROLES
import com.example.mimochat.data.MessageEntity
import com.example.mimochat.data.MessageStatus
import com.example.mimochat.data.ModelId
import com.example.mimochat.data.Role
import com.example.mimochat.data.local.AppDatabase
import com.example.mimochat.data.local.SettingsStorage
import com.example.mimochat.data.remote.MimoClient
import com.example.mimochat.data.remote.StreamChunk
import com.example.mimochat.data.repository.AgentRepository
import com.example.mimochat.data.repository.ChatRepository
import com.example.mimochat.data.repository.ConversationRepository
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 聊天生成编排控制器：发送消息、流式生成任务管理、停止/重试/重新生成/编辑重发。
 *
 * 负责生成任务的互斥与生命周期（[GenerationTask]），通过回调与宿主 ViewModel 解耦
 * （会话切换、流式状态、工作区状态、toast）。
 */
class ChatGenerationController(
    private val db: AppDatabase,
    private val conversationRepo: ConversationRepository,
    private val chatRepo: ChatRepository,
    private val agentRepo: AgentRepository,
    private val settingsStorage: SettingsStorage,
    private val workspace: GitHubWorkspace,
    private val scope: CoroutineScope,
    private val conversationIdProvider: () -> String?,
    private val availableModelsProvider: () -> Set<String>,
    private val rolesProvider: () -> List<Role>,
    private val approvalManager: ApprovalManager,
    private val isStreamingSetter: (Boolean) -> Unit,
    private val workspaceConfigSetter: (GitHubWorkspaceConfig) -> Unit,
    private val workspaceSyncStateSetter: (WorkspaceSyncState) -> Unit,
    private val showToast: (String) -> Unit
) {
    data class GenerationTask(
        val token: String,
        val conversationId: String,
        val assistantMessageId: String,
        val userMessageId: String,
        val job: Job
    )

    companion object {
        private const val MAX_USER_MESSAGE_CHARS = 28_000
    }

    private var generationTask: GenerationTask? = null
    private val generationMutex = Mutex()

    // ── Chat: Send Message ──

    fun sendMessage(text: String) {
        val clean = validateMessage(text) ?: return
        val convId = conversationIdProvider() ?: return

        scope.launch {
            generationMutex.withLock {
                if (hasActiveGenerationLocked()) return@withLock

                val conv = conversationRepo.getConversation(convId) ?: return@withLock
                val role = roleFor(conv.roleId)
                val model = ModelId.fromApiName(conv.model)
                if (availableModelsProvider().isNotEmpty() && model.apiName !in availableModelsProvider()) {
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

    private fun roleFor(roleId: String?): Role {
        val roles = rolesProvider()
        return roles.find { it.id == roleId }
            ?: roles.firstOrNull()
            ?: DEFAULT_ROLES[0]
    }

    // ── Generation lifecycle ──

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
        isStreamingSetter(true)

        val job = scope.launch(start = CoroutineStart.LAZY) {
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
                        isStreamingSetter(false)
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
        scope.launch {
            val task = generationMutex.withLock {
                val current = generationTask ?: return@withLock null
                generationTask = null
                isStreamingSetter(false)
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

    suspend fun cancelGeneration(conversationId: String? = null) {
        approvalManager.cancelPending()
        val task = generationMutex.withLock {
            val current = generationTask ?: return@withLock null
            if (conversationId != null && current.conversationId != conversationId) return@withLock null
            generationTask = null
            isStreamingSetter(false)
            current
        } ?: return
        task.job.cancelAndJoin()
    }

    fun retryMessage(messageId: String) {
        val convId = conversationIdProvider() ?: return

        scope.launch {
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
        val convId = conversationIdProvider() ?: return

        scope.launch {
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
        val convId = conversationIdProvider() ?: return
        val cleanText = validateMessage(newText) ?: return

        scope.launch {
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

    // ── Helpers ──

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
        workspaceConfigSetter(config)
        if (!workspace.isReadyFor(config)) {
            showToast("正在准备 GitHub 代码工作区…")
            workspaceSyncStateSetter(WorkspaceSyncState.Syncing)
            val ready = workspace.sync(config)
            workspaceSyncStateSetter(ready)
        }
    }

    private suspend fun touchConversation(conversationId: String) {
        val conv = conversationRepo.getConversation(conversationId) ?: return
        db.conversationDao().update(conv.copy(updatedAt = System.currentTimeMillis()))
    }
}
