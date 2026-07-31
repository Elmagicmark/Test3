package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.RepeaterTabEntity
import com.example.ui.components.CyberCard
import com.example.ui.components.MethodBadge
import com.example.ui.components.StatusCodeBadge
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepeaterScreen(
    tabs: List<RepeaterTabEntity>,
    onUpdateTab: (RepeaterTabEntity) -> Unit,
    onDeleteTab: (Long) -> Unit,
    onCreateTab: () -> Unit,
    onExecuteRequest: (RepeaterTabEntity, (Int, String) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTabId by remember { mutableStateOf<Long?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var inspectTabMode by remember { mutableStateOf("PRETTY") } // PRETTY, RAW, JSON
    val context = LocalContext.current

    // Set default active tab
    LaunchedEffect(tabs) {
        if (activeTabId == null || tabs.none { it.id == activeTabId }) {
            activeTabId = tabs.firstOrNull()?.id
        }
    }

    val activeTab = tabs.find { it.id == activeTabId }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Tab Selector Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(tabs) { tab ->
                    val isSelected = tab.id == activeTabId
                    Box(
                        modifier = Modifier
                            .background(if (isSelected) CyberSurfaceVariant else CyberSurface)
                            .border(
                                1.dp,
                                if (isSelected) NeonGreen else CyberBorder,
                                RoundedCornerShape(4.dp)
                            )
                            .clickable { activeTabId = tab.id }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = tab.tabName,
                                color = if (isSelected) NeonGreen else OnCyberDark,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            if (tabs.size > 1) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Tab",
                                    tint = WarningCrimson,
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clickable { onDeleteTab(tab.id) }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(
                onClick = { onCreateTab() },
                modifier = Modifier
                    .size(32.dp)
                    .testTag("add_repeater_tab_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Tab", tint = NeonGreen, modifier = Modifier.size(18.dp))
            }
        }

        if (activeTab == null) {
            CyberCard(modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No active repeater tab. Click + to create a tab.", color = OnCyberSurfaceMuted, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
        } else {
            var method by remember(activeTab.id) { mutableStateOf(activeTab.method) }
            var url by remember(activeTab.id) { mutableStateOf(activeTab.url) }
            var headersJson by remember(activeTab.id) { mutableStateOf(activeTab.headersJson) }
            var body by remember(activeTab.id) { mutableStateOf(activeTab.body) }

            // Split View: Request Builder (Top) & Response Inspector (Bottom)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Request Crafting Section
                CyberCard(borderColor = CyberCyan) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "REQUEST CRAFTER",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberCyan,
                                fontFamily = FontFamily.Monospace
                            )

                            Button(
                                onClick = {
                                    isLoading = true
                                    val updated = activeTab.copy(method = method, url = url, headersJson = headersJson, body = body)
                                    onUpdateTab(updated)
                                    onExecuteRequest(updated) { status, resp ->
                                        isLoading = false
                                        Toast.makeText(context, "Executed: HTTP $status", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black),
                                shape = RoundedCornerShape(4.dp),
                                enabled = !isLoading,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("send_repeater_request_button")
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.Black, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Send, contentDescription = "Send", modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("SEND REQUEST", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = method,
                                onValueChange = { method = it },
                                modifier = Modifier.width(85.dp),
                                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = CyberCyan),
                                label = { Text("Method", fontSize = 9.sp) }
                            )

                            OutlinedTextField(
                                value = url,
                                onValueChange = { url = it },
                                modifier = Modifier.weight(1f),
                                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = OnCyberDark),
                                label = { Text("Target URL", fontSize = 9.sp) }
                            )
                        }

                        OutlinedTextField(
                            value = headersJson,
                            onValueChange = { headersJson = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp),
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = OnCyberDark),
                            label = { Text("Headers (JSON Format)", fontSize = 9.sp) }
                        )

                        OutlinedTextField(
                            value = body,
                            onValueChange = { body = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(75.dp),
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = OnCyberDark),
                            label = { Text("Request Body Payload", fontSize = 9.sp) }
                        )
                    }
                }

                // Response Inspector Section
                CyberCard(borderColor = NeonGreen) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "RESPONSE INSPECTOR",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonGreen,
                                    fontFamily = FontFamily.Monospace
                                )
                                activeTab.lastResponseStatus?.let { code ->
                                    StatusCodeBadge(statusCode = code)
                                }
                                activeTab.lastResponseTimeMs?.let { time ->
                                    Text("$time ms", fontSize = 10.sp, color = OnCyberSurfaceMuted, fontFamily = FontFamily.Monospace)
                                }
                            }

                            // View Mode Toggle (Pretty, Raw, JSON)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("PRETTY", "RAW", "JSON").forEach { mode ->
                                    Box(
                                        modifier = Modifier
                                            .background(if (inspectTabMode == mode) CyberSurfaceVariant else CyberSurface)
                                            .border(0.5.dp, if (inspectTabMode == mode) CyberCyan else CyberBorder, RoundedCornerShape(2.dp))
                                            .clickable { inspectTabMode = mode }
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text(mode, fontSize = 8.5.sp, fontFamily = FontFamily.Monospace, color = if (inspectTabMode == mode) CyberCyan else OnCyberSurfaceMuted)
                                    }
                                }
                            }
                        }

                        // Response Headers Summary
                        activeTab.lastResponseHeadersJson?.let { h ->
                            Text(
                                text = "Headers: $h",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = OnCyberSurfaceMuted,
                                maxLines = 2
                            )
                        }

                        // Response Body Display
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 100.dp, max = 200.dp)
                                .background(CyberDarkBg)
                                .border(1.dp, CyberBorder, RoundedCornerShape(4.dp))
                                .padding(6.dp)
                                .verticalScroll(rememberScrollState())
                                .horizontalScroll(rememberScrollState())
                        ) {
                            val displayText = activeTab.lastResponseBody ?: "No response received yet. Click 'SEND REQUEST' to execute."
                            Text(
                                text = displayText,
                                color = if (activeTab.lastResponseBody != null) OnCyberDark else OnCyberSurfaceMuted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}
