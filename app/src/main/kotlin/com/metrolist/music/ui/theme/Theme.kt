/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.theme

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import com.materialkolor.score.Score

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val DefaultThemeColor = Color(0xFFED5564)
val NothingThemeColor = Color(0xFFD71921)

val LocalNothingTheme = staticCompositionLocalOf { false }

@Composable
fun MetrolistTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    themeColor: Color = DefaultThemeColor,
    nothingTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val effectiveThemeColor = if (nothingTheme) NothingThemeColor else themeColor
    // Determine if system dynamic colors should be used (Android S+ and default theme color)
    val useSystemDynamicColor = (!nothingTheme && effectiveThemeColor == DefaultThemeColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)

    // Select the appropriate color scheme generation method
    val baseColorScheme = if (useSystemDynamicColor) {
        // Use standard Material 3 dynamic color functions for system wallpaper colors
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        // Use materialKolor only when a specific seed color is provided
        rememberDynamicColorScheme(
            seedColor = effectiveThemeColor,
            isDark = if (nothingTheme) true else darkTheme,
            specVersion = ColorSpec.SpecVersion.SPEC_2025,
            style = PaletteStyle.TonalSpot
        )
    }

    // Apply Nothing OS minimal palette or pureBlack modification
    val colorScheme = remember(baseColorScheme, pureBlack, darkTheme, nothingTheme) {
        if (nothingTheme) {
            baseColorScheme.copy(
                primary = Color(0xFFD71921),
                onPrimary = Color.White,
                primaryContainer = Color(0xFF2A1012),
                onPrimaryContainer = Color(0xFFFFDAD9),
                surface = Color.Black,
                onSurface = Color(0xFFEEEEEE),
                background = Color.Black,
                onBackground = Color(0xFFEEEEEE),
                surfaceVariant = Color(0xFF141414),
                onSurfaceVariant = Color(0xFFB0B0B0),
                surfaceContainer = Color(0xFF121212),
                surfaceContainerHigh = Color(0xFF1A1A1A),
                surfaceContainerHighest = Color(0xFF222222),
                outline = Color(0xFF2E2E2E),
                outlineVariant = Color(0xFF1F1F1F),
            )
        } else if (darkTheme && pureBlack) {
            baseColorScheme.pureBlack(true)
        } else {
            baseColorScheme
        }
    }

    val typography = if (nothingTheme) NothingTypography else MaterialTheme.typography

    CompositionLocalProvider(
        LocalNothingTheme provides nothingTheme,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content,
        )
    }
}

fun Bitmap.extractThemeColor(): Color = Color(
    Palette.from(this)
        .maximumColorCount(8)
        .generate()
        .rankedColors(1, DefaultThemeColor.toArgb())
        .first()
)

internal fun Palette.rankedColors(
    desiredColorCount: Int,
    fallbackColor: Int,
): List<Int> = Score.score(
    swatches.associate { it.rgb to it.population },
    desiredColorCount,
    fallbackColor,
    true,
)

fun ColorScheme.pureBlack(apply: Boolean) =
    if (apply) copy(
        surface = Color.Black,
        background = Color.Black
    ) else this

val ColorSaver = object : Saver<Color, Int> {
    override fun restore(value: Int): Color = Color(value)
    override fun SaverScope.save(value: Color): Int = value.toArgb()
}
