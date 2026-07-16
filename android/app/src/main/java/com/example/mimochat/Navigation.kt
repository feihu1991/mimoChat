package com.example.mimochat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mimochat.data.*
import com.example.mimochat.ui.main.MainViewModel
import com.example.mimochat.ui.screens.*
import com.example.mimochat.ui.voice.VoiceViewModel

@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel(),
    voiceViewModel: VoiceViewModel = viewModel()
) {
    val screen by viewModel.screen.collectAsState()
    val drawerOpen by viewModel.drawerOpen.collectAsState()
    val modelOpen by viewModel.modelOpen.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val conversationId by viewModel.conversationId.collectAsState()
    val messages by viewModel.currentMessages.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val toast by viewModel.toast.collectAsState()
    val theme by viewModel.theme.collectAsState()
    val roles by viewModel.roles.collectAsState()
    val workspaceConfig by viewModel.workspaceConfig.collectAsState()
    val workspaceState by viewModel.workspaceSyncState.collectAsState()
    val pendingApproval by viewModel.pendingApproval.collectAsState()

    val playingMessageId by voiceViewModel.playingMessageId.collectAsState()
    val loadingMessageId by voiceViewModel.loadingMessageId.collectAsState()
    val voiceDesignState by voiceViewModel.designState.collectAsState()
    val voiceToast by voiceViewModel.toast.collectAsState()

    var input by remember { mutableStateOf("") }

    val currentConversation = conversations.find { it.id == conversationId }
    val activeRole = roles.find { it.id == currentConversation?.roleId }
        ?: roles.firstOrNull()
        ?: DEFAULT_ROLES[0]
    val currentModel = currentConversation?.model?.let { ModelId.fromApiName(it) }
        ?: ModelId.MIMO_V2_5
    val displayToast = voiceToast.ifBlank { toast }

    Box(modifier = Modifier.fillMaxSize()) {
        when (screen) {
            Screen.CHAT -> {
                ChatScreen(
                    messages = messages,
                    conversationTitle = currentConversation?.title ?: "新对话",
                    role = activeRole,
                    model = currentModel,
                    input = input,
                    isStreaming = isStreaming,
                    hasApiKey = viewModel.hasApiKey,
                    playingMessageId = playingMessageId,
                    voiceLoadingMessageId = loadingMessageId,
                    onMenu = { viewModel.setDrawerOpen(true) },
                    onNew = { viewModel.startNewConversation(); input = "" },
                    onModel = { viewModel.setModelOpen(true) },
                    onInput = { input = it },
                    onSend = {
                        if (input.isNotBlank()) {
                            viewModel.sendMessage(input)
                            input = ""
                        }
                    },
                    onStop = { viewModel.stopGeneration() },
                    onRetry = { viewModel.retryMessage(it) },
                    onRegenerate = { viewModel.regenerateMessage(it) },
                    onCopy = { viewModel.copyMessage(it) },
                    onEdit = { id, text -> viewModel.editAndResend(id, text) },
                    onSpeak = { voiceViewModel.toggleMessage(it, activeRole) },
                    onRegenerateVoice = { voiceViewModel.regenerateMessage(it, activeRole) }
                )
            }
            Screen.SETTINGS -> {
                SettingsScreen(
                    connection = viewModel.loadConnection(),
                    workspaceConfig = workspaceConfig,
                    workspaceState = workspaceState,
                    theme = theme,
                    roleCount = roles.size,
                    onBack = { viewModel.setScreen(Screen.CHAT) },
                    onThemeChange = { viewModel.setTheme(it) },
                    onOpenRoles = { viewModel.setScreen(Screen.ROLES) },
                    onOpenConnection = { viewModel.setScreen(Screen.CONNECTION) },
                    onSaveConnection = { viewModel.saveConnection(it) },
                    onClearApiKey = { viewModel.clearApiKey() },
                    onSaveWorkspace = { viewModel.saveWorkspaceConfig(it) },
                    onSyncWorkspace = { viewModel.syncWorkspace(it) },
                    onClearGitHubToken = { viewModel.clearGitHubToken() }
                )
            }
            Screen.CONNECTION -> {
                val phase by viewModel.connectionPhase.collectAsState()
                val error by viewModel.connectionError.collectAsState()
                val probeResults by viewModel.probeResults.collectAsState()
                ConnectionScreen(
                    connection = viewModel.loadConnection(),
                    phase = phase,
                    error = error,
                    probeResults = probeResults,
                    onBack = { viewModel.setScreen(Screen.SETTINGS) },
                    onConnect = { viewModel.connect() },
                    onSave = { viewModel.saveConnection(it) }
                )
            }
            Screen.ROLES -> {
                RolesScreen(
                    roles = roles,
                    defaultRoleId = viewModel.defaultRoleId.collectAsState().value,
                    voiceDesignState = voiceDesignState,
                    onBack = { viewModel.setScreen(Screen.SETTINGS) },
                    onSave = { viewModel.setRoles(it) },
                    onDefault = { viewModel.setDefaultRoleId(it) },
                    onGenerateVoiceCandidates = { roleId, description ->
                        voiceViewModel.generateVoiceCandidates(roleId, description)
                    },
                    onPreviewVoiceCandidate = { voiceViewModel.previewCandidate(it) },
                    onSelectVoiceCandidate = { roleId, candidateId ->
                        voiceViewModel.selectCandidate(roleId, candidateId) { samplePath ->
                            viewModel.setRoles(
                                roles.map { role ->
                                    if (role.id == roleId) {
                                        role.copy(
                                            voiceModel = VoiceModel.MIMO_V2_5_TTS_VOICECLONE,
                                            voiceSample = samplePath
                                        )
                                    } else {
                                        role
                                    }
                                }
                            )
                        }
                    }
                )
            }
            Screen.MEMORY -> {
                MemoryScreen(
                    onBack = { viewModel.setScreen(Screen.SETTINGS) },
                    memories = viewModel.getMemories().collectAsState(initial = emptyList()).value,
                    onAdd = { viewModel.addMemory(it) },
                    onDelete = { viewModel.deleteMemory(it) },
                    onToggle = { id, enabled -> viewModel.toggleMemory(id, enabled) }
                )
            }
        }

        if (drawerOpen) {
            HistoryDrawer(
                conversations = conversations,
                roles = roles,
                currentId = conversationId,
                onClose = { viewModel.setDrawerOpen(false) },
                onNew = { viewModel.startNewConversation(); input = "" },
                onSelect = { id ->
                    viewModel.selectConversation(id)
                    viewModel.setDrawerOpen(false)
                },
                onSettings = {
                    viewModel.setDrawerOpen(false)
                    viewModel.setScreen(Screen.SETTINGS)
                },
                onRename = { id, title -> viewModel.renameConversation(id, title) },
                onDelete = { id -> viewModel.deleteConversation(id) }
            )
        }

        if (modelOpen) {
            ModelPanel(
                model = currentModel,
                onClose = { viewModel.setModelOpen(false) },
                onSelect = {
                    viewModel.setModel(it)
                    viewModel.setModelOpen(false)
                }
            )
        }

        if (displayToast.isNotBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    modifier = Modifier.padding(bottom = 100.dp)
                ) {
                    Text(
                        displayToast,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        color = MaterialTheme.colorScheme.inverseOnSurface
                    )
                }
            }
        }
    }

    pendingApproval?.let { approval ->
        AlertDialog(
            onDismissRequest = { viewModel.denyAgentAction() },
            title = { Text(approval.title) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(approval.description, style = MaterialTheme.typography.bodyMedium)
                    if (approval.diff.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            SelectionContainer {
                                Text(
                                    approval.diff.take(40_000),
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                    Text(
                        "Token 不会发送给模型；拒绝后 Agent 会收到 DENIED 工具结果。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.approveAgentAction() }) { Text("允许本次") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.denyAgentAction() }) { Text("拒绝") }
            }
        )
    }
}
