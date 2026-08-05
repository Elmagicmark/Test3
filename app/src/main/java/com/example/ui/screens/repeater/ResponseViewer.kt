package com.example.ui.screens.repeater

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.CyberCard
import com.example.ui.components.StatusCodeBadge
import com.example.ui.theme.*
import org.json.JSONObject

@Composable
fun ResponseViewer(
    statusCode: Int?,
    responseTimeMs: Long?,
    responseHeadersJson: String?,
    responseBody: String?,
    modifier: Modifier = Modifier
) {
    var activeTabMode by remember { mutableStateOf("PRETTY") } // PRETTY, RAW, HEADERS, BODY, HEX
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val bodyLength = responseBody?.length ?: 0
    val sizeText = when {
        bodyLength < 1024 -> "$bodyLength B"
        bodyLength < 1024 * 1024 -> String.format("%.1f KB", bodyLength / 1024.0)
        else -> String.format("%.2f MB", bodyLength / (1024.0 * 1024.0))
    }

    val parsedHeaders = remember(responseHeadersJson) {
        parseHeadersMap(responseHeadersJson)
    }

    CyberCard(
        modifier = modifier.fillMaxWidth(),
        borderColor = NeonGreen,
        backgroundColor = CyberSurface
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Response Top Toolbar / Status Info Bar
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
                        text = "RESPONSE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonGreen,
                        fontFamily = FontFamily.Monospace
                    )

                    statusCode?.let { code ->
                        StatusCodeBadge(statusCode = code)
                        Text(
                            text = getHttpStatusText(code),
                            color = getHttpStatusColor(code),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    responseTimeMs?.let { time ->
                        Text(
                            text = "$time ms",
                            fontSize = 10.sp,
                            color = OnCyberSurfaceMuted,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    if (responseBody != null) {
                        Text(
                            text = "• $sizeText",
                            fontSize = 10.sp,
                            color = OnCyberSurfaceMuted,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Copy Response Button
                if (responseBody != null) {
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(responseBody))
                            Toast.makeText(context, "Response copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Response",
                            tint = CyberCyan,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Inner Tabs Row: Pretty, Raw, Headers, Body, Hex
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("PRETTY", "RAW", "HEADERS", "BODY", "HEX").forEach { mode ->
                    val isSelected = activeTabMode == mode
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) CyberSurfaceVariant else CyberDarkBg)
                            .border(
                                width = if (isSelected) 1.dp else 0.5.dp,
                                color = if (isSelected) CyberCyan else CyberBorder,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .clickable { activeTabMode = mode }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = mode,
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) CyberCyan else OnCyberSurfaceMuted
                        )
                    }
                }
            }

            // Response Body / Inspector Output Content Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 320.dp)
                    .background(CyberDarkBg)
                    .border(1.dp, CyberBorder, RoundedCornerShape(6.dp))
                    .padding(6.dp)
            ) {
                if (responseBody == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No response received yet.\nClick 'SEND REQUEST' to execute.",
                            color = OnCyberSurfaceMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    when (activeTabMode) {
                        "PRETTY" -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                PrettyBodyViewer(
                                    body = responseBody,
                                    headersJson = responseHeadersJson
                                )
                            }
                        }

                        "RAW" -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                val rawText = buildString {
                                    statusCode?.let { append("HTTP/1.1 $it ${getHttpStatusText(it)}\n") }
                                    parsedHeaders.forEach { (k, v) -> append("$k: $v\n") }
                                    append("\n")
                                    append(responseBody)
                                }
                                Text(
                                    text = rawText,
                                    color = OnCyberDark,
                                    fontSize = 10.5.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        "HEADERS" -> {
                            if (parsedHeaders.isEmpty()) {
                                Text("No headers present", color = OnCyberSurfaceMuted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(parsedHeaders.toList()) { (key, value) ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(CyberSurface)
                                                .padding(4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = key,
                                                color = CyberCyan,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.weight(0.4f)
                                            )
                                            Text(
                                                text = value,
                                                color = OnCyberDark,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.weight(0.6f)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        "BODY" -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = responseBody,
                                    color = OnCyberDark,
                                    fontSize = 10.5.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        "HEX" -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = generateHexDump(responseBody),
                                    color = NeonAmber,
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
}

private fun parseHeadersMap(headersJson: String?): Map<String, String> {
    if (headersJson.isNullOrBlank()) return emptyMap()
    return try {
        val map = mutableMapOf<String, String>()
        val obj = JSONObject(headersJson)
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            map[k] = obj.getString(k)
        }
        map
    } catch (e: Exception) {
        emptyMap()
    }
}

private fun getHttpStatusText(code: Int): String {
    return when (code) {
        200 -> "OK"
        201 -> "Created"
        204 -> "No Content"
        301 -> "Moved Permanently"
        302 -> "Found"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        403 -> "Forbidden"
        404 -> "Not Found"
        500 -> "Internal Server Error"
        502 -> "Bad Gateway"
        503 -> "Service Unavailable"
        else -> "Status"
    }
}

private fun getHttpStatusColor(code: Int): Color {
    return when (code) {
        in 200..299 -> NeonGreen
        in 300..399 -> CyberCyan
        in 400..499 -> NeonAmber
        in 500..599 -> WarningCrimson
        else -> OnCyberSurfaceMuted
    }
}

private fun generateHexDump(text: String): String {
    val bytes = text.toByteArray(Charsets.UTF_8)
    val sb = StringBuilder()
    val chunkSize = 16

    for (i in bytes.indices step chunkSize) {
        val chunk = bytes.sliceArray(i until minOf(i + chunkSize, bytes.size))
        sb.append(String.format("%08x  ", i))

        // Hex representation
        for (j in 0 until chunkSize) {
            if (j < chunk.size) {
                sb.append(String.format("%02x ", chunk[j]))
            } else {
                sb.append("   ")
            }
            if (j == 7) sb.append(" ")
        }

        sb.append(" |")
        // ASCII representation
        for (b in chunk) {
            val char = b.toInt().toChar()
            if (char in ' '..'~') {
                sb.append(char)
            } else {
                sb.append('.')
            }
        }
        sb.append("|\n")
    }
    return sb.toString()
}
