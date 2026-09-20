package com.example.newworkspace.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppColorScheme = darkColorScheme(
    primary = SeahawksGreen,
    onPrimary = SeahawksNavy,
    primaryContainer = SeahawksBlue,
    onPrimaryContainer = SeahawksSky,
    secondary = SeahawksSilver,
    onSecondary = SeahawksNavy,
    background = SeahawksNavy,
    onBackground = SeahawksSky,
    surface = Color(0xFF12345A),
    onSurface = SeahawksSky,
    surfaceVariant = Color(0xFF315D82),
    onSurfaceVariant = Color(0xFFD0DFEA)
)

@Composable
fun NewWorkspaceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = Typography,
        content = content
    )
}
