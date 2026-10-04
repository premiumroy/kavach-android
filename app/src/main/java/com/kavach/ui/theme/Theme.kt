package com.kavach.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Accent = Color(0xFF6B8AFD)
private val AccentDark = Color(0xFF3B5BDB)

private val Dark = darkColorScheme(
    primary = Accent,
    secondary = AccentDark,
    background = Color(0xFF0B0D12),
    surface = Color(0xFF14171E),
    onPrimary = Color(0xFF0B0D12),
)

private val Light = lightColorScheme(
    primary = AccentDark,
    secondary = Accent,
)

@Composable
fun KavachTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) Dark else Light, content = content)
}
