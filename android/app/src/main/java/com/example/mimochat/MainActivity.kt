package com.example.mimochat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mimochat.data.Screen
import com.example.mimochat.theme.MiMoChatTheme
import com.example.mimochat.ui.main.MainViewModel
import com.example.mimochat.ui.main.ThemeMode
import com.example.mimochat.ui.screens.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            val theme by viewModel.theme.collectAsState()

            MiMoChatTheme(
                darkTheme = theme == ThemeMode.DARK || (theme == ThemeMode.SYSTEM && isSystemInDarkTheme())
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MiMoChatApp(viewModel = viewModel)
                }
            }
        }
    }

    private fun isSystemInDarkTheme(): Boolean {
        val uiMode = resources.configuration.uiMode
        return (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
}

@Composable
fun MiMoChatApp(viewModel: MainViewModel) {
    val screen by viewModel.screen.collectAsState()
    val drawerOpen by viewModel.drawerOpen.collectAsState()
    val attachmentOpen by viewModel.attachmentOpen.collectAsState()
    val modelOpen by viewModel.modelOpen.collectAsState()
    val theme by viewModel.theme.collectAsState()
    val roles by viewModel.roles.collectAsState()
    val defaultRoleId by viewModel.defaultRoleId.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val conversationId by viewModel.conversationId.collectAsState()
    val model by viewModel.model.collectAsState()
    val input by viewModel.input.collectAsState()
    val attachments by viewModel.attachments.collectAsState()
    val thinking by viewModel.thinking.collectAsState()
    val recording by viewModel.recording.collectAsState()
    val voiceStatus by viewModel.voiceStatus.collectAsState()
    val toast by viewModel.toast.collectAsState()
    val playingMessageId by viewModel.playingMessageId.collectAsState()
    val connection by viewModel.connection.collectAsState()
    val probeResults by viewModel.probeResults.collectAsState()
    val connectionPhase by viewModel.connectionPhase.collectAsState()
    val connectionError by viewModel.connectionError.collectAsState()

    val currentConversation = viewModel.currentConversation
    val activeRole = viewModel.activeRole

    when (screen) {
        Screen.CHAT -> {
            ChatScreen(
                conversation = currentConversation,
                role = activeRole,
                model = model,
                input = input,
                attachments = attachments,
                thinking = thinking,
                recording = recording,
                voiceStatus = voiceStatus,
                playingMessageId = playingMessageId,
                onMenu = { viewModel.setDrawerOpen(true) },
                onNew = { viewModel.startNewConversation() },
                onModel = { viewModel.setModelOpen(true) },
                onInput = { viewModel.setInput(it) },
                onSend = { viewModel.sendPrompt(input) },
                onVoice = { viewModel.toggleRecording() },
                onAttachment = { viewModel.setAttachmentOpen(true) },
                onRemoveAttachment = { viewModel.removeAttachment(it) },
                onSpeak = { text, id -> viewModel.playRoleVoice(text, id) }
            )
        }
        Screen.SETTINGS -> {
            SettingsScreen(
                onBack = { viewModel.setScreen(Screen.CHAT) },
                onOpen = { viewModel.setScreen(it) },
                roleCount = roles.size,
                theme = theme,
                onThemeChange = { viewModel.setTheme(it) }
            )
        }
        Screen.CONNECTION -> {
            ConnectionScreen(
                onBack = { viewModel.setScreen(Screen.SETTINGS) },
                connection = connection,
                onConnectionChange = { viewModel.setConnection(it) },
                probeResults = probeResults,
                phase = connectionPhase,
                error = connectionError,
                onConnect = { viewModel.connect() }
            )
        }
        Screen.ROLES -> {
            RolesScreen(
                roles = roles,
                defaultRoleId = defaultRoleId,
                onBack = { viewModel.setScreen(Screen.SETTINGS) },
                onSave = { viewModel.setRoles(it) },
                onDefault = { viewModel.setDefaultRoleId(it) }
            )
        }
        Screen.MEMORY -> {
            MemoryScreen(
                onBack = { viewModel.setScreen(Screen.SETTINGS) }
            )
        }
    }

    // Drawer
    if (drawerOpen) {
        HistoryDrawer(
            conversations = conversations,
            roles = roles,
            currentId = conversationId,
            onClose = { viewModel.setDrawerOpen(false) },
            onNew = { viewModel.startNewConversation() },
            onSelect = { id ->
                viewModel.setConversationId(id)
                viewModel.setDrawerOpen(false)
            },
            onSettings = {
                viewModel.setDrawerOpen(false)
                viewModel.setScreen(Screen.SETTINGS)
            }
        )
    }

    // Attachment panel
    if (attachmentOpen) {
        AttachmentPanel(
            onClose = { viewModel.setAttachmentOpen(false) },
            onRecent = { url, index ->
                viewModel.addAttachment(
                    com.example.mimochat.data.Attachment(
                        id = "recent-${System.currentTimeMillis()}",
                        name = "最近图片 ${index + 1}",
                        type = com.example.mimochat.data.AttachmentType.IMAGE,
                        url = url
                    )
                )
                viewModel.setAttachmentOpen(false)
            },
            onCamera = { /* TODO: Open camera */ },
            onAlbum = { /* TODO: Open album */ },
            onFile = { /* TODO: Open file picker */ }
        )
    }

    // Model panel
    if (modelOpen) {
        ModelPanel(
            model = model,
            onClose = { viewModel.setModelOpen(false) },
            onSelect = { id ->
                viewModel.setModel(id)
                viewModel.setModelOpen(false)
            }
        )
    }

    // Toast
    if (toast.isNotEmpty()) {
        // TODO: Show toast
    }
}