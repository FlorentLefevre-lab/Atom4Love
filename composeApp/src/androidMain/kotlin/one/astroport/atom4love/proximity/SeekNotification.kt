package one.astroport.atom4love.proximity

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import one.astroport.atom4love.MainActivity
import one.astroport.atom4love.R

/**
 * « On vous cherche » — la même notification, dite par deux endroits.
 *
 * La balise sait qu'une lanterne s'est ouverte sur nous ([SeekAlert]) ; le
 * moteur de causerie, lui, reçoit le visage. Ce sont deux composants qui ne se
 * connaissent pas, et qui parlent pourtant de la même chose à la même personne.
 * D'où ce point unique : **même canal, même identifiant**, si bien que la
 * seconde remplace la première au lieu de s'empiler avec elle.
 *
 * ⚠ Et c'est voulu dans cet ordre : d'abord « quelqu'un veut vous reconnaître »
 * sans nom ni visage, puis, quand la photo arrive, la même ligne **avec le
 * visage**. La notification s'enrichit à mesure que l'autre se déclare, elle ne
 * se répète pas.
 */
object SeekNotification {

    /**
     * ⚠ Canal NEUF et non celui de la présence : l'importance d'un canal
     * existant appartient à la personne dès sa création (leçon du 19/08). Celui
     * -ci doit pouvoir passer en tête d'écran — qui vous cherche est dans la
     * salle, maintenant.
     */
    const val CHANNEL_ID = "seek"

    /** Un seul identifiant : la version avec visage remplace celle sans. */
    const val ID = 3

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.seek_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.seek_channel_description)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /**
     * ⚠ **Sans visage, elle ne nomme personne.** Une notification se lit sur un
     * écran verrouillé, posé sur une table, par-dessus l'épaule de n'importe
     * qui. Le Plateau, lui, nomme — on y est venu.
     */
    fun anonymous(context: Context): Notification =
        base(context, context.getString(R.string.seek_body)).build()

    // ⚠⚠ **Il n'y a pas de version avec le visage, et c'est une décision.**
    // Elle a existé une heure le 20/08, puis Florent l'a coupée net : « la photo
    // arrive dans les notifications Android, il faut pas ». Il a raison — un
    // visage est ce qu'il y a de plus reconnaissable au monde, et une
    // notification se lit sur un écran verrouillé, posé sur une table, par-dessus
    // l'épaule de n'importe qui. Le visage se montre **dans l'application** : un
    // bandeau en haut, la carte du Plateau, la lanterne. Voir
    // [one.astroport.atom4love.chat.Faces].

    private fun base(context: Context, body: String): NotificationCompat.Builder {
        val openApp = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_beacon)
            .setContentTitle(context.getString(R.string.seek_title))
            .setContentText(body)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
    }
}
