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
    modifier: Modifier = Modifier
) {
    var selectedItem by remember { mutableStateOf<InterceptedRequestEntity?>(null) }
    var isFetchingResponse by remember { mutableStateOf(false) }

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
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Intercept Control Header
        CyberCard(
            borderColor = if (isInterceptEnabled) WarningCrimson else CyberBorder
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "INTERCEPT CONTROL",
                                color = OnCyberDark,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = if (isInterceptEnabled) "INTERCEPT IS ON - Holding Request" else "INTERCEPT IS OFF - Traffic Passthrough",
                                color = if (isInterceptEnabled) WarningCrimson else OnCyberSurfaceMuted,
                                fontSize = 10.sp,
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
                        modifier = Modifier.testTag("intercept_switch")
                    )
                }

                Divider(color = CyberBorder, thickness = 0.5.dp)

                // Method Filters Selector (تصفية الميثود)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "CAPTURE METHOD FILTERS:",
                        fontSize = 9.sp,
                        color = OnCyberSurfaceMuted,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
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
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = method,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (isSelected) CyberCyan else OnCyberSurfaceMuted
                                    )
                                    if (isSelected) {
                                        Text("✓", fontSize = 9.sp, color = NeonGreen, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (interceptedList.isEmpty()) {
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
                            contentDescription = "Empty",
                            tint = OnCyberSurfaceMuted,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "NO INTERCEPTED REQUESTS QUEUED",
                            color = OnCyberSurfaceMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (isInterceptEnabled) "Listening for methods: ${interceptMethods.joinToString(", ")}" else "Turn Intercept ON to pause live client HTTP/HTTPS requests",
                            color = OnCyberSurfaceMuted.copy(alpha = 0.7f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        } else {
            // Queue & Action Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "QUEUED REQUESTS (${interceptedList.size})",
                    color = CyberCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                OutlinedButton(
                    onClick = { onForwardAll() },
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "FORWARD ALL",
                        color = NeonGreen,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // List of Queued Items
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 130.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(interceptedList) { item ->
                    val isSelected = item.id == selectedItem?.id
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isSelected) CyberSurfaceVariant else CyberSurface)
                            .border(
                                1.dp,
                                if (isSelected) CyberCyan else CyberBorder,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (item.isResponse) {
                                    Surface(
                                        color = WarningCrimson.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, WarningCrimson)
                                    ) {
                                        Text(
                                            text = "RESP ${item.statusCode}",
                                            color = WarningCrimson,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                } else {
                                    MethodBadge(method = item.method)
                                }
                                Text(
                                    text = item.url,
                                    color = OnCyberDark,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Button(
                                onClick = { selectedItem = item },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberSurfaceVariant),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isSelected) "Editing" else "Edit",
                                    fontSize = 9.sp,
                                    color = if (isSelected) CyberCyan else OnCyberDark
                                )
                            }
                        }
                    }
                }
            }

            // Request Interactive Editor View
            selectedItem?.let { req ->
                var editableMethod by remember(req.id, req.method) { mutableStateOf(req.method) }
                var editableUrl by remember(req.id, req.url) { mutableStateOf(req.url) }
                var editableHeaders by remember(req.id, req.headersJson) { mutableStateOf(req.headersJson) }
                var editableBody by remember(req.id, req.body) { mutableStateOf(req.body) }

                CyberCard(
                    modifier = Modifier.weight(1f),
                    borderColor = if (req.isResponse) WarningCrimson else CyberCyan
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (req.isResponse) "SERVER RESPONSE INTERCEPTED (INSPECT / SPOOF BEFORE CLIENT)" else "CLIENT REQUEST INTERCEPTED (MODIFIED BEFORE SERVER)",
                            color = if (req.isResponse) WarningCrimson else CyberCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )

                        // Method & URL row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = editableMethod,
                                onValueChange = { editableMethod = it },
                                modifier = Modifier.width(90.dp),
                                textStyle = LocalTextStyle.current.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = if (req.isResponse) WarningCrimson else CyberCyan
                                ),
                                label = { Text(if (req.isResponse) "Status" else "Method", fontSize = 9.sp) }
                            )

                            OutlinedTextField(
                                value = editableUrl,
                                onValueChange = { editableUrl = it },
                                modifier = Modifier.weight(1f),
                                textStyle = LocalTextStyle.current.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = OnCyberDark
                                ),
                                label = { Text("Target URL", fontSize = 9.sp) }
                            )
                        }

                        // Headers JSON Field
                        OutlinedTextField(
                            value = editableHeaders,
                            onValueChange = { editableHeaders = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp),
                            textStyle = LocalTextStyle.current.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = OnCyberDark
                            ),
                            label = { Text(if (req.isResponse) "Response Headers (JSON)" else "Request Headers (JSON)", fontSize = 9.sp) }
                        )

                        // Body Text Field
                        OutlinedTextField(
                            value = editableBody,
                            onValueChange = { editableBody = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            textStyle = LocalTextStyle.current.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = OnCyberDark
                            ),
                            label = { Text(if (req.isResponse) "Response Body (Spoof Payload)" else "Request Body", fontSize = 9.sp) }
                        )

                        // Action Buttons
                        if (req.isResponse) {
                            // SERVER RESPONSE ACTION BUTTONS
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val statusCodeInt = editableMethod.toIntOrNull() ?: req.statusCode ?: 200
                                        onForwardResponse(req.id, statusCodeInt, editableHeaders, editableBody)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black),
                                    modifier = Modifier.weight(1.3f),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("FORWARD RESP", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                                }

                                Button(
                                    onClick = { onDrop(req.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = WarningCrimson, contentColor = Color.White),
                                    modifier = Modifier.weight(0.9f),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text("DROP", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        onSendToRepeater(editableMethod, editableUrl, editableHeaders, editableBody)
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(4.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text("TO REPEATER", color = CyberCyan, fontFamily = FontFamily.Monospace, fontSize = 9.5.sp)
                                }
                            }
                        } else {
                            // CLIENT REQUEST ACTION BUTTONS
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = {
                                        onForward(req.id, editableMethod, editableUrl, editableHeaders, editableBody)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black),
                                    modifier = Modifier.weight(1.1f),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text("FORWARD REQ", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 9.5.sp)
                                }

                                // FETCH RESPONSE BUTTON (زرار معاينة الريسبونس قبل ما يبعته)
                                Button(
                                    onClick = {
                                        isFetchingResponse = true
                                        onFetchResponse(req.id, editableMethod, editableUrl, editableHeaders, editableBody) {
                                            isFetchingResponse = false
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black),
                                    modifier = Modifier.weight(1.3f),
                                    shape = RoundedCornerShape(4.dp),
                                    enabled = !isFetchingResponse,
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    if (isFetchingResponse) {
                                        CircularProgressIndicator(modifier = Modifier.size(12.dp), color = Color.Black, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("FETCHING...", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                    } else {
                                        Icon(Icons.Default.Visibility, contentDescription = "Inspect Response", modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("VIEW RESP", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 9.5.sp)
                                    }
                                }

                                Button(
                                    onClick = { onDrop(req.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = WarningCrimson, contentColor = Color.White),
                                    modifier = Modifier.weight(0.8f),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text("DROP", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 9.5.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        onSendToRepeater(editableMethod, editableUrl, editableHeaders, editableBody)
                                    },
                                    modifier = Modifier.weight(0.9f),
                                    shape = RoundedCornerShape(4.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text("REPEATER", color = CyberCyan, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
