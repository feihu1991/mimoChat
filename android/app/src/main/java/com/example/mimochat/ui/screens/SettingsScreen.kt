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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.mimochat.BuildConfig
import com.example.mimochat.core.workspace.GitHubWorkspaceConfig
import com.example.mimochat.core.workspace.WorkspaceSyncState
import com.example.mimochat.data.*
import com.example.mimochat.data.update.AppUpdateManager
import com.example.mimochat.data.update.AppUpdateState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    connection: MimoConnection,
    workspaceConfig: GitHubWorkspaceConfig,
    workspaceState: WorkspaceSyncState,
    theme: ThemeMode,
    roleCount: Int,
    onBack: () -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onOpenRoles: () -> Unit,
    onOpenConnection: () -> Unit,
    onSaveConnection: (MimoConnection) -> Unit,
    onClearApiKey: () -> Unit,
    onSaveWorkspace: (GitHubWorkspaceConfig) -> Unit,
    onSyncWorkspace: (GitHubWorkspaceConfig) -> Unit,
    onClearGitHubToken: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showWorkspaceDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val updateManager = remember(context) { AppUpdateManager.getInstance(context) }
    val updateScope = rememberCoroutineScope()
    val updateState by updateManager.state.collectAsState()

    fun checkForUpdate() {
        if (updateState is AppUpdateState.Checking || updateState is AppUpdateState.Downloading) return
        updateScope.launch { updateManager.checkForUpdate(BuildConfig.VERSION_NAME) }
    }

    fun handleUpdateClick() {
        when (updateState) {
            is AppUpdateState.Available -> updateManager.downloadAndInstall()
            is AppUpdateState.Downloading,
            AppUpdateState.Checking -> Unit
            else -> checkForUpdate()
        }
    }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
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
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("本地 Agent 模式", fontWeight = FontWeight.SemiBold)
                        Text(
                            "对话和项目副本保存在本机；文件与 Git 操作必须经过结构化工具和确认",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            SectionHeader("聊天")
            SettingsCard {
                SettingsRow(
                    icon = Icons.Default.Chat,
                    title = "聊天角色",
                    detail = "$roleCount 个角色",
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

            SectionHeader("代码工作区")
            SettingsCard {
                SettingsRow(
                    icon = Icons.Default.FolderOpen,
                    title = "GitHub 工作区",
                    detail = workspaceDetail(workspaceConfig, workspaceState),
                    onClick = { showWorkspaceDialog = true }
                )
                if (workspaceState is WorkspaceSyncState.Syncing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            Spacer(Modifier.height(16.dp))

            SectionHeader("安全")
            SettingsCard {
                SettingsRow(
                    icon = Icons.Default.Key,
                    title = "MiMo API Key",
                    detail = if (connection.apiKey.isNotBlank()) "••••••••" else "未设置",
                    onClick = { showApiKeyDialog = true }
                )
                SettingsRow(
                    icon = Icons.Default.Hub,
                    title = "GitHub Token",
                    detail = if (workspaceConfig.token.isNotBlank()) "已加密保存" else "未设置",
                    onClick = { showWorkspaceDialog = true }
                )
            }

            Spacer(Modifier.height(16.dp))

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

            SectionHeader("关于")
            SettingsCard {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("MiMo Code", fontWeight = FontWeight.Medium)
                        Text(
                            "Android 本地 Agent · ${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider()
                SettingsRow(
                    icon = Icons.Default.Download,
                    title = "软件更新",
                    detail = updateDetail(updateState),
                    onClick = ::handleUpdateClick
                )
                if (updateState is AppUpdateState.Checking || updateState is AppUpdateState.Downloading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                val available = updateState as? AppUpdateState.Available
                if (available != null && available.release.releaseNotes.isNotBlank()) {
                    Text(
                        text = available.release.releaseNotes.take(400),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 56.dp, end = 16.dp, bottom = 16.dp)
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

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
                                Icon(if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
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
                        ) { Text("清除 API Key") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val baseUrl = urlInput.ifBlank { "https://api.xiaomimimo.com/v1" }
                    onSaveConnection(
                        MimoConnection(
                            baseUrl = if (baseUrl.startsWith("http")) baseUrl else "https://$baseUrl",
                            apiKey = keyInput,
                            authMode = connection.authMode
                        )
                    )
                    showApiKeyDialog = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) { Text("取消") }
            }
        )
    }

    if (showWorkspaceDialog) {
        var repository by remember { mutableStateOf(workspaceConfig.repository) }
        var baseBranch by remember { mutableStateOf(workspaceConfig.baseBranch) }
        var token by remember { mutableStateOf(workspaceConfig.token) }
        var showToken by remember { mutableStateOf(false) }

        fun formConfig() = GitHubWorkspaceConfig(
            repository = repository.trim(),
            baseBranch = baseBranch.trim().ifBlank { "master" },
            token = token.trim(),
            workingBranch = workspaceConfig.workingBranch
        )

        AlertDialog(
            onDismissRequest = { showWorkspaceDialog = false },
            title = { Text("GitHub 工作区") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "项目会下载到 App 私有目录。Token 只由本地 GitHub 客户端使用，不会发送给 MiMo。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = repository,
                        onValueChange = { repository = it.trim() },
                        label = { Text("仓库") },
                        placeholder = { Text("owner/repository") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = baseBranch,
                        onValueChange = { baseBranch = it.trim() },
                        label = { Text("基础分支") },
                        placeholder = { Text("master") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it.trim() },
                        label = { Text("Fine-grained Personal Access Token") },
                        supportingText = { Text("需要目标仓库 Contents 读写和 Pull requests 读写权限") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showToken = !showToken }) {
                                Icon(if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                            }
                        }
                    )
                    if (workspaceConfig.workingBranch.isNotBlank()) {
                        Text(
                            "当前工作分支：${workspaceConfig.workingBranch}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (workspaceState is WorkspaceSyncState.Error) {
                        Text(
                            workspaceState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Row {
                        TextButton(onClick = {
                            onSaveWorkspace(formConfig())
                            showWorkspaceDialog = false
                        }) { Text("仅保存") }
                        if (workspaceConfig.token.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    onClearGitHubToken()
                                    token = ""
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) { Text("清除 Token") }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = workspaceState !is WorkspaceSyncState.Syncing,
                    onClick = {
                        onSyncWorkspace(formConfig())
                        showWorkspaceDialog = false
                    }
                ) { Text("保存并同步") }
            },
            dismissButton = {
                TextButton(onClick = { showWorkspaceDialog = false }) { Text("取消") }
            }
        )
    }
}

private fun workspaceDetail(config: GitHubWorkspaceConfig, state: WorkspaceSyncState): String = when (state) {
    WorkspaceSyncState.Idle -> if (config.repository.isBlank()) "未配置" else "${config.repository} · 待同步"
    WorkspaceSyncState.Syncing -> "正在同步 ${config.repository}…"
    is WorkspaceSyncState.Ready -> "${config.repository} · ${state.files} 个文件"
    is WorkspaceSyncState.Error -> "同步失败 · ${state.message.take(32)}"
}

private fun updateDetail(state: AppUpdateState): String = when (state) {
    AppUpdateState.Idle -> "当前版本 ${BuildConfig.VERSION_NAME} · 点击检查"
    AppUpdateState.Checking -> "正在检查 GitHub Releases…"
    is AppUpdateState.UpToDate -> "已是最新版本 ${state.currentVersion}"
    is AppUpdateState.Available -> "发现 ${state.release.tagName} · 点击下载并安装"
    is AppUpdateState.Downloading -> "正在下载 ${state.release.tagName}…"
    is AppUpdateState.Installing -> "已打开 ${state.release.tagName} 安装程序"
    is AppUpdateState.Error -> state.message
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
    ) { Column(content = content) }
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
