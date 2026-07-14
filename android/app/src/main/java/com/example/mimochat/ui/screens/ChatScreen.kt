package com.example.mimochat.ui.screens

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mimochat.data.*
import com.example.mimochat.theme.*
import com.example.mimochat.ui.util.isNearBottomForAutoScroll
import com.example.mimochat.ui.util.shouldShowScrollToBottomButton
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    messages: List<Message>,
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

    // 是否在底部附近（reverseLayout 下 index 0=最新消息）
    var isNearBottom by remember { mutableStateOf(true) }

    // 监听滚动位置变化，实时更新 isNearBottom
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { index ->
                isNearBottom = isNearBottomForAutoScroll(index)
            }
    }

    // 仅在用户处于底部附近时自动跟随新内容
    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
        if (isNearBottom && messages.isNotEmpty()) {
            listState.scrollToItem(0) // 瞬移，不用动画，避免流式更新时反复执行昂贵的滚动动画
        }
    }

    val showScrollButton by remember {
        derivedStateOf { shouldShowScrollToBottomButton(listState.firstVisibleItemIndex) }
    }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ChatTopBar(role = role, model = model, onMenu = onMenu, onNew = onNew, onModel = onModel)

        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 17.dp),
                contentPadding = PaddingValues(bottom = 100.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                reverseLayout = true
            ) {
                if (messages.isEmpty()) item { RoleWelcome(role = role) }
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
                FloatingActionButton(
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).size(40.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) { Icon(Icons.Default.KeyboardArrowDown, "回到底部", modifier = Modifier.size(20.dp)) }
            }
        }

        if (!hasApiKey) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("请先在设置中配置 API Key", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }

        ComposerArea(input = input, isStreaming = isStreaming, onInput = onInput, onSend = onSend, onStop = onStop)
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
    var showMenu by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf(message.text) }
    val isUser = message.role == MessageRole.USER
    val isFailed = message.status == MessageStatus.FAILED
    val isStopped = message.status == MessageStatus.STOPPED
    val isStreamingMsg = message.status == MessageStatus.STREAMING
    val isPending = message.status == MessageStatus.PENDING

    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        SelectionContainer {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 19.dp, topEnd = 19.dp,
                    bottomStart = if (isUser) 19.dp else 6.dp,
                    bottomEnd = if (isUser) 6.dp else 19.dp
                ),
                color = when {
                    isUser -> UserMessageBackground
                    isFailed -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    else -> Color.Transparent
                },
                // 修复：所有消息都可以点击打开菜单
                modifier = Modifier.clickable { showMenu = !showMenu }
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
                            TextButton(onClick = { isEditing = false; editText = message.text }) { Text("取消") }
                        }
                    }
                } else {
                    Column {
                        if (isUser) {
                            Text(
                                text = message.text,
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                                fontSize = 14.sp, lineHeight = 22.sp,
                                color = UserMessageText
                            )
                        } else {
                            // 助手消息使用 Markdown 渲染
                            MarkdownText(
                                text = when {
                                    message.text.isEmpty() && isStreamingMsg -> "正在思考…"
                                    message.text.isEmpty() && isPending -> "正在连接…"
                                    isFailed && message.text.isEmpty() -> "请求失败"
                                    else -> message.text
                                },
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp)
                            )
                            // 代码块复制按钮 - 当消息包含代码块时显示
                            if (!isUser && message.text.contains("```") && message.text.isNotBlank()) {
                                TextButton(
                                    onClick = { onCopy() },
                                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(12.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("复制代码", fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 流式进度
        if (isStreamingMsg && message.text.isNotEmpty()) {
            LinearProgressIndicator(
                modifier = Modifier.width(40.dp).height(2.dp).padding(top = 2.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }

        // 错误信息
        if (isFailed && message.errorMessage != null) {
            Text(message.errorMessage, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 2.dp, start = 4.dp))
        }

        // 停止标识
        if (isStopped && message.text.isNotBlank()) {
            Text("已停止生成", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp, start = 4.dp))
        }

        // 操作菜单
        if (showMenu && !isStreamingMsg && !isPending) {
            Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isUser) {
                    SmallButton(onClick = { onCopy(); showMenu = false }, icon = Icons.Default.ContentCopy, label = "复制")
                    SmallButton(onClick = { editText = message.text; isEditing = true; showMenu = false }, icon = Icons.Default.Edit, label = "编辑")
                } else {
                    if (message.text.isNotBlank()) {
                        SmallButton(onClick = { onCopy(); showMenu = false }, icon = Icons.Default.ContentCopy, label = "复制")
                        SmallButton(onClick = { onRegenerate(); showMenu = false }, icon = Icons.Default.AutoFixHigh, label = "重新生成")
                    }
                    if (isFailed) {
                        SmallButton(onClick = { onRetry(); showMenu = false }, icon = Icons.Default.Refresh, label = "重试")
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallButton(onClick: () -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp), modifier = Modifier.height(32.dp)) {
        Icon(icon, null, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp)
    }
}

/**
 * Markdown 文本渲染 - 纯文本版本
 */
@Composable
private fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun ChatTopBar(role: Role, model: ModelId, onMenu: () -> Unit, onNew: () -> Unit, onModel: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, "打开历史记录", tint = MaterialTheme.colorScheme.onSurface) }
        Spacer(Modifier.width(8.dp))
        Row(modifier = Modifier.weight(1f).clickable(onClick = onModel), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(35.dp).clip(RoundedCornerShape(13.dp)).background(Color(android.graphics.Color.parseColor(role.color))), contentAlignment = Alignment.Center) {
                Text(role.name.first().toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(role.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(model.displayName, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        }
        IconButton(onClick = onNew) { Icon(Icons.Default.Chat, "新建对话", tint = MaterialTheme.colorScheme.onSurface) }
    }
}

@Composable
private fun RoleWelcome(role: Role) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(78.dp).clip(RoundedCornerShape(29.dp)).background(Color(android.graphics.Color.parseColor(role.color))), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Star, null, tint = Color.White, modifier = Modifier.size(31.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("和 ${role.name} 聊聊", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Text(role.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ComposerArea(input: String, isStreaming: Boolean, onInput: (String) -> Unit, onSend: () -> Unit, onStop: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), shape = RoundedCornerShape(21.dp), color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(modifier = Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = input, onValueChange = onInput, modifier = Modifier.weight(1f), placeholder = { Text("发消息…", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent), maxLines = 6)
            if (isStreaming) {
                IconButton(onClick = onStop, modifier = Modifier.size(38.dp)) { Icon(Icons.Default.Stop, "停止生成", tint = MaterialTheme.colorScheme.error) }
            } else {
                IconButton(onClick = onSend, enabled = input.isNotBlank(), modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Default.Send, "发送", tint = if (input.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp).background(if (input.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, CircleShape).padding(4.dp))
                }
            }
        }
    }
}
