package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CyberpunkColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = OnCyberCyan,
    primaryContainer = Color(0xFF003840),
    onPrimaryContainer = CyberCyan,
    secondary = NeonGreen,
    onSecondary = Color(0xFF003814),
    secondaryContainer = Color(0xFF004D1A),
    onSecondaryContainer = NeonGreen,
    tertiary = PurpleNeon,
    onTertiary = Color(0xFF2A004D),
    background = CyberDarkBg,
    onBackground = OnCyberDark,
    surface = CyberSurface,
    onSurface = OnCyberDark,
    surfaceVariant = CyberSurfaceVariant,
    onSurfaceVariant = OnCyberSurfaceMuted,
    outline = CyberBorder,
    error = WarningCrimson,
    onError = Color.White
)

@Composable
fun InterceptXTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CyberpunkColorScheme,
        typography = Typography,
        content = content
    )
}

