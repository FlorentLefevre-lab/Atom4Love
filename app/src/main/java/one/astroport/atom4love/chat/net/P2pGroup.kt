package one.astroport.atom4love.chat.net

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
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
        private const val TAG = "CabinChat"

        /** Le propriétaire d'un groupe Wi-Fi Direct est toujours à cette adresse. */
        const val OWNER_ADDRESS = "192.168.49.1"

        /** Rejoindre par identifiants demande l'API 29 (`WifiP2pConfig.Builder`). */
        private val JOIN_SUPPORTED = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        /** Formation d'un groupe : quelques secondes en pratique, jamais instantané. */
        private const val FORM_TIMEOUT_MS = 20_000L
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
    @SuppressLint("MissingPermission")
    suspend fun join(credentials: Credentials): Boolean {
        val manager = this.manager ?: return false
        val channel = channel() ?: return false
        if (!JOIN_SUPPORTED) {
            Log.w(TAG, "Wi-Fi Direct : rejoindre par identifiants demande Android 10")
            return false
        }
        if (!permissionsGranted(appContext)) {
            Log.w(TAG, "Wi-Fi Direct : permission manquante")
            return false
        }
        // Déjà dedans ? `connect` refuserait (raison 0) alors qu'il n'y a rien à
        // faire. Un groupe survit à l'arrêt de l'app qui l'a formé — vu au banc
        // le 2026-08-11 : après un redémarrage, l'appareil était toujours
        // membre et n'arrivait plus à « rejoindre » ce qu'il n'avait pas quitté.
        existingGroup(manager, channel)?.let { group ->
            if (!group.isGroupOwner && group.networkName == credentials.networkName) {
                Log.i(TAG, "déjà dans le groupe ${credentials.networkName}")
                engage(group.networkName)
                return true
            }
        }
        val config = runCatching {
            WifiP2pConfig.Builder()
                .setNetworkName(credentials.networkName)
                .setPassphrase(credentials.passphrase)
                .build()
        }.getOrElse {
            Log.w(TAG, "identifiants de groupe refusés — $it")
            return false
        }
        val asked = runCatching {
            suspendCancellableCoroutine { cont ->
                manager.connect(channel, config, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() = cont.resume(true)
                    override fun onFailure(reason: Int) {
                        Log.w(TAG, "connexion au groupe refusée (raison $reason)")
                        cont.resume(false)
                    }
                })
            }
        }.getOrDefault(false)
        if (!asked) return false
        val joined = withTimeoutOrNull(FORM_TIMEOUT_MS) {
            while (true) {
                val info = connectionInfo(manager, channel)
                if (info != null && info.groupFormed) return@withTimeoutOrNull true
                delay(POLL_MS)
            }
            @Suppress("UNREACHABLE_CODE") false
        } ?: false
        Log.i(TAG, if (joined) "groupe ${credentials.networkName} rejoint" else "groupe non formé")
        // Rejoindre engage autant qu'ouvrir : un membre reste membre après la
        // mort de l'app, et c'est `removeGroup` qui l'en fait sortir.
        if (joined) engage(credentials.networkName)
        return joined
    }

    /**
     * Le réseau du groupe, quand Android le publie. Une socket non liée part
     * par le réseau par défaut — la station — et n'atteindrait jamais le
     * 192.168.49.1 du propriétaire. Quand rien n'est publié, on rend null et
     * l'appelant tente la route ordinaire, qui suffit sur bien des appareils.
     */
    @Suppress("DEPRECATION")
    fun network(): Network? {
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
