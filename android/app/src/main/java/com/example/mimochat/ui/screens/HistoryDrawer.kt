package com.example.mimochat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mimochat.data.ConversationEntity
import com.example.mimochat.data.Role

@Composable
fun HistoryDrawer(
    conversations: List<ConversationEntity>,
    roles: List<Role>,
    currentId: String?,
    onClose: () -> Unit,
    onNew: () -> Unit,
    onSelect: (String) -> Unit,
    onSettings: () -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var deleteConfirmId by remember { mutableStateOf<String?>(null) }

    val filteredConversations = if (searchQuery.isBlank()) conversations
        else conversations.filter { it.title.contains(searchQuery, ignoreCase = true) }

    Surface(
        modifier = Modifier.fillMaxHeight().width(320.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 14.dp, top = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("MiMo Chat", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "关闭") }
            }

            // Search
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜索对话…", fontSize = 13.sp) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp)
            )

            // New conversation button
            TextButton(
                onClick = onNew,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("新对话", fontWeight = FontWeight.Medium)
            }

            // Conversation list
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                items(filteredConversations, key = { it.id }) { conversation ->
                    val role = roles.find { it.id == conversation.roleId } ?: roles.firstOrNull()
                    val isEditing = editingId == conversation.id

                    if (isEditing) {
                        // Inline rename
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = editTitle,
                                onValueChange = { editTitle = it },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium
                            )
                            IconButton(onClick = {
                                if (editTitle.isNotBlank()) onRename(conversation.id, editTitle)
                                editingId = null
                            }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Check, "确认", modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { editingId = null }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, "取消", modifier = Modifier.size(16.dp))
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (conversation.id == currentId)
                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                    else Color.Transparent
                                )
                                .clickable { onSelect(conversation.id) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Role avatar
                            Box(
                                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(10.dp))
                                    .background(androidx.compose.ui.graphics.Color(
                                        android.graphics.Color.parseColor(role?.color ?: "#f06c3b")
                                    )),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    role?.name?.firstOrNull()?.toString() ?: "M",
                                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    conversation.title,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (conversation.id == currentId) FontWeight.SemiBold else FontWeight.Normal
                                )
                                Text(
                                    "${role?.name ?: "MiMo"} · ${formatTime(conversation.updatedAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            // Actions
                            var showMenu by remember { mutableStateOf(false) }
                            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.MoreVert, "选项", modifier = Modifier.size(16.dp))
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("重命名") },
                                    leadingIcon = { Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp)) },
                                    onClick = {
                                        showMenu = false
                                        editTitle = conversation.title
                                        editingId = conversation.id
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) },
                                    onClick = {
                                        showMenu = false
                                        deleteConfirmId = conversation.id
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Settings button
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TextButton(
                onClick = onSettings,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Settings, null)
                Spacer(Modifier.width(8.dp))
                Text("设置", fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.ChevronRight, null)
            }
        }
    }

    // Delete confirmation dialog
    deleteConfirmId?.let { id ->
        val conv = conversations.find { it.id == id }
        AlertDialog(
            onDismissRequest = { deleteConfirmId = null },
            title = { Text("删除对话") },
            text = { Text("确定要删除「${conv?.title ?: ""}」吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = { onDelete(id); deleteConfirmId = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteConfirmId = null }) { Text("取消") } }
        )
    }
}

private fun formatTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3600_000 -> "${diff / 60_000}分钟前"
        diff < 86400_000 -> "${diff / 3600_000}小时前"
        diff < 172800_000 -> "昨天"
        else -> "${diff / 86400_000}天前"
    }
}
