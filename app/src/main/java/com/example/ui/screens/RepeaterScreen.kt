package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.HttpTransactionEntity
import com.example.data.local.RepeaterTabEntity
import com.example.ui.components.CyberCard
import com.example.ui.screens.repeater.HistoryPanel
import com.example.ui.screens.repeater.RequestEditor
import com.example.ui.screens.repeater.RequestTabs
import com.example.ui.screens.repeater.ResponseViewer
import com.example.ui.theme.*

@Composable
fun RepeaterScreen(
    tabs: List<RepeaterTabEntity>,
    historyTransactions: List<HttpTransactionEntity> = emptyList(),
    onUpdateTab: (RepeaterTabEntity) -> Unit,
    onDeleteTab: (Long) -> Unit,
    onCreateTab: () -> Unit,
    onExecuteRequest: (RepeaterTabEntity, (Int, String) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTabId by remember { mutableStateOf<Long?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showHistoryDrawer by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val isTabletOrWide = configuration.screenWidthDp >= 600
    var isSideBySideLayout by remember { mutableStateOf(isTabletOrWide) }

    val context = LocalContext.current

    // Automatically pick the first tab as active if none is selected or selected was deleted
    LaunchedEffect(tabs) {
        if (activeTabId == null || tabs.none { it.id == activeTabId }) {
            activeTabId = tabs.firstOrNull()?.id
        }
    }

    val activeTab = tabs.find { it.id == activeTabId }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CyberDarkBg)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Top Toolbar: Multi-Request Tabs & Layout Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                RequestTabs(
                    tabs = tabs,
                    activeTabId = activeTabId,
                    onSelectTab = { activeTabId = it },
                    onCreateTab = onCreateTab,
                    onCloseTab = onDeleteTab,
                    onRenameTab = { tab, newName ->
                        onUpdateTab(tab.copy(tabName = newName))
                    }
                )
            }

            // Layout Toggle (Split View vs Vertical Stacked)
            IconButton(
                onClick = { isSideBySideLayout = !isSideBySideLayout },
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(CyberSurface)
                    .border(0.5.dp, CyberCyan, RoundedCornerShape(6.dp))
            ) {
                Icon(
                    imageVector = if (isSideBySideLayout) Icons.Default.ViewStream else Icons.Default.ViewColumn,
                    contentDescription = "Toggle Layout",
                    tint = CyberCyan,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (activeTab == null) {
            CyberCard(modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No active repeater request tab.\nClick '+' to open a new tab.",
                        color = OnCyberSurfaceMuted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Main Workspace: Request & Response
                Box(modifier = Modifier.weight(if (showHistoryDrawer) 0.65f else 1f)) {
                    if (isSideBySideLayout) {
                        // Side-by-Side Split View (Burp Desktop Style for Wide / Tablet Screens)
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RequestEditor(
                                tab = activeTab,
                                isLoading = isLoading,
                                onSaveTab = onUpdateTab,
                                onSendRequest = { tabToSend ->
                                    isLoading = true
                                    onExecuteRequest(tabToSend) { status, _ ->
                                        isLoading = false
                                        Toast.makeText(context, "Executed: HTTP $status", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onToggleHistory = { showHistoryDrawer = !showHistoryDrawer },
                                onDuplicateTab = {
                                    onUpdateTab(activeTab)
                                    onCreateTab()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )

                            ResponseViewer(
                                statusCode = activeTab.lastResponseStatus,
                                responseTimeMs = activeTab.lastResponseTimeMs,
                                responseHeadersJson = activeTab.lastResponseHeadersJson,
                                responseBody = activeTab.lastResponseBody,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                    } else {
                        // Vertical Stacked Layout (Reqable Mobile Style)
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RequestEditor(
                                tab = activeTab,
                                isLoading = isLoading,
                                onSaveTab = onUpdateTab,
                                onSendRequest = { tabToSend ->
                                    isLoading = true
                                    onExecuteRequest(tabToSend) { status, _ ->
                                        isLoading = false
                                        Toast.makeText(context, "Executed: HTTP $status", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onToggleHistory = { showHistoryDrawer = !showHistoryDrawer },
                                onDuplicateTab = {
                                    onUpdateTab(activeTab)
                                    onCreateTab()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )

                            ResponseViewer(
                                statusCode = activeTab.lastResponseStatus,
                                responseTimeMs = activeTab.lastResponseTimeMs,
                                responseHeadersJson = activeTab.lastResponseHeadersJson,
                                responseBody = activeTab.lastResponseBody,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )
                        }
                    }
                }

                // History Side Drawer Panel
                AnimatedVisibility(visible = showHistoryDrawer) {
                    HistoryPanel(
                        transactions = historyTransactions,
                        onSelectTransaction = { tx ->
                            val updated = activeTab.copy(
                                method = tx.method,
                                url = tx.url,
                                headersJson = tx.requestHeadersJson,
                                body = tx.requestBody
                            )
                            onUpdateTab(updated)
                            Toast.makeText(context, "Loaded into ${activeTab.tabName}", Toast.LENGTH_SHORT).show()
                        },
                        onClose = { showHistoryDrawer = false },
                        modifier = Modifier
                            .width(260.dp)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}
