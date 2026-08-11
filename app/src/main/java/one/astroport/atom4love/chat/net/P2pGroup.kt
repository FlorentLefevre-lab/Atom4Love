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
    }

    /** De quoi entrer dans un groupe sans rien découvrir. */
    data class Credentials(val networkName: String, val passphrase: String)

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(WifiP2pManager::class.java)
    private var channel: WifiP2pManager.Channel? = null

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

    /** Referme le groupe. Sans ça, l'interface P2P survivrait à la cabine. */
    @SuppressLint("MissingPermission")
    fun release() {
        val manager = this.manager ?: return
        val channel = this.channel ?: return
        runCatching { manager.removeGroup(channel, null) }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) channel.close()
        }
        this.channel = null
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
