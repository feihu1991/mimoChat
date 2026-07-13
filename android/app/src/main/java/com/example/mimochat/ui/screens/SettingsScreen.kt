package com.example.mimochat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mimochat.data.Screen
import com.example.mimochat.ui.main.ThemeMode
import com.example.mimochat.theme.*

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpen: (Screen) -> Unit,
    roleCount: Int,
    theme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var sound by remember { mutableStateOf(true) }
    var haptics by remember { mutableStateOf(true) }
    var sendOnEnter by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Page header
        PageHeader(
            title = "设置",
            onBack = onBack
        )

        // Settings scroll
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(18.dp)
        ) {
            // Private note
            Surface(
                shape = RoundedCornerShape(19.dp),
                color = MaterialTheme.colorScheme.tertiary
            ) {
                Row(
                    modifier = Modifier.padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(31.dp),
                        tint = MaterialTheme.colorScheme.onTertiary
                    )
                    Spacer(modifier = Modifier.width(11.dp))
                    Column {
                        Text(
                            text = "私人模式",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "无需登录，数据仅保存在本机",
                            fontSize = 8.sp,
                            color = MaterialTheme.colorScheme.onTertiary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Chat settings
            SettingsGroup(title = "聊天") {
                SettingsRow(
                    icon = Icons.Default.Group,
                    title = "聊天角色",
                    detail = "$roleCount 个角色",
                    onClick = { onOpen(Screen.ROLES) }
                )
                SettingsRow(
                    icon = Icons.Default.Cable,
                    title = "模型服务",
                    detail = "未连接",
                    onClick = { onOpen(Screen.CONNECTION) }
                )
            }

            Spacer(modifier = Modifier.height(19.dp))

            // Data settings
            SettingsGroup(title = "数据与偏好") {
                SettingsRow(
                    icon = Icons.Default.Storage,
                    title = "记忆管理",
                    detail = "3 条",
                    onClick = { onOpen(Screen.MEMORY) }
                )
            }

            Spacer(modifier = Modifier.height(19.dp))

            // Interaction settings
            SettingsGroup(title = "交互") {
                ToggleRow(
                    title = "语音回复",
                    detail = "语音聊天时自动播放角色声音",
                    value = sound,
                    onChange = { sound = it }
                )
                ToggleRow(
                    title = "触感反馈",
                    detail = "重要操作使用轻触反馈",
                    value = haptics,
                    onChange = { haptics = it }
                )
                ToggleRow(
                    title = "回车发送",
                    detail = "键盘回车直接发送消息",
                    value = sendOnEnter,
                    onChange = { sendOnEnter = it }
                )
            }

            Spacer(modifier = Modifier.height(19.dp))

            // Theme settings
            SettingsGroup(title = "主题") {
                ThemeRow(
                    value = theme,
                    onChange = onThemeChange
                )
            }

            Spacer(modifier = Modifier.height(19.dp))

            // About
            SettingsGroup(title = "关于") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "MiMo Chat",
                        fontSize = 9.sp
                    )
                    Text(
                        text = "本地私人版本 · 0.2.0",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PageHeader(
    title: String,
    onBack: () -> Unit,
    action: @Composable (() -> Unit)? = null
) {
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
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.weight(1f))

        if (action != null) {
            action()
        } else {
            Spacer(modifier = Modifier.size(42.dp))
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            modifier = Modifier.padding(start = 8.dp, bottom = 7.dp),
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            shape = RoundedCornerShape(17.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column {
                content()
            }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    detail: String? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.secondary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = MaterialTheme.colorScheme.onSecondary
                )
            }

            Spacer(modifier = Modifier.width(9.dp))

            Text(
                text = title,
                modifier = Modifier.weight(1f),
                fontSize = 10.sp
            )

            if (detail != null) {
                Text(
                    text = detail,
                    fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
            }

            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    detail: String,
    value: Boolean,
    onChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.clickable { onChange(!value) },
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 10.sp
                )
                Text(
                    text = detail,
                    fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = value,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = MaterialTheme.colorScheme.outline
                )
            )
        }
    }
}

@Composable
private fun ThemeRow(
    value: ThemeMode,
    onChange: (ThemeMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ThemeButton(
            icon = Icons.Default.LightMode,
            label = "浅色",
            isSelected = value == ThemeMode.LIGHT,
            onClick = { onChange(ThemeMode.LIGHT) },
            modifier = Modifier.weight(1f)
        )
        ThemeButton(
            icon = Icons.Default.DarkMode,
            label = "深色",
            isSelected = value == ThemeMode.DARK,
            onClick = { onChange(ThemeMode.DARK) },
            modifier = Modifier.weight(1f)
        )
        ThemeButton(
            icon = Icons.Default.Computer,
            label = "跟随系统",
            isSelected = value == ThemeMode.SYSTEM,
            onClick = { onChange(ThemeMode.SYSTEM) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ThemeButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(54.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(13.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.secondary
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 9.sp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondary
            )
        }
    }
}