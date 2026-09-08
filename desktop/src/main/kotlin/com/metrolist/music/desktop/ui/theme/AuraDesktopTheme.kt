package com.metrolist.music.desktop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val AuraPrimary = Color(0xFFFF2E56)
val AuraPrimaryGlow = Color(0x40FF2E56)
val AuraSecondary = Color(0xFF8B5CF6)
val AuraAccentCyan = Color(0xFF00E5FF)
val AuraBackground = Color(0xFF09090B)
val AuraSurface = Color(0xFF111116)
val AuraSurfaceVariant = Color(0xFF181820)
val AuraSurfaceGlass = Color(0xDD14141B)
val AuraBorder = Color(0x1AFFFFFF)
val AuraBorderStrong = Color(0x33FFFFFF)
val AuraOnSurface = Color(0xFFF4F4F7)
val AuraOnSurfaceVariant = Color(0xFFA1A1AA)

val AuraNeonGradient = Brush.linearGradient(
    listOf(AuraPrimary, AuraSecondary)
)
val AuraSubtleGlassGradient = Brush.verticalGradient(
    listOf(Color(0xFF1A1A24), Color(0xFF101017))
)

val AuraDarkColorScheme = darkColorScheme(
    primary = AuraPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF38101A),
    onPrimaryContainer = Color(0xFFFFD9E2),
    secondary = AuraSecondary,
    onSecondary = Color.White,
    background = AuraBackground,
    onBackground = AuraOnSurface,
    surface = AuraSurface,
    onSurface = AuraOnSurface,
    surfaceVariant = AuraSurfaceVariant,
    onSurfaceVariant = AuraOnSurfaceVariant,
    error = Color(0xFFFF453A),
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

