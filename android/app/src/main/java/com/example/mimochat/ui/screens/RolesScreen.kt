package com.example.mimochat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
fun RolesScreen(
    roles: List<Role>,
    defaultRoleId: String,
    onBack: () -> Unit,
    onSave: (List<Role>) -> Unit,
    onDefault: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var editingId by remember { mutableStateOf(roles.first().id) }
    val role = roles.find { it.id == editingId } ?: roles.first()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Page header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "返回"
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "聊天角色",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(
                onClick = {
                    val newRole = DEFAULT_ROLES.first().copy(
                        id = "role-${System.currentTimeMillis()}",
                        name = "新角色",
                        description = "自定义聊天角色",
                        color = "#6f766e"
                    )
                    onSave(roles + newRole)
                    editingId = newRole.id
                }
            ) {
                Text(
                    text = "新增",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Roles scroll
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(18.dp)
        ) {
            // Role tabs
            LazyRow(
                modifier = Modifier.padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                items(roles) { item ->
                    RoleTab(
                        role = item,
                        isActive = item.id == editingId,
                        isDefault = item.id == defaultRoleId,
                        onClick = { editingId = item.id }
                    )
                }
            }

            // Role editor
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.padding(14.dp)
                ) {
                    // Role name
                    RoleField(
                        label = "角色名称",
                        value = role.name,
                        onValueChange = { name ->
                            onSave(roles.map { if (it.id == role.id) it.copy(name = name) else it })
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    // Role description
                    RoleField(
                        label = "角色介绍",
                        value = role.description,
                        onValueChange = { description ->
                            onSave(roles.map { if (it.id == role.id) it.copy(description = description) else it })
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    // Capabilities
                    RoleField(
                        label = "能力设定",
                        value = role.capabilities,
                        onValueChange = { capabilities ->
                            onSave(roles.map { if (it.id == role.id) it.copy(capabilities = capabilities) else it })
                        },
                        multiline = true
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    // Prompt
                    RoleField(
                        label = "角色提示词",
                        value = role.prompt,
                        onValueChange = { prompt ->
                            onSave(roles.map { if (it.id == role.id) it.copy(prompt = prompt) else it })
                        },
                        multiline = true
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    // Voice prompt
                    RoleField(
                        label = "音色提示词",
                        value = role.voicePrompt ?: "",
                        onValueChange = { voicePrompt ->
                            onSave(roles.map { if (it.id == role.id) it.copy(voicePrompt = voicePrompt) else it })
                        },
                        multiline = true,
                        placeholder = "例如：温柔、清晰、语速偏慢，重点处稍微停顿。"
                    )

                    // Voice choice
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = "聊天音色",
                            fontSize = 8.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            VoiceChoiceButton(
                                label = "内置 TTS",
                                description = "自然音色",
                                isActive = role.voiceModel == VoiceModel.MIMO_V2_5_TTS,
                                onClick = {
                                    onSave(roles.map { if (it.id == role.id) it.copy(voiceModel = VoiceModel.MIMO_V2_5_TTS) else it })
                                },
                                modifier = Modifier.weight(1f)
                            )
                            VoiceChoiceButton(
                                label = "声音克隆",
                                description = if (role.voiceSample != null) "已录入样本" else "需要音频样本",
                                isActive = role.voiceModel == VoiceModel.MIMO_V2_5_TTS_VOICECLONE,
                                onClick = {
                                    onSave(roles.map { if (it.id == role.id) it.copy(voiceModel = VoiceModel.MIMO_V2_5_TTS_VOICECLONE) else it })
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Voice preview button
                    Button(
                        onClick = { /* TODO: Preview voice */ },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "试听音色",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Upload voice button
                    Button(
                        onClick = { /* TODO: Upload voice */ },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Upload,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSecondary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "上传声音样本",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Set default button
                    Button(
                        onClick = { onDefault(role.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        shape = RoundedCornerShape(13.dp),
                        enabled = role.id != defaultRoleId
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (role.id == defaultRoleId) "当前默认" else "设为默认",
                            fontSize = 9.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Delete button
                    if (roles.size > 1) {
                        Button(
                            onClick = {
                                val newRoles = roles.filter { it.id != role.id }
                                onSave(newRoles)
                                editingId = newRoles.first().id
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                            shape = RoundedCornerShape(13.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "删除角色",
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleTab(
    role: Role,
    isActive: Boolean,
    isDefault: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(74.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (isActive) MaterialTheme.colorScheme.secondary else Color.Transparent
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(android.graphics.Color.parseColor(role.color))),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = role.name.first().toString(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = role.name,
                fontSize = 9.sp
            )

            if (isDefault) {
                Spacer(modifier = Modifier.height(2.dp))
                Surface(
                    shape = RoundedCornerShape(5.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = "默认",
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        fontSize = 6.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun RoleField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    multiline: Boolean = false,
    placeholder: String? = null
) {
    Column(
        modifier = Modifier.padding(vertical = 11.dp)
    ) {
        Text(
            text = label,
            fontSize = 8.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = placeholder?.let {
                {
                    Text(
                        text = it,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent
            ),
            maxLines = if (multiline) 4 else 1
        )
    }
}

@Composable
private fun VoiceChoiceButton(
    label: String,
    description: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.secondary
    ) {
        Column(
            modifier = Modifier.padding(9.dp)
        ) {
            Text(
                text = label,
                fontSize = 9.sp,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                fontSize = 7.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}