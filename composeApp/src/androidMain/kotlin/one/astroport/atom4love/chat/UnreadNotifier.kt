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
import one.astroport.atom4love.ui.ChatHost

/**
 * 💬 « Un message vous attend » — la notification système des conversations.
 *
 * ## Ce qu'elle dit, et ce qu'elle continue de cacher
 *
 * ⚠ **Elle nomme et elle cite — c'est une décision, prise le 19/08.** Elle ne
 * disait qu'un compte : la règle était qu'une notification se lit sur un écran
 * verrouillé, par-dessus l'épaule, sur un téléphone posé sur une table, et
 * qu'une ligne d'aperçu redonnerait en clair ce qu'une cabine Noise chiffre de
 * bout en bout. La contrepartie était lourde : il fallait ouvrir l'application
 * pour savoir s'il fallait l'ouvrir. Florent a tranché pour le nom et l'extrait.
 *
 * ⚠ **Ce qui tient encore : `VISIBILITY_PRIVATE`.** Sur un écran **verrouillé**,
 * le système masque tout seul le contenu et ne laisse que le titre. L'aperçu ne
 * se lit donc que sur un téléphone déjà déverrouillé — celui qu'on tient. C'est
 * la moitié de la prudence d'origine, et c'est la moitié qui protège vraiment.
 *
 * ⚠ **Le canal est en `IMPORTANCE_HIGH`, et c'est ce qui fait le bandeau.** Un
 * bandeau qui tombe du haut et se retire seul au bout de quelques secondes n'a
 * pas à être écrit : c'est le comportement du système pour ce niveau-là. Il
 * fallait un canal NEUF pour l'obtenir — l'importance d'un canal existant est
 * réglée par la personne, pas par l'application, et la relever dans le code
 * n'aurait rien changé sur les appareils où l'ancien canal existait déjà.
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
        /**
         * ⚠ **« message » et non « chat ».** L'ancien canal était en
         * `IMPORTANCE_DEFAULT` : pas de bandeau. Le relever ne suffit pas —
         * une fois un canal créé, son importance appartient à la personne, et
         * le système ignore toute élévation venue du code. Un identifiant neuf
         * est le seul chemin ; l'ancien est supprimé pour ne pas laisser un
         * réglage orphelin dans les paramètres.
         */
        const val CHANNEL_ID = "message"
        private const val OLD_CHANNEL_ID = "chat"

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
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.chat_channel_description)
        }
        context.getSystemService(NotificationManager::class.java).apply {
            createNotificationChannel(channel)
            runCatching { deleteNotificationChannel(OLD_CHANNEL_ID) }
        }
    }

    /**
     * Dit qu'il y a [count] messages en attente. Silencieux si la permission
     * manque : refuser les notifications est une réponse, pas une panne.
     */
    fun waiting(unread: ChatHost.Unread) {
        val count = unread.count
        if (count <= 0 || !granted()) return
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val who = unread.from ?: context.getString(R.string.chat_from_unnamed)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_beacon)
            .setContentTitle(who)
            .setContentText(unread.extract)
            // Le compte reste, mais en second : ce qui fait prendre le
            // téléphone est un nom, pas un nombre.
            .apply {
                if (count > 1) {
                    setSubText(
                        context.resources.getQuantityString(
                            R.plurals.unread_banner,
                            count,
                            count,
                        ),
                    )
                }
            }
            .setStyle(NotificationCompat.BigTextStyle().bigText(unread.extract))
            .setContentIntent(open)
            .setAutoCancel(true)
            // ⚠ **Pas `setOnlyAlertOnce`.** Il était là quand la notification ne
            // portait qu'un compte : la faire sonner à chaque incrément aurait
            // été du harcèlement pour une information qui ne changeait pas de
            // nature. Maintenant qu'elle nomme et qu'elle cite, chaque message
            // est une nouvelle — et un deuxième message qui ne ferait rien
            // apparaître serait un message perdu.
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            // ⚠ Sur l'écran verrouillé, le titre seul — c'est ce qui reste de
            // la prudence d'origine, et c'est la part qui compte. `SECRET` la
            // ferait disparaître ; `PRIVATE` montre qu'il y a une notification
            // et cache ce qu'elle porte tant que l'écran est verrouillé.
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
