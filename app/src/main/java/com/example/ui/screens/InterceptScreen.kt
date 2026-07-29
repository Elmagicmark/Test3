package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.InterceptedRequestEntity
import com.example.ui.components.CyberCard
import com.example.ui.components.MethodBadge
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterceptScreen(
    isInterceptEnabled: Boolean,
    interceptedList: List<InterceptedRequestEntity>,
    interceptMethods: Set<String>,
    onToggleIntercept: (Boolean) -> Unit,
    onToggleInterceptMethod: (String) -> Unit,
    onForward: (Long, String, String, String, String) -> Unit,
    onForwardResponse: (Long, Int, String, String) -> Unit,
    onFetchResponse: (Long, String, String, String, String, onComplete: () -> Unit) -> Unit,
    onDrop: (Long) -> Unit,
    onForwardAll: () -> Unit,
    onSendToRepeater: (String, String, String, String) -> Unit,
    onSimulateTestIntercept: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedItem by remember { mutableStateOf<InterceptedRequestEntity?>(null) }
    var isFetchingResponse by remember { mutableStateOf(false) }

    // Pure HTTP Data Methods (CONNECT is handled automatically by SSL tunnel layer)
    val allMethods = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")

    // Sync selected item
    LaunchedEffect(interceptedList) {
        if (selectedItem == null || !interceptedList.any { it.id == selectedItem?.id }) {
            selectedItem = interceptedList.firstOrNull()
        } else {
            selectedItem = interceptedList.find { it.id == selectedItem?.id }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Intercept Control Header Card (Compact View)
        CyberCard(
            borderColor = if (isInterceptEnabled) WarningCrimson else CyberBorder
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isInterceptEnabled) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                            contentDescription = "Intercept Status",
                            tint = if (isInterceptEnabled) WarningCrimson else NeonGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "INTERCEPT CONTROL",
                                color = OnCyberDark,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = if (isInterceptEnabled) "INTERCEPT ON - Capturing GET, POST, PUT..." else "INTERCEPT OFF - Passthrough Mode",
                                color = if (isInterceptEnabled) WarningCrimson else OnCyberSurfaceMuted,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Switch(
                        checked = isInterceptEnabled,
                        onCheckedChange = { onToggleIntercept(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = WarningCrimson,
                            checkedTrackColor = WarningCrimson.copy(alpha = 0.3f),
                            uncheckedThumbColor = OnCyberSurfaceMuted,
                            uncheckedTrackColor = CyberSurfaceVariant
                        ),
                        modifier = Modifier
                            .graphicsLayer(scaleX = 0.85f, scaleY = 0.85f)
                            .testTag("intercept_switch")
                    )
                }

                Divider(color = CyberBorder, thickness = 0.5.dp)

                // Method Filters Selector (تصفية الميثود)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "METHODS:",
                        fontSize = 9.sp,
                        color = OnCyberSurfaceMuted,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        items(allMethods) { method ->
                            val isSelected = interceptMethods.contains(method)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isSelected) CyberCyan.copy(alpha = 0.2f) else CyberDarkBg)
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) CyberCyan else CyberBorder,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .clickable { onToggleInterceptMethod(method) }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = method,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (isSelected) CyberCyan else OnCyberSurfaceMuted
                                    )
                                    if (isSelected) {
                                        Text("✓", fontSize = 8.sp, color = NeonGreen, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Action Toolbar & Queued Requests Header (جميع الأدوات بجانب FORWARD ALL)
        selectedItem?.let { req ->
            var editableMethod by remember(req.id, req.method) { mutableStateOf(req.method) }
            var editableUrl by remember(req.id, req.url) { mutableStateOf(req.url) }
            var editableHeaders by remember(req.id, req.headersJson) { mutableStateOf(req.headersJson) }
            var editableBody by remember(req.id, req.body) { mutableStateOf(req.body) }

            // TOP INTEGRATED ACTION TOOLBAR (جميع أزرار التحكم في مكان موحد أعلى المحرر)
            CyberCard(borderColor = CyberCyan) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "QUEUED (${interceptedList.size})",
                            color = CyberCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )

                        // Scrollable Toolbar containing FORWARD, FORWARD ALL, VIEW RESP, DROP, REPEATER
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            item {
                                // FORWARD BUTTON
                                Button(
                                    onClick = {
                                        if (req.isResponse) {
                                            val code = editableMethod.toIntOrNull() ?: req.statusCode ?: 200
                                            onForwardResponse(req.id, code, editableHeaders, editableBody)
                                        } else {
                                            onForward(req.id, editableMethod, editableUrl, editableHeaders, editableBody)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(11.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(if (req.isResponse) "FORWARD RESP" else "FORWARD", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                                }
                            }

                            item {
                                // FORWARD ALL BUTTON
                                OutlinedButton(
                                    onClick = { onForwardAll() },
                                    shape = RoundedCornerShape(4.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(Icons.Default.FastForward, contentDescription = null, modifier = Modifier.size(11.dp), tint = NeonGreen)
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("FORWARD ALL", color = NeonGreen, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (!req.isResponse) {
                                item {
                                    // VIEW RESP BUTTON
                                    Button(
                                        onClick = {
                                            isFetchingResponse = true
                                            onFetchResponse(req.id, editableMethod, editableUrl, editableHeaders, editableBody) {
                                                isFetchingResponse = false
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black),
                                        shape = RoundedCornerShape(4.dp),
                                        enabled = !isFetchingResponse,
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        if (isFetchingResponse) {
                                            CircularProgressIndicator(modifier = Modifier.size(10.dp), color = Color.Black, strokeWidth = 1.5.dp)
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("FETCHING...", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                        } else {
                                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(11.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("VIEW RESP", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                                        }
                                    }
                                }
                            }

                            item {
                                // DROP BUTTON
                                Button(
                                    onClick = { onDrop(req.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = WarningCrimson, contentColor = Color.White),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(11.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("DROP", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                                }
                            }

                            item {
                                // TO REPEATER BUTTON
                                OutlinedButton(
                                    onClick = {
                                        onSendToRepeater(editableMethod, editableUrl, editableHeaders, editableBody)
                                    },
                                    shape = RoundedCornerShape(4.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(11.dp), tint = CyberCyan)
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("REPEATER", color = CyberCyan, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Compact Horizontal Queue Strip
                    if (interceptedList.size > 1) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(interceptedList) { item ->
                                val isSelected = item.id == req.id
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isSelected) CyberSurfaceVariant else CyberDarkBg)
                                        .border(1.dp, if (isSelected) CyberCyan else CyberBorder, RoundedCornerShape(4.dp))
                                        .clickable { selectedItem = item }
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        MethodBadge(method = item.method)
                                        Text(
                                            text = item.url.takeLast(25),
                                            color = if (isSelected) CyberCyan else OnCyberDark,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Compact Request / Response Interactive Editor View
            CyberCard(
                modifier = Modifier.weight(1f),
                borderColor = if (req.isResponse) WarningCrimson else CyberCyan
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (req.isResponse) "SPOOF RESPONSE BEFORE CLIENT RECEIVES" else "EDIT REQUEST PAYLOAD BEFORE SERVER RECEIVES",
                        color = if (req.isResponse) WarningCrimson else CyberCyan,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    // Method & Target URL Row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = editableMethod,
                            onValueChange = { editableMethod = it },
                            modifier = Modifier.width(80.dp),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.5.sp,
                                color = if (req.isResponse) WarningCrimson else CyberCyan,
                                fontWeight = FontWeight.Bold
                            ),
                            label = { Text(if (req.isResponse) "Status" else "Method", fontSize = 8.sp) }
                        )

                        OutlinedTextField(
                            value = editableUrl,
                            onValueChange = { editableUrl = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.5.sp,
                                color = OnCyberDark
                            ),
                            label = { Text("Target URL", fontSize = 8.sp) }
                        )
                    }

                    // Request / Response Headers JSON Field
                    OutlinedTextField(
                        value = editableHeaders,
                        onValueChange = { editableHeaders = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(55.dp),
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = OnCyberDark
                        ),
                        label = { Text("Headers (JSON)", fontSize = 8.sp) }
                    )

                    // Request / Response Body Text Field
                    OutlinedTextField(
                        value = editableBody,
                        onValueChange = { editableBody = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = OnCyberDark
                        ),
                        label = { Text(if (req.isResponse) "Response Body (Payload)" else "Request Body (GET/POST/PUT)", fontSize = 8.sp) }
                    )
                }
            }
        } ?: run {
            // Empty State View when no request is queued
            CyberCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Empty Queue",
                            tint = OnCyberSurfaceMuted,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "NO INTERCEPTED REQUESTS QUEUED",
                            color = OnCyberSurfaceMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (isInterceptEnabled) "Listening for methods: ${interceptMethods.joinToString(", ")}" else "Turn Intercept ON to capture live GET, POST, PUT, DELETE requests",
                            color = OnCyberSurfaceMuted.copy(alpha = 0.7f),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { onSimulateTestIntercept?.invoke() },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Icon(Icons.Default.AddAlert, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("GENERATE TEST INTERCEPT REQUEST", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}
