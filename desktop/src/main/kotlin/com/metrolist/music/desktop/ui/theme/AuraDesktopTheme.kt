package com.metrolist.music.desktop.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AuraPrimary = Color(0xFFFF3366)
val AuraPrimaryVariant = Color(0xFFE91E63)
val AuraSecondary = Color(0xFF9C27B0)
val AuraBackground = Color(0xFF121214)
val AuraSurface = Color(0xFF1E1E24)
val AuraSurfaceVariant = Color(0xFF2A2A34)
val AuraOnSurface = Color(0xFFF1F1F5)
val AuraOnSurfaceVariant = Color(0xFFA0A0B0)
val AuraAccent = Color(0xFFFF5252)

val AuraDarkColorScheme = darkColorScheme(
    primary = AuraPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4A1020),
    onPrimaryContainer = Color(0xFFFFD9E2),
    secondary = AuraSecondary,
    onSecondary = Color.White,
    background = AuraBackground,
    onBackground = AuraOnSurface,
    surface = AuraSurface,
    onSurface = AuraOnSurface,
    surfaceVariant = AuraSurfaceVariant,
    onSurfaceVariant = AuraOnSurfaceVariant,
    error = Color(0xFFCF6679),
    onError = Color.Black,
)

@Composable
fun AuraDesktopTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AuraDarkColorScheme,
        content = content
    )
}
