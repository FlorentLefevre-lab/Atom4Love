package one.astroport.atom4love.proximity

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import one.astroport.atom4love.MainActivity
import one.astroport.atom4love.R

/**
 * La balise de proximité : un service foreground qui fait tourner [ProximityEngine]
 * (annonce de l'adresse 4D + scan des pairs, en continu) tant qu'il est vivant.
 *
 * L'état est exposé en statique le temps que l'app se dote d'une vraie injection
 * (Hilt est dans le build mais pas encore câblé) : les écrans Compose s'abonnent à
 * [neighbors] et [running] sans tenir de référence au service.
 */
class ProximityService : Service() {

    companion object {
        private const val CHANNEL_ID = "proximity"
        private const val NOTIFICATION_ID = 1

        /**
         * Le réveil de présence a **son propre canal**, et pas par rangement :
         * celui de la balise est en `IMPORTANCE_LOW` parce qu'il porte une
         * notification permanente qu'on ne veut pas entendre. Un réveil muet ne
         * réveille personne. Deux canaux, donc, et chacun se coupe séparément —
         * on doit pouvoir taire les réveils sans éteindre la balise.
         */
        private const val PRESENCE_CHANNEL_ID = "presence"
        private const val PRESENCE_NOTIFICATION_ID = 2

        private val _running = MutableStateFlow(false)
        /** La balise tourne (service démarré et non détruit). */
        val running: StateFlow<Boolean> = _running.asStateFlow()

        private val _neighbors = MutableStateFlow<List<NeighborRegistry.Neighbor>>(emptyList())
        /** Noyaux à portée — vidé quand la balise se coupe. */
        val neighbors: StateFlow<List<NeighborRegistry.Neighbor>> = _neighbors.asStateFlow()

        private val _advertisedCell4d = MutableStateFlow<Long?>(null)
        /** Adresse 4D que la balise annonce (null = cellule non résolue ou balise coupée). */
        val advertisedCell4d: StateFlow<Long?> = _advertisedCell4d.asStateFlow()

        private val _nostrKey = MutableStateFlow<ByteArray?>(null)
        private val _signature = MutableStateFlow(ProximityPayload.Signature.Unknown)

        /**
         * Notre propre signature — l'écran en a besoin pour comparer : une
         * résonance se calcule entre deux phases, et l'une des deux est la
         * nôtre.
         */
        val signature: StateFlow<ProximityPayload.Signature> = _signature.asStateFlow()

        /**
         * Le noyau, confié à la balise pour qu'elle sache dériver son jeton de
         * présence — jamais pour l'annoncer. La clé ne quitte pas l'appareil :
         * seul [ProximityPayload.token] part dans l'air, et il ne se remonte
         * pas en npub. À rappeler quand l'identité change (clé LOVE reçue).
         */
        fun bindIdentity(nostrKey: ByteArray?) {
            _nostrKey.value = nostrKey
        }

        /**
         * La signature de résonance — polarité, sceau maya, phase — que la
         * balise diffuse en clair, elle.
         *
         * Elle ne nomme personne et c'est ce qui la rend diffusable : trois
         * nombres qui disent comment deux ondes se croiseraient, jamais qui les
         * porte. Le npub reste dans le jeton haché, l'instrument de cabine-33
         * n'existe pas ici.
         */
        fun bindResonance(signature: ProximityPayload.Signature) {
            _signature.value = signature
        }

        /** Sans elles, ni l'annonce ni le scan ne peuvent démarrer (runtime dès API 31). */
        fun corePermissionsGranted(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || listOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_SCAN,
            ).all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }

        /**
         * Ce que l'UI doit demander avant [start]. Localisation et notifications
         * sont optionnelles (cellule inconnue / notification muette si refusées),
         * mais autant les demander d'un coup.
         */
        fun runtimePermissions(): Array<String> = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, ProximityService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ProximityService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var engineStarted = false

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.beacon_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.beacon_channel_description)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val presence = NotificationChannel(
            PRESENCE_CHANNEL_ID,
            getString(R.string.presence_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = getString(R.string.presence_channel_description)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(presence)
    }

    /** Voisins qui montraient une carte au dernier balayage, et dernier réveil. */
    private var showingBefore = 0
    private var lastPresenceMs = 0L

    /**
     * Le réveil de présence — cf. [PresenceAlert] pour la règle et, surtout,
     * pour ce qu'il ne dit pas.
     */
    private fun maybeAnnouncePresence(neighbors: List<NeighborRegistry.Neighbor>) {
        val showing = neighbors.count {
            it.signature != ProximityPayload.Signature.Unknown
        }
        val now = System.currentTimeMillis()
        if (PresenceAlert.shouldAnnounce(showingBefore, showing, lastPresenceMs, now)) {
            lastPresenceMs = now
            runCatching {
                getSystemService(NotificationManager::class.java)
                    .notify(PRESENCE_NOTIFICATION_ID, buildPresenceNotification())
            }
        }
        showingBefore = showing
    }

    private fun buildPresenceNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // ⚠ Le texte ne compte personne et n'en nomme aucun : il dit qu'il y a
        // de quoi jouer, pas qui, ni que quiconque vous cherche.
        return NotificationCompat.Builder(this, PRESENCE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_beacon)
            .setContentTitle(getString(R.string.presence_title))
            .setContentText(getString(R.string.presence_body))
            .setStyle(NotificationCompat.BigTextStyle().bigText(getString(R.string.presence_body)))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!corePermissionsGranted(this)) {
            // Démarré sans les permissions Bluetooth : rien d'utile à faire.
            stopSelf()
            return START_NOT_STICKY
        }

        // Sans le type location, Android coupe la localisation du service dès que
        // l'app passe en arrière-plan — la cellule deviendrait irrésoluble. On ne
        // l'ajoute que si la permission est là : le déclarer sans elle jette une
        // SecurityException à partir d'API 34.
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            val locationGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
            if (locationGranted) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            type
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(neighborCount = 0), fgsType)

        if (!engineStarted) {
            engineStarted = true
            val registry = NeighborRegistry()
            val engine = ProximityEngine(
                this, registry, CellLocator(this),
                nostrKey = { _nostrKey.value },
                signature = { _signature.value },
            )
            scope.launch { engine.run() }
            scope.launch {
                engine.state.collect { _advertisedCell4d.value = it.advertisedCell4d }
            }
            scope.launch {
                registry.neighbors.collect { list ->
                    _neighbors.value = list
                    runCatching {
                        getSystemService(NotificationManager::class.java)
                            .notify(NOTIFICATION_ID, buildNotification(list.size))
                    }
                    maybeAnnouncePresence(list)
                }
            }
            _running.value = true
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        _running.value = false
        _neighbors.value = emptyList()
        _advertisedCell4d.value = null
        engineStarted = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(neighborCount: Int): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_beacon)
            .setContentTitle(getString(R.string.beacon_notification_title))
            .setContentText(
                // Une notification déjà postée garde la langue du moment où
                // elle a été construite ; celle-ci se reconstruit à chaque
                // changement du voisinage, ce qui la rattrape vite.
                if (neighborCount == 0) {
                    getString(R.string.beacon_none_in_range)
                } else {
                    resources.getQuantityString(
                        R.plurals.beacon_in_range,
                        neighborCount,
                        neighborCount,
                    )
                },
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
            .build()
    }
}
