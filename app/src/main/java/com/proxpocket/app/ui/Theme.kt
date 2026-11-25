package com.proxpocket.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1976D2),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E8FB),
    onPrimaryContainer = Color(0xFF0D3C6E),
    secondary = Color(0xFF43A047),
    background = Color(0xFFF5F7FA),
    surface = Color.White,
    surfaceVariant = Color(0xFFF0F2F5),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6FB6FF),
    primaryContainer = Color(0xFF0D3C6E),
    secondary = Color(0xFF7BD88A),
)

@Composable
fun ProxPocketTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
