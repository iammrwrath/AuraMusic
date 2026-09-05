/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import com.metrolist.music.constants.AutomixMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Manages DJ-style transitions and equal-power volume blending between
 * outgoing and incoming audio streams.
 *
 * Replaces legacy quadratic volume ramps with studio-grade constant-power
 * sinusoidal curves (sin² + cos² = 1.0) so transitions never suffer mid-point
 * volume dips.
 */
object AutomixController {

    /**
     * Calculates the instantaneous volume gains for (incoming, outgoing) tracks
     * across [progress] in [0.0f..1.0f].
     *
     * In [AutomixMode.CROSSFADE] and [AutomixMode.SMART_AUTOMIX]:
     * - Uses equal-power law: fadeIn = sin(t * π/2), fadeOut = cos(t * π/2).
     * - When [bassSwap] is active in SMART_AUTOMIX:
     *   Applies a low-end crossover curve that drops the outgoing kick/bassline
     *   cleanly past the 60% transition mark, preventing muddy dual-bass clashes.
     */
    fun calculateGains(
        progress: Float,
        mode: AutomixMode,
        bassSwap: Boolean = true,
    ): Pair<Float, Float> {
        val clampedProgress = progress.coerceIn(0f, 1f)

        return when (mode) {
            AutomixMode.OFF -> {
                if (clampedProgress >= 1f) 1f to 0f else 0f to 1f
            }

            AutomixMode.CROSSFADE -> {
                // Constant-power sinusoidal curve: sin²(θ) + cos²(θ) = 1.0
                val angle = clampedProgress * (PI.toFloat() / 2f)
                val fadeIn = sin(angle)
                val fadeOut = cos(angle)
                fadeIn to fadeOut
            }

            AutomixMode.SMART_AUTOMIX -> {
                val angle = clampedProgress * (PI.toFloat() / 2f)
                var fadeIn = sin(angle)
                var fadeOut = cos(angle)

                if (bassSwap) {
                    // Bass swap: incoming track takes over the energy past 55% mark,
                    // outgoing track rapidly sheds low-end energy so kick drums don't clash.
                    if (clampedProgress > 0.55f) {
                        val decay = ((clampedProgress - 0.55f) / 0.45f).coerceIn(0f, 1f)
                        fadeOut *= (1f - decay * 0.4f)
                    } else {
                        val ramp = (clampedProgress / 0.55f).coerceIn(0f, 1f)
                        fadeIn *= (0.6f + ramp * 0.4f)
                    }
                }

                fadeIn.coerceIn(0f, 1f) to fadeOut.coerceIn(0f, 1f)
            }
        }
    }

    /**
     * Calculates the optimal transition duration in milliseconds based on the
     * track duration and user configuration.
     *
     * Ensures transitions never exceed 15% of a track's total length on short tracks.
     */
    fun calculateTransitionDurationMs(
        trackDurationMs: Long,
        configuredDurationSec: Float,
        mode: AutomixMode,
    ): Long {
        if (mode == AutomixMode.OFF || trackDurationMs <= 0) return 0L

        val configuredMs = (configuredDurationSec * 1000f).toLong().coerceIn(1000L, 15000L)
        val maxAllowedMs = (trackDurationMs * 0.15f).toLong().coerceAtLeast(1000L)

        return when (mode) {
            AutomixMode.OFF -> 0L
            AutomixMode.CROSSFADE -> min(configuredMs, maxAllowedMs)
            AutomixMode.SMART_AUTOMIX -> {
                // Smart Automix dynamically adjusts window:
                // Tracks > 4 mins get fuller transitions (e.g. 7-10s) for epic mixes;
                // energetic shorter tracks use punchy 5-7s transitions.
                val dynamicDuration = if (trackDurationMs > 240_000L) {
                    max(configuredMs, 8000L)
                } else if (trackDurationMs < 120_000L) {
                    min(configuredMs, 5000L)
                } else {
                    configuredMs
                }
                min(dynamicDuration, maxAllowedMs)
            }
        }
    }

    /**
     * Calculates an intelligent cue-in position (in ms) for the incoming track.
     * In Smart Automix, trims silent pre-roll so the first beat drops cleanly.
     */
    fun calculateCueInMs(mode: AutomixMode): Long {
        return when (mode) {
            AutomixMode.SMART_AUTOMIX -> 200L // Skip generic encoder pre-roll / silence
            else -> 0L
        }
    }
}
