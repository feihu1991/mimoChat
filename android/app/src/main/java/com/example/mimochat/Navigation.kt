package com.example.mimochat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val conversations by viewModel.conversations.collectAsState()
    val conversationId by viewModel.conversationId.collectAsState()
    val messages by viewModel.currentMessages.collectAsState()
    val model by viewModel.model.collectAsState()
    val input by viewModel.input.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val toast by viewModel.toast.collectAsState()
    val theme by viewModel.theme.collectAsState()
    val roles by viewModel.roles.collectAsState()

    val currentConversation = conversations.find { it.id == conversationId }
    val activeRole = roles.find { it.id == currentConversation?.roleId } ?: roles.firstOrNull() ?: DEFAULT_ROLES[0]

    Box(modifier = Modifier.fillMaxSize()) {
        when (screen) {
            Screen.CHAT -> {
                ChatScreen(
                    messages = messages,
                    role = activeRole,
                    model = model,
                    input = input,
                    isStreaming = isStreaming,
                    hasApiKey = viewModel.hasApiKey,
                    onMenu = { viewModel.setDrawerOpen(true) },
                    onNew = { viewModel.startNewConversation() },
                    onModel = { viewModel.setModelOpen(true) },
                    onInput = { viewModel.setInput(it) },
                    onSend = { viewModel.sendMessage() },
                    onStop = { viewModel.stopGeneration() },
                    onRetry = { viewModel.retryMessage(it) },
                    onRegenerate = { viewModel.regenerateMessage(it) },
                    onCopy = { viewModel.copyMessage(it) },
                    onEdit = { id, text -> viewModel.editAndResend(id, text) }
                )
            }
            Screen.SETTINGS -> {
                SettingsScreen(
                    connection = viewModel.loadConnection(),
                    theme = theme,
                    roleCount = roles.size,
                    onBack = { viewModel.setScreen(Screen.CHAT) },
                    onThemeChange = { viewModel.setTheme(it) },
                    onOpenRoles = { viewModel.setScreen(Screen.ROLES) },
                    onOpenConnection = { viewModel.setScreen(Screen.CONNECTION) },
                    onSaveConnection = { viewModel.saveConnection(it) },
                    onClearApiKey = { viewModel.clearApiKey() }
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
                    onBack = { viewModel.setScreen(Screen.SETTINGS) },
                    onSave = { viewModel.setRoles(it) },
                    onDefault = { viewModel.setDefaultRoleId(it) }
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

        // History drawer (overlay)
        if (drawerOpen) {
            HistoryDrawer(
                conversations = conversations,
                roles = roles,
                currentId = conversationId,
                onClose = { viewModel.setDrawerOpen(false) },
                onNew = { viewModel.startNewConversation() },
                onSelect = { id -> viewModel.selectConversation(id); viewModel.setDrawerOpen(false) },
                onSettings = { viewModel.setDrawerOpen(false); viewModel.setScreen(Screen.SETTINGS) },
                onRename = { id, title -> viewModel.renameConversation(id, title) },
                onDelete = { id -> viewModel.deleteConversation(id) }
            )
        }

        // Model panel (overlay)
        if (modelOpen) {
            ModelPanel(
                model = model,
                onClose = { viewModel.setModelOpen(false) },
                onSelect = { viewModel.setModel(it); viewModel.setModelOpen(false) }
            )
        }

        // Toast
        if (toast.isNotBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    modifier = androidx.compose.ui.Modifier.padding(bottom = 100.dp)
                ) {
                    androidx.compose.material3.Text(
                        toast,
                        modifier = androidx.compose.ui.Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        color = MaterialTheme.colorScheme.inverseOnSurface
                    )
                }
            }
        }
    }
}
