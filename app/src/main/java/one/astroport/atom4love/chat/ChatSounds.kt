package one.astroport.atom4love.chat

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * Deux petits sons synthétisés — aucun asset : sinusoïde avec attaque brève
 * et extinction exponentielle. Blip aigu à l'envoi, plus grave à la réception.
 */
class ChatSounds {

    private val handler = Handler(Looper.getMainLooper())

    fun send() = blip(frequency = 880.0, durationMs = 90)

    fun receive() = blip(frequency = 587.33, durationMs = 130)

    private fun blip(frequency: Double, durationMs: Int) {
        runCatching {
            val rate = 22_050
            val count = rate * durationMs / 1000
            val attack = min(count, rate * 5 / 1000)
            val pcm = ShortArray(count) { i ->
                val envelope = (if (i < attack) i.toDouble() / attack else 1.0) * exp(-5.0 * i / count)
                (sin(2 * PI * frequency * i / rate) * envelope * 0.35 * Short.MAX_VALUE)
                    .toInt().toShort()
            }
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(rate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
            track.write(pcm, 0, pcm.size)
            track.play()
            handler.postDelayed({ runCatching { track.release() } }, durationMs + 100L)
        }
    }
}
