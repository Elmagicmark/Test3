package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import com.example.ui.theme.*
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import android.util.Base64
import org.json.JSONObject

enum class TransformType(val label: String) {
    URL("URL"),
    BASE64("Base64"),
    HEX("Hex"),
    HTML("HTML"),
    JWT("JWT Decoder"),
    HASH("Hash (MD5/SHA)")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecoderScreen(
    onSendToRepeater: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by remember {
        mutableStateOf(
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkFsZXggSHVudGVyIiwiaWF0IjoxNTE2MjM5MDIyLCJyb2xlIjoiYWRtaW4ifQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"
        )
    }

    var selectedType by remember { mutableStateOf(TransformType.JWT) }
    var outputText by remember { mutableStateOf("") }
    var jwtHeader by remember { mutableStateOf("") }
    var jwtPayload by remember { mutableStateOf("") }
    var jwtSignature by remember { mutableStateOf("") }

    val context = LocalContext.current

    // Perform real-time transformation
    LaunchedEffect(inputText, selectedType) {
        val input = inputText.trim()
        if (input.isEmpty()) {
            outputText = ""
            jwtHeader = ""
            jwtPayload = ""
            jwtSignature = ""
            return@LaunchedEffect
        }

        try {
            when (selectedType) {
                TransformType.URL -> {
                    outputText = try {
                        val decoded = URLDecoder.decode(input, "UTF-8")
                        if (decoded == input) URLEncoder.encode(input, "UTF-8") else decoded
                    } catch (e: Exception) {
                        URLEncoder.encode(input, "UTF-8")
                    }
                }
                TransformType.BASE64 -> {
                    outputText = try {
                        val decodedBytes = Base64.decode(input, Base64.DEFAULT)
                        String(decodedBytes, Charsets.UTF_8)
                    } catch (e: Exception) {
                        Base64.encodeToString(input.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                    }
                }
                TransformType.HEX -> {
                    outputText = if (input.matches(Regex("^[0-9a-fA-F\\s]+$")) && input.replace(" ", "").length % 2 == 0) {
                        try {
                            val hex = input.replace(" ", "")
                            val bytes = ByteArray(hex.length / 2) { i ->
                                hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
                            }
                            String(bytes, Charsets.UTF_8)
                        } catch (e: Exception) {
                            input.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }
                        }
                    } else {
                        input.toByteArray(Charsets.UTF_8).joinToString(" ") { "%02X".format(it) }
                    }
                }
                TransformType.HTML -> {
                    outputText = input
                        .replace("&amp;", "&")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                        .replace("&quot;", "\"")
                        .replace("&#39;", "'")
                    if (outputText == input) {
                        outputText = input
                            .replace("&", "&amp;")
                            .replace("<", "&lt;")
                            .replace(">", "&gt;")
                            .replace("\"", "&quot;")
                            .replace("'", "&#39;")
                    }
                }
                TransformType.JWT -> {
                    val tokenParts = input.removePrefix("Bearer ").trim().split(".")
                    if (tokenParts.size >= 2) {
                        jwtHeader = try {
                            val decoded = String(Base64.decode(tokenParts[0], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
                            JSONObject(decoded).toString(2)
                        } catch (e: Exception) { "Invalid JWT Header" }

                        jwtPayload = try {
                            val decoded = String(Base64.decode(tokenParts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
                            JSONObject(decoded).toString(2)
                        } catch (e: Exception) { "Invalid JWT Payload" }

                        jwtSignature = if (tokenParts.size > 2) tokenParts[2] else "Unsigned JWT"
                        outputText = "JWT Decoded Successfully"
                    } else {
                        jwtHeader = "Not a valid 3-part JWT token"
                        jwtPayload = "Expected header.payload.signature"
                        jwtSignature = ""
                        outputText = "Invalid JWT Format"
                    }
                }
                TransformType.HASH -> {
                    val md5 = MessageDigest.getInstance("MD5").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
                    val sha1 = MessageDigest.getInstance("SHA-1").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
                    val sha256 = MessageDigest.getInstance("SHA-256").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
                    outputText = "MD5:    $md5\nSHA1:   $sha1\nSHA256: $sha256"
                }
            }
        } catch (e: Exception) {
            outputText = "Error in conversion: ${e.localizedMessage}"
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Mode Selector Bar
        CyberCard(borderColor = CyberCyan) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "DECODER & ENCODER LAB",
                    color = CyberCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TransformType.values().forEach { type ->
                        val selected = selectedType == type
                        FilterChip(
                            selected = selected,
                            onClick = { selectedType = type },
                            label = { Text(type.label, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyberCyan,
                                selectedLabelColor = Color.Black,
                                containerColor = CyberDarkBg,
                                labelColor = OnCyberSurfaceMuted
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = CyberBorder,
                                selectedBorderColor = CyberCyan
                            )
                        )
                    }
                }
            }
        }

        // Input Box
        CyberCard(borderColor = NeonAmber) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "INPUT RAW PAYLOAD",
                        color = NeonAmber,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clipData = clipboard.primaryClip
                            if (clipData != null && clipData.itemCount > 0) {
                                inputText = clipData.getItemAt(0).text.toString()
                                Toast.makeText(context, "Pasted from Clipboard", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = NeonAmber, modifier = Modifier.size(18.dp))
                        }

                        IconButton(onClick = { inputText = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = WarningCrimson, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = OnCyberDark),
                    placeholder = { Text("Enter text, URL string, Base64, Hex or JWT token...", fontSize = 11.sp, color = OnCyberSurfaceMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonAmber,
                        unfocusedBorderColor = CyberBorder,
                        focusedContainerColor = CyberDarkBg,
                        unfocusedContainerColor = CyberDarkBg
                    )
                )

                // Quick Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = {
                            try { outputText = URLEncoder.encode(inputText, "UTF-8") } catch (_: Exception) {}
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberSurfaceVariant, contentColor = CyberCyan),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("URL ENC", fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            try { outputText = URLDecoder.decode(inputText, "UTF-8") } catch (_: Exception) {}
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberSurfaceVariant, contentColor = NeonGreen),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("URL DEC", fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            try { outputText = Base64.encodeToString(inputText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP) } catch (_: Exception) {}
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberSurfaceVariant, contentColor = NeonAmber),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("B64 ENC", fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            try { outputText = String(Base64.decode(inputText, Base64.DEFAULT), Charsets.UTF_8) } catch (_: Exception) {}
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberSurfaceVariant, contentColor = PurpleNeon),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("B64 DEC", fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Output Result Box
        if (selectedType == TransformType.JWT) {
            // JWT Formatted Displays
            CyberCard(borderColor = CyberCyan) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("JWT HEADER (ALGORITHM)", color = CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("JWT Header", jwtHeader))
                            Toast.makeText(context, "Header Copied", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = CyberCyan, modifier = Modifier.size(16.dp))
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CyberDarkBg)
                            .border(1.dp, CyberBorder, RoundedCornerShape(4.dp))
                            .padding(8.dp)
                    ) {
                        Text(jwtHeader, color = NeonAmber, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("JWT PAYLOAD (CLAIMS)", color = NeonGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("JWT Payload", jwtPayload))
                            Toast.makeText(context, "Payload Copied", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = NeonGreen, modifier = Modifier.size(16.dp))
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CyberDarkBg)
                            .border(1.dp, CyberBorder, RoundedCornerShape(4.dp))
                            .padding(8.dp)
                    ) {
                        Text(jwtPayload, color = NeonGreen, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }

                    Text("JWT SIGNATURE", color = PurpleNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CyberDarkBg)
                            .border(1.dp, CyberBorder, RoundedCornerShape(4.dp))
                            .padding(8.dp)
                    ) {
                        Text(jwtSignature, color = PurpleNeon, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        } else {
            // General Output Box
            CyberCard(borderColor = NeonGreen) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TRANSFORMED OUTPUT (${selectedType.label})",
                            color = NeonGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Transformed Output", outputText))
                                Toast.makeText(context, "Output Copied", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = NeonGreen, modifier = Modifier.size(18.dp))
                            }

                            IconButton(onClick = {
                                val temp = inputText
                                inputText = outputText
                                outputText = temp
                            }) {
                                Icon(Icons.Default.SwapVert, contentDescription = "Swap", tint = CyberCyan, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 220.dp)
                            .background(CyberDarkBg)
                            .border(1.dp, CyberBorder, RoundedCornerShape(4.dp))
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = outputText.ifEmpty { "Transformed payload result will appear here..." },
                            color = if (outputText.isNotEmpty()) NeonGreen else OnCyberSurfaceMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
