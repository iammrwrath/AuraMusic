/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Procedural generative ambient motion art canvas that produces smooth, fluid aurora mesh
 * gradients and floating ambient color light bodies derived from the album art color palette.
 *
 * Runs with 0 network bandwidth and minimal GPU load, breathing subtly with music playback.
 */
@Composable
fun GenerativeMotionCanvas(
    colors: List<Color>,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val themeFallback = MaterialTheme.colorScheme.primary
    val themeSecondary = MaterialTheme.colorScheme.secondary
    val themeTertiary = MaterialTheme.colorScheme.tertiary

    val palette = when {
        colors.size >= 3 -> colors
        colors.size == 2 -> listOf(colors[0], colors[1], colors[0].copy(alpha = 0.6f))
        colors.size == 1 -> listOf(colors[0], themeSecondary, colors[0].copy(alpha = 0.5f))
        else -> listOf(themeFallback, themeSecondary, themeTertiary)
    }

    val primaryColor = palette[0]
    val secondaryColor = palette[1]
    val tertiaryColor = palette.getOrElse(2) { palette[0] }
    val accentColor = palette.getOrElse(3) { secondaryColor }

    // Infinite transitions for smooth orbiting color blobs
    val infiniteTransition = rememberInfiniteTransition(label = "motionCanvasTransition")

    val orbitDuration = if (isPlaying) 16000 else 32000
    val pulseDuration = if (isPlaying) 4000 else 8000

    val angle1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(orbitDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orbitAngle1",
    )

    val angle2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween((orbitDuration * 1.35f).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orbitAngle2",
    )

    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(pulseDuration, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathingScale",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val minDim = minOf(width, height)

            // Deep background gradient base
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.45f),
                        Color.Black.copy(alpha = 0.85f),
                        secondaryColor.copy(alpha = 0.35f),
                        Color.Black,
                    ),
                ),
            )

            // Blob 1: Floating primary ambient light orb
            val center1 = Offset(
                x = width * 0.35f + cos(angle1) * (width * 0.25f),
                y = height * 0.35f + sin(angle1) * (height * 0.18f),
            )
            val radius1 = minDim * 0.75f * breathingScale
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.55f),
                        primaryColor.copy(alpha = 0.25f),
                        Color.Transparent,
                    ),
                    center = center1,
                    radius = radius1,
                ),
                center = center1,
                radius = radius1,
            )

            // Blob 2: Floating secondary counter-rotating light orb
            val center2 = Offset(
                x = width * 0.65f + cos(angle2) * (width * 0.28f),
                y = height * 0.65f + sin(angle2) * (height * 0.22f),
            )
            val radius2 = minDim * 0.85f * (2f - breathingScale)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        secondaryColor.copy(alpha = 0.50f),
                        secondaryColor.copy(alpha = 0.20f),
                        Color.Transparent,
                    ),
                    center = center2,
                    radius = radius2,
                ),
                center = center2,
                radius = radius2,
            )

            // Blob 3: Tertiary drifting accent orb
            val center3 = Offset(
                x = width * 0.5f + sin(angle1 * 0.7f) * (width * 0.30f),
                y = height * 0.20f + cos(angle1 * 0.7f) * (height * 0.12f),
            )
            val radius3 = minDim * 0.60f * breathingScale
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        tertiaryColor.copy(alpha = 0.40f),
                        tertiaryColor.copy(alpha = 0.15f),
                        Color.Transparent,
                    ),
                    center = center3,
                    radius = radius3,
                ),
                center = center3,
                radius = radius3,
            )

            // Blob 4: Lower accent orb
            val center4 = Offset(
                x = width * 0.45f + cos(angle2 * 0.8f) * (width * 0.20f),
                y = height * 0.80f + sin(angle2 * 0.8f) * (height * 0.15f),
            )
            val radius4 = minDim * 0.70f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.45f),
                        accentColor.copy(alpha = 0.15f),
                        Color.Transparent,
                    ),
                    center = center4,
                    radius = radius4,
                ),
                center = center4,
                radius = radius4,
            )
        }
    }
}
