package one.astroport.atom4love.nostr

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import one.astroport.atom4love.MainActivity
import one.astroport.atom4love.R
import one.astroport.atom4love.domain.KinMaya

/**
 * La notification de bienvenue — la seule que cette application poste sans
 * qu'on lui ait rien demandé.
 *
 * **Son propre canal**, distinct de celui de la balise, et pour une raison
 * pratique : celui de la balise est en `IMPORTANCE_LOW` parce qu'il porte une
 * notification permanente qu'on ne veut pas voir sonner. Une arrivée est
 * l'inverse — rare, ponctuelle, et sans intérêt si on la découvre trois jours
 * plus tard. Deux canaux, donc, et chacun réglable séparément par la personne :
 * couper les fêtes sans éteindre la balise doit rester possible en deux gestes.
 *
 * ⚠ Une par arrivée, groupées par Android sous le même canal. Pas de compteur
 * qui s'incrémente en silence : quelqu'un qui entre mérite sa ligne, et une
 * salve de trois est de toute façon plus rare qu'une par semaine.
 */
class WelcomeNotifier(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "welcome"

        /**
         * Les identifiants partent d'un rang haut pour ne jamais croiser celui
         * de la balise ([one.astroport.atom4love.proximity.ProximityService],
         * qui tient le 1) : deux notifications au même identifiant se
         * remplacent l'une l'autre, et c'est la balise qui disparaîtrait.
         */
        private const val ID_BASE = 2000
    }

    fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.welcome_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.welcome_channel_description)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    /**
     * Ce qu'on peut dire de quelqu'un sans rien savoir de lui : son sceau, et
     * qu'il vient d'arriver. Pas de nom — le certificat n'en porte pas — et
     * surtout pas sa clé publique en toutes lettres dans une barre d'état.
     *
     * Silencieux si la permission manque : refuser les notifications est une
     * réponse, pas une panne.
     */
    fun celebrate(atom: Constellation.Atom) {
        if (!granted()) return
        val seal = KinMaya.glyphName(atom.kin?.glyph)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = if (seal != null) {
            context.getString(R.string.welcome_body_seal, seal, atom.shortKey)
        } else {
            context.getString(R.string.welcome_body, atom.shortKey)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_beacon)
            .setContentTitle(context.getString(R.string.welcome_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .build()
        runCatching {
            context.getSystemService(NotificationManager::class.java)
                .notify(ID_BASE + (atom.pubkey.hashCode() and 0xFFF), notification)
        }
    }

    private fun granted(): Boolean =
        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
}
