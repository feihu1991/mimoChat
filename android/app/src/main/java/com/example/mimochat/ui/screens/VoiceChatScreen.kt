package com.example.mimochat.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mimochat.data.Message
import com.example.mimochat.data.MessageRole
import com.example.mimochat.data.MessageStatus
import com.example.mimochat.data.ModelId
import com.example.mimochat.data.Role
import com.example.mimochat.theme.UserMessageBackground
import com.example.mimochat.theme.UserMessageText
import kotlinx.coroutines.launch

/**
 * Voice-enabled overload of ChatScreen. The existing non-voice screen remains
 * available, while callers that provide voice parameters get cached playback
 * and explicit voice regeneration controls for every assistant message.
 */
@Composable
fun ChatScreen(
    messages: List<Message>,
    conversationTitle: String,
    role: Role,
    model: ModelId,
    input: String,
    isStreaming: Boolean,
    hasApiKey: Boolean,
    playingMessageId: String?,
    voiceLoadingMessageId: String?,
    onMenu: () -> Unit,
    onNew: () -> Unit,
    onModel: () -> Unit,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onRetry: (String) -> Unit,
    onRegenerate: (String) -> Unit,
    onCopy: (String) -> Unit,
    onEdit: (String, String) -> Unit,
    onSpeak: (Message) -> Unit,
    onRegenerateVoice: (Message) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val latestMessageId = messages.lastOrNull()?.id

    LaunchedEffect(latestMessageId, messages.lastOrNull()?.text?.length) {
        if (messages.isNotEmpty()) listState.scrollToItem(0)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        VoiceChatTopBar(
            conversationTitle = conversationTitle,
            role = role,
            model = model,
            onMenu = onMenu,
            onNew = onNew,
            onModel = onModel
        )

        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                reverseLayout = true,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (messages.isEmpty()) {
                    item { VoiceRoleWelcome(role) }
                }
                items(messages.reversed(), key = { it.id }) { message ->
                    VoiceMessageBubble(
                        message = message,
                        role = role,
                        isPlaying = playingMessageId == message.id,
                        isVoiceLoading = voiceLoadingMessageId == message.id,
                        onRetry = { onRetry(message.id) },
                        onRegenerate = { onRegenerate(message.id) },
                        onCopy = { onCopy(message.text) },
                        onEdit = { text -> onEdit(message.id, text) },
                        onSpeak = { onSpeak(message) },
                        onRegenerateVoice = { onRegenerateVoice(message) }
                    )
                }
            }

            if (listState.firstVisibleItemIndex > 0) {
                Surface(
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
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
                        Icon(Icons.Default.KeyboardArrowDown, "回到底部", modifier = Modifier.size(20.dp))
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
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "请先在设置中配置 API Key",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        VoiceComposer(
            input = input,
            model = model,
            isStreaming = isStreaming,
            onInput = onInput,
            onSend = onSend,
            onStop = onStop
        )
    }
}

@Composable
private fun VoiceMessageBubble(
    message: Message,
    role: Role,
    isPlaying: Boolean,
    isVoiceLoading: Boolean,
    onRetry: () -> Unit,
    onRegenerate: () -> Unit,
    onCopy: () -> Unit,
    onEdit: (String) -> Unit,
    onSpeak: () -> Unit,
    onRegenerateVoice: () -> Unit
) {
    var isEditing by remember(message.id) { mutableStateOf(false) }
    var editText by remember(message.id) { mutableStateOf(message.text) }
    val isUser = message.role == MessageRole.USER
    val isPending = message.status == MessageStatus.PENDING
    val isStreaming = message.status == MessageStatus.STREAMING
    val isFailed = message.status == MessageStatus.FAILED

    if (isUser) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            Surface(
                modifier = Modifier.widthIn(max = 560.dp),
                shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp),
                color = UserMessageBackground
            ) {
                if (isEditing) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        OutlinedTextField(
                            value = editText,
                            onValueChange = { editText = it },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 10
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = {
                                if (editText.isNotBlank() && editText != message.text) onEdit(editText)
                                isEditing = false
                            }) { Text("保存并重新发送") }
                            TextButton(onClick = {
                                editText = message.text
                                isEditing = false
                            }) { Text("取消") }
                        }
                    }
                } else {
                    SelectionContainer {
                        Text(
                            message.text,
                            modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp),
                            color = UserMessageText,
                            fontSize = 14.sp,
                            lineHeight = 22.sp
                        )
                    }
                }
            }
            if (!isEditing) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    VoiceActionButton(Icons.Default.ContentCopy, "复制", onCopy)
                    VoiceActionButton(Icons.Default.Edit, "编辑") {
                        editText = message.text
                        isEditing = true
                    }
                }
            }
        }
        return
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Surface(
            modifier = Modifier.size(32.dp),
            shape = RoundedCornerShape(10.dp),
            color = Color(android.graphics.Color.parseColor(role.color))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    role.name.firstOrNull()?.toString() ?: "M",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f).widthIn(max = 760.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(role.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                when {
                    isStreaming -> VoiceStatus("生成中", MaterialTheme.colorScheme.primary)
                    isPending -> VoiceStatus("连接中", MaterialTheme.colorScheme.onSurfaceVariant)
                    message.status == MessageStatus.STOPPED -> VoiceStatus("已停止", MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(6.dp))

            val displayText = when {
                message.text.isBlank() && isStreaming -> "正在分析…"
                message.text.isBlank() && isPending -> "正在连接…"
                message.text.isBlank() && isFailed -> "请求失败"
                else -> message.text
            }
            Surface(
                color = if (isFailed) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            ) {
                SelectionContainer {
                    Text(
                        displayText,
                        modifier = Modifier.padding(
                            horizontal = if (isFailed) 12.dp else 0.dp,
                            vertical = if (isFailed) 10.dp else 0.dp
                        ),
                        fontSize = 14.sp,
                        lineHeight = 23.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (isFailed && message.errorMessage != null) {
                Text(
                    message.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            if (!isPending && !isStreaming && message.text.isNotBlank()) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    VoiceActionButton(
                        icon = when {
                            isVoiceLoading -> Icons.Default.HourglassTop
                            isPlaying -> Icons.Default.Stop
                            else -> Icons.Default.VolumeUp
                        },
                        label = when {
                            isVoiceLoading -> "生成语音"
                            isPlaying -> "停止"
                            else -> "播放"
                        },
                        onClick = onSpeak
                    )
                    VoiceActionButton(Icons.Default.Refresh, "重做语音", onRegenerateVoice)
                    VoiceActionButton(Icons.Default.ContentCopy, "复制", onCopy)
                    VoiceActionButton(Icons.Default.AutoFixHigh, "重新回答", onRegenerate)
                    if (isFailed) {
                        VoiceActionButton(Icons.Default.Replay, "重试", onRetry)
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 3.dp),
        modifier = Modifier.height(32.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp)
    }
}

@Composable
private fun VoiceStatus(text: String, color: Color) {
    Spacer(Modifier.width(8.dp))
    Text(text, style = MaterialTheme.typography.labelSmall, color = color)
}

@Composable
private fun VoiceChatTopBar(
    conversationTitle: String,
    role: Role,
    model: ModelId,
    onMenu: () -> Unit,
    onNew: () -> Unit,
    onModel: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, "打开会话列表") }
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    conversationTitle.ifBlank { "新对话" },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                TextButton(
                    onClick = onModel,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(
                        "${role.name} · ${model.displayName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(14.dp))
                }
            }
            IconButton(onClick = onNew) { Icon(Icons.Default.AddComment, "新建对话") }
        }
    }
}

@Composable
private fun VoiceRoleWelcome(role: Role) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(android.graphics.Color.parseColor(role.color))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(role.name.firstOrNull()?.toString() ?: "M", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("今天想聊什么？", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "你可以聊天、使用语音，也可以让 ${role.name} 帮你阅读、修改或生成文件。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun VoiceComposer(
    input: String,
    model: ModelId,
    isStreaming: Boolean,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .navigationBarsPadding()
            .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = onInput,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("给 MiMo 发消息", fontSize = 14.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    minLines = 1,
                    maxLines = 6
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(model.displayName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Surface(
                        onClick = if (isStreaming) onStop else onSend,
                        enabled = isStreaming || input.isNotBlank(),
                        modifier = Modifier.size(36.dp),
                        shape = CircleShape,
                        color = when {
                            isStreaming -> MaterialTheme.colorScheme.onSurface
                            input.isNotBlank() -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (isStreaming) Icons.Default.Stop else Icons.Default.ArrowUpward,
                                if (isStreaming) "停止生成" else "发送",
                                modifier = Modifier.size(17.dp),
                                tint = if (isStreaming) MaterialTheme.colorScheme.surface
                                else if (input.isNotBlank()) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
