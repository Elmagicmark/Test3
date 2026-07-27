package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.ui.components.CyberCard
import com.example.ui.components.StatusCodeBadge
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposerScreen(
    onExecuteRaw: (String, String, String, String, (Int, String, Long) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    var rawText by remember {
        mutableStateOf(
            "POST /v1/telemetry/event HTTP/1.1\n" +
            "Host: api.target-app.internal\n" +
            "User-Agent: InterceptX-RawComposer/1.0\n" +
            "Content-Type: application/json\n" +
            "Authorization: Bearer sec_raw_token_9918\n" +
            "\n" +
            "{\n" +
            "  \"event_id\": \"ev_88291\",\n" +
            "  \"event_type\": \"security_scan\",\n" +
            "  \"timestamp\": 1774483200\n" +
            "}"
        )
    }

    var lastStatus by remember { mutableStateOf<Int?>(null) }
    var lastResponse by remember { mutableStateOf<String?>(null) }
    var lastTimeMs by remember { mutableStateOf<Long?>(null) }
    var isExecuting by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CyberCard(borderColor = PurpleNeon) {
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
                        Icon(Icons.Default.Terminal, contentDescription = "Composer", tint = PurpleNeon)
                        Text(
                            text = "RAW HTTP PACKET BUILDER",
                            color = OnCyberDark,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Button(
                        onClick = {
                            isExecuting = true
                            // Simple parser for method, url, headers, body
                            val lines = rawText.lines()
                            val firstLine = lines.firstOrNull() ?: "GET / HTTP/1.1"
                            val parts = firstLine.split(" ")
                            val method = if (parts.isNotEmpty()) parts[0] else "GET"
                            val path = if (parts.size >= 2) parts[1] else "/"

                            var host = "api.target-app.internal"
                            val headersMap = mutableMapOf<String, String>()
                            var bodyStartIdx = -1

                            for (i in 1 until lines.size) {
                                val line = lines[i]
                                if (line.trim().isEmpty()) {
                                    bodyStartIdx = i + 1
                                    break
                                }
                                val kv = line.split(":")
                                if (kv.size >= 2) {
                                    val k = kv[0].trim()
                                    val v = line.substring(line.indexOf(":") + 1).trim()
                                    headersMap[k] = v
                                    if (k.equals("Host", ignoreCase = true)) host = v
                                }
                            }

                            val targetUrl = if (path.startsWith("http")) path else "https://$host$path"
                            val headersJson = "{" + headersMap.entries.joinToString(",") { "\"${it.key}\":\"${it.value}\"" } + "}"
                            val bodyPayload = if (bodyStartIdx != -1 && bodyStartIdx < lines.size) {
                                lines.subList(bodyStartIdx, lines.size).joinToString("\n")
                            } else ""

                            onExecuteRaw(method, targetUrl, headersJson, bodyPayload) { status, resp, time ->
                                isExecuting = false
                                lastStatus = status
                                lastResponse = resp
                                lastTimeMs = time
                                Toast.makeText(context, "Packet Sent: HTTP $status", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PurpleNeon, contentColor = Color.White),
                        shape = RoundedCornerShape(4.dp),
                        enabled = !isExecuting,
                        modifier = Modifier.testTag("execute_raw_packet_button")
                    ) {
                        if (isExecuting) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("EXECUTE PACKET", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                Text(
                    text = "Craft low-level raw HTTP request packets line by line (Method, Headers, and Payload):",
                    color = OnCyberSurfaceMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )

                OutlinedTextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    textStyle = LocalTextStyle.current.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = NeonGreen
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PurpleNeon,
                        unfocusedBorderColor = CyberBorder,
                        focusedContainerColor = CyberDarkBg,
                        unfocusedContainerColor = CyberDarkBg
                    )
                )
            }
        }

        // Response Result Box
        CyberCard(borderColor = CyberCyan) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RAW PACKET RESPONSE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberCyan,
                        fontFamily = FontFamily.Monospace
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        lastStatus?.let { code -> StatusCodeBadge(statusCode = code) }
                        lastTimeMs?.let { time -> Text("$time ms", fontSize = 11.sp, color = OnCyberSurfaceMuted, fontFamily = FontFamily.Monospace) }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 240.dp)
                        .background(CyberDarkBg)
                        .border(1.dp, CyberBorder, RoundedCornerShape(4.dp))
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = lastResponse ?: "No raw packet execution response.",
                        color = if (lastResponse != null) OnCyberDark else OnCyberSurfaceMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
