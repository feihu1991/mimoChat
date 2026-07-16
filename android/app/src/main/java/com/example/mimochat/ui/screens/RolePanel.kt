package com.example.mimochat.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mimochat.data.Role

@Composable
fun RolePanel(
    roles: List<Role>,
    selected: Role,
    onClose: () -> Unit,
    onSelect: (Role) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.28f)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 72.dp, start = 20.dp, end = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("切换角色", style = MaterialTheme.typography.titleLarge)
                        IconButton(onClick = onClose) { Icon(Icons.Default.Close, "关闭") }
                    }
                    roles.forEach { role ->
                        ListItem(
                            headlineContent = { Text(role.name) },
                            supportingContent = { Text(role.description) },
                            trailingContent = { if (role.id == selected.id) Text("当前", color = MaterialTheme.colorScheme.primary) },
                            modifier = Modifier.fillMaxWidth().clickable { onSelect(role) },
                            tonalElevation = if (role.id == selected.id) 2.dp else 0.dp
                        )
                        if (role.id != roles.lastOrNull()?.id) {
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
