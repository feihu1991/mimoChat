package com.example.mimochat.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable
private fun MessageBubble(
    message: Message,
    onRetry: () -> Unit,
    onRegenerate: () -> Unit,
    onCopy: () -> Unit,
    onEdit: (String) -> Unit,
    onSpeak: () -> Unit,
    isSpeaking: Boolean
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
                    "MiMo",
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

            if (message.text.isNotBlank()) {
                SmallButton(
                    onClick = onSpeak,
                    icon = if (isSpeaking) Icons.Default.Pause else Icons.Default.PlayArrow,
                    label = if (isSpeaking) "暂停播放" else "播放语音"
                )
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(8.dp))
        if (text == "生成中" || text == "连接中") {
            LoadingIcon(
                Icons.Default.AutoAwesome,
                contentDescription = "等待模型返回",
                modifier = Modifier.size(13.dp),
                tint = color,
                alternateImageVector = Icons.Default.HourglassTop
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
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
    val colors = MaterialTheme.colorScheme
    val rendered = remember(text, colors.onSurface, colors.surfaceVariant) {
        markdownAnnotatedString(text, colors.onSurface, colors.surfaceVariant)
    }
    Text(
        text = rendered,
        modifier = modifier,
        fontSize = 14.sp,
        lineHeight = 23.sp,
        color = MaterialTheme.colorScheme.onSurface
    )
}

private fun markdownAnnotatedString(text: String, textColor: Color, codeBackground: Color): AnnotatedString =
    buildAnnotatedString {
        val lines = text.split('\n')
        var inCode = false
        lines.forEachIndexed { index, rawLine ->
            val line = rawLine.trimEnd()
            if (line.trimStart().startsWith("```")) {
                inCode = !inCode
            } else if (inCode) {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground, color = textColor)) { append(line) }
            } else {
                val heading = Regex("^#{1,6}\\s+").find(line)
                if (heading != null) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = textColor)) { append(line.removeRange(heading.range)) }
                } else if (line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ")) {
                    append("• ")
                    appendInlineMarkdown(line.trimStart().drop(2), textColor, codeBackground)
                } else {
                    appendInlineMarkdown(line, textColor, codeBackground)
                }
            }
            if (index < lines.lastIndex) append('\n')
        }
    }

private fun AnnotatedString.Builder.appendInlineMarkdown(value: String, textColor: Color, codeBackground: Color) {
    var index = 0
    while (index < value.length) {
        when {
            value.startsWith("**", index) || value.startsWith("__", index) -> {
                val marker = value.substring(index, index + 2)
                val end = value.indexOf(marker, index + 2)
                if (end > index + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = textColor)) { append(value.substring(index + 2, end)) }
                    index = end + 2
                } else { append(value[index]); index++ }
            }
            value[index] == '`' -> {
                val end = value.indexOf('`', index + 1)
                if (end > index + 1) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground, color = textColor)) { append(value.substring(index + 1, end)) }
                    index = end + 1
                } else { append(value[index]); index++ }
            }
            value[index] == '*' || value[index] == '_' -> {
                val marker = value[index]
                val end = value.indexOf(marker, index + 1)
                if (end > index + 1) {
                    withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = textColor)) { append(value.substring(index + 1, end)) }
                    index = end + 1
                } else { append(value[index]); index++ }
            }
            else -> { append(value[index]); index++ }
        }
    }
}

@Composable
private fun ChatTopBar(
    conversationTitle: String,
    role: Role,
    model: ModelId,
    onMenu: () -> Unit,
    onNew: () -> Unit,
    onRole: () -> Unit,
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
                .height(76.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenu) {
                Icon(Icons.Default.Menu, contentDescription = "打开会话列表")
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "MiMo",
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
                Spacer(Modifier.height(7.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onRole,
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.62f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(role.color)))
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(role.name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Default.ExpandMore, null, modifier = Modifier.size(15.dp))
                        }
                    }
                    Surface(
                        onClick = onModel,
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(model.displayName, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Default.ExpandMore, null, modifier = Modifier.size(15.dp))
                        }
                    }
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
            "今天想聊点什么？",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "和 ${role.name} 分享问题、想法或计划，它会像聊天一样陪你梳理和解决。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 21.sp
        )
        Spacer(Modifier.height(24.dp))
        WelcomeHint(Icons.Default.Forum, "自然对话与问题梳理")
        Spacer(Modifier.height(10.dp))
        WelcomeHint(Icons.Default.Lightbulb, "一起思考、解释和规划")
        Spacer(Modifier.height(10.dp))
        WelcomeHint(Icons.Default.Code, "需要时再帮你处理代码")
        Spacer(Modifier.height(22.dp))
        Text(
            "普通聊天不会自动读取或修改代码；你明确提出时才会使用 GitHub。",
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
    isStreaming: Boolean,
    voiceState: VoiceChatState,
    voiceInputMode: Boolean,
    onToggleVoiceInput: () -> Unit,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onVoiceStart: () -> Unit,
    onVoiceStop: () -> Unit,
    onVoiceCancel: () -> Unit
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
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f))
                        .clickable(
                            enabled = !isStreaming && voiceState != VoiceChatState.LISTENING,
                            onClick = onToggleVoiceInput
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = voiceInputMode,
                        transitionSpec = { (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut()) },
                        label = "voice-keyboard-toggle"
                    ) { voiceMode ->
                        Icon(
                            imageVector = if (voiceMode) Icons.Default.Keyboard else Icons.Default.Mic,
                            contentDescription = if (voiceMode) "切换文字输入" else "切换语音输入",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                if (voiceInputMode) {
                    VoiceInputSurface(
                        voiceState = voiceState,
                        enabled = !isStreaming &&
                            voiceState != VoiceChatState.TRANSCRIBING &&
                            voiceState != VoiceChatState.THINKING,
                        onToggle = onToggleVoiceInput,
                        onVoiceStart = onVoiceStart,
                        onVoiceStop = onVoiceStop,
                        onVoiceCancel = onVoiceCancel,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    OutlinedTextField(
                        value = input,
                        onValueChange = onInput,
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                "和 MiMo 聊聊，遇到代码问题时直接告诉它",
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
                }

                Spacer(Modifier.width(8.dp))

                Surface(
                    onClick = if (isStreaming) onStop else onSend,
                    enabled = isStreaming || input.isNotBlank(),
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    color = when {
                        isStreaming -> MaterialTheme.colorScheme.onSurface
                        input.isNotBlank() -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AnimatedContent(
                            targetState = isStreaming,
                            transitionSpec = { (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut()) },
                            label = "send-stop-icon"
                        ) { waiting ->
                            Icon(
                                imageVector = if (waiting) Icons.Default.Stop else Icons.Default.ArrowUpward,
                                contentDescription = if (waiting) "停止生成" else "发送",
                                modifier = Modifier.size(19.dp),
                                tint = if (waiting || input.isNotBlank()) {
                                    if (waiting) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onPrimary
                                } else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceInputSurface(
    voiceState: VoiceChatState,
    enabled: Boolean,
    onToggle: () -> Unit,
    onVoiceStart: () -> Unit,
    onVoiceStop: () -> Unit,
    onVoiceCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .heightIn(min = 42.dp, max = 132.dp)
            .voicePressGesture(
                enabled = enabled,
                onTap = onToggle,
                onLongPress = onVoiceStart,
                onRelease = onVoiceStop,
                onCancel = onVoiceCancel
            ),
        shape = RoundedCornerShape(18.dp),
        color = if (voiceState == VoiceChatState.LISTENING) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedContent(
                targetState = voiceState == VoiceChatState.LISTENING,
                transitionSpec = { (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut()) },
                label = "voice-record-state"
            ) { recording ->
                Icon(
                    imageVector = if (recording) Icons.Default.StopCircle else Icons.Default.RecordVoiceOver,
                    contentDescription = if (recording) "松开结束录音" else "按住说话",
                    tint = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = when (voiceState) {
                    VoiceChatState.LISTENING -> "松开结束"
                    VoiceChatState.TRANSCRIBING -> "正在识别语音…"
                    VoiceChatState.THINKING -> "正在理解语音…"
                    else -> "按住说话"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun Modifier.voicePressGesture(
    enabled: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onRelease: () -> Unit,
    onCancel: () -> Unit
): Modifier = if (!enabled) this else pointerInput(Unit) {
    var longPressed = false
    detectTapGestures(
        onLongPress = {
            longPressed = true
            onLongPress()
        },
        onPress = {
            longPressed = false
            val released = tryAwaitRelease()
            if (longPressed) {
                if (released) onRelease() else onCancel()
            }
        },
        onTap = {
            if (!longPressed) onTap()
        }
    )
}
