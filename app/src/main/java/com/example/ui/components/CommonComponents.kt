package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun MethodBadge(method: String, modifier: Modifier = Modifier) {
    val (bgColor, textColor) = when (method.uppercase()) {
        "GET" -> Pair(Color(0xFF00382B), NeonGreen)
        "POST" -> Pair(Color(0xFF00334E), CyberCyan)
        "PUT" -> Pair(Color(0xFF3B2E00), NeonAmber)
        "DELETE" -> Pair(Color(0xFF4A0E17), WarningCrimson)
        "PATCH" -> Pair(Color(0xFF2A004D), PurpleNeon)
        else -> Pair(CyberSurfaceVariant, OnCyberDark)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .border(1.dp, textColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = method.uppercase(),
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun StatusCodeBadge(statusCode: Int, modifier: Modifier = Modifier) {
    val (bgColor, textColor) = when (statusCode) {
        in 200..299 -> Pair(Color(0xFF003814), NeonGreen)
        in 300..399 -> Pair(Color(0xFF00334E), CyberCyan)
        in 400..499 -> Pair(Color(0xFF3B2E00), NeonAmber)
        in 500..599 -> Pair(Color(0xFF4A0E17), WarningCrimson)
        else -> Pair(CyberSurfaceVariant, OnCyberSurfaceMuted)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .border(0.5.dp, textColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = statusCode.toString(),
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun CyberCard(
    modifier: Modifier = Modifier,
    borderColor: Color = CyberBorder,
    backgroundColor: Color = CyberSurface,
    accentLeftColor: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var baseModifier = modifier
        .clip(RoundedCornerShape(10.dp))
        .background(backgroundColor)
        .border(1.dp, borderColor, RoundedCornerShape(10.dp))

    if (accentLeftColor != null) {
        baseModifier = baseModifier.drawWithContent {
            drawContent()
            drawRect(
                color = accentLeftColor,
                size = androidx.compose.ui.geometry.Size(10.dp.toPx(), size.height)
            )
        }
    }
        
    val finalModifier = if (onClick != null) {
        baseModifier.clickable { onClick() }
    } else {
        baseModifier
    }

    Column(
        modifier = finalModifier.padding(10.dp),
        content = content
    )
}

@Composable
fun StatsCard(
    title: String,
    value: String,
    icon: ImageVector,
    accentColor: Color = CyberCyan,
    modifier: Modifier = Modifier
) {
    CyberCard(
        modifier = modifier,
        borderColor = accentColor.copy(alpha = 0.4f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = title.uppercase(),
                    color = OnCyberSurfaceMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = value,
                    color = accentColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = accentColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
