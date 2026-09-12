/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.tan

/**
 * High-performance, studio-grade audio filter processor for djay Pro-style
 * NeuralMix FX transitions and frequency isolation.
 *
 * Implements a topology-preserving (trapezoidal-integrator) State Variable Filter (SVF)
 * with two cascaded second-order sections delivering a sharp 24 dB/octave Butterworth slope.
 *
 * Features:
 * - Low-pass filter sweeps (20,000 Hz down to 200 Hz)
 * - High-pass filter sweeps (20 Hz up to 3,500 Hz)
 * - True DJ Bass Swap / frequency isolation for drum & bass handoffs
 * - Sub-block geometric cutoff frequency gliding to eliminate zipper noise and clicks
 * - Zero-overhead bypass when cutoffs are at standard endpoints (LP >= 20 kHz, HP <= 20 Hz)
 */
@UnstableApi
class NeuralMixAudioProcessor : BaseAudioProcessor() {

    @Volatile
    private var targetLowPassHz: Float = OPEN_LP_HZ

    @Volatile
    private var targetHighPassHz: Float = OFF_HP_HZ

    private var channelCount = 0
    private var sampleRate = 0
    private var encoding = C.ENCODING_PCM_16BIT

    private var currentLowPassHz = OPEN_LP_HZ
    private var currentHighPassHz = OFF_HP_HZ

    /** Two integrator states per second-order section, per channel. */
    private var lowState = FloatArray(0)
    private var highState = FloatArray(0)

    private val lowA1 = FloatArray(STAGES)
    private val lowA2 = FloatArray(STAGES)
    private val lowA3 = FloatArray(STAGES)
    private val highA1 = FloatArray(STAGES)
    private val highA2 = FloatArray(STAGES)
    private val highA3 = FloatArray(STAGES)
    private val highK = FloatArray(STAGES)

    companion object {
        private const val TAG = "NeuralMixAudioProcessor"
        const val OPEN_LP_HZ = 20_000f
        const val MIN_LP_HZ = 150f
        const val OFF_HP_HZ = 20f
        const val MAX_HP_HZ = 3_500f

        private const val STAGES = 2
        private const val GLIDE_FRAMES = 32
        private val Q_STAGES = floatArrayOf(0.5411961f, 1.306563f) // 4th-order Butterworth Q factors
    }

    /**
     * Sets target cutoff frequencies.
     * Cutoffs will smoothly glide towards targets to prevent any zipper artifacts.
     */
    fun setCutoffs(lowPassHz: Float, highPassHz: Float) {
        targetLowPassHz = lowPassHz.coerceIn(MIN_LP_HZ, OPEN_LP_HZ)
        targetHighPassHz = highPassHz.coerceIn(OFF_HP_HZ, MAX_HP_HZ)
    }

    /**
     * Resets filter to full open bypass state.
     */
    fun resetToBypass() {
        targetLowPassHz = OPEN_LP_HZ
        targetHighPassHz = OFF_HP_HZ
        currentLowPassHz = OPEN_LP_HZ
        currentHighPassHz = OFF_HP_HZ
        lowState.fill(0f)
        highState.fill(0f)
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        channelCount = inputAudioFormat.channelCount
        sampleRate = inputAudioFormat.sampleRate
        encoding = inputAudioFormat.encoding

        val stateCount = channelCount * STAGES * 2
        if (lowState.size != stateCount) {
            lowState = FloatArray(stateCount)
            highState = FloatArray(stateCount)
        }
        lowState.fill(0f)
        highState.fill(0f)

        currentLowPassHz = targetLowPassHz
        currentHighPassHz = targetHighPassHz

        recomputeLowPass(currentLowPassHz)
        recomputeHighPass(currentHighPassHz)

        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val bytesPerSample = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val bytesPerFrame = channelCount * bytesPerSample
        if (bytesPerFrame == 0) return
        val frames = remaining / bytesPerFrame
        val outBuffer = replaceOutputBuffer(frames * bytesPerFrame)

        // Bypass check: if filters are fully open and targets haven't changed, fast copy
        val isLpOpen = currentLowPassHz >= OPEN_LP_HZ && targetLowPassHz >= OPEN_LP_HZ
        val isHpOff = currentHighPassHz <= OFF_HP_HZ && targetHighPassHz <= OFF_HP_HZ
        if (isLpOpen && isHpOff) {
            outBuffer.put(inputBuffer)
            outBuffer.flip()
            return
        }

        var frameOffset = 0
        while (frameOffset < frames) {
            val subBlock = min(GLIDE_FRAMES, frames - frameOffset)

            // Smooth logarithmic glide towards target frequencies
            if (currentLowPassHz != targetLowPassHz) {
                val ratio = targetLowPassHz / currentLowPassHz
                val stepRatio = exp(ln(ratio) * (subBlock.toFloat() / (sampleRate * 0.035f)).coerceIn(0f, 1f))
                currentLowPassHz = (currentLowPassHz * stepRatio).coerceIn(MIN_LP_HZ, OPEN_LP_HZ)
                if (kotlin.math.abs(currentLowPassHz - targetLowPassHz) < 1f) {
                    currentLowPassHz = targetLowPassHz
                }
                recomputeLowPass(currentLowPassHz)
            }

            if (currentHighPassHz != targetHighPassHz) {
                val ratio = targetHighPassHz / currentHighPassHz.coerceAtLeast(1f)
                val stepRatio = exp(ln(ratio) * (subBlock.toFloat() / (sampleRate * 0.035f)).coerceIn(0f, 1f))
                currentHighPassHz = (currentHighPassHz * stepRatio).coerceIn(OFF_HP_HZ, MAX_HP_HZ)
                if (kotlin.math.abs(currentHighPassHz - targetHighPassHz) < 1f) {
                    currentHighPassHz = targetHighPassHz
                }
                recomputeHighPass(currentHighPassHz)
            }

            val filteringLow = currentLowPassHz < OPEN_LP_HZ
            val filteringHigh = currentHighPassHz > OFF_HP_HZ

            for (f in 0 until subBlock) {
                for (ch in 0 until channelCount) {
                    var sample = if (encoding == C.ENCODING_PCM_FLOAT) {
                        inputBuffer.float
                    } else {
                        inputBuffer.short.toFloat() / 32768.0f
                    }

                    if (filteringHigh) {
                        sample = processHighPass(ch, sample)
                    }
                    if (filteringLow) {
                        sample = processLowPass(ch, sample)
                    }

                    if (encoding == C.ENCODING_PCM_FLOAT) {
                        outBuffer.putFloat(sample)
                    } else {
                        val pcmOut = (sample * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()
                        outBuffer.putShort(pcmOut)
                    }
                }
            }
            frameOffset += subBlock
        }

        outBuffer.flip()
    }

    private fun recomputeLowPass(cutoffHz: Float) {
        val nyquist = sampleRate * 0.499f
        val clampedCutoff = cutoffHz.coerceIn(MIN_LP_HZ, nyquist)
        val g = tan(Math.PI * (clampedCutoff.toDouble() / sampleRate.toDouble())).toFloat()

        for (stage in 0 until STAGES) {
            val k = 1.0f / Q_STAGES[stage]
            val a1 = 1.0f / (1.0f + g * (g + k))
            val a2 = g * a1
            val a3 = g * a2

            lowA1[stage] = a1
            lowA2[stage] = a2
            lowA3[stage] = a3
        }
    }

    private fun recomputeHighPass(cutoffHz: Float) {
        val nyquist = sampleRate * 0.499f
        val clampedCutoff = cutoffHz.coerceIn(OFF_HP_HZ, nyquist)
        val g = tan(Math.PI * (clampedCutoff.toDouble() / sampleRate.toDouble())).toFloat()

        for (stage in 0 until STAGES) {
            val k = 1.0f / Q_STAGES[stage]
            val a1 = 1.0f / (1.0f + g * (g + k))
            val a2 = g * a1
            val a3 = g * a2

            highA1[stage] = a1
            highA2[stage] = a2
            highA3[stage] = a3
            highK[stage] = k
        }
    }

    private fun processLowPass(ch: Int, input: Float): Float {
        var x = input
        for (stage in 0 until STAGES) {
            val baseIdx = (ch * STAGES + stage) * 2
            val s1 = lowState[baseIdx]
            val s2 = lowState[baseIdx + 1]

            val v1 = lowA1[stage] * s1 + lowA2[stage] * (x - s2)
            val v2 = s2 + lowA3[stage] * (x - s2) + lowA2[stage] * s1

            lowState[baseIdx] = 2.0f * v1 - s1
            lowState[baseIdx + 1] = 2.0f * v2 - s2

            x = v2
        }
        return x
    }

    private fun processHighPass(ch: Int, input: Float): Float {
        var x = input
        for (stage in 0 until STAGES) {
            val baseIdx = (ch * STAGES + stage) * 2
            val s1 = highState[baseIdx]
            val s2 = highState[baseIdx + 1]

            val v1 = highA1[stage] * s1 + highA2[stage] * (x - s2)
            val v2 = s2 + highA3[stage] * (x - s2) + highA2[stage] * s1
            val hp = x - highK[stage] * v1 - v2

            highState[baseIdx] = 2.0f * v1 - s1
            highState[baseIdx + 1] = 2.0f * v2 - s2

            x = hp
        }
        return x
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onFlush() {
        lowState.fill(0f)
        highState.fill(0f)
    }

    override fun onReset() {
        lowState.fill(0f)
        highState.fill(0f)
        resetToBypass()
    }
}
