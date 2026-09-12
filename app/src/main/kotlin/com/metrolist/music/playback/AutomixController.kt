/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import com.metrolist.music.constants.AutomixMode
import com.metrolist.music.constants.NeuralMixStyle
import com.metrolist.music.playback.audio.NeuralMixAudioProcessor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Manages DJ-style transitions, NeuralMix FX frequency cuts, and equal-power
 * volume blending between outgoing and incoming audio streams.
 *
 * Implements Algoriddim djay Pro-style NeuralMix FX:
 * - Constant-power sinusoidal volume curves (sin² + cos² = 1.0)
 * - Independent stem and frequency isolation (kick/bass swap, filter dissolve, resonant sweep)
 * - Support for both automatic track-end transitions and manual skip transitions
 */
object AutomixController {

    data class FilterCutoffs(
        val outgoingLowPass: Float,
        val outgoingHighPass: Float,
        val incomingLowPass: Float,
        val incomingHighPass: Float,
    )

    /**
     * Calculates the instantaneous volume gains for (incoming, outgoing) tracks
     * across [progress] in [0.0f..1.0f].
     */
    fun calculateGains(
        progress: Float,
        mode: AutomixMode,
        bassSwap: Boolean = true,
        style: NeuralMixStyle = NeuralMixStyle.BASS_SWAP,
    ): Pair<Float, Float> {
        val clampedProgress = progress.coerceIn(0f, 1f)

        return when (mode) {
            AutomixMode.OFF -> {
                if (clampedProgress >= 1f) 1f to 0f else 0f to 1f
            }

            AutomixMode.CROSSFADE -> {
                // Constant-power sinusoidal curve: sin²(θ) + cos²(θ) = 1.0
                val angle = clampedProgress * (PI.toFloat() / 2f)
                sin(angle) to cos(angle)
            }

            AutomixMode.SMART_AUTOMIX -> {
                val angle = clampedProgress * (PI.toFloat() / 2f)
                var fadeIn = sin(angle)
                var fadeOut = cos(angle)

                if (bassSwap) {
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

            AutomixMode.NEURALMIX_FX -> {
                // Full Studio Constant-Power Crossfade — filtering handles the frequency carve-out
                val angle = clampedProgress * (PI.toFloat() / 2f)
                val fadeIn = sin(angle)
                val fadeOut = cos(angle)
                fadeIn.coerceIn(0f, 1f) to fadeOut.coerceIn(0f, 1f)
            }
        }
    }

    /**
     * Calculates the real-time DSP filter cutoffs for NeuralMix FX during a transition.
     */
    fun calculateFilterCutoffs(
        progress: Float,
        mode: AutomixMode,
        style: NeuralMixStyle,
    ): FilterCutoffs {
        if (mode != AutomixMode.NEURALMIX_FX && mode != AutomixMode.SMART_AUTOMIX) {
            return FilterCutoffs(
                outgoingLowPass = NeuralMixAudioProcessor.OPEN_LP_HZ,
                outgoingHighPass = NeuralMixAudioProcessor.OFF_HP_HZ,
                incomingLowPass = NeuralMixAudioProcessor.OPEN_LP_HZ,
                incomingHighPass = NeuralMixAudioProcessor.OFF_HP_HZ,
            )
        }

        val p = progress.coerceIn(0f, 1f)

        return when (style) {
            NeuralMixStyle.BASS_SWAP -> {
                // Outgoing track: full until 50%, then high-pass kills low end (< 300 Hz) so bass drops cleanly
                val outHp = if (p < 0.48f) {
                    NeuralMixAudioProcessor.OFF_HP_HZ
                } else if (p < 0.62f) {
                    val ramp = (p - 0.48f) / 0.14f
                    20f + ramp * 280f
                } else {
                    300f
                }

                // Incoming track: low-end kept in check until 50%, then bass drops right on beat
                val inHp = if (p < 0.50f) {
                    280f - (p / 0.50f) * 60f // ~250 Hz
                } else if (p < 0.60f) {
                    val ramp = (p - 0.50f) / 0.10f
                    220f * (1f - ramp) + 20f
                } else {
                    NeuralMixAudioProcessor.OFF_HP_HZ
                }

                FilterCutoffs(
                    outgoingLowPass = NeuralMixAudioProcessor.OPEN_LP_HZ,
                    outgoingHighPass = outHp,
                    incomingLowPass = NeuralMixAudioProcessor.OPEN_LP_HZ,
                    incomingHighPass = inHp,
                )
            }

            NeuralMixStyle.FILTER_DISSOLVE -> {
                // Outgoing track sweeps high-pass up to 1,800 Hz, dissolving into ambient air
                val outHp = 20f + (p * p) * 1_780f
                val outLp = 20_000f - p * 6_000f

                // Incoming track opens from 350 Hz to 20,000 Hz
                val inLp = 350f + (sin(p * (PI.toFloat() / 2f))) * 19_650f

                FilterCutoffs(
                    outgoingLowPass = outLp,
                    outgoingHighPass = outHp,
                    incomingLowPass = inLp,
                    incomingHighPass = NeuralMixAudioProcessor.OFF_HP_HZ,
                )
            }

            NeuralMixStyle.NEURAL_SWEEP -> {
                // Outgoing closes down
                val outLp = 20_000f * (1f - p * 0.85f) // Down to ~3,000 Hz
                // Incoming opens up
                val inHp = 800f * (1f - p) + 20f

                FilterCutoffs(
                    outgoingLowPass = outLp,
                    outgoingHighPass = NeuralMixAudioProcessor.OFF_HP_HZ,
                    incomingLowPass = NeuralMixAudioProcessor.OPEN_LP_HZ,
                    incomingHighPass = inHp,
                )
            }

            NeuralMixStyle.EQUAL_POWER -> {
                FilterCutoffs(
                    outgoingLowPass = NeuralMixAudioProcessor.OPEN_LP_HZ,
                    outgoingHighPass = NeuralMixAudioProcessor.OFF_HP_HZ,
                    incomingLowPass = NeuralMixAudioProcessor.OPEN_LP_HZ,
                    incomingHighPass = NeuralMixAudioProcessor.OFF_HP_HZ,
                )
            }
        }
    }

    /**
     * Calculates the optimal transition duration in milliseconds based on track duration,
     * user configuration, and mode.
     */
    fun calculateTransitionDurationMs(
        trackDurationMs: Long,
        configuredDurationSec: Float,
        mode: AutomixMode,
        isManual: Boolean = false,
        manualDurationSec: Float = 2.5f,
    ): Long {
        if (mode == AutomixMode.OFF || trackDurationMs <= 0) return 0L

        if (isManual) {
            return (manualDurationSec * 1000f).toLong().coerceIn(1000L, 5000L)
        }

        val configuredMs = (configuredDurationSec * 1000f).toLong().coerceIn(1000L, 15000L)
        val maxAllowedMs = (trackDurationMs * 0.15f).toLong().coerceAtLeast(1000L)

        return when (mode) {
            AutomixMode.OFF -> 0L
            AutomixMode.CROSSFADE -> min(configuredMs, maxAllowedMs)
            AutomixMode.SMART_AUTOMIX,
            AutomixMode.NEURALMIX_FX -> {
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
     */
    fun calculateCueInMs(mode: AutomixMode): Long {
        return when (mode) {
            AutomixMode.SMART_AUTOMIX,
            AutomixMode.NEURALMIX_FX -> 200L // Skip silence/encoder pre-roll so beats land cleanly
            else -> 0L
        }
    }
}
