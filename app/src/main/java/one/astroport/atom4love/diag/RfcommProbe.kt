package one.astroport.atom4love.diag

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.system.measureTimeMillis

/**
 * Banc d'essai : que vaut vraiment le **Bluetooth classique** (RFCOMM) entre
 * deux appareils du projet ?
 *
 * La question vient de Florent, le 16/08 : le BLE plafonne à 14 ko/s et
 * contraint le chat, les pièces jointes et la recherche — le BR/EDR ferait-il
 * mieux, et assez pour s'en passer ? On ne répond pas à ça de mémoire : la
 * cabine a déjà appris qu'un débit annoncé et un débit mesuré font deux (le
 * Wi-Fi Direct s'est effondré à 100 ko/s tant que des liens BLE tournaient).
 *
 * ## Ce que la sonde établit, et ce qu'elle n'établit pas
 *
 * Elle mesure un **transfert brut**, du client vers le serveur, sur un socket
 * RFCOMM **non apparié**. Elle ne dit rien de la tenue écran éteint, ni de la
 * cohabitation avec la balise sur la même puce — deux choses qui se mesurent
 * séparément, et qui ont déjà réservé des surprises ici.
 *
 * ## ⚠ Le point qui change l'analyse
 *
 * `listenUsingInsecureRfcommWithServiceRecord` **n'exige ni appairage ni
 * découvrabilité** : le client se connecte à une adresse qu'il connaît déjà. Le
 * Bluetooth classique n'oblige donc pas à diffuser son nom et sa MAC à toute la
 * salle — ce qu'il faut, c'est connaître l'adresse du pair, et le lien BLE
 * chiffré sait déjà transporter ce genre de secret (il le fait pour le nom et
 * la passe du groupe Wi-Fi). D'où l'idée d'un **quatrième médium** plutôt que
 * d'un remplacement du BLE.
 */
object RfcommProbe {

    private const val TAG = "RfcommProbe"

    /** Nom du service — visible seulement de qui interroge le SDP de l'appareil. */
    private const val NAME = "A4L-probe"

    /** UUID dédié, sans rapport avec les services BLE du projet. */
    private val UUID_PROBE: UUID = UUID.fromString("6f2c1a40-8b3e-4f21-9c77-0a1b2c3d4e5f")

    /** Un tampon confortable : on mesure le lien, pas le coût des appels. */
    private const val CHUNK = 8 * 1024

    /**
     * Côté récepteur : attend une connexion, avale ce qui vient, rend le débit.
     *
     * Le débit se compte sur ce qui est **reçu**, jamais sur ce que l'émetteur
     * croit avoir envoyé : un `write` rendu ne veut pas dire un octet parti.
     */
    @SuppressLint("MissingPermission")
    suspend fun serve(adapter: BluetoothAdapter): String = withContext(Dispatchers.IO) {
        var server: BluetoothServerSocket? = null
        var socket: BluetoothSocket? = null
        try {
            server = adapter.listenUsingInsecureRfcommWithServiceRecord(NAME, UUID_PROBE)
            Log.i(TAG, "en écoute (RFCOMM insecure, sans appairage)")
            socket = server.accept()
            Log.i(TAG, "connecté par ${socket.remoteDevice?.address}")
            val input: InputStream = socket.inputStream
            val buffer = ByteArray(CHUNK)
            var total = 0L
            var first = 0L
            var last = 0L
            // ⚠ La fin du transfert arrive par **exception**, pas par un `read`
            // qui rendrait −1 : côté Android, un socket RFCOMM fermé en face
            // lève « bt socket closed, read return: -1 ». Le premier jet de
            // cette sonde perdait donc sa mesure au moment précis où elle était
            // complète. On sort de la boucle par l'exception, et on compte.
            runCatching {
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    if (first == 0L) first = System.nanoTime()
                    total += read
                    last = System.nanoTime()
                }
            }
            val seconds = (last - first).coerceAtLeast(1) / 1e9
            val rate = total / seconds / 1024.0
            val verdict = "REÇU %.2f Mo en %.2f s → %.0f ko/s".format(
                total / 1024.0 / 1024.0, seconds, rate,
            )
            Log.i(TAG, verdict)
            verdict
        } catch (e: Exception) {
            val message = "échec côté récepteur : ${e.message}"
            Log.w(TAG, message)
            message
        } finally {
            runCatching { socket?.close() }
            runCatching { server?.close() }
        }
    }

    /**
     * Côté émetteur : se connecte à [mac] et pousse [megabytes] Mo.
     *
     * ⚠ `cancelDiscovery()` d'abord, sans quoi une découverte en cours ralentit
     * ou fait échouer la connexion — c'est écrit dans la doc d'Android, et ça
     * ne pardonne pas.
     */
    @SuppressLint("MissingPermission")
    suspend fun send(
        adapter: BluetoothAdapter,
        mac: String,
        megabytes: Int,
    ): String = withContext(Dispatchers.IO) {
        var socket: BluetoothSocket? = null
        try {
            adapter.cancelDiscovery()
            val device = adapter.getRemoteDevice(mac)
            socket = device.createInsecureRfcommSocketToServiceRecord(UUID_PROBE)
            val connectMs = measureTimeMillis { socket.connect() }
            Log.i(TAG, "connecté à $mac en $connectMs ms (appairé=${device.bondState})")
            val output: OutputStream = socket.outputStream
            val chunk = ByteArray(CHUNK) { it.toByte() }
            val rounds = megabytes * 1024 * 1024 / CHUNK
            var sent = 0L
            val elapsed = measureTimeMillis {
                repeat(rounds) {
                    output.write(chunk)
                    sent += CHUNK
                }
                output.flush()
            }
            val rate = sent / (elapsed / 1000.0) / 1024.0
            val verdict = "ÉMIS %.2f Mo en %.2f s → %.0f ko/s (connexion %d ms)".format(
                sent / 1024.0 / 1024.0, elapsed / 1000.0, rate, connectMs,
            )
            Log.i(TAG, verdict)
            verdict
        } catch (e: Exception) {
            val message = "échec côté émetteur : ${e.message}"
            Log.w(TAG, message)
            message
        } finally {
            runCatching { socket?.close() }
        }
    }
}
