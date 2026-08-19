package one.astroport.atom4love.chat

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

/**
 * 💬 « Un message vous attend » — la notification système des conversations.
 *
 * ## Ce qu'elle ne dit pas, et pourquoi c'est le point important
 *
 * ⚠ **Ni le nom de qui a écrit, ni un mot de ce qui est écrit.** C'est la règle
 * du bandeau intérieur, portée là où elle compte vraiment : une notification se
 * lit sur un **écran verrouillé**, par-dessus l'épaule, dans une salle de
 * réunion, sur un téléphone posé sur une table. Tout ce que cette application
 * chiffre de bout en bout dans une cabine Noise, une ligne d'aperçu le
 * redonnerait en clair à qui passe.
 *
 * Ce qui reste — un compte — suffit à faire prendre le téléphone, et c'est tout
 * ce qu'on lui demande. La contrepartie assumée : on ne peut pas trier depuis la
 * barre d'état. Dans une application où les conversations ne survivent pas à la
 * session, ce tri n'a de toute façon pas de sens.
 *
 * ## Une seule notification, qui compte
 *
 * ⚠ À l'inverse de [one.astroport.atom4love.nostr.WelcomeNotifier], qui en poste
 * **une par arrivée** : une bienvenue est un évènement, rare et daté, et chacune
 * mérite sa ligne. Un message qui attend est un **état** — il y en a n en
 * attente, et ce nombre monte et redescend. Un identifiant fixe, donc : la
 * notification se remplace au lieu de s'empiler, et une conversation animée ne
 * remplit pas le tiroir.
 *
 * ## Son propre canal
 *
 * Comme la bienvenue, et pour la même raison pratique : celui de la balise est
 * en `IMPORTANCE_LOW` parce qu'il porte une notification permanente qu'on ne
 * veut pas entendre. Trois canaux séparés, et chacun se coupe sans toucher aux
 * autres — taire les messages sans éteindre la balise doit rester possible en
 * deux gestes.
 */
class UnreadNotifier(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "chat"

        /**
         * Un identifiant **fixe et isolé** : la balise tient le 1, les
         * bienvenues la plage 2000+. Celui-ci ne croise ni l'une ni l'autre, et
         * son unicité est ce qui fait qu'une notification en remplace une autre
         * au lieu de s'ajouter.
         */
        private const val ID = 3000
    }

    fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.chat_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.chat_channel_description)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    /**
     * Dit qu'il y a [count] messages en attente. Silencieux si la permission
     * manque : refuser les notifications est une réponse, pas une panne.
     */
    fun waiting(count: Int) {
        if (count <= 0 || !granted()) return
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = context.resources.getQuantityString(R.plurals.unread_banner, count, count)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_beacon)
            .setContentTitle(context.getString(R.string.chat_notification_title))
            .setContentText(body)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            // ⚠ Sur l'écran verrouillé, le titre seul. `SECRET` la ferait
            // disparaître — or il faut bien que quelque chose fasse prendre le
            // téléphone ; `PRIVATE` montre qu'il y a une notification et cache
            // ce qu'elle porte, ce qui est exactement le partage voulu.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        runCatching {
            context.getSystemService(NotificationManager::class.java).notify(ID, notification)
        }
    }

    /** Tout est lu : la notification n'a plus d'objet. */
    fun clear() {
        runCatching {
            context.getSystemService(NotificationManager::class.java).cancel(ID)
        }
    }

    private fun granted(): Boolean =
        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
}
