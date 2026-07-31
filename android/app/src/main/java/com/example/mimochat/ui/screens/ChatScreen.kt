package com.example.mimochat.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.app.Activity
import com.example.mimochat.data.*
import com.example.mimochat.theme.*
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    messages: List<Message>,
    conversationTitle: String,
    role: Role,
    model: ModelId,
    input: String,
    isStreaming: Boolean,
    hasApiKey: Boolean,
    onMenu: () -> Unit,
    onNew: () -> Unit,
    onRole: () -> Unit,
    onModel: () -> Unit,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onRetry: (String) -> Unit,
    onRegenerate: (String) -> Unit,
    onCopy: (String) -> Unit,
    onEdit: (String, String) -> Unit,
    voiceState: VoiceChatState,
    speakingId: String?,
    onVoiceStart: () -> Unit,
    onVoiceStop: () -> Unit,
    onVoiceCancel: () -> Unit,
    onSpeak: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val scrollPolicy = remember { ChatScrollPolicy() }
    var isProgrammaticScroll by remember { mutableStateOf(false) }
    var voiceInputMode by remember { mutableStateOf(false) }

    suspend fun scrollToBottom(animated: Boolean) {
        if (messages.isEmpty()) return
        isProgrammaticScroll = true
        try {
            if (animated) listState.animateScrollToItem(0) else listState.scrollToItem(0)
        } finally {
            isProgrammaticScroll = false
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            Triple(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
                listState.isScrollInProgress
            )
        }.collect { (index, offset, scrolling) ->
            if (scrolling && !isProgrammaticScroll) {
                scrollPolicy.onUserScrollPositionChanged(index, offset)
            }
        }
    }

    val latestUserMessageId = messages.lastOrNull { it.role == MessageRole.USER }?.id
    var previousUserMessageId by remember { mutableStateOf(latestUserMessageId) }

    LaunchedEffect(latestUserMessageId) {
        if (latestUserMessageId != null && latestUserMessageId != previousUserMessageId) {
            scrollPolicy.onNewUserMessage()
            scrollToBottom(animated = false)
        }
        previousUserMessageId = latestUserMessageId
    }

    val latestMessage = messages.lastOrNull()
    LaunchedEffect(
        latestMessage?.id,
        latestMessage?.text?.length,
        latestMessage?.status,
        isStreaming
    ) {
        if (isStreaming && scrollPolicy.shouldAutoScroll()) {
            scrollToBottom(animated = false)
        }
    }

    val showScrollButton by remember {
        derivedStateOf {
            scrollPolicy.shouldShowScrollButton(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        ChatTopBar(
            conversationTitle = conversationTitle,
            role = role,
            model = model,
            onMenu = onMenu,
            onNew = onNew,
            onRole = onRole,
            onModel = onModel
        )

        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                reverseLayout = true
            ) {
                if (messages.isEmpty()) {
                    item { RoleWelcome(role = role) }
                }
                items(messages.reversed(), key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        onRetry = { onRetry(message.id) },
                        onRegenerate = { onRegenerate(message.id) },
                        onCopy = { onCopy(message.text) },
                        onEdit = { newText -> onEdit(message.id, newText) },
                        onSpeak = { onSpeak(message.id, message.text) },
                        isSpeaking = speakingId == message.id && voiceState == VoiceChatState.SPEAKING
                    )
                }
            }

            if (showScrollButton) {
                Surface(
                    onClick = {
                        scrollPolicy.onScrollToBottomClicked()
                        scope.launch { scrollToBottom(animated = true) }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                        .size(38.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "回到底部",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        if (!hasApiKey) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "请先在设置中配置模型 API Key",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        ComposerArea(
            input = input,
            isStreaming = isStreaming,
            voiceState = voiceState,
            voiceInputMode = voiceInputMode,
            onToggleVoiceInput = { voiceInputMode = !voiceInputMode },
            onInput = onInput,
            onSend = onSend,
            onStop = onStop,
            onVoiceStart = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    onVoiceStart()
                } else if (context is Activity) {
                    ActivityCompat.requestPermissions(context, arrayOf(Manifest.permission.RECORD_AUDIO), 4101)
                }
            },
            onVoiceStop = onVoiceStop,
            onVoiceCancel = onVoiceCancel
        )
    }
}
