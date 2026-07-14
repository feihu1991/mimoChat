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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.mimochat.data.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    connection: MimoConnection,
    theme: ThemeMode,
    roleCount: Int,
    onBack: () -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onOpenRoles: () -> Unit,
    onOpenConnection: () -> Unit,
    onSaveConnection: (MimoConnection) -> Unit,
    onClearApiKey: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showApiKeyDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Header
        TopAppBar(
            title = { Text("设置", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // Private mode note
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("私人模式", fontWeight = FontWeight.SemiBold, fontSize = MaterialTheme.typography.bodyLarge.fontSize)
                        Text("无需登录，数据仅保存在本机", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Chat section
            SectionHeader("聊天")
            SettingsCard {
                SettingsRow(
                    icon = Icons.Default.Chat,
                    title = "聊天角色",
                    detail = "${roleCount} 个角色",
                    onClick = onOpenRoles
                )
                SettingsRow(
                    icon = Icons.Default.Cloud,
                    title = "模型服务",
                    detail = if (connection.apiKey.isNotBlank()) "已配置" else "未配置",
                    onClick = onOpenConnection
                )
            }

            Spacer(Modifier.height(16.dp))

            // API Key section
            SectionHeader("安全")
            SettingsCard {
                SettingsRow(
                    icon = Icons.Default.Key,
                    title = "API Key",
                    detail = if (connection.apiKey.isNotBlank()) "••••••••" else "未设置",
                    onClick = { showApiKeyDialog = true }
                )
            }

            Spacer(Modifier.height(16.dp))

            // Theme section
            SectionHeader("外观")
            SettingsCard {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Palette, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("主题", modifier = Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ThemeChip("浅色", theme == ThemeMode.LIGHT) { onThemeChange(ThemeMode.LIGHT) }
                        ThemeChip("深色", theme == ThemeMode.DARK) { onThemeChange(ThemeMode.DARK) }
                        ThemeChip("系统", theme == ThemeMode.SYSTEM) { onThemeChange(ThemeMode.SYSTEM) }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // About
            SectionHeader("关于")
            SettingsCard {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("MiMo Chat", fontWeight = FontWeight.Medium)
                        Text("本地私人版本 · 1.0.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // API Key Dialog
    if (showApiKeyDialog) {
        var keyInput by remember { mutableStateOf(connection.apiKey) }
        var showKey by remember { mutableStateOf(false) }
        var urlInput by remember { mutableStateOf(connection.baseUrl) }

        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = { Text("模型服务配置") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it.trim() },
                        label = { Text("API 地址") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("https://api.xiaomimimo.com/v1") }
                    )
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it.trim() },
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(
                                    if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        }
                    )
                    if (connection.apiKey.isNotBlank()) {
                        TextButton(
                            onClick = {
                                onClearApiKey()
                                keyInput = ""
                                showApiKeyDialog = false
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("清除 API Key")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val baseUrl = urlInput.ifBlank { "https://api.xiaomimimo.com/v1" }
                    val finalUrl = if (baseUrl.startsWith("http")) baseUrl else "https://$baseUrl"
                    onSaveConnection(MimoConnection(
                        baseUrl = finalUrl,
                        apiKey = keyInput,
                        authMode = connection.authMode
                    ))
                    showApiKeyDialog = false
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ThemeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = MaterialTheme.typography.bodySmall.fontSize,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
