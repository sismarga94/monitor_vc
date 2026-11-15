package com.example.jkt48bonusmonitor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val AppColorScheme = lightColorScheme(
    primary = Pink40,
    onPrimary = CardBackground,
    primaryContainer = Pink80,
    secondary = PinkDark,
    surface = Surface,
    onSurface = OnSurface,
    background = Surface,
    onBackground = OnSurface
)

@Composable
fun JKT48BonusMonitorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        content = content
    )
}
