package com.example.mimochat.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mimochat.data.Message
import com.example.mimochat.data.MessageRole
import com.example.mimochat.data.MessageStatus
import com.example.mimochat.theme.UserMessageBackground
import com.example.mimochat.theme.UserMessageText

/** 单条消息气泡：用户消息（可编辑/复制）与助手消息（状态、操作菜单、语音播放）。 */
@Composable
internal fun MessageBubble(
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
