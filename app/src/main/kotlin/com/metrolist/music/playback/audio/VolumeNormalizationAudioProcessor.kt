package com.metrolist.music.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.pow

@UnstableApi
@Suppress("DEPRECATION")
class VolumeNormalizationAudioProcessor : AudioProcessor {

    private var sampleRate = 0
    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID
    private var bytesPerSample = 0
    private var isActive = false

    @Volatile
    var enabled = false
        set(value) {
            if (field != value) {
                field = value
                updateGain()
                Timber.tag(TAG).d("Normalization processor enabled: $value")
            }
        }

    @Volatile
    var normalizationGainMb: Int = 0
        private set

    @Volatile
    var volumeBoostDb: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                updateGain()
                Timber.tag(TAG).d("Volume boost set to: $value dB")
            }
        }

    private var buffer: ByteBuffer = EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var inputEnded = false

    private data class GainState(val totalGainMb: Int, val linearGain: Double)

    @Volatile
    private var currentGain: GainState = GainState(0, 1.0)

    companion object {
        private const val TAG = "VolumeNormalizationProcessor"
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())

        // Soft-knee limiter constants:
        // Linear region: [-0.85, +0.85] (~ -1.4 dBFS)
        // Compression region: (0.85, infinity) smoothly and asymptotically approaches 0.85 + 0.149 = 0.999
        // Continuous C1 first derivative at threshold 0.85
        private const val LIMITER_THRESHOLD = 0.85
        private const val LIMITER_HEADROOM = 0.149
    }

    @Synchronized
    fun setTargetGain(gainMb: Int) {
        if (normalizationGainMb != gainMb) {
            normalizationGainMb = gainMb
            updateGain()
            Timber.tag(TAG).d("Target normalization gain set to $gainMb mB")
        }
    }

    @Synchronized
    fun setVolumeBoost(boostDb: Float) {
        if (volumeBoostDb != boostDb) {
            volumeBoostDb = boostDb
            updateGain()
            Timber.tag(TAG).d("Volume boost gain set to $boostDb dB")
        }
    }

    private fun updateGain() {
        val normGain = if (enabled) normalizationGainMb else 0
        val boostGainMb = (volumeBoostDb * 100.0).toInt()
        val totalMb = normGain + boostGainMb
        val linear = 10.0.pow(totalMb / 2000.0)
        currentGain = GainState(totalMb, linear)
        Timber.tag(TAG).d("Combined gain: totalMb=$totalMb, linearGain=$linear (norm=$normGain, boost=$boostGainMb)")
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding

        bytesPerSample = when (encoding) {
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_24BIT -> 3
            C.ENCODING_PCM_32BIT -> 4
            C.ENCODING_PCM_FLOAT -> 4
            else -> throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }

        Timber.tag(TAG).d("Configured: sampleRate=$sampleRate, channels=$channelCount, encoding=$encoding")

        isActive = true
        return AudioProcessor.AudioFormat(sampleRate, channelCount, encoding)
    }

    override fun isActive(): Boolean = isActive

    private fun limitSample16(sampleVal: Double): Short {
        val norm = sampleVal / 32768.0
        val absNorm = abs(norm)
        val limitedNorm = if (absNorm <= LIMITER_THRESHOLD) {
            norm
        } else {
            val excess = absNorm - LIMITER_THRESHOLD
            val compressed = LIMITER_THRESHOLD + LIMITER_HEADROOM * (excess / (LIMITER_HEADROOM + excess))
            if (norm > 0.0) compressed else -compressed
        }
        return (limitedNorm * 32767.0).toInt().toShort()
    }

    private fun limitSample24(sampleVal: Double): Int {
        val norm = sampleVal / 8388608.0
        val absNorm = abs(norm)
        val limitedNorm = if (absNorm <= LIMITER_THRESHOLD) {
            norm
        } else {
            val excess = absNorm - LIMITER_THRESHOLD
            val compressed = LIMITER_THRESHOLD + LIMITER_HEADROOM * (excess / (LIMITER_HEADROOM + excess))
            if (norm > 0.0) compressed else -compressed
        }
        return (limitedNorm * 8388607.0).toInt()
    }

    private fun limitSample32(sampleVal: Double): Int {
        val norm = sampleVal / 2147483648.0
        val absNorm = abs(norm)
        val limitedNorm = if (absNorm <= LIMITER_THRESHOLD) {
            norm
        } else {
            val excess = absNorm - LIMITER_THRESHOLD
            val compressed = LIMITER_THRESHOLD + LIMITER_HEADROOM * (excess / (LIMITER_HEADROOM + excess))
            if (norm > 0.0) compressed else -compressed
        }
        return (limitedNorm * 2147483647.0).toLong().toInt()
    }

    private fun limitSampleFloat(sampleVal: Float): Float {
        if (sampleVal.isNaN()) return 0f
        val absVal = abs(sampleVal)
        val thresh = LIMITER_THRESHOLD.toFloat()
        val headroom = LIMITER_HEADROOM.toFloat()
        return if (absVal <= thresh) {
            sampleVal
        } else {
            val excess = absVal - thresh
            val compressed = thresh + headroom * (excess / (headroom + excess))
            if (sampleVal > 0f) compressed else -compressed
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val gain = currentGain
        val applyGain = gain.totalGainMb != 0

        val inputSize = inputBuffer.remaining()
        if (inputSize == 0) return

        val sampleCount = inputSize / bytesPerSample
        val out = replaceOutputBuffer(sampleCount * bytesPerSample)

        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        out.order(ByteOrder.LITTLE_ENDIAN)

        if (!applyGain) {
            val bytesToCopy = sampleCount * bytesPerSample
            val oldLimit = inputBuffer.limit()
            inputBuffer.limit(inputBuffer.position() + bytesToCopy)
            out.put(inputBuffer)
            inputBuffer.limit(oldLimit)
            out.flip()
            return
        }

        when (encoding) {
            C.ENCODING_PCM_16BIT -> {
                repeat(sampleCount) {
                    val sample = inputBuffer.getShort()
                    val processed = limitSample16(sample * gain.linearGain)
                    out.putShort(processed)
                }
            }

            C.ENCODING_PCM_24BIT -> {
                repeat(sampleCount) {
                    val b0 = inputBuffer.get().toInt() and 0xFF
                    val b1 = inputBuffer.get().toInt() and 0xFF
                    val b2 = inputBuffer.get().toInt()
                    val sample = (b2 shl 16) or (b1 shl 8) or b0

                    val processed = limitSample24(sample * gain.linearGain)
                    out.put((processed and 0xFF).toByte())
                    out.put(((processed shr 8) and 0xFF).toByte())
                    out.put(((processed shr 16) and 0xFF).toByte())
                }
            }

            C.ENCODING_PCM_32BIT -> {
                repeat(sampleCount) {
                    val sample = inputBuffer.getInt()
                    val processed = limitSample32(sample * gain.linearGain)
                    out.putInt(processed)
                }
            }

            C.ENCODING_PCM_FLOAT -> {
                repeat(sampleCount) {
                    val sample = inputBuffer.getFloat()
                    val processed = limitSampleFloat((sample * gain.linearGain).toFloat())
                    out.putFloat(processed)
                }
            }
        }

        out.flip()
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val buffer = outputBuffer
        outputBuffer = EMPTY_BUFFER
        return buffer
    }

    override fun isEnded(): Boolean {
        return inputEnded && outputBuffer === EMPTY_BUFFER
    }

    @Deprecated("Deprecated in AudioProcessor")
    override fun flush() {
        outputBuffer = EMPTY_BUFFER
        inputEnded = false
    }

    @Deprecated("Deprecated in AudioProcessor")
    override fun reset() {
        flush()
        buffer = EMPTY_BUFFER
        sampleRate = 0
        channelCount = 0
        encoding = C.ENCODING_INVALID
        bytesPerSample = 0
        isActive = false
        // DO NOT reset enabled, normalizationGainMb, volumeBoostDb, or currentGain, as they are controlled by the service
    }

    private fun replaceOutputBuffer(size: Int): ByteBuffer {
        if (buffer.capacity() < size) {
            buffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
        } else {
            buffer.clear()
        }
        outputBuffer = buffer
        return buffer
    }
}
