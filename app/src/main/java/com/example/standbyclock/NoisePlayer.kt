package com.example.standbyclock

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlin.random.Random

/**
 * Generates the background sounds on the device and streams them to the speaker: white, pink and
 * brown noise, rain, ocean, fire, wind and stream. It runs on its own thread and keeps playing until
 * its timer ends (with a fade-out) or the user turns it off. It is not a foreground service, so
 * Android may stop it under strict battery restrictions.
 */
object NoisePlayer {

    private const val TAG = "StandbyNoise"
    private const val SAMPLE_RATE = 44100
    private const val FADE_MS = 30_000L

    @Volatile
    private var running = false

    @Volatile
    private var endAt = 0L

    private var track: AudioTrack? = null

    val isPlaying: Boolean
        get() = running

    /** Starts [kind] (a [Settings] noise id). [minutes] is the sleep timer, or 0 to play until stopped. */
    fun start(kind: String, minutes: Int) {
        stop()
        if (kind == Settings.NOISE_OFF) return
        val minBuffer = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            Log.w(TAG, "No audio buffer available")
            return
        }
        val audio = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(minBuffer * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = audio
        endAt = if (minutes > 0) System.currentTimeMillis() + minutes * 60_000L else 0L
        running = true
        audio.play()
        Thread {
            val generator = Generator(kind)
            val buffer = ShortArray(2048)
            try {
                while (running) {
                    val gain = fadeGain()
                    if (gain <= 0f) break
                    for (i in buffer.indices) {
                        buffer[i] = (generator.next() * gain * 0.25f * Short.MAX_VALUE).toInt().toShort()
                    }
                    audio.write(buffer, 0, buffer.size)
                }
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Sound stopped: ${e.message}")
            }
            stop()
        }.apply {
            name = "standby-noise"
            start()
        }
    }

    fun stop() {
        running = false
        track?.let {
            try {
                it.stop()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Track already stopped: ${e.message}")
            }
            it.release()
        }
        track = null
    }

    /** 1 while playing, fading to 0 over the last 30 seconds of the timer, then 0. */
    private fun fadeGain(): Float {
        if (endAt == 0L) return 1f
        val left = endAt - System.currentTimeMillis()
        return when {
            left <= 0L -> 0f
            left < FADE_MS -> left / FADE_MS.toFloat()
            else -> 1f
        }
    }

    /** One sample per call, in the range -1 to 1, for the chosen sound. */
    private class Generator(private val kind: String) {
        // Paul Kellet's pink-noise filter state.
        private var b0 = 0f
        private var b1 = 0f
        private var b2 = 0f
        private var b3 = 0f
        private var b4 = 0f
        private var b5 = 0f
        private var b6 = 0f
        private var brownState = 0f
        private var lowState = 0f
        private var highState = 0f
        private var crackle = 0f
        private var phase = 0.0
        private var sample = 0L

        fun next(): Float {
            sample++
            val white = Random.nextFloat() * 2f - 1f
            return when (kind) {
                Settings.NOISE_PINK -> pink(white)
                Settings.NOISE_BROWN -> brown(white)
                Settings.NOISE_RAIN -> {
                    // Pink noise with a little bright hiss on top.
                    pink(white) * 0.8f + white * 0.12f
                }
                Settings.NOISE_OCEAN -> {
                    // Brown noise swelling in and out, like waves on a beach.
                    phase += 0.00006
                    val swell = 0.55f + 0.45f * kotlin.math.sin(phase).toFloat()
                    brown(white) * swell
                }
                Settings.NOISE_FIRE -> {
                    // Low rumble with random crackles that decay quickly.
                    if (Random.nextFloat() < 0.0009f) crackle = 0.9f * (Random.nextFloat() * 2f - 1f)
                    crackle *= 0.985f
                    brown(white) * 0.6f + crackle
                }
                Settings.NOISE_WIND -> {
                    // Band-limited noise with a slow gust cycle.
                    lowState = 0.985f * lowState + 0.015f * white
                    phase += 0.00002
                    val gust = 0.5f + 0.5f * kotlin.math.sin(phase).toFloat()
                    (lowState * 6f) * gust
                }
                Settings.NOISE_STREAM -> {
                    // Mid-band noise with a gentle babble-like flutter.
                    highState = 0.6f * highState + 0.4f * white
                    val flutter = 0.7f + 0.3f * kotlin.math.sin(sample * 0.0009).toFloat()
                    (white - highState) * flutter
                }
                else -> white
            }.coerceIn(-1f, 1f)
        }

        private fun pink(white: Float): Float {
            b0 = 0.99886f * b0 + white * 0.0555179f
            b1 = 0.99332f * b1 + white * 0.0750759f
            b2 = 0.96900f * b2 + white * 0.1538520f
            b3 = 0.86650f * b3 + white * 0.3104856f
            b4 = 0.55000f * b4 + white * 0.5329522f
            b5 = -0.7616f * b5 - white * 0.0168980f
            val out = b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362f
            b6 = white * 0.115926f
            return out * 0.11f
        }

        private fun brown(white: Float): Float {
            brownState = (brownState + 0.02f * white) / 1.02f
            return (brownState * 3.5f).coerceIn(-1f, 1f)
        }
    }
}
