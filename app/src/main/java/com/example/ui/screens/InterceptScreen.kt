package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
    onToggleIntercept: (Boolean) -> Unit,
    onForward: (Long, String, String, String, String) -> Unit,
    onDrop: (Long) -> Unit,
    onForwardAll: () -> Unit,
    onSendToRepeater: (String, String, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedItem by remember { mutableStateOf<InterceptedRequestEntity?>(null) }

    // Sync selected item
    LaunchedEffect(interceptedList) {
        if (selectedItem == null || !interceptedList.contains(selectedItem)) {
            selectedItem = interceptedList.firstOrNull()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Intercept Status & Control Bar
        CyberCard(
            borderColor = if (isInterceptEnabled) WarningCrimson else CyberBorder
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isInterceptEnabled) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                        contentDescription = "Intercept Status",
                        tint = if (isInterceptEnabled) WarningCrimson else NeonGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "INTERCEPT MODE",
                            color = OnCyberDark,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (isInterceptEnabled) "INTERCEPT IS ON - Holding Request" else "INTERCEPT IS OFF - Traffic Passthrough",
                            color = if (isInterceptEnabled) WarningCrimson else OnCyberSurfaceMuted,
                            fontSize = 11.sp,
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
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "NO INTERCEPTED REQUESTS QUEUED",
                            color = OnCyberSurfaceMuted,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Turn Intercept ON to pause live client HTTP/HTTPS requests",
                            color = OnCyberSurfaceMuted.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 4.dp)
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
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                OutlinedButton(
                    onClick = { onForwardAll() },
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen)
                ) {
                    Text(
                        text = "FORWARD ALL",
                        color = NeonGreen,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // List of Queued Items
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
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
                            .padding(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                MethodBadge(method = item.method)
                                Text(
                                    text = item.url,
                                    color = OnCyberDark,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Button(
                                onClick = { selectedItem = item },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberSurfaceVariant)
                            ) {
                                Text(
                                    text = if (isSelected) "Editing" else "Edit",
                                    fontSize = 10.sp,
                                    color = if (isSelected) CyberCyan else OnCyberDark
                                )
                            }
                        }
                    }
                }
            }

            // Request Interactive Editor View
            selectedItem?.let { req ->
                var editableMethod by remember(req.id) { mutableStateOf(req.method) }
                var editableUrl by remember(req.id) { mutableStateOf(req.url) }
                var editableHeaders by remember(req.id) { mutableStateOf(req.headersJson) }
                var editableBody by remember(req.id) { mutableStateOf(req.body) }

                CyberCard(
                    modifier = Modifier.weight(1f),
                    borderColor = CyberCyan
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Method & URL row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = editableMethod,
                                onValueChange = { editableMethod = it },
                                modifier = Modifier.width(90.dp),
                                textStyle = LocalTextStyle.current.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = CyberCyan
                                ),
                                label = { Text("Method", fontSize = 10.sp) }
                            )

                            OutlinedTextField(
                                value = editableUrl,
                                onValueChange = { editableUrl = it },
                                modifier = Modifier.weight(1f),
                                textStyle = LocalTextStyle.current.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = OnCyberDark
                                ),
                                label = { Text("Target URL", fontSize = 10.sp) }
                            )
                        }

                        // Headers JSON Field
                        OutlinedTextField(
                            value = editableHeaders,
                            onValueChange = { editableHeaders = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp),
                            textStyle = LocalTextStyle.current.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = OnCyberDark
                            ),
                            label = { Text("Request Headers (JSON)", fontSize = 10.sp) }
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
                                fontSize = 11.sp,
                                color = OnCyberDark
                            ),
                            label = { Text("Request Body", fontSize = 10.sp) }
                        )

                        // Action Buttons: Forward, Drop, Send to Repeater
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    onForward(req.id, editableMethod, editableUrl, editableHeaders, editableBody)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text("FORWARD", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                            }

                            Button(
                                onClick = { onDrop(req.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = WarningCrimson, contentColor = Color.White),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text("DROP", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    onSendToRepeater(editableMethod, editableUrl, editableHeaders, editableBody)
                                },
                                modifier = Modifier.weight(1.2f),
                                shape = RoundedCornerShape(4.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan)
                            ) {
                                Text("TO REPEATER", color = CyberCyan, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
