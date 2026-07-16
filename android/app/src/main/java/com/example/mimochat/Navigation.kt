package com.example.mimochat

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mimochat.data.*
import com.example.mimochat.ui.main.MainViewModel
import com.example.mimochat.ui.screens.*

@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val screen by viewModel.screen.collectAsState()
    val drawerOpen by viewModel.drawerOpen.collectAsState()
    val modelOpen by viewModel.modelOpen.collectAsState()
    val roleOpen by viewModel.roleOpen.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val conversationId by viewModel.conversationId.collectAsState()
    val messages by viewModel.currentMessages.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val toast by viewModel.toast.collectAsState()
    val theme by viewModel.theme.collectAsState()
    val roles by viewModel.roles.collectAsState()
    val workspaceConfig by viewModel.workspaceConfig.collectAsState()
    val pendingApproval by viewModel.pendingApproval.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    val isGeneratingVoice by viewModel.isGeneratingVoice.collectAsState()
    val speakingId by viewModel.speakingId.collectAsState()

    var input by remember { mutableStateOf("") }
    var showExitConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val currentConversation = conversations.find { it.id == conversationId }
    val activeRole = roles.find { it.id == currentConversation?.roleId }
        ?: roles.firstOrNull()
        ?: DEFAULT_ROLES[0]
    val currentModel = currentConversation?.model?.let { ModelId.fromApiName(it) }
        ?: ModelId.MIMO_V2_5

    BackHandler {
        when {
            showExitConfirm -> showExitConfirm = false
            roleOpen -> viewModel.setRoleOpen(false)
            modelOpen -> viewModel.setModelOpen(false)
            drawerOpen -> viewModel.setDrawerOpen(false)
            viewModel.goBack() -> Unit
            else -> showExitConfirm = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                (fadeIn() + slideInHorizontally { it / 10 }) togetherWith
                    (fadeOut() + slideOutHorizontally { -it / 10 })
            },
            label = "screen-transition"
        ) { currentScreen ->
        when (currentScreen) {
            Screen.CHAT -> {
                ChatScreen(
                    messages = messages,
                    conversationTitle = currentConversation?.title ?: "新对话",
                    role = activeRole,
                    model = currentModel,
                    input = input,
                    isStreaming = isStreaming,
                    hasApiKey = viewModel.hasApiKey,
                    onMenu = { viewModel.setDrawerOpen(true) },
                    onNew = { viewModel.startNewConversation(); input = "" },
                    onRole = { viewModel.setRoleOpen(true) },
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
                    voiceState = voiceState,
                    speakingId = speakingId,
                    onVoiceStart = { viewModel.startVoiceRecording() },
                    onVoiceStop = { viewModel.stopVoiceRecording() },
                    onVoiceCancel = { viewModel.cancelVoiceRecording() },
                    onSpeak = { id, text -> viewModel.speakMessage(id, text) }
                )
            }
            Screen.SETTINGS -> {
                SettingsScreen(
                    connection = viewModel.loadConnection(),
                    workspaceConfig = workspaceConfig,
                    theme = theme,
                    roleCount = roles.size,
                    onBack = { if (!viewModel.goBack()) viewModel.setScreen(Screen.CHAT) },
                    onThemeChange = { viewModel.setTheme(it) },
                    onOpenRoles = { viewModel.setScreen(Screen.ROLES) },
                    onOpenConnection = { viewModel.setScreen(Screen.CONNECTION) },
                    onSaveConnection = { viewModel.saveConnection(it) },
                    onClearApiKey = { viewModel.clearApiKey() },
                    onSaveGitHubToken = { viewModel.saveGitHubToken(it) },
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
                    onBack = { viewModel.goBack() },
                    onConnect = { viewModel.connect() },
                    onSave = { viewModel.saveConnection(it) }
                )
            }
            Screen.ROLES -> {
                RolesScreen(
                    roles = roles,
                    defaultRoleId = viewModel.defaultRoleId.collectAsState().value,
                    onBack = { viewModel.goBack() },
                    onSave = { viewModel.setRoles(it) },
                    onDefault = { viewModel.setDefaultRoleId(it) },
                    onGenerateVoice = { viewModel.generateRoleVoice(it) },
                    onPreviewVoice = { viewModel.previewRoleVoice(it) },
                    voiceState = voiceState,
                    isGeneratingVoice = isGeneratingVoice
                )
            }
            Screen.MEMORY -> {
                MemoryScreen(
                    onBack = { viewModel.goBack() },
                    memories = viewModel.getMemories().collectAsState(initial = emptyList()).value,
                    onAdd = { viewModel.addMemory(it) },
                    onDelete = { viewModel.deleteMemory(it) },
                    onToggle = { id, enabled -> viewModel.toggleMemory(id, enabled) }
                )
            }
        }
        }

        AnimatedVisibility(
            visible = drawerOpen,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn() + slideInHorizontally { -it },
            exit = fadeOut() + slideOutHorizontally { -it },
            label = "history-drawer"
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.24f))
                        .clickable { viewModel.setDrawerOpen(false) }
                )
                HistoryDrawer(
                    conversations = conversations,
                    roles = roles,
                    currentId = conversationId,
                    onClose = { viewModel.setDrawerOpen(false) },
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
        }

        AnimatedVisibility(
            visible = modelOpen,
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut() + slideOutVertically { it / 3 },
            label = "model-panel"
        ) {
            ModelPanel(
                model = currentModel,
                availableModels = availableModels,
                onClose = { viewModel.setModelOpen(false) },
                onSelect = {
                    viewModel.setModel(it)
                    viewModel.setModelOpen(false)
                }
            )
        }

        AnimatedVisibility(
            visible = roleOpen,
            enter = fadeIn() + slideInVertically { -it / 8 },
            exit = fadeOut() + slideOutVertically { -it / 8 },
            label = "role-panel"
        ) {
            RolePanel(
                roles = roles,
                selected = activeRole,
                onClose = { viewModel.setRoleOpen(false) },
                onSelect = { viewModel.setCurrentRole(it.id); viewModel.setRoleOpen(false) }
            )
        }

        // Toast
        if (toast.isNotBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    modifier = Modifier.padding(bottom = 100.dp)
                ) {
                    Text(
                        toast,
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

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("退出 MiMo？") },
            text = { Text("当前对话会自动保存，确定要退出应用吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirm = false
                    (context as? Activity)?.finish()
                }) { Text("退出") }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) { Text("取消") }
            }
        )
    }
}
