package com.example.mimochat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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

@Composable
fun ChatScreen(
    conversation: Conversation,
    role: Role,
    model: ModelId,
    input: String,
    attachments: List<Attachment>,
    thinking: Boolean,
    recording: Boolean,
    voiceStatus: String,
    playingMessageId: String?,
    onMenu: () -> Unit,
    onNew: () -> Unit,
    onModel: () -> Unit,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onVoice: () -> Unit,
    onAttachment: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onSpeak: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top bar
        ChatTopBar(
            role = role,
            model = model,
            onMenu = onMenu,
            onNew = onNew,
            onModel = onModel
        )

        // Messages
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 17.dp),
            contentPadding = PaddingValues(bottom = 165.dp)
        ) {
            if (conversation.messages.isEmpty()) {
                item {
                    RoleWelcome(role = role)
                }
            }

            items(conversation.messages) { message ->
                ChatMessage(
                    message = message,
                    playingMessageId = playingMessageId,
                    onSpeak = onSpeak
                )
            }

            if (thinking) {
                item {
                    ThinkingRow(model = model)
                }
            }
        }

        // Composer
        ComposerArea(
            input = input,
            attachments = attachments,
            thinking = thinking,
            recording = recording,
            voiceStatus = voiceStatus,
            model = model,
            role = role,
            onInput = onInput,
            onSend = onSend,
            onVoice = onVoice,
            onAttachment = onAttachment,
            onRemoveAttachment = onRemoveAttachment
        )
    }
}

@Composable
private fun ChatTopBar(
    role: Role,
    model: ModelId,
    onMenu: () -> Unit,
    onNew: () -> Unit,
    onModel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onMenu) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "打开历史记录",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onModel),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(35.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Color(android.graphics.Color.parseColor(role.color))),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = role.name.first().toString(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column {
                Text(
                    text = role.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (model == ModelId.MIMO_V2_5) "MiMo v2.5 · 多模态" else "MiMo v2.5 Pro · 深度推理",
                    fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
        }

        IconButton(onClick = onNew) {
            Icon(
                imageVector = Icons.Default.Chat,
                contentDescription = "新建对话",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun RoleWelcome(role: Role) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(78.dp)
                .clip(RoundedCornerShape(29.dp))
                .background(Color(android.graphics.Color.parseColor(role.color))),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(31.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "和 ${role.name} 聊聊",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(5.dp))

        Text(
            text = role.description,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AbilityChip(text = role.capabilities)
            AbilityChip(text = if (role.voiceModel == VoiceModel.MIMO_V2_5_TTS_VOICECLONE) "克隆音色" else "自然语音")
        }
    }
}

@Composable
private fun AbilityChip(text: String) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = MaterialTheme.colorScheme.secondary
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            fontSize = 8.sp,
            color = MaterialTheme.colorScheme.onSecondary
        )
    }
}

@Composable
private fun ChatMessage(
    message: Message,
    playingMessageId: String?,
    onSpeak: (String, String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 18.dp),
        horizontalAlignment = if (message.role == MessageRole.USER) Alignment.End else Alignment.Start
    ) {
        // Attachments
        if (message.attachments.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                message.attachments.take(2).forEach { attachment ->
                    if (attachment.url != null) {
                        // Image placeholder
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .background(MaterialTheme.colorScheme.secondary)
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondary
                        ) {
                            Row(
                                modifier = Modifier.padding(9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AttachFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSecondary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = attachment.name,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSecondary
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Message bubble
        Surface(
            shape = RoundedCornerShape(
                topStart = 19.dp,
                topEnd = 19.dp,
                bottomStart = if (message.role == MessageRole.USER) 19.dp else 6.dp,
                bottomEnd = if (message.role == MessageRole.USER) 6.dp else 19.dp
            ),
            color = if (message.role == MessageRole.USER) UserMessageBackground else Color.Transparent
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(
                    horizontal = 13.dp,
                    vertical = 11.dp
                ),
                fontSize = 12.sp,
                lineHeight = 20.sp,
                color = if (message.role == MessageRole.USER) UserMessageText else MaterialTheme.colorScheme.onSurface
            )
        }

        // Tools for assistant messages
        if (message.role == MessageRole.ASSISTANT) {
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = { onSpeak(message.text, message.id) },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = if (playingMessageId == message.id) Icons.Default.Stop else Icons.Default.VolumeUp,
                        contentDescription = if (playingMessageId == message.id) "停止朗读" else "朗读",
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { /* TODO: Copy to clipboard */ },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "复制",
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ThinkingRow(model: ModelId) {
    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Text(
            text = if (model == ModelId.MIMO_V2_5_PRO) "Pro 正在思考" else "MiMo 正在回应",
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ComposerArea(
    input: String,
    attachments: List<Attachment>,
    thinking: Boolean,
    recording: Boolean,
    voiceStatus: String,
    model: ModelId,
    role: Role,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onVoice: () -> Unit,
    onAttachment: () -> Unit,
    onRemoveAttachment: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 18.dp)
    ) {
        // Voice status
        if (voiceStatus.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = VoiceStatusBackground
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = VoiceStatusText
                    )
                    Text(
                        text = voiceStatus,
                        fontSize = 9.sp,
                        color = VoiceStatusText
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Attachment chips
        if (attachments.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                attachments.forEach { attachment ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondary
                    ) {
                        Row(
                            modifier = Modifier.padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (attachment.url != null) {
                                Box(
                                    modifier = Modifier
                                        .size(27.dp)
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AttachFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSecondary
                                )
                            }
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = attachment.name,
                                fontSize = 8.sp,
                                color = MaterialTheme.colorScheme.onSecondary,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = { onRemoveAttachment(attachment.id) },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(11.dp),
                                    tint = MaterialTheme.colorScheme.onSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Composer
        Surface(
            shape = RoundedCornerShape(21.dp),
            color = if (recording) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface,
            border = if (!recording) ButtonDefaults.outlinedButtonBorder else null
        ) {
            if (recording) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onVoice,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                    // Record wave visualization
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(20) { index ->
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(if (index % 3 == 0) 21.dp else if (index % 4 == 0) 14.dp else 8.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            if (index < 19) Spacer(modifier = Modifier.width(3.dp))
                        }
                    }
                    Text(
                        text = "再次点击发送",
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onAttachment,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "添加附件",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    OutlinedTextField(
                        value = input,
                        onValueChange = onInput,
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                text = "发消息或按住说话",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        maxLines = 4
                    )

                    if (input.isNotBlank() || attachments.isNotEmpty()) {
                        IconButton(
                            onClick = onSend,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "发送",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary,
                                        CircleShape
                                    )
                                    .padding(4.dp)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onVoice,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "语音聊天",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Composer hint
        Text(
            text = "语音链路：ASR → ${if (model == ModelId.MIMO_V2_5_PRO) "v2.5 Pro" else "v2.5"} → ${if (role.voiceModel == VoiceModel.MIMO_V2_5_TTS_VOICECLONE) "克隆音色" else "TTS"}",
            modifier = Modifier.padding(start = 9.dp, top = 6.dp),
            fontSize = 8.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}