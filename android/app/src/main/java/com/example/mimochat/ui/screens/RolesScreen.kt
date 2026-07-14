package com.example.mimochat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RolesScreen(
    roles: List<Role>,
    defaultRoleId: String,
    onBack: () -> Unit,
    onSave: (List<Role>) -> Unit,
    onDefault: (String) -> Unit
) {
    var editingId by remember { mutableStateOf(roles.firstOrNull()?.id ?: "") }
    val role = roles.find { it.id == editingId } ?: roles.firstOrNull() ?: DEFAULT_ROLES[0]

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
            // Role tabs
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
                                Text(r.name.first().toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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

            // Editor
            SettingsField("角色名称", role.name) { updateRole { r -> r.copy(name = it) } }
            SettingsField("角色介绍", role.description) { updateRole { r -> r.copy(description = it) } }
            SettingsField("能力设定", role.capabilities) { updateRole { r -> r.copy(capabilities = it) } }
            SettingsField("角色提示词", role.prompt, maxLines = 6) { updateRole { r -> r.copy(prompt = it) } }
            SettingsField("音色提示词", role.voicePrompt ?: "", maxLines = 3, placeholder = "例如：温柔、清晰、语速偏慢") {
                updateRole { r -> r.copy(voicePrompt = it) }
            }

            // Voice model selection
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
                                if (vm == VoiceModel.MIMO_V2_5_TTS) "自然音色" else "需要音频样本",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Set default
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

            // Delete
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
