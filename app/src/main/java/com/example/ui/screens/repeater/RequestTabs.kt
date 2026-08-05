package com.example.ui.screens.repeater

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.RepeaterTabEntity
import com.example.ui.components.MethodBadge
import com.example.ui.theme.*

@Composable
fun RequestTabs(
    tabs: List<RepeaterTabEntity>,
    activeTabId: Long?,
    onSelectTab: (Long) -> Unit,
    onCreateTab: () -> Unit,
    onCloseTab: (Long) -> Unit,
    onRenameTab: (RepeaterTabEntity, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var renamingTab by remember { mutableStateOf<RepeaterTabEntity?>(null) }
    var renameText by remember { mutableStateOf("") }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(CyberDarkBg)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(tabs) { tab ->
                val isSelected = tab.id == activeTabId

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(if (isSelected) CyberSurfaceVariant else CyberSurface)
                        .border(
                            width = if (isSelected) 1.dp else 0.5.dp,
                            color = if (isSelected) NeonGreen else CyberBorder,
                            shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                        )
                        .clickable { onSelectTab(tab.id) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MethodBadge(method = tab.method)

                        Text(
                            text = tab.tabName,
                            color = if (isSelected) NeonGreen else OnCyberDark,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )

                        // Quick Rename Icon
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Rename",
                            tint = if (isSelected) CyberCyan.copy(alpha = 0.7f) else Color.Transparent,
                            modifier = Modifier
                                .size(12.dp)
                                .clickable {
                                    renamingTab = tab
                                    renameText = tab.tabName
                                }
                        )

                        // Close Tab Icon
                        if (tabs.size > 1) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Tab",
                                tint = WarningCrimson.copy(alpha = 0.8f),
                                modifier = Modifier
                                    .size(13.dp)
                                    .clickable { onCloseTab(tab.id) }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // New Tab Button
        IconButton(
            onClick = onCreateTab,
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(CyberSurface)
                .border(1.dp, NeonGreen, RoundedCornerShape(6.dp))
                .testTag("add_repeater_tab_button")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "New Tab",
                tint = NeonGreen,
                modifier = Modifier.size(18.dp)
            )
        }
    }

    // Rename Dialog
    renamingTab?.let { tab ->
        AlertDialog(
            onDismissRequest = { renamingTab = null },
            title = {
                Text(
                    text = "RENAME TAB",
                    color = CyberCyan,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, color = OnCyberDark),
                    label = { Text("Tab Name", fontSize = 10.sp) }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            onRenameTab(tab, renameText)
                        }
                        renamingTab = null
                    }
                ) {
                    Text("SAVE", color = NeonGreen, fontFamily = FontFamily.Monospace)
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingTab = null }) {
                    Text("CANCEL", color = OnCyberSurfaceMuted, fontFamily = FontFamily.Monospace)
                }
            },
            containerColor = CyberSurface,
            shape = RoundedCornerShape(8.dp)
        )
    }
}
