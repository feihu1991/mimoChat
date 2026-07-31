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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mimochat.data.VoiceChatState

/** 聊天页底部输入区：文字/语音输入切换、发送/停止按钮。 */
@Composable
internal fun ComposerArea(
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
