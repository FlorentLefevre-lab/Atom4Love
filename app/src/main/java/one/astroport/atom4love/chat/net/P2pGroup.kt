package one.astroport.atom4love.chat.net

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.wifi.WifiNetworkSpecifier
import android.net.NetworkRequest
import android.net.NetworkCapabilities
import android.net.Network
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.security.SecureRandom
import androidx.core.content.ContextCompat
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Un groupe Wi-Fi Direct, vu comme un lieu où deux cabines peuvent se poser.
 *
 * C'est le médium du dernier recours : quand aucune station ne porte les deux
 * noyaux, ils s'en fabriquent une. Un côté **ouvre** le groupe et devient son
 * propriétaire — toujours joignable en `192.168.49.1` —, l'autre le
 * **rejoint** avec les identifiants reçus par le canal BLE scellé. Aucune
 * découverte P2P n'est lancée : `discoverPeers` coûte des secondes et de
 * l'antenne, là où le nom et la passe suffisent à entrer directement.
 *
 * Mesuré au banc le 2026-08-11 (sonde `diag/WifiDirectProbe`, commit 9acf643) :
 * sur les deux appareils, tenir un groupe P2P **ne coûte pas la station** — le
 * relais local reste joignable avant, pendant et après.
 */
class P2pGroup(context: Context) {

    companion object {
        /** Attente maximale d'un retrait de groupe avant d'en rejoindre un autre. */
        private const val RELEASE_WAIT_MS = 6_000L
        private const val RELEASE_POLL_MS = 250L

        private const val TAG = "CabinChat"

        /** Le propriétaire d'un groupe Wi-Fi Direct est toujours à cette adresse. */
        const val OWNER_ADDRESS = "192.168.49.1"

        /** Rejoindre par identifiants demande l'API 29 (`WifiP2pConfig.Builder`). */
        private val JOIN_SUPPORTED = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        /** Formation d'un groupe : quelques secondes en pratique, jamais instantané. */
        private const val FORM_TIMEOUT_MS = 20_000L

        /**
         * Ce qu'on laisse pour entrer dans un groupe — **le temps d'un humain**,
         * pas celui d'une radio : Android demande son accord avant de connecter,
         * et lire ce dialogue prend plus de vingt secondes.
         */
        private const val JOIN_TIMEOUT_MS = 90_000L

        /** Répit avant de tenir un groupe perdu pour un groupe fermé. */
        private const val LOST_GRACE_MS = 6_000L
        private const val POLL_MS = 400L

        /**
         * Un groupe qui vient de se former refuse d'être retiré : `removeGroup`
         * rend `BUSY` tant que le système finit son travail (vu au banc le
         * 2026-08-12, systématiquement, dans la seconde qui suit l'ouverture).
         * Une seule demande ne suffit donc pas — et c'est ce qui laissait des
         * groupes ouverts derrière des fermetures que le code croyait propres.
         */
        private const val REMOVE_ATTEMPTS = 6
        private const val REMOVE_RETRY_MS = 400L

        /** Sans caractères qu'on puisse confondre à l'oral ou à l'œil. */
        private const val ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"

        /**
         * Ce qu'Android exige pour toucher au Wi-Fi Direct. Depuis l'API 33 la
         * localisation n'est plus de mise — `NEARBY_WIFI_DEVICES` la remplace,
         * déclarée `neverForLocation` au manifeste. Avant, il n'y avait que la
         * localisation fine, et il n'y a pas de contournement.
         */
        val RUNTIME_PERMISSIONS: Array<String> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
            } else {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }

        fun permissionsGranted(context: Context): Boolean = RUNTIME_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        private const val TRACE_FILE = "a4l_p2p"
        private const val TRACE_KEY = "engaged_group"

        internal fun decide(trace: String?, currentGroup: String?): Verdict = when {
            trace == null -> Verdict.Nothing
            currentGroup == null -> Verdict.Forget
            currentGroup == trace -> Verdict.Remove(trace)
            else -> Verdict.Forget
        }
    }

    /** De quoi entrer dans un groupe sans rien découvrir. */
    data class Credentials(val networkName: String, val passphrase: String)

    /**
     * Ce que la trace commande au lancement suivant. Sortie du framework pour
     * être vérifiable : c'est ici que tient la règle, et elle tient en une
     * phrase — **on ne retire que le groupe qu'on a soi-même engagé**. Un
     * groupe formé par une autre application (un partage de fichiers, une
     * diffusion d'écran) porte un autre nom et ne nous regarde pas.
     */
    internal sealed interface Verdict {
        /** Aucune trace : on n'a rien laissé derrière nous. */
        data object Nothing : Verdict
        /** La trace ne désigne plus rien de vivant — l'oublier suffit. */
        data object Forget : Verdict
        /** Le groupe est encore là, et il est de nous. */
        data class Remove(val networkName: String) : Verdict
    }

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(WifiP2pManager::class.java)
    private var channel: WifiP2pManager.Channel? = null

    /**
     * La requête réseau qui tient notre place dans le groupe d'un autre.
     *
     * Rejoindre ne passe plus par `WifiP2pManager.connect()` : mesuré au banc
     * le 13/08, il est accepté puis échoue en silence sur ce téléphone-ci
     * (Android 17), dix secondes plus tard, en `FORMATION_FAILED`. Le même
     * groupe rejoint par une requête réseau ordinaire marche — l'appareil y
     * reçoit son adresse en 192.168.49.x du propriétaire.
     *
     * La connexion vit tant que la requête vit : la rendre, c'est sortir.
     */
    private var joinedCallback: ConnectivityManager.NetworkCallback? = null
    private var joinedNetwork: Network? = null
    private var joinedName: String? = null

    /**
     * Prévenu quand le groupe qu'on avait rejoint disparaît pour de bon.
     *
     * L'hôte, lui, l'apprend par `WIFI_P2P_CONNECTION_CHANGED` — mais depuis
     * qu'un invité entre par requête réseau, Android ne le voit plus comme un
     * client P2P et ne lui diffuse plus rien. Sans ce rappel, l'invité gardait
     * une ligne affirmant que le pair tient encore un groupe fermé depuis
     * longtemps.
     */
    var onGroupLost: (() -> Unit)? = null

    /**
     * Le nom du groupe engagé, écrit sur le disque — parce qu'il doit survivre
     * précisément à ce que la cabine, elle, ne survit pas. Un processus tué ne
     * passe par aucun `stop()` : le groupe reste monté côté système, et il ne
     * reste rien en mémoire pour le nommer au lancement suivant.
     */
    private val trace = appContext.getSharedPreferences(TRACE_FILE, Context.MODE_PRIVATE)

    private fun engage(networkName: String) {
        trace.edit().putString(TRACE_KEY, networkName).apply()
    }

    private fun forget() {
        trace.edit().remove(TRACE_KEY).apply()
    }

    /** Vrai quand cet appareil peut ouvrir ou rejoindre un groupe. */
    fun usable(): Boolean = manager != null && JOIN_SUPPORTED

    private fun channel(): WifiP2pManager.Channel? {
        val manager = this.manager ?: return null
        return channel ?: runCatching {
            manager.initialize(appContext, Looper.getMainLooper(), null)
        }.getOrNull()?.also { channel = it }
    }

    /**
     * Ouvre un groupe et rend de quoi y entrer. null si l'appareil refuse.
     *
     * Un groupe déjà ouvert est réutilisé tel quel : en ouvrir un second
     * n'aurait pas de sens, et `createGroup` échouerait de toute façon.
     */
    @SuppressLint("MissingPermission")
    suspend fun host(): Credentials? {
        val manager = this.manager ?: return null
        val channel = channel() ?: return null
        if (!permissionsGranted(appContext)) {
            Log.w(TAG, "Wi-Fi Direct : permission manquante")
            return null
        }
        existingGroup(manager, channel)?.let { group ->
            if (group.isGroupOwner) {
                Log.i(
                    TAG,
                    "groupe Wi-Fi Direct déjà ouvert : ${group.networkName} " +
                        "sur ${group.frequency} MHz",
                )
                engage(group.networkName)
                return Credentials(group.networkName, group.passphrase)
            }
        }
        // On DEMANDE la bande 5 GHz. C'est le seul endroit du projet où l'app
        // choisit sa bande : par la station, elle subit celle du lieu. Or le
        // Bluetooth ne vit qu'en 2,4 GHz — un groupe en 5 GHz sort de la
        // dispute d'antenne mesurée le 2026-08-11 (2,2 Mo/s contre 100 Ko/s
        // selon que le Bluetooth se taise ou non). Ce n'est qu'une préférence :
        // réglementation, DFS ou un pair qui ne sait pas faire ramènent en
        // 2,4 GHz, d'où les replis successifs.
        //
        // Choisir la bande OBLIGE à fournir nos propres identifiants : le
        // constructeur refuse une configuration qui n'a ni adresse de pair ni
        // nom+passe (« peer address must be set… », vu au banc). Tant mieux —
        // on les connaît alors d'avance, au lieu de les découvrir après coup.
        val chosen = newCredentials()
        val opened =
            createGroup(manager, channel, chosen, WifiP2pConfig.GROUP_OWNER_BAND_5GHZ) ||
                createGroup(manager, channel, chosen, WifiP2pConfig.GROUP_OWNER_BAND_AUTO)
        // Dernier repli : le groupe que le système compose lui-même, sans que
        // nous choisissions rien. On perd la bande et les identifiants, pas la
        // fonction.
        val fallback = !opened && createSystemGroup(manager, channel)
        if (!opened && !fallback) return null

        return withTimeoutOrNull(FORM_TIMEOUT_MS) {
            while (true) {
                val group = existingGroup(manager, channel)
                if (group != null && group.isGroupOwner && group.passphrase != null) {
                    // la fréquence dit la bande OBTENUE, pas celle demandée :
                    // c'est la seule façon de savoir si le repli a joué
                    Log.i(
                        TAG,
                        "groupe Wi-Fi Direct ouvert : ${group.networkName} sur " +
                            "${group.frequency} MHz (${if (group.frequency > 3000) "5 GHz" else "2,4 GHz"})",
                    )
                    engage(group.networkName)
                    return@withTimeoutOrNull Credentials(group.networkName, group.passphrase)
                }
                delay(POLL_MS)
            }
            @Suppress("UNREACHABLE_CODE") null
        }
    }

    /**
     * Un nom de réseau et une passe tirés au sort. Le nom doit commencer par
     * `DIRECT-` (spec Wi-Fi Direct, et Android le vérifie) et tenir en 32
     * caractères ; la passe entre 8 et 63.
     */
    private fun newCredentials(): Credentials {
        val random = SecureRandom()
        fun draw(n: Int) = String(CharArray(n) { ALPHABET[random.nextInt(ALPHABET.length)] })
        return Credentials("DIRECT-${draw(2)}-A4L", draw(12))
    }

    /**
     * Ouvre un groupe sur la bande demandée. `createGroup` sans configuration
     * laisse le système choisir — et il choisit la 2,4 GHz par compatibilité.
     */
    @SuppressLint("MissingPermission")
    private suspend fun createGroup(
        manager: WifiP2pManager,
        channel: WifiP2pManager.Channel,
        credentials: Credentials,
        band: Int,
    ): Boolean {
        val config = runCatching {
            WifiP2pConfig.Builder()
                .setNetworkName(credentials.networkName)
                .setPassphrase(credentials.passphrase)
                .setGroupOperatingBand(band)
                .build()
        }.getOrElse {
            Log.w(TAG, "bande $band refusée à la construction — $it")
            return false
        }
        return runCatching {
            suspendCancellableCoroutine { cont ->
                manager.createGroup(channel, config, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() = cont.resume(true)
                    override fun onFailure(reason: Int) {
                        Log.w(TAG, "createGroup bande $band refusé (raison $reason)")
                        cont.resume(false)
                    }
                })
            }
        }.getOrDefault(false)
    }

    /** Le groupe que le système compose seul : ni bande ni identifiants choisis. */
    @SuppressLint("MissingPermission")
    private suspend fun createSystemGroup(
        manager: WifiP2pManager,
        channel: WifiP2pManager.Channel,
    ): Boolean = runCatching {
        suspendCancellableCoroutine { cont ->
            manager.createGroup(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() = cont.resume(true)
                override fun onFailure(reason: Int) {
                    Log.w(TAG, "createGroup système refusé (raison $reason)")
                    cont.resume(false)
                }
            })
        }
    }.getOrDefault(false)

    /**
     * Rejoint le groupe d'un pair. Rend vrai quand l'interface est montée et
     * que le propriétaire est joignable.
     *
     * Pas de `discoverPeers` : le nom et la passe suffisent à entrer, et la
     * découverte coûterait des secondes d'antenne pour retrouver quelqu'un dont
     * on sait déjà tout.
     */
    suspend fun join(credentials: Credentials): Boolean {
        if (!JOIN_SUPPORTED) {
            Log.w(TAG, "Wi-Fi Direct : rejoindre par identifiants demande Android 10")
            return false
        }
        val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
            ?: return false
        // Déjà dedans : la requête tient la connexion tant qu'on la garde, il
        // n'y a rien à refaire — et en relancer une seconde couperait la
        // première.
        if (joinedName == credentials.networkName && joinedNetwork != null) {
            Log.i(TAG, "déjà dans le groupe ${credentials.networkName}")
            return true
        }
        leaveJoined()

        val specifier = runCatching {
            WifiNetworkSpecifier.Builder()
                .setSsid(credentials.networkName)
                .setWpa2Passphrase(credentials.passphrase)
                .build()
        }.getOrElse {
            Log.w(TAG, "identifiants de groupe refusés — $it")
            return false
        }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            // un groupe ne mène nulle part : réclamer Internet ferait attendre
            // une validation qui n'arrivera jamais
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier)
            .build()

        // Le délai n'est PAS confié à `requestNetwork` : le sien court pendant
        // qu'Android demande son accord à la personne, et vingt secondes se
        // passent à lire le dialogue (mesuré au banc le 13/08, la requête est
        // relâchée « (timeout) » à la seconde près pendant que le doigt
        // approche). Il est donc porté ici, et taillé pour un humain.
        return withTimeoutOrNull(JOIN_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    joinedNetwork = network
                    joinedName = credentials.networkName
                    Log.i(TAG, "groupe ${credentials.networkName} rejoint")
                    if (cont.isActive) cont.resume(true)
                }

                override fun onLost(network: Network) {
                    // La requête vit encore et retentera : une perte peut n'être
                    // qu'un trou d'antenne. On laisse donc un répit avant de
                    // déclarer le groupe fermé — sinon le moindre clignotement
                    // annoncerait un départ qui n'a pas eu lieu.
                    Log.i(TAG, "groupe ${credentials.networkName} perdu")
                    joinedNetwork = null
                    val mine = joinedCallback
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (joinedCallback === mine && joinedNetwork == null) {
                            Log.i(TAG, "groupe ${credentials.networkName} : parti pour de bon")
                            onGroupLost?.invoke()
                        }
                    }, LOST_GRACE_MS)
                }

                override fun onUnavailable() {
                    Log.w(TAG, "groupe ${credentials.networkName} non rejoint")
                    if (cont.isActive) cont.resume(false)
                }
            }
            joinedCallback = callback
            runCatching {
                connectivity.requestNetwork(request, callback)
            }.onFailure {
                joinedCallback = null
                Log.w(TAG, "requête de groupe refusée — $it")
                if (cont.isActive) cont.resume(false)
            }
            cont.invokeOnCancellation { leaveJoined() }
            }
        } ?: run {
            // personne n'a répondu au dialogue, ou le groupe n'a pas répondu
            Log.w(TAG, "groupe ${credentials.networkName} : délai dépassé")
            leaveJoined()
            false
        }
    }

    /** Rend la requête réseau : la connexion au groupe s'arrête avec elle. */
    private fun leaveJoined() {
        val callback = joinedCallback ?: return
        joinedCallback = null
        joinedNetwork = null
        joinedName = null
        runCatching {
            appContext.getSystemService(ConnectivityManager::class.java)
                ?.unregisterNetworkCallback(callback)
        }
    }

    /**
     * Le réseau du groupe, quand Android le publie. Une socket non liée part
     * par le réseau par défaut — la station — et n'atteindrait jamais le
     * 192.168.49.1 du propriétaire. Quand rien n'est publié, on rend null et
     * l'appelant tente la route ordinaire, qui suffit sur bien des appareils.
     */
    @Suppress("DEPRECATION")
    fun network(): Network? {
        // Invité : le réseau que la requête nous a rendu, à lier aux sockets.
        joinedNetwork?.let { return it }
        // Hôte : c'est notre propre interface p2p, qu'aucune requête ne porte.
        val connectivity = appContext.getSystemService(ConnectivityManager::class.java) ?: return null
        return connectivity.allNetworks.firstOrNull { network ->
            connectivity.getLinkProperties(network)?.interfaceName?.startsWith("p2p") == true
        }
    }

    /**
     * Referme le groupe. Sans ça, l'interface P2P survivrait à la cabine.
     *
     * Le retrait est asynchrone : fermer le canal dans la foulée, comme on le
     * faisait, coupait la commande avant qu'elle passe — vu au banc le
     * 2026-08-12, un groupe restait ouvert après une fermeture pourtant propre.
     * On attend donc le verdict du système pour fermer le canal, et la trace
     * n'est effacée qu'en cas de succès : un retrait refusé laisse de quoi
     * reprendre au lancement suivant plutôt qu'un groupe orphelin.
     */
    @SuppressLint("MissingPermission")
    fun release() {
        // Invité : rendre la requête suffit et se fait tout de suite.
        leaveJoined()
        val manager = this.manager ?: return
        val channel = this.channel ?: return
        this.channel = null
        val handler = Handler(Looper.getMainLooper())
        val done = { removed: Boolean ->
            if (removed) forget()
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) channel.close()
            }
        }
        // La cabine se ferme sans attendre : les tentatives vivent sur le
        // Looper principal, celui-là même qui porte le canal P2P, et survivent
        // donc à l'annulation du scope de CabinChat.
        fun attempt(left: Int) {
            runCatching {
                manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        done(true)
                    }

                    override fun onFailure(reason: Int) {
                        if (reason == WifiP2pManager.BUSY && left > 0) {
                            handler.postDelayed({ attempt(left - 1) }, REMOVE_RETRY_MS)
                        } else {
                            Log.w(
                                TAG,
                                "removeGroup refusé (raison $reason) — repris au prochain lancement",
                            )
                            done(false)
                        }
                    }
                })
            }.onFailure { done(false) }
        }
        attempt(REMOVE_ATTEMPTS)
    }

    /**
     * Referme le groupe **et attend** que le système l'ait vraiment retiré.
     *
     * [release] rend la main aussitôt, ce qui suffit à une fermeture de cabine :
     * personne n'attend derrière. Céder son groupe à un pair, en revanche,
     * enchaîne sur un `join` — et rejoindre pendant qu'on possède encore le
     * sien échouerait. Rend faux si le groupe est toujours là au bout du délai.
     */
    suspend fun releaseAndWait(timeoutMs: Long = RELEASE_WAIT_MS): Boolean {
        release()
        val manager = this.manager ?: return true
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            // le canal a été fermé par release() : on en rouvre un pour lire
            val channel = channel() ?: return true
            if (existingGroup(manager, channel) == null) return true
            delay(RELEASE_POLL_MS)
        }
        return false
    }

    /**
     * Referme le groupe qu'un arrêt brutal a laissé monté. Rend vrai quand il y
     * en avait un et qu'il est parti.
     *
     * `release()` ne peut rien pour ce cas : il ne parle qu'au canal de sa
     * propre instance, et l'instance qui a ouvert le groupe est morte avec le
     * processus. Ici on repart de la trace écrite sur le disque — le seul
     * témoin qui ait survécu.
     *
     * Sans permission, on ne touche à rien et on garde la trace : le groupe
     * attendra un lancement où on aura le droit de le fermer, plutôt que d'être
     * oublié en silence.
     */
    @SuppressLint("MissingPermission")
    suspend fun reclaim(): Boolean {
        val engaged = trace.getString(TRACE_KEY, null) ?: return false
        val manager = this.manager ?: return false
        if (!permissionsGranted(appContext)) {
            Log.i(TAG, "groupe $engaged laissé ouvert : permission Wi-Fi Direct retirée")
            return false
        }
        val channel = channel() ?: return false
        val verdict = decide(engaged, existingGroup(manager, channel)?.networkName)
        val removed = when (verdict) {
            is Verdict.Remove -> {
                Log.i(TAG, "groupe ${verdict.networkName} retrouvé ouvert — on le referme")
                removeGroup(manager, channel)
            }
            // Le groupe est tombé de lui-même (redémarrage, Wi-Fi coupé), ou
            // ce qui est monté appartient à quelqu'un d'autre : dans les deux
            // cas il n'y a rien à retirer, seulement une trace à effacer.
            Verdict.Forget, Verdict.Nothing -> false
        }
        forget()
        closeChannel()
        return removed
    }

    /** `removeGroup` qui insiste tant que le système se dit occupé. */
    @SuppressLint("MissingPermission")
    private suspend fun removeGroup(
        manager: WifiP2pManager,
        channel: WifiP2pManager.Channel,
    ): Boolean {
        repeat(REMOVE_ATTEMPTS) {
            val failure = runCatching {
                suspendCancellableCoroutine { cont ->
                    manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
                        override fun onSuccess() = cont.resume(null)
                        override fun onFailure(reason: Int) = cont.resume(reason)
                    })
                }
            }.getOrDefault(WifiP2pManager.ERROR)
            if (failure == null) return true
            if (failure != WifiP2pManager.BUSY) {
                Log.w(TAG, "removeGroup refusé (raison $failure)")
                return false
            }
            delay(REMOVE_RETRY_MS)
        }
        Log.w(TAG, "removeGroup encore occupé après $REMOVE_ATTEMPTS tentatives")
        return false
    }

    private fun closeChannel() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) channel?.close()
        }
        this.channel = null
    }

    /**
     * Le nom du groupe monté sur cet appareil, quel qu'en soit le propriétaire.
     * Réservé aux vérifications : c'est la seule façon de constater qu'un
     * groupe est vraiment parti, là où la trace ne dit que notre intention.
     */
    @SuppressLint("MissingPermission")
    internal suspend fun currentGroupName(): String? {
        val manager = this.manager ?: return null
        if (!permissionsGranted(appContext)) return null
        val channel = channel() ?: return null
        return existingGroup(manager, channel)?.networkName
    }

    @SuppressLint("MissingPermission")
    private suspend fun existingGroup(
        manager: WifiP2pManager,
        channel: WifiP2pManager.Channel,
    ): WifiP2pGroup? = runCatching {
        suspendCancellableCoroutine { cont ->
            manager.requestGroupInfo(channel) { group -> cont.resume(group) }
        }
    }.getOrNull()

    private suspend fun connectionInfo(
        manager: WifiP2pManager,
        channel: WifiP2pManager.Channel,
    ): WifiP2pInfo? = runCatching {
        suspendCancellableCoroutine { cont ->
            manager.requestConnectionInfo(channel) { info -> cont.resume(info) }
        }
    }.getOrNull()
}
