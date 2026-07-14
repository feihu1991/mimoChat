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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mimochat.data.ModelId

@Composable
fun ModelPanel(
    model: ModelId,
    onClose: () -> Unit,
    onSelect: (ModelId) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize().clickable(onClick = onClose),
        color = Color.Black.copy(alpha = 0.3f)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.4f).align(Alignment.BottomCenter),
            verticalArrangement = Arrangement.Bottom
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Grabber
                    Box(
                        modifier = Modifier.width(40.dp).height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.outlineVariant)
                            .align(Alignment.CenterHorizontally)
                    )
                    Spacer(Modifier.height(16.dp))

                    Text("选择对话模型", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(16.dp))

                    // MiMo 2.5
                    ModelOption(
                        selected = model == ModelId.MIMO_V2_5,
                        icon = Icons.Default.AutoAwesome,
                        iconColor = Color(0xFFf06c3b),
                        title = ModelId.MIMO_V2_5.displayName,
                        subtitle = "快速回答 · 文字、图片多模态理解",
                        onClick = { onSelect(ModelId.MIMO_V2_5) }
                    )

                    Spacer(Modifier.height(8.dp))

                    // MiMo 2.5 Pro
                    ModelOption(
                        selected = model == ModelId.MIMO_V2_5_PRO,
                        icon = Icons.Default.Psychology,
                        iconColor = Color(0xFF6750A4),
                        title = ModelId.MIMO_V2_5_PRO.displayName,
                        subtitle = "深度回答 · 复杂推理与长任务",
                        onClick = { onSelect(ModelId.MIMO_V2_5_PRO) }
                    )

                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ModelOption(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) {
                Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
