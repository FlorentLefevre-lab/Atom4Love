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

    /**
     * Le binaural de cabine-33 — l'eau à gauche, l'eau décalée de F_Φ à droite.
     *
     * Deux fréquences voisines dans deux oreilles, et c'est le cerveau qui fait
     * le troisième son : leur différence. Ici elle vaut 33,17 Hz, F_Φ, toujours.
     *
     * ⚠ Ce son a longtemps porté deux ω_bio — le vôtre à gauche, celui de
     * l'autre à droite — pour que le battement mesure l'écart entre deux corps.
     * C'était **notre** lecture, pas celle de Fred : `miz.html` fixe le couple
     * (429,62 Hz et 462,79 Hz) et l'attache au rituel des 33 secondes, pas à
     * une rencontre. Fred n'a jamais relié ω_bio au binaural — question posée
     * le 14/08, retournée sans réponse. On s'en tient donc à son couple.
     *
     * Les deux fréquences sont déjà audibles : aucun repliement d'octave, il
     * décalerait l'une sans l'autre et détruirait précisément le battement.
     */
    fun binaural(durationMs: Int = 2600) {
        val left = Phi2X.F_WATER
        val right = Phi2X.F_WATER_D
        runCatching {
            val rate = 44_100
            val frames = rate * durationMs / 1000
            val fade = rate * 260 / 1000
            // Entrelacé gauche/droite : c'est ce que réclame CHANNEL_OUT_STEREO,
            // et c'est là que le binaural se joue — un mélange dans le buffer
            // ferait battre l'air au lieu de faire battre l'écoute.
            val pcm = ShortArray(frames * 2)
            for (i in 0 until frames) {
                val envelope = when {
                    i < fade -> i.toDouble() / fade
                    i > frames - fade -> (frames - i).toDouble() / fade
                    else -> 1.0
                }
                pcm[i * 2] = sample(left, i, rate, envelope)
                pcm[i * 2 + 1] = sample(right, i, rate, envelope)
            }
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(rate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
            track.write(pcm, 0, pcm.size)
            track.play()
            handler.postDelayed({ runCatching { track.release() } }, durationMs + 200L)
        }
    }

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
