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

    private companion object {
        /** Le la grave de `phi2xToAudible` — la fenêtre où les ondes se posent. */
        const val AUDIBLE_ROOT = 110.0
    }

    fun send() = blip(frequency = 880.0, durationMs = 90)

    fun receive() = blip(frequency = 587.33, durationMs = 130)

    /**
     * Le battement d'une rencontre : votre onde à gauche, la sienne à droite.
     *
     * Deux fréquences voisines dans deux oreilles, et c'est le cerveau qui fait
     * le troisième son — leur différence. Deux corps proches battent lentement,
     * deux corps éloignés battent vite : le battement **est** l'écart, il n'est
     * pas une illustration de l'écart. C'est ce que Fred appelle le binaural,
     * et c'est ce qu'on peut faire de deux masses d'eau.
     *
     * Les ω_bio valent quelques centaines de hertz, souvent déjà audibles ;
     * [toAudible] rattrape celles qui ne le sont pas en doublant des octaves,
     * exactement comme `phi2xToAudible` de zelkova. Une octave ne change pas
     * une note, et surtout elle ne change pas l'écart entre deux notes voisines.
     */
    fun binaural(mineHz: Double, theirsHz: Double, durationMs: Int = 2600) {
        val left = toAudible(mineHz) ?: return
        val right = toAudible(theirsHz) ?: return
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

    /**
     * Ramène une fréquence dans [AUDIBLE_ROOT, AUDIBLE_ROOT × 4) en doublant ou
     * en divisant par deux. null si elle n'est pas un nombre exploitable.
     */
    private fun toAudible(hz: Double): Double? {
        if (!hz.isFinite() || hz <= 0.0) return null
        var f = hz
        while (f < AUDIBLE_ROOT) f *= 2
        while (f >= AUDIBLE_ROOT * 4) f /= 2
        return f
    }

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
