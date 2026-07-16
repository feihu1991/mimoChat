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
    onModel: () -> Unit,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onRetry: (String) -> Unit,
    onRegenerate: (String) -> Unit,
    onCopy: (String) -> Unit,
    onEdit: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val scrollPolicy = remember { ChatScrollPolicy() }
    var isProgrammaticScroll by remember { mutableStateOf(false) }

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
    ) {
        ChatTopBar(
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
                        onEdit = { newText -> onEdit(message.id, newText) }
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
                        "请先在设置中配置 API Key",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        ComposerArea(
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
private fun MessageBubble(
    message: Message,
    onRetry: () -> Unit,
    onRegenerate: () -> Unit,
    onCopy: () -> Unit,
    onEdit: (String) -> Unit
) {
    var showMenu by remember(message.id) { mutableStateOf(false) }
    var isEditing by remember(message.id) { mutableStateOf(false) }
    var editText by remember(message.id) { mutableStateOf(message.text) }
    val isUser = message.role == MessageRole.USER
    val isFailed = message.status == MessageStatus.FAILED
    val isStopped = message.status == MessageStatus.STOPPED
    val isStreamingMsg = message.status == MessageStatus.STREAMING
    val isPending = message.status == MessageStatus.PENDING

    if (isUser) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .clickable(enabled = !isEditing) { showMenu = !showMenu },
                shape = RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = 20.dp,
                    bottomEnd = 6.dp
                ),
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
                        Row(modifier = Modifier.padding(top = 8.dp)) {
                            TextButton(onClick = {
                                isEditing = false
                                if (editText.isNotBlank() && editText != message.text) onEdit(editText)
                            }) { Text("保存并重新发送") }
                            TextButton(onClick = {
                                isEditing = false
                                editText = message.text
                            }) { Text("取消") }
                        }
                    }
                } else {
                    SelectionContainer {
                        Text(
                            text = message.text,
                            modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp),
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            color = UserMessageText
                        )
                    }
                }
            }

            if (showMenu && !isPending && !isStreamingMsg) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SmallButton(
                        onClick = { onCopy(); showMenu = false },
                        icon = Icons.Default.ContentCopy,
                        label = "复制"
                    )
                    SmallButton(
                        onClick = {
                            editText = message.text
                            isEditing = true
                            showMenu = false
                        },
                        icon = Icons.Default.Edit,
                        label = "编辑"
                    )
                }
            }
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            modifier = Modifier.size(30.dp),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("M", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.width(10.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 760.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "MiMo Code",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                when {
                    isStreamingMsg -> StatusLabel("生成中", MaterialTheme.colorScheme.primary)
                    isPending -> StatusLabel("连接中", MaterialTheme.colorScheme.onSurfaceVariant)
                    isStopped -> StatusLabel("已停止", MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(6.dp))

            val displayText = when {
                message.text.isEmpty() && isStreamingMsg -> "正在分析…"
                message.text.isEmpty() && isPending -> "正在连接…"
                isFailed && message.text.isEmpty() -> "请求失败"
                else -> message.text
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isPending && !isStreamingMsg) { showMenu = !showMenu },
                color = if (isFailed) {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                } else {
                    Color.Transparent
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                SelectionContainer {
                    MarkdownText(
                        text = displayText,
                        modifier = Modifier.padding(
                            horizontal = if (isFailed) 12.dp else 0.dp,
                            vertical = if (isFailed) 10.dp else 0.dp
                        )
                    )
                }
            }

            if (isStreamingMsg && message.text.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier.width(44.dp).height(2.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (message.text.contains("```") && message.text.isNotBlank()) {
                TextButton(
                    onClick = onCopy,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("复制代码", fontSize = 11.sp)
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

            if (showMenu && !isStreamingMsg && !isPending) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (message.text.isNotBlank()) {
                        SmallButton(
                            onClick = { onCopy(); showMenu = false },
                            icon = Icons.Default.ContentCopy,
                            label = "复制"
                        )
                        SmallButton(
                            onClick = { onRegenerate(); showMenu = false },
                            icon = Icons.Default.AutoFixHigh,
                            label = "重新生成"
                        )
                    }
                    if (isFailed) {
                        SmallButton(
                            onClick = { onRetry(); showMenu = false },
                            icon = Icons.Default.Refresh,
                            label = "重试"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusLabel(text: String, color: Color) {
    Spacer(Modifier.width(8.dp))
    Text(text, style = MaterialTheme.typography.labelSmall, color = color)
}

@Composable
private fun SmallButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        modifier = Modifier.height(32.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp)
    }
}

@Composable
private fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 14.sp,
        lineHeight = 23.sp,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun ChatTopBar(
    conversationTitle: String,
    role: Role,
    model: ModelId,
    onMenu: () -> Unit,
    onNew: () -> Unit,
    onModel: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenu) {
                Icon(Icons.Default.Menu, contentDescription = "打开会话列表")
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onModel)
                    .padding(horizontal = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "MiMo Code",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "  /  ",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        conversationTitle.ifBlank { "新对话" },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(android.graphics.Color.parseColor(role.color)))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${role.name} · ${model.displayName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(3.dp))
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onNew) {
                Icon(Icons.Default.AddComment, contentDescription = "新建对话")
            }
        }
    }
}

@Composable
private fun RoleWelcome(role: Role) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(android.graphics.Color.parseColor(role.color))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    role.name.firstOrNull()?.toString() ?: "M",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
        Spacer(Modifier.height(22.dp))
        Text(
            "今天想改什么？",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "描述代码、错误或需求，让 ${role.name} 帮你分析并给出可执行修改方案。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 21.sp
        )
        Spacer(Modifier.height(24.dp))
        WelcomeHint(Icons.Default.Code, "分析代码与定位问题")
        Spacer(Modifier.height(10.dp))
        WelcomeHint(Icons.Default.Difference, "整理修改方案和代码差异")
        Spacer(Modifier.height(10.dp))
        WelcomeHint(Icons.Default.Rule, "检查风险、边界和遗漏")
        Spacer(Modifier.height(22.dp))
        Text(
            "本地端不提供构建、打包、部署或发布操作。",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WelcomeHint(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ComposerArea(
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
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 760.dp),
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
                    placeholder = {
                        Text(
                            "向 MiMo Code 描述要完成的任务",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    minLines = 1,
                    maxLines = 6
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 6.dp, end = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "本地会话",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        model.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
                                imageVector = if (isStreaming) Icons.Default.Stop else Icons.Default.ArrowUpward,
                                contentDescription = if (isStreaming) "停止生成" else "发送",
                                modifier = Modifier.size(if (isStreaming) 15.dp else 18.dp),
                                tint = when {
                                    isStreaming -> MaterialTheme.colorScheme.surface
                                    input.isNotBlank() -> MaterialTheme.colorScheme.onPrimary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            "本地端不执行打包、部署或发布操作",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f)
        )
    }
}
