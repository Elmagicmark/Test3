package com.example.ui.screens.repeater

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.RepeaterTabEntity
import com.example.ui.components.CyberCard
import com.example.ui.theme.*
import java.net.URLDecoder
import java.net.URLEncoder
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestEditor(
    tab: RepeaterTabEntity,
    isLoading: Boolean,
    onSaveTab: (RepeaterTabEntity) -> Unit,
    onSendRequest: (RepeaterTabEntity) -> Unit,
    onToggleHistory: () -> Unit,
    onDuplicateTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    var method by remember(tab.id, tab.method) { mutableStateOf(tab.method) }
    var url by remember(tab.id, tab.url) { mutableStateOf(tab.url) }
    var headersJson by remember(tab.id, tab.headersJson) { mutableStateOf(tab.headersJson) }
    var body by remember(tab.id, tab.body) { mutableStateOf(tab.body) }

    var editorMode by remember { mutableStateOf("RAW") } // RAW vs PARAMETERS
    var isMethodDropdownExpanded by remember { mutableStateOf(false) }

    val httpMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD")
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    // Generate Raw HTTP string format
    var rawText by remember(method, url, headersJson, body) {
        mutableStateOf(generateRawHttpRequestString(method, url, headersJson, body))
    }

    CyberCard(
        modifier = modifier.fillMaxWidth(),
        borderColor = CyberCyan,
        backgroundColor = CyberSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Top Toolbar: Method, URL, Send Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Method Selector Dropdown
                Box {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(CyberSurfaceVariant)
                            .border(1.dp, getMethodColor(method), RoundedCornerShape(6.dp))
                            .clickable { isMethodDropdownExpanded = true }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = method,
                                color = getMethodColor(method),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Method", tint = CyberCyan, modifier = Modifier.size(16.dp))
                        }
                    }

                    DropdownMenu(
                        expanded = isMethodDropdownExpanded,
                        onDismissRequest = { isMethodDropdownExpanded = false },
                        modifier = Modifier.background(CyberSurface)
                    ) {
                        httpMethods.forEach { m ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = m,
                                        color = getMethodColor(m),
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                },
                                onClick = {
                                    method = m
                                    isMethodDropdownExpanded = false
                                    rawText = generateRawHttpRequestString(method, url, headersJson, body)
                                }
                            )
                        }
                    }
                }

                // URL Input Field
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        rawText = generateRawHttpRequestString(method, url, headersJson, body)
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = OnCyberDark),
                    placeholder = { Text("https://target.com/api/v1/resource", fontSize = 10.sp, color = OnCyberSurfaceMuted) }
                )

                // Send Button
                Button(
                    onClick = {
                        val currentTab = tab.copy(method = method, url = url, headersJson = headersJson, body = body)
                        onSaveTab(currentTab)
                        onSendRequest(currentTab)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black),
                    shape = RoundedCornerShape(6.dp),
                    enabled = !isLoading,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("send_repeater_request_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.Black, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Send, contentDescription = "Send", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("SEND", fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            // Context Utilities Toolbar (Save, History, URL Encode/Decode, Copy, Mode toggle)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Mode Selector (RAW vs STRUCTURED)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("RAW", "STRUCTURED").forEach { mode ->
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
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = mode,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) CyberCyan else OnCyberSurfaceMuted
                            )
                        }
                    }
                }

                // Right: Quick Context Actions (Save, History, Encode/Decode, Copy Raw)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Quick URL Encode Button
                    IconButton(
                        onClick = {
                            try {
                                url = URLEncoder.encode(url, "UTF-8")
                                rawText = generateRawHttpRequestString(method, url, headersJson, body)
                                Toast.makeText(context, "URL Encoded", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Code, contentDescription = "Encode URL", tint = CyberCyan, modifier = Modifier.size(14.dp))
                    }

                    // Quick URL Decode Button
                    IconButton(
                        onClick = {
                            try {
                                url = URLDecoder.decode(url, "UTF-8")
                                rawText = generateRawHttpRequestString(method, url, headersJson, body)
                                Toast.makeText(context, "URL Decoded", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.LockOpen, contentDescription = "Decode URL", tint = NeonAmber, modifier = Modifier.size(14.dp))
                    }

                    // Copy Raw HTTP Request
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(generateRawHttpRequestString(method, url, headersJson, body)))
                            Toast.makeText(context, "Raw HTTP Request copied", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Raw", tint = NeonGreen, modifier = Modifier.size(14.dp))
                    }

                    // Duplicate Tab Button
                    IconButton(
                        onClick = onDuplicateTab,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate Tab", tint = PurpleNeon, modifier = Modifier.size(14.dp))
                    }

                    // Toggle History Drawer Panel
                    IconButton(
                        onClick = onToggleHistory,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.History, contentDescription = "History", tint = CyberCyan, modifier = Modifier.size(15.dp))
                    }
                }
            }

            // Editor Input View
            if (editorMode == "RAW") {
                Column {
                    Text(
                        text = "HTTP RAW EDITOR",
                        fontSize = 9.sp,
                        color = CyberCyan,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = rawText,
                        onValueChange = { newRaw ->
                            rawText = newRaw
                            parseRawHttpRequestString(newRaw)?.let { parsed ->
                                method = parsed.first
                                url = parsed.second
                                headersJson = parsed.third
                                body = parsed.fourth
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = OnCyberDark
                        ),
                        placeholder = { Text("GET /api/v1 HTTP/1.1\nHost: example.com", fontSize = 10.sp, color = OnCyberSurfaceMuted) }
                    )
                }
            } else {
                // STRUCTURED FORM EDITOR
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = headersJson,
                        onValueChange = {
                            headersJson = it
                            rawText = generateRawHttpRequestString(method, url, headersJson, body)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, color = OnCyberDark),
                        label = { Text("Headers (JSON)", fontSize = 9.5.sp, color = CyberCyan) },
                        placeholder = { Text("{\"Content-Type\":\"application/json\"}", fontSize = 9.5.sp) }
                    )

                    OutlinedTextField(
                        value = body,
                        onValueChange = {
                            body = it
                            rawText = generateRawHttpRequestString(method, url, headersJson, body)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(95.dp),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, color = OnCyberDark),
                        label = { Text("Request Body Payload", fontSize = 9.5.sp, color = NeonGreen) },
                        placeholder = { Text("{\"key\":\"value\"}", fontSize = 9.5.sp) }
                    )
                }
            }
        }
    }
}

private fun getMethodColor(method: String): Color {
    return when (method.uppercase()) {
        "GET" -> NeonGreen
        "POST" -> CyberCyan
        "PUT" -> NeonAmber
        "DELETE" -> WarningCrimson
        "PATCH" -> PurpleNeon
        else -> OnCyberDark
    }
}

private fun generateRawHttpRequestString(method: String, url: String, headersJson: String, body: String): String {
    val host = extractHostFromUrl(url)
    val path = extractPathFromUrl(url)

    val sb = StringBuilder()
    sb.append("$method $path HTTP/1.1\n")
    if (host.isNotEmpty()) {
        sb.append("Host: $host\n")
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
        // Fallback
    }

    sb.append("User-Agent: InterceptX-Repeater/1.0\n")
    sb.append("\n")
    sb.append(body)
    return sb.toString()
}

private fun parseRawHttpRequestString(raw: String): Quadruple<String, String, String, String>? {
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
        Quadruple(parsedMethod, parsedUrl, headersJsonObject, parsedBody)
    } catch (e: Exception) {
        null
    }
}

private fun extractHostFromUrl(url: String): String {
    if (url.isBlank()) return ""
    var clean = url.removePrefix("http://").removePrefix("https://")
    val idx = clean.indexOf('/')
    if (idx != -1) clean = clean.substring(0, idx)
    return clean
}

private fun extractPathFromUrl(url: String): String {
    if (url.isBlank()) return "/"
    var clean = url.removePrefix("http://").removePrefix("https://")
    val idx = clean.indexOf('/')
    return if (idx != -1) clean.substring(idx) else "/"
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
