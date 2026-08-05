package com.example.ui.screens.repeater

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

enum class DetectedContentType {
    JSON,
    XML,
    FORM_URL_ENCODED,
    PLAIN_TEXT
}

@Composable
fun PrettyBodyViewer(
    body: String,
    headersJson: String? = null,
    modifier: Modifier = Modifier
) {
    val detectedType = remember(body, headersJson) {
        detectBodyType(body, headersJson)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Detected Content Type Badge Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(getContentTypeBadgeBg(detectedType))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "FORMAT: ${detectedType.name}",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = getContentTypeBadgeFg(detectedType)
                )
            }
        }

        // View Content
        when (detectedType) {
            DetectedContentType.JSON -> {
                PrettyJsonViewer(jsonString = body)
            }

            DetectedContentType.XML -> {
                PrettyXmlViewer(xmlString = body)
            }

            DetectedContentType.FORM_URL_ENCODED -> {
                FormUrlEncodedViewer(body = body)
            }

            DetectedContentType.PLAIN_TEXT -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CyberDarkBg)
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                ) {
                    Text(
                        text = body,
                        color = OnCyberDark,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun FormUrlEncodedViewer(body: String) {
    val params = remember(body) {
        body.split("&").mapNotNull { pair ->
            val parts = pair.split("=", limit = 2)
            if (parts.isNotEmpty()) {
                val key = parts[0]
                val value = if (parts.size > 1) parts[1] else ""
                key to value
            } else null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberDarkBg)
            .padding(8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        params.forEach { (key, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CyberSurface)
                    .border(0.5.dp, CyberBorder, RoundedCornerShape(4.dp))
                    .padding(6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = CyberCyan, fontWeight = FontWeight.Bold)) {
                            append(key)
                        }
                        withStyle(SpanStyle(color = OnCyberDark)) {
                            append(" = ")
                        }
                        withStyle(SpanStyle(color = NeonGreen)) {
                            append(value)
                        }
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.5.sp
                )
            }
        }
    }
}

private fun detectBodyType(body: String, headersJson: String?): DetectedContentType {
    val trimmed = body.trim()
    if (trimmed.isEmpty()) return DetectedContentType.PLAIN_TEXT

    // 1. Check Headers if available
    headersJson?.let {
        val lower = it.lowercase()
        if (lower.contains("application/json")) return DetectedContentType.JSON
        if (lower.contains("text/xml") || lower.contains("application/xml") || lower.contains("text/html")) return DetectedContentType.XML
        if (lower.contains("application/x-www-form-urlencoded")) return DetectedContentType.FORM_URL_ENCODED
    }

    // 2. Sniff content structure
    if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
        return DetectedContentType.JSON
    }

    if (trimmed.startsWith("<") && (trimmed.endsWith(">") || trimmed.contains("</"))) {
        return DetectedContentType.XML
    }

    if (trimmed.contains("=") && trimmed.contains("&") && !trimmed.contains(" ") && !trimmed.contains("\n")) {
        return DetectedContentType.FORM_URL_ENCODED
    }

    return DetectedContentType.PLAIN_TEXT
}

private fun getContentTypeBadgeBg(type: DetectedContentType): Color {
    return when (type) {
        DetectedContentType.JSON -> NeonGreen.copy(alpha = 0.2f)
        DetectedContentType.XML -> PurpleNeon.copy(alpha = 0.2f)
        DetectedContentType.FORM_URL_ENCODED -> CyberCyan.copy(alpha = 0.2f)
        DetectedContentType.PLAIN_TEXT -> CyberBorder
    }
}

private fun getContentTypeBadgeFg(type: DetectedContentType): Color {
    return when (type) {
        DetectedContentType.JSON -> NeonGreen
        DetectedContentType.XML -> PurpleNeon
        DetectedContentType.FORM_URL_ENCODED -> CyberCyan
        DetectedContentType.PLAIN_TEXT -> OnCyberDark
    }
}
