package com.example.mimochat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mimochat.data.*
import com.example.mimochat.ui.main.ConnectionPhase
import com.example.mimochat.theme.*

@Composable
fun ConnectionScreen(
    onBack: () -> Unit,
    connection: MimoConnection,
    onConnectionChange: (MimoConnection) -> Unit,
    probeResults: List<ProbeResult>,
    phase: ConnectionPhase,
    error: String,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showKey by remember { mutableStateOf(false) }

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
                text = "模型服务",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.size(42.dp))
        }

        // Connection scroll
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(18.dp)
        ) {
            // Connection intro
            Row(
                modifier = Modifier.padding(bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Cable,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "连接模型服务",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "保存后自动加载模型列表并逐一验证能力。",
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Config form
            Surface(
                shape = RoundedCornerShape(19.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.padding(13.dp)
                ) {
                    // Base URL
                    Column(
                        modifier = Modifier.padding(vertical = 11.dp)
                    ) {
                        Text(
                            text = "模型地址",
                            fontSize = 8.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        OutlinedTextField(
                            value = connection.baseUrl,
                            onValueChange = { onConnectionChange(connection.copy(baseUrl = it)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            )
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    // API Key
                    Column(
                        modifier = Modifier.padding(vertical = 11.dp)
                    ) {
                        Text(
                            text = "API Key",
                            fontSize = 8.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = connection.apiKey,
                                onValueChange = { onConnectionChange(connection.copy(apiKey = it)) },
                                modifier = Modifier.weight(1f),
                                placeholder = {
                                    Text(
                                        text = "输入密钥",
                                        fontSize = 10.sp
                                    )
                                },
                                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent
                                )
                            )
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(
                                    imageVector = if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    // Auth mode
                    Column(
                        modifier = Modifier.padding(vertical = 11.dp)
                    ) {
                        Text(
                            text = "认证方式",
                            fontSize = 8.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(11.dp))
                                .background(MaterialTheme.colorScheme.secondary)
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            AuthModeButton(
                                label = "api-key",
                                isSelected = connection.authMode == AuthMode.API_KEY,
                                onClick = { onConnectionChange(connection.copy(authMode = AuthMode.API_KEY)) },
                                modifier = Modifier.weight(1f)
                            )
                            AuthModeButton(
                                label = "Bearer",
                                isSelected = connection.authMode == AuthMode.BEARER,
                                onClick = { onConnectionChange(connection.copy(authMode = AuthMode.BEARER)) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(11.dp))

            // Connect button
            Button(
                onClick = onConnect,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(15.dp),
                enabled = phase == ConnectionPhase.IDLE
            ) {
                Text(
                    text = when (phase) {
                        ConnectionPhase.LOADING -> "正在加载模型"
                        ConnectionPhase.TESTING -> "正在检测 ${probeResults.count { it.status == ProbeStatus.PASSED || it.status == ProbeStatus.REACHABLE }}/${probeResults.size}"
                        ConnectionPhase.DONE -> "连接完成"
                        else -> "连接并自动检测"
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Error
            if (error.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(10.dp),
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Probe results
            if (probeResults.isNotEmpty()) {
                Spacer(modifier = Modifier.height(17.dp))

                Row(
                    modifier = Modifier.padding(bottom = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "能力体检",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${probeResults.count { it.status == ProbeStatus.PASSED || it.status == ProbeStatus.REACHABLE }}/${probeResults.size} 通过",
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                probeResults.forEach { result ->
                    ProbeResultItem(result = result)
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun AuthModeButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
        shadowElevation = if (isSelected) 2.dp else 0.dp
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(6.dp),
            fontSize = 8.sp,
            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ProbeResultItem(result: ProbeResult) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(27.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        when (result.status) {
                            ProbeStatus.FAILED -> FailedBackground
                            else -> SuccessBackground
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                when (result.status) {
                    ProbeStatus.TESTING -> CircularProgressIndicator(
                        modifier = Modifier.size(13.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    ProbeStatus.FAILED -> Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = FailedColor
                    )
                    else -> Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = SuccessColor
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = result.model,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = "${result.capability} · ${result.detail}",
                    fontSize = 7.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            if (result.latency != null) {
                Text(
                    text = "${result.latency}ms",
                    fontSize = 7.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}