package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.HttpTransactionEntity
import com.example.ui.components.MethodBadge
import com.example.ui.components.StatusCodeBadge
import com.example.ui.theme.*

@Composable
fun TransactionDetailDialog(
    transaction: HttpTransactionEntity,
    onDismiss: () -> Unit,
    onSendToRepeater: (String, String, String, String) -> Unit
) {
    var selectedTab by remember { mutableStateOf("REQUEST") } // REQUEST vs RESPONSE

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(8.dp),
            color = CyberSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MethodBadge(method = transaction.method)
                        StatusCodeBadge(statusCode = transaction.statusCode)
                        Text(
                            text = "${transaction.responseTimeMs} ms",
                            color = OnCyberCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(CyberCyan)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val context = androidx.compose.ui.platform.LocalContext.current
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("URL", transaction.url)
                            clipboard.setPrimaryClip(clip)
                            android.widget.Toast.makeText(context, "URL Copied to Clipboard", android.widget.Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy URL", tint = CyberCyan, modifier = Modifier.size(18.dp))
                        }

                        IconButton(onClick = {
                            onSendToRepeater(
                                transaction.method,
                                transaction.url,
                                transaction.requestHeadersJson,
                                transaction.requestBody
                            )
                            onDismiss()
                        }) {
                            Icon(Icons.Default.Repeat, contentDescription = "To Repeater", tint = NeonGreen, modifier = Modifier.size(18.dp))
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = WarningCrimson, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                // URL Display Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CyberDarkBg)
                        .border(1.dp, CyberBorder, RoundedCornerShape(6.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = transaction.url,
                        color = OnCyberDark,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Request/Response Tab Switcher
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { selectedTab = "REQUEST" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedTab == "REQUEST") CyberCyan else CyberSurfaceVariant,
                            contentColor = if (selectedTab == "REQUEST") Color.Black else OnCyberDark
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Req", modifier = Modifier.size(14.dp))
                            Text("REQUEST", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = { selectedTab = "RESPONSE" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedTab == "RESPONSE") NeonGreen else CyberSurfaceVariant,
                            contentColor = if (selectedTab == "RESPONSE") Color.Black else OnCyberDark
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Resp", modifier = Modifier.size(14.dp))
                            Text("RESPONSE", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Tab Content Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (selectedTab == "REQUEST") {
                        Text("Headers:", color = CyberCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CyberDarkBg)
                                .border(1.dp, CyberBorder, RoundedCornerShape(4.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = transaction.requestHeadersJson,
                                color = OnCyberDark,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Body Payload:", color = CyberCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 100.dp)
                                .background(CyberDarkBg)
                                .border(1.dp, CyberBorder, RoundedCornerShape(4.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = if (transaction.requestBody.isNotBlank()) transaction.requestBody else "<Empty Body>",
                                color = if (transaction.requestBody.isNotBlank()) OnCyberDark else OnCyberSurfaceMuted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else {
                        Text("Response Headers:", color = NeonGreen, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CyberDarkBg)
                                .border(1.dp, CyberBorder, RoundedCornerShape(4.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = transaction.responseHeadersJson,
                                color = OnCyberDark,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Response Body Payload:", color = NeonGreen, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp)
                                .background(CyberDarkBg)
                                .border(1.dp, CyberBorder, RoundedCornerShape(4.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = if (transaction.responseBody.isNotBlank()) transaction.responseBody else "<Empty Response>",
                                color = if (transaction.responseBody.isNotBlank()) OnCyberDark else OnCyberSurfaceMuted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}
