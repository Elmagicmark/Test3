package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.InterceptedRequestEntity
import com.example.ui.components.CyberCard
import com.example.ui.components.MethodBadge
import com.example.ui.screens.repeater.PrettyJsonViewer
import com.example.ui.theme.*
import java.net.URLDecoder
import java.net.URLEncoder
import android.util.Base64
import org.json.JSONObject

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

    val allMethods = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "CONNECT")
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // Sync selected item
    LaunchedEffect(interceptedList) {
        if (selectedItem == null || interceptedList.none { it.id == selectedItem?.id }) {
            selectedItem = interceptedList.firstOrNull()
        } else {
            selectedItem = interceptedList.find { it.id == selectedItem?.id }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CyberDarkBg)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 1. Intercept Status & Method Filters Master Header Card
        CyberCard(
            borderColor = if (isInterceptEnabled) WarningCrimson else CyberBorder,
            backgroundColor = CyberSurface
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Prominent Status Indicator
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isInterceptEnabled) WarningCrimson.copy(alpha = 0.2f)
                                    else OnCyberSurfaceMuted.copy(alpha = 0.15f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isInterceptEnabled) WarningCrimson else CyberBorder,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (isInterceptEnabled) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                    contentDescription = "Intercept Status",
                                    tint = if (isInterceptEnabled) WarningCrimson else NeonGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (isInterceptEnabled) "INTERCEPT IS ON" else "INTERCEPT IS OFF",
                                    color = if (isInterceptEnabled) WarningCrimson else OnCyberSurfaceMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        if (interceptedList.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(NeonAmber)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${interceptedList.size} QUEUED",
                                    color = Color.Black,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    // Master Toggle Switch
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isInterceptEnabled) "PAUSE" else "CAPTURE",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (isInterceptEnabled) WarningCrimson else NeonGreen,
                            fontWeight = FontWeight.Bold
                        )
                        Switch(
                            checked = isInterceptEnabled,
                            onCheckedChange = { onToggleIntercept(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = WarningCrimson,
                                checkedTrackColor = WarningCrimson.copy(alpha = 0.3f),
                                uncheckedThumbColor = NeonGreen,
                                uncheckedTrackColor = NeonGreen.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier
                                .graphicsLayer(scaleX = 0.85f, scaleY = 0.85f)
                                .testTag("intercept_switch")
                        )
                    }
                }

                Divider(color = CyberBorder, thickness = 0.5.dp)

                // Method Filters Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "FILTER METHODS:",
                        fontSize = 9.sp,
                        color = CyberCyan,
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

        // 2. Active Intercept Workstation
        selectedItem?.let { req ->
            var editableMethod by remember(req.id, req.method) { mutableStateOf(req.method) }
            var editableUrl by remember(req.id, req.url) { mutableStateOf(req.url) }
            var editableHeaders by remember(req.id, req.headersJson) { mutableStateOf(req.headersJson) }
            var editableBody by remember(req.id, req.body) { mutableStateOf(req.body) }
            var editorMode by remember { mutableStateOf("RAW") } // RAW, PRETTY, HEADERS, UTILS

            // Raw Text State
            var rawHttpText by remember(editableMethod, editableUrl, editableHeaders, editableBody) {
                mutableStateOf(buildRawHttpString(editableMethod, editableUrl, editableHeaders, editableBody, req.isResponse))
            }

            // Action Toolbar (FORWARD, DROP, FORWARD ALL, VIEW RESP, REPEATER)
            CyberCard(borderColor = if (req.isResponse) WarningCrimson else CyberCyan) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (req.isResponse) "SPOOF RESPONSE (#${req.id})" else "INTERCEPTED REQUEST (#${req.id})",
                            color = if (req.isResponse) WarningCrimson else CyberCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )

                        // Action Buttons Bar
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                                        Toast.makeText(context, "Forwarded #${req.id}", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        if (req.isResponse) "FORWARD RESP" else "FORWARD",
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            item {
                                // DROP BUTTON
                                Button(
                                    onClick = {
                                        onDrop(req.id)
                                        Toast.makeText(context, "Dropped #${req.id}", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = WarningCrimson, contentColor = Color.White),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("DROP", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                                }
                            }

                            item {
                                // FORWARD ALL BUTTON
                                OutlinedButton(
                                    onClick = {
                                        onForwardAll()
                                        Toast.makeText(context, "Forwarded all queued requests", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(4.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(Icons.Default.FastForward, contentDescription = null, modifier = Modifier.size(12.dp), tint = NeonGreen)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("FORWARD ALL", color = NeonGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (!req.isResponse) {
                                item {
                                    // VIEW / FETCH RESPONSE BUTTON
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
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        if (isFetchingResponse) {
                                            CircularProgressIndicator(modifier = Modifier.size(12.dp), color = Color.Black, strokeWidth = 1.5.dp)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("FETCHING...", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                        } else {
                                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("VIEW RESP", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }

                            item {
                                // SEND TO REPEATER BUTTON
                                OutlinedButton(
                                    onClick = {
                                        onSendToRepeater(editableMethod, editableUrl, editableHeaders, editableBody)
                                        Toast.makeText(context, "Sent to Repeater", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(4.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(12.dp), tint = CyberCyan)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("TO REPEATER", color = CyberCyan, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Queued Strip selector if multiple items present
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

            // Editor Sub-Tabs: RAW, PRETTY, HEADERS, UTILS
            CyberCard(
                modifier = Modifier.weight(1f),
                borderColor = CyberBorder
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("RAW", "PRETTY", "HEADERS", "UTILS").forEach { mode ->
                                val isSelected = editorMode == mode
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isSelected) CyberSurfaceVariant else CyberDarkBg)
                                        .border(
                                            width = if (isSelected) 1.dp else 0.5.dp,
                                            color = if (isSelected) CyberCyan else CyberBorder,
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .clickable { editorMode = mode }
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = mode,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) CyberCyan else OnCyberSurfaceMuted
                                    )
                                }
                            }
                        }

                        // Copy All Action
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(rawHttpText))
                                Toast.makeText(context, "Copied Raw Payload", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = CyberCyan, modifier = Modifier.size(14.dp))
                        }
                    }

                    // Content based on selected mode
                    when (editorMode) {
                        "RAW" -> {
                            Column(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = rawHttpText,
                                    onValueChange = { newRaw ->
                                        rawHttpText = newRaw
                                        parseRawHttpString(newRaw, req.isResponse)?.let { parsed ->
                                            editableMethod = parsed.first
                                            editableUrl = parsed.second
                                            editableHeaders = parsed.third
                                            editableBody = parsed.fourth
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                    textStyle = LocalTextStyle.current.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = OnCyberDark
                                    ),
                                    placeholder = { Text("GET / HTTP/1.1\nHost: target.com", fontSize = 10.sp, color = OnCyberSurfaceMuted) }
                                )
                            }
                        }

                        "PRETTY" -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(CyberDarkBg)
                                    .border(1.dp, CyberBorder, RoundedCornerShape(6.dp))
                                    .padding(6.dp)
                                    .verticalScroll(rememberScrollState())
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                PrettyJsonViewer(jsonString = editableBody)
                            }
                        }

                        "HEADERS" -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedTextField(
                                    value = editableHeaders,
                                    onValueChange = { editableHeaders = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp),
                                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, color = OnCyberDark),
                                    label = { Text("Headers (JSON Format)", fontSize = 9.sp, color = CyberCyan) }
                                )

                                OutlinedTextField(
                                    value = editableBody,
                                    onValueChange = { editableBody = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, color = OnCyberDark),
                                    label = { Text("Payload Body", fontSize = 9.sp, color = NeonGreen) }
                                )
                            }
                        }

                        "UTILS" -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(4.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "ENCODING / DECODING TOOLS",
                                    color = CyberCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Button(
                                        onClick = {
                                            try {
                                                editableUrl = URLEncoder.encode(editableUrl, "UTF-8")
                                                Toast.makeText(context, "URL Encoded", Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) { e.printStackTrace() }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = CyberSurfaceVariant),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("URL ENCODE", fontSize = 9.5.sp, color = CyberCyan, fontFamily = FontFamily.Monospace)
                                    }

                                    Button(
                                        onClick = {
                                            try {
                                                editableUrl = URLDecoder.decode(editableUrl, "UTF-8")
                                                Toast.makeText(context, "URL Decoded", Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) { e.printStackTrace() }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = CyberSurfaceVariant),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("URL DECODE", fontSize = 9.5.sp, color = NeonAmber, fontFamily = FontFamily.Monospace)
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Button(
                                        onClick = {
                                            try {
                                                editableBody = Base64.encodeToString(editableBody.toByteArray(), Base64.NO_WRAP)
                                                Toast.makeText(context, "Base64 Encoded Body", Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) { e.printStackTrace() }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = CyberSurfaceVariant),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("BASE64 ENCODE BODY", fontSize = 9.sp, color = PurpleNeon, fontFamily = FontFamily.Monospace)
                                    }

                                    Button(
                                        onClick = {
                                            try {
                                                editableBody = String(Base64.decode(editableBody, Base64.DEFAULT))
                                                Toast.makeText(context, "Base64 Decoded Body", Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) { e.printStackTrace() }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = CyberSurfaceVariant),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("BASE64 DECODE BODY", fontSize = 9.sp, color = NeonGreen, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }
                    }
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
                            text = if (isInterceptEnabled) "Listening for methods: ${interceptMethods.joinToString(", ")}" else "Turn Intercept ON to capture live GET, POST, PUT, DELETE requests",
                            color = OnCyberSurfaceMuted.copy(alpha = 0.7f),
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { onSimulateTestIntercept?.invoke() },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Icon(Icons.Default.AddAlert, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("GENERATE TEST INTERCEPT REQUEST", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}

private fun buildRawHttpString(method: String, url: String, headersJson: String, body: String, isResponse: Boolean): String {
    val sb = StringBuilder()
    if (isResponse) {
        sb.append("HTTP/1.1 ${method} OK\n")
    } else {
        val path = extractPath(url)
        val host = extractHost(url)
        sb.append("$method $path HTTP/1.1\n")
        if (host.isNotEmpty()) {
            sb.append("Host: $host\n")
        }
    }

    try {
        if (headersJson.trim().startsWith("{")) {
            val obj = JSONObject(headersJson)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                if (!k.equals("Host", ignoreCase = true)) {
                    sb.append("$k: ${obj.getString(k)}\n")
                }
            }
        }
    } catch (e: Exception) {
        // Ignored
    }

    sb.append("User-Agent: InterceptX/1.0\n")
    sb.append("\n")
    sb.append(body)
    return sb.toString()
}

data class InterceptParsedData(val first: String, val second: String, val third: String, val fourth: String)

private fun parseRawHttpString(raw: String, isResponse: Boolean): InterceptParsedData? {
    return try {
        val lines = raw.split("\n")
        if (lines.isEmpty()) return null

        val requestLine = lines[0].trim().split("\\s+".toRegex())
        val parsedMethod = if (requestLine.isNotEmpty()) requestLine[0].uppercase() else "GET"
        val rawPath = if (requestLine.size >= 2) requestLine[1] else "/"

        var host = ""
        val headersMap = mutableMapOf<String, String>()
        var bodyStartIndex = -1

        for (i in 1 until lines.size) {
            val line = lines[i]
            if (line.trim().isEmpty()) {
                bodyStartIndex = i + 1
                break
            }
            val parts = line.split(":", limit = 2)
            if (parts.size == 2) {
                val k = parts[0].trim()
                val v = parts[1].trim()
                if (k.equals("Host", ignoreCase = true)) {
                    host = v
                } else {
                    headersMap[k] = v
                }
            }
        }

        val parsedBody = if (bodyStartIndex != -1 && bodyStartIndex < lines.size) {
            lines.subList(bodyStartIndex, lines.size).joinToString("\n")
        } else {
            ""
        }

        val parsedUrl = if (host.isNotEmpty()) {
            val scheme = if (host.contains("443") || !host.contains("localhost")) "https" else "http"
            "$scheme://$host$rawPath"
        } else {
            rawPath
        }

        val headersJsonObject = JSONObject(headersMap as Map<*, *>).toString()
        InterceptParsedData(parsedMethod, parsedUrl, headersJsonObject, parsedBody)
    } catch (e: Exception) {
        null
    }
}

private fun extractHost(url: String): String {
    if (url.isBlank()) return ""
    var clean = url.removePrefix("http://").removePrefix("https://")
    val idx = clean.indexOf('/')
    if (idx != -1) clean = clean.substring(0, idx)
    return clean
}

private fun extractPath(url: String): String {
    if (url.isBlank()) return "/"
    var clean = url.removePrefix("http://").removePrefix("https://")
    val idx = clean.indexOf('/')
    return if (idx != -1) clean.substring(idx) else "/"
}
