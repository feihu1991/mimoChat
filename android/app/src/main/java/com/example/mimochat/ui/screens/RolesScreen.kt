package com.example.mimochat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.mimochat.data.DEFAULT_ROLES
import com.example.mimochat.data.Role
import com.example.mimochat.data.VoiceModel
import com.example.mimochat.ui.voice.VoiceDesignState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RolesScreen(
    roles: List<Role>,
    defaultRoleId: String,
    voiceDesignState: VoiceDesignState,
    onBack: () -> Unit,
    onSave: (List<Role>) -> Unit,
    onDefault: (String) -> Unit,
    onGenerateVoiceCandidates: (String, String) -> Unit,
    onPreviewVoiceCandidate: (String) -> Unit,
    onSelectVoiceCandidate: (String, String) -> Unit
) {
    var editingId by remember { mutableStateOf(roles.firstOrNull()?.id ?: "") }
    val role = roles.find { it.id == editingId } ?: roles.firstOrNull() ?: DEFAULT_ROLES[0]
    val activeDesignState = voiceDesignState.takeIf { it.roleId == role.id }

    fun updateRole(patch: (Role) -> Role) {
        onSave(roles.map { if (it.id == role.id) patch(it) else it })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("聊天角色") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
            actions = {
                TextButton(onClick = {
                    val newRole = DEFAULT_ROLES[0].copy(id = "role-${System.currentTimeMillis()}", name = "新角色")
                    onSave(roles + newRole)
                    editingId = newRole.id
                }) { Text("新增") }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                roles.forEach { r ->
                    val isSelected = r.id == role.id
                    Surface(
                        onClick = { editingId = r.id },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(24.dp).clip(RoundedCornerShape(8.dp))
                                    .background(Color(android.graphics.Color.parseColor(r.color))),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(r.name.firstOrNull()?.toString() ?: "?", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(r.name, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
                            if (r.id == defaultRoleId) {
                                Spacer(Modifier.width(4.dp))
                                Text("默认", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            SettingsField("角色名称", role.name) { updateRole { r -> r.copy(name = it) } }
            SettingsField("角色介绍", role.description) { updateRole { r -> r.copy(description = it) } }
            SettingsField("能力设定", role.capabilities) { updateRole { r -> r.copy(capabilities = it) } }
            SettingsField("角色提示词", role.prompt, maxLines = 6) { updateRole { r -> r.copy(prompt = it) } }
            SettingsField(
                "音色提示词",
                role.voicePrompt ?: "",
                maxLines = 4,
                placeholder = "例如：青年女性，声音温暖清亮，语速适中，吐字自然，像可靠的朋友在面对面聊天。"
            ) {
                updateRole { r -> r.copy(voicePrompt = it) }
            }

            Text("聊天音色", fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VoiceModel.entries.forEach { vm ->
                    val isSelected = role.voiceModel == vm
                    Surface(
                        onClick = { updateRole { r -> r.copy(voiceModel = vm) } },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                if (vm == VoiceModel.MIMO_V2_5_TTS) "内置 TTS" else "声音克隆",
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                            Text(
                                if (vm == VoiceModel.MIMO_V2_5_TTS) "固定预置音色" else "固定同一段音色样本",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (role.voiceModel == VoiceModel.MIMO_V2_5_TTS_VOICECLONE) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (role.voiceSample.isNullOrBlank()) {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (role.voiceSample.isNullOrBlank()) Icons.Default.Warning else Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (role.voiceSample.isNullOrBlank()) {
                                "尚未固定音色样本，请在下方生成并选择一个方案。"
                            } else {
                                "已固定专属音色。后续不同消息会使用同一段样本进行声音克隆。"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text("设计专属音色", fontWeight = FontWeight.SemiBold)
            Text(
                "每次会调用声音设计模型生成 3 个不同试听方案。选择一个后，App 会保存该音频并自动切换到声音克隆模型。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { onGenerateVoiceCandidates(role.id, role.voicePrompt.orEmpty()) },
                enabled = activeDesignState?.isGenerating != true && !role.voicePrompt.isNullOrBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (activeDesignState?.isGenerating == true) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("正在生成 ${activeDesignState.candidates.size}/3")
                } else {
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (activeDesignState?.candidates.isNullOrEmpty()) "生成 3 个试听音色" else "重新生成 3 个方案")
                }
            }

            activeDesignState?.error?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            activeDesignState?.candidates?.forEach { candidate ->
                val isPreviewing = activeDesignState.previewingCandidateId == candidate.id
                val isSelected = activeDesignState.selectedCandidateId == candidate.id
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(candidate.label, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (isSelected) "已设为当前角色音色" else "播放试听后选择",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { onPreviewVoiceCandidate(candidate.id) }) {
                            Icon(
                                if (isPreviewing) Icons.Default.Stop else Icons.Default.PlayArrow,
                                null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (isPreviewing) "停止" else "试听")
                        }
                        Button(
                            onClick = { onSelectVoiceCandidate(role.id, candidate.id) },
                            enabled = !isSelected
                        ) {
                            if (isSelected) {
                                Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("已选择")
                            } else {
                                Text("使用")
                            }
                        }
                    }
                }
            }

            if (role.id != defaultRoleId) {
                OutlinedButton(
                    onClick = { onDefault(role.id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Star, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("设为默认角色")
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("当前默认角色", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            if (roles.size > 1) {
                OutlinedButton(
                    onClick = {
                        val next = roles.filter { it.id != role.id }
                        onSave(next)
                        editingId = next.first().id
                        if (defaultRoleId == role.id) onDefault(next.first().id)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("删除角色")
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsField(
    label: String,
    value: String,
    maxLines: Int = 1,
    placeholder: String = "",
    onChange: (String) -> Unit
) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            maxLines = maxLines,
            placeholder = { if (placeholder.isNotBlank()) Text(placeholder) }
        )
    }
}
