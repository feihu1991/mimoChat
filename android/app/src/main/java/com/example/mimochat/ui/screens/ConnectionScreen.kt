package com.example.mimochat.ui.screens

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
fun ConnectionScreen(
    connection: MimoConnection,
    phase: ConnectionPhase,
    error: String,
    probeResults: List<ProbeResult>,
    onBack: () -> Unit,
    onConnect: () -> Unit,
    onSave: (MimoConnection) -> Unit
) {
    var baseUrl by remember { mutableStateOf(connection.baseUrl) }
    var apiKey by remember { mutableStateOf(connection.apiKey) }
    var authMode by remember(connection.authMode) { mutableStateOf(connection.authMode) }
    var showKey by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("模型服务") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Intro
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            ) {
                Row(modifier = Modifier.padding(16.dp)) {
                    Icon(Icons.Default.Cloud, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("连接模型服务", fontWeight = FontWeight.SemiBold)
                        Text("配置 MiMo API 地址和 Key，保存后自动检测可用模型。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Form
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it.trim() },
                label = { Text("模型地址") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("https://api.xiaomimimo.com/v1") }
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it.trim() },
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

            Text("认证方式", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = authMode == AuthMode.API_KEY,
                    onClick = { authMode = AuthMode.API_KEY },
                    label = { Text("api-key") }
                )
                FilterChip(
                    selected = authMode == AuthMode.BEARER,
                    onClick = { authMode = AuthMode.BEARER },
                    label = { Text("Bearer") }
                )
            }

            // Connect button
            Button(
                onClick = {
                    val normalizedUrl = baseUrl.ifBlank { "https://api.xiaomimimo.com/v1" }
                    val finalUrl = if (normalizedUrl.startsWith("http")) normalizedUrl else "https://$normalizedUrl"
                    onSave(MimoConnection(baseUrl = finalUrl, apiKey = apiKey, authMode = authMode))
                    onConnect()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = phase != ConnectionPhase.LOADING && phase != ConnectionPhase.TESTING
            ) {
                if (phase == ConnectionPhase.LOADING || phase == ConnectionPhase.TESTING) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    when (phase) {
                        ConnectionPhase.LOADING -> "正在加载模型…"
                        ConnectionPhase.TESTING -> "正在检测…"
                        ConnectionPhase.DONE -> "重新连接"
                        ConnectionPhase.IDLE -> "连接并检测"
                    }
                )
            }

            // Error
            if (error.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp)) {
                        Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(error, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            // Probe results
            if (probeResults.isNotEmpty()) {
                Text("模型检测结果", fontWeight = FontWeight.SemiBold)
                val passed = probeResults.count { it.status == ProbeStatus.PASSED || it.status == ProbeStatus.REACHABLE }
                Text("$passed/${probeResults.size} 通过", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                probeResults.forEach { result ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = when (result.status) {
                                ProbeStatus.PASSED, ProbeStatus.REACHABLE -> MaterialTheme.colorScheme.surfaceVariant
                                ProbeStatus.FAILED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                ProbeStatus.TESTING -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            when (result.status) {
                                ProbeStatus.TESTING -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                ProbeStatus.FAILED -> Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                else -> Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(result.model, fontWeight = FontWeight.Medium)
                                Text("${result.capability} · ${result.detail}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (result.latency != null) {
                                Text("${result.latency}ms", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
