package one.astroport.atom4love.chat

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import one.astroport.atom4love.domain.Phi2X
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

    // ⚠ `binaural(durationMs)` vivait ici : deux fréquences voisines dans deux
    // oreilles (429,62 Hz à gauche, 462,79 à droite), dont le cerveau tirait un
    // troisième son de 33,17 Hz — F_Φ. C'était le couple figé de `miz.html`,
    // joué une fois au déverrouillage du rituel des 33 secondes.
    //
    // **Retiré le 15/08, sur décision de Florent**, avec le concept entier de
    // binaural. Le rituel se déverrouille désormais sans un son.


    private fun sample(hz: Double, i: Int, rate: Int, envelope: Double): Short =
        (sin(2 * PI * hz * i / rate) * envelope * 0.28 * Short.MAX_VALUE).toInt().toShort()

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
