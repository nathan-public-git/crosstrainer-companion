package com.crosstrainer.companion.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AppColors = darkColorScheme(
    primary = Teal,
    onPrimary = Ink,
    secondaryContainer = TealDark,
    onSecondaryContainer = Teal,
    background = Ink,
    onBackground = White,
    surface = Ink,
    onSurface = White,
    surfaceContainer = Panel,
    onSurfaceVariant = Muted,
)

@Composable
fun CrosstrainerCompanionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColors,
        content = content,
    )
}

