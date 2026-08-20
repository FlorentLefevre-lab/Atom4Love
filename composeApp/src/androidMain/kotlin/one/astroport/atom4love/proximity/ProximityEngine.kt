package one.astroport.atom4love.proximity

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Cœur du module proximité : annonce BLE continue de l'adresse 4D + scan continu
 * des pairs, alimentant [NeighborRegistry].
 *
 * Leçon du POC ([one.astroport.atom4love.diag.BleConcurrencyProbe]) : deux sondes
 * à fenêtres courtes (~12 s) ne se voient que lancées simultanément. D'où le parti
 * pris inverse — annonce et scan permanents en mode BALANCED, portés par un service
 * foreground. Le scan est filtré sur [SERVICE_UUID] : c'est la condition pour
 * qu'Android le laisse tourner écran éteint au lieu de le throttler comme un scan nu.
 */
class ProximityEngine(
    context: Context,
    private val registry: NeighborRegistry,
    private val locator: CellLocator,
    private val rotation: CellRotation = CellRotation.None,
    /**
     * La clé publique du noyau, relue à chaque annonce : elle n'existe pas
     * forcément quand la balise démarre (fiche pas encore forgée), et elle
     * change le jour où une station rend la clé LOVE. Rien d'elle ne part
     * dans l'air — seulement le jeton de [ProximityPayload.token].
     */
    private val nostrKey: () -> ByteArray? = { null },
    /**
     * Ce que le noyau dit de lui sans se nommer — polarité, sceau maya, phase.
     * Relue comme la clé à chaque tour : elle naît avec la fiche, morceau par
     * morceau (le sceau dès la date, la phase seulement avec le lieu).
     */
    private val signature: () -> ProximityPayload.Signature = {
        ProximityPayload.Signature.Unknown
    },
) {

    companion object {
        /**
         * Même UUID 16 bits (plage vendor) que le POC : les anciennes sondes
         * apparaissent comme des payloads non décodables, ignorées proprement.
         */
        val SERVICE_UUID: ParcelUuid =
            ParcelUuid.fromString("0000fff0-0000-1000-8000-00805f9b34fb")

        /**
         * L'UUID de la déclaration de recherche ([SeekingPayload]) — **distinct**
         * de celui de la présence.
         *
         * Deux UUID plutôt qu'un seul avec deux formats : un pair qui ne
         * comprend pas les recherches ignore proprement ce service au lieu
         * d'essayer de décoder une trame qui n'est pas la sienne, et le filtre
         * de scan reste explicite sur ce qu'il accepte. Le voisin d'à côté
         * (cabine-33) ne voit strictement rien de neuf sur `fff0`.
         */
        val SEEK_UUID: ParcelUuid =
            ParcelUuid.fromString("0000fff1-0000-1000-8000-00805f9b34fb")

        private const val TAG = "Proximity"

        /**
         * **Avant Android 12, un scan BLE est aveugle sans la position.**
         *
         * Le système traite une liste de balises vues comme une position
         * déduite, et la refuse donc à qui n'a pas la localisation — permission
         * ET interrupteur système. Le scan démarre alors sans la moindre erreur
         * et ne remonte simplement jamais rien.
         *
         * ⚠ Depuis Android 12 nous demandons `BLUETOOTH_SCAN` avec
         * `neverForLocation` : le scan y tourne sans la position, et la seule
         * chose qui manque alors est la **cellule** — donc le jeton de présence
         * et les compteurs de portail, jamais les cartes elles-mêmes. La
         * différence est exactement ce que cette fonction dit.
         *
         * Constaté sur l'A5 (LineageOS 17.1, Android 10) le 19/08 : « à portée
         * (0) » et « Personne ne montre sa carte », alors que l'appareil ne
         * pouvait pas regarder. Voir [State.scanBlind].
         */
        fun scanNeedsLocation(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S

        /** Re-résolution périodique de la cellule (et de l'annonce si elle change). */
        private const val CELL_REFRESH_MS = 5 * 60_000L

        /** Cadence resserrée tant que la cellule n'a pas pu être résolue. */
        private const val CELL_RETRY_MS = 30_000L

        /** Le ciel ne s'ouvre pas en trente secondes — voir la boucle. */
        private const val CELL_IMPRECISE_RETRY_MS = 3 * 60_000L
        private const val SWEEP_INTERVAL_MS = 10_000L
        private const val ADVERTISE_START_TIMEOUT_MS = 10_000L
    }

    data class State(
        val advertising: Boolean = false,
        /** Adresse 4D actuellement diffusée (null = cellule non résolue ou balise coupée). */
        val advertisedCell4d: Long? = null,
        val scanning: Boolean = false,
        /**
         * Le scan tourne, et il ne verra rien : la position manque sur un
         * Android qui l'exige pour balayer ([scanNeedsLocation]).
         *
         * ⚠ Ce n'est pas la même chose que `!scanning`. Un scan refusé se dit
         * dans [lastError] ; celui-ci a démarré, il est en règle du point de
         * vue du Bluetooth, et il est muet. Sans ce champ, un écran ne peut
         * que conclure « il n'y a personne », ce qui est faux.
         */
        val scanBlind: Boolean = false,
        val lastError: String? = null,
    )

    private val adapter: BluetoothAdapter? =
        (context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
            .adapter

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** Jeton actuellement dans l'air — il n'a pas à sortir d'ici. */
    private var advertisedToken: Int? = null
    private var advertisedSignature = ProximityPayload.Signature.Unknown

    /** La déclaration de recherche en cours, et de quoi la couper. */
    private var advertisedSeeking: ByteArray? = null
    private var seekingCallback: AdvertisingSetCallback? = null

    /**
     * Sans annonce étendue, pas de déclaration : les 31 octets de la legacy
     * sont pleins et gelés. Le jeu retombe alors sur ce qu'il faisait avant —
     * la double sélection, réciproque deux fois sur trois — au lieu de refuser
     * de fonctionner. Relevé sur le banc le 16/08 : supportée des deux côtés,
     * 1650 octets sur le Pixel, 304 sur la tablette.
     */
    private fun seekingSupported(adapter: BluetoothAdapter): Boolean =
        adapter.isLeExtendedAdvertisingSupported

    @SuppressLint("MissingPermission")
    private fun startSeeking(
        advertiser: BluetoothLeAdvertiser,
        data: ByteArray,
    ): AdvertisingSetCallback {
        val parameters = AdvertisingSetParameters.Builder()
            .setLegacyMode(false)
            .setConnectable(false)
            .setScannable(false)
            .setInterval(AdvertisingSetParameters.INTERVAL_MEDIUM)
            .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_MEDIUM)
            .build()
        val payload = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(SEEK_UUID)
            .addServiceData(SEEK_UUID, data)
            .build()
        val callback = object : AdvertisingSetCallback() {
            override fun onAdvertisingSetStarted(
                set: android.bluetooth.le.AdvertisingSet?,
                txPower: Int,
                status: Int,
            ) {
                if (status == ADVERTISE_SUCCESS) {
                    Log.d(TAG, "recherche annoncée (${data.size} octets, $txPower dBm)")
                } else {
                    Log.w(TAG, "recherche refusée (status=$status)")
                }
            }
        }
        runCatching { advertiser.startAdvertisingSet(parameters, payload, null, null, null, callback) }
            .onFailure { Log.w(TAG, "recherche impossible : ${it.message}") }
        return callback
    }

    @SuppressLint("MissingPermission")
    private fun stopSeeking(advertiser: BluetoothLeAdvertiser) {
        seekingCallback?.let { runCatching { advertiser.stopAdvertisingSet(it) } }
        seekingCallback = null
        advertisedSeeking = null
    }

    /**
     * Tourne jusqu'à annulation de la coroutine appelante ; l'annonce et le scan
     * sont toujours coupés (et le registre vidé) en sortie.
     */
    @SuppressLint("MissingPermission")
    suspend fun run() {
        val adapter = this.adapter
        if (adapter == null || !adapter.isEnabled) {
            _state.update { it.copy(lastError = "Bluetooth indisponible ou désactivé") }
            return
        }
        // Ce que la puce sait faire — relevé une fois, au démarrage.
        //
        // L'annonce **legacy** plafonne à 31 octets, et nous y sommes déjà à
        // 31/31 : plus un bit pour dire quoi que ce soit de neuf. L'annonce
        // **étendue** du Bluetooth 5 monte à plusieurs centaines d'octets et
        // peut tourner en parallèle de la legacy, donc sans rien casser de ce
        // que lit cabine-33. Savoir si le matériel suit décide de ce qu'on peut
        // se permettre — d'où ce log, plutôt que de le supposer.
        Log.i(
            TAG,
            "BLE : annonce étendue=${adapter.isLeExtendedAdvertisingSupported}" +
                " · taille max=${adapter.leMaximumAdvertisingDataLength}" +
                " · PHY 2M=${adapter.isLe2MPhySupported}" +
                " · PHY codé=${adapter.isLeCodedPhySupported}" +
                " · offload filtres=${adapter.isOffloadedFilteringSupported}",
        )
        val advertiser = adapter.bluetoothLeAdvertiser
        val scanner = adapter.bluetoothLeScanner
        if (advertiser == null || scanner == null) {
            _state.update { it.copy(lastError = "ce chipset ne sait pas annoncer ou scanner en BLE") }
            return
        }

        var advertiseCallback: AdvertiseCallback? = null
        var scanCallback: ScanCallback? = null
        try {
            while (currentCoroutineContext().isActive) {
                // Silence demandé : la cabine transfère, et la même puce porte
                // les deux. On se tait entièrement plutôt que de lui disputer
                // l'antenne — la balise n'a rien d'urgent à dire.
                if (RadioSilence.requested.value) {
                    advertiseCallback?.let { runCatching { advertiser.stopAdvertising(it) } }
                    advertiseCallback = null
                    scanCallback?.let { runCatching { scanner.stopScan(it) } }
                    scanCallback = null
                    _state.update { it.copy(advertising = false, scanning = false) }
                    Log.d(TAG, "balise en silence : transfert en cours")
                    RadioSilence.requested.first { !it }
                    Log.d(TAG, "balise relancée : antenne rendue")
                    continue
                }
                if (scanCallback == null) scanCallback = startScan(scanner)
                val h3Cell = locator.currentCell()
                Log.d(TAG, "cellule H3 résolue : ${h3Cell?.toString(16) ?: "échec (pas de position)"}")
                val cell4d = h3Cell?.let { rotation.apply(it, System.currentTimeMillis()) }

                // ⚠ **Dire que le scan est aveugle, plutôt que laisser croire
                // que la salle est vide.** Une cellule résolue prouve à elle
                // seule que la position est accordée et allumée : on n'interroge
                // le motif de blocage que dans le cas contraire. La cadence
                // resserrée de 30 s (CELL_RETRY_MS) vaut alors aussi pour cette
                // ligne — c'est elle qui court tant que la cellule manque.
                // ⚠ **L'imprécision n'aveugle pas le scan.** Tout est accordé
                // et allumé, la radio voit très bien : c'est le LIEU qu'on ne
                // sait pas nommer. Le compter comme une cécité écrirait « il me
                // manque la position » à quelqu'un qui vient de l'accorder.
                val blocker = if (h3Cell == null) locator.blocker() else null
                val blind = scanNeedsLocation() &&
                    blocker != null && blocker != CellLocator.Blocker.IMPRECISE
                if (blind != _state.value.scanBlind) {
                    Log.i(TAG, if (blind) "scan aveugle : pas de position" else "scan à nouveau voyant")
                }
                // ⚠⚠ **Un scan démarré sans la position reste aveugle POUR
                // TOUJOURS, même une fois la permission accordée.** Android
                // fige le droit de voir au moment du `startScan` : le
                // rappel continue de tourner, sans erreur, et ne remonte plus
                // jamais rien. Mesuré sur l'A5 le 20/08 — permission rendue,
                // ligne d'écran repartie, **zéro pair en 80 s** ; le même
                // appareil, processus relancé, en voyait 14 en 25 s.
                //
                // Sans ce redémarrage, le « Accorder la localisation » qu'on
                // vient d'écrire mènerait à un écran qui continue de ne voir
                // personne — c'est-à-dire à un mensonge de plus, en pire :
                // celui qui a l'air d'obéir.
                if (_state.value.scanBlind && !blind) {
                    Log.i(TAG, "position revenue : scan relancé (un scan aveugle le reste)")
                    scanCallback?.let { runCatching { scanner.stopScan(it) } }
                    scanCallback = startScan(scanner)
                }
                _state.update { it.copy(scanBlind = blind) }

                // Le jeton suit la cellule ET le noyau : refaire l'annonce quand
                // l'un des deux bouge, sinon un noyau forgé après le démarrage
                // de la balise n'y figurerait jamais.
                val token = ProximityPayload.token(nostrKey(), cell4d)
                // La signature entre dans la même comparaison : une fiche
                // complétée pendant que la balise tourne doit passer dans
                // l'air, et une fiche inchangée ne doit pas relancer l'annonce.
                val signature = signature()
                if (advertiseCallback == null ||
                    cell4d != _state.value.advertisedCell4d ||
                    token != advertisedToken ||
                    signature != advertisedSignature
                ) {
                    advertiseCallback?.let { runCatching { advertiser.stopAdvertising(it) } }
                    advertisedToken = token
                    advertisedSignature = signature
                    advertiseCallback = startAdvertising(advertiser, cell4d, token, signature)
                }

                // ── La déclaration de recherche, en annonce étendue ────────
                //
                // Elle vit à côté de l'annonce ordinaire, jamais dedans : la
                // legacy reste à 31/31 et ne bouge pas d'un octet, cabine-33 lit
                // exactement ce qu'elle lisait. Ce qui suit ne s'allume que si
                // quelqu'un a touché une carte, et s'éteint dès qu'il ferme.
                val wanted = SeekingBeacon.targets.value
                val seekData = advertisedToken?.let { SeekingPayload.encode(it, wanted) }
                if (seekData == null || !seekingSupported(adapter)) {
                    stopSeeking(advertiser)
                } else if (!seekData.contentEquals(advertisedSeeking)) {
                    stopSeeking(advertiser)
                    advertisedSeeking = seekData
                    seekingCallback = startSeeking(advertiser, seekData)
                }

                var waited = 0L
                // ⚠ Trois cadences, et la troisième est une question
                // d'énergie : une position trop imprécise ne se corrige pas en
                // trente secondes — on est à l'intérieur, et le ciel ne va pas
                // s'ouvrir. Réessayer aussi vite y tiendrait le GNSS allumé en
                // continu sur une balise qui, elle, tourne tout le temps.
                val refreshMs = when {
                    cell4d != null -> CELL_REFRESH_MS
                    blocker == CellLocator.Blocker.IMPRECISE -> CELL_IMPRECISE_RETRY_MS
                    else -> CELL_RETRY_MS
                }
                // l'attente se rompt sur demande de silence : attendre les 30 s
                // du prochain rafraîchissement laisserait l'antenne occupée
                // pendant tout le transfert
                // ⚠ L'attente se rompt aussi quand la recherche change, sinon
                // toucher une carte n'annoncerait rien avant cinq minutes — et
                // fermer la lanterne continuerait de parler tout ce temps-là.
                // ⚠ Elle se rompt aussi quand la position revient à un scan
                // aveugle : attendre les 30 s du tour suivant pour relancer un
                // scan qu'on sait mort ferait douter du geste qu'on vient de
                // faire. La sonde ne coûte qu'un test de permission, et ne
                // tourne que pendant la cécité.
                while (waited < refreshMs &&
                    !RadioSilence.requested.value &&
                    SeekingBeacon.targets.value == wanted &&
                    !(blind && locator.blocker() == null)
                ) {
                    delay(SWEEP_INTERVAL_MS)
                    waited += SWEEP_INTERVAL_MS
                    registry.sweep()
                }
            }
        } finally {
            advertiseCallback?.let { runCatching { advertiser.stopAdvertising(it) } }
            scanCallback?.let { runCatching { scanner.stopScan(it) } }
            registry.clear()
            _state.value = State()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun startAdvertising(
        advertiser: BluetoothLeAdvertiser,
        cell4d: Long?,
        token: Int?,
        signature: ProximityPayload.Signature,
    ): AdvertiseCallback? {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setConnectable(false)
            .setTimeout(0)
            .build()
        // ── La puissance d'émission, dans l'annonce ───────────────────────
        //
        // `setIncludeTxPowerLevel` ajoute le champ **normalisé** du Bluetooth
        // (AD type 0x0A) : la puissance que la puce émet vraiment, en dBm, telle
        // que le contrôleur la connaît. Le pair d'en face la lit par
        // `ScanResult.getTxPower()` et peut enfin retrancher ce que l'émetteur y
        // a mis avant de conclure sur une distance.
        //
        // ⚠ Sans elle, un RSSI ne dit rien tout seul. Deux téléphones à un
        // mètre, l'un émettant à −4 dBm et l'autre à −20, sont entendus 16 dB
        // plus loin l'un que l'autre — soit un facteur 4 sur la distance
        // déduite. C'est le défaut de fond que la note du 15/08 signalait à
        // Fred, et **il se règle sans toucher à notre charge utile** : le
        // VERSION 4 à 18 octets qu'elle proposait n'a plus lieu d'être.
        //
        // ⚠ Le budget est **exact**, pas confortable : 3 octets de drapeaux,
        // 4 pour l'UUID de service, 21 pour le bloc de données (2 + 2 + 17),
        // soit 28 sur les 31 d'une annonce legacy. Le champ en coûte 3 : 31/31.
        // Un stack qui compterait autrement refuserait tout, d'où le repli.
        fun advertiseData(withTxPower: Boolean) = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(withTxPower)
            .addServiceUuid(SERVICE_UUID)
            .addServiceData(SERVICE_UUID, ProximityPayload.encode(cell4d, token, signature))
            .build()

        // Un essai avec la puissance, un sans : la seule erreur qu'on rattrape
        // est celle du budget dépassé, les autres n'ont rien à voir avec ce
        // champ et se reproduiraient à l'identique.
        suspend fun attempt(withTxPower: Boolean): AdvertiseCallback? {
            var cb: AdvertiseCallback? = null
            val ok = withTimeoutOrNull(ADVERTISE_START_TIMEOUT_MS) {
                suspendCancellableCoroutine<Boolean> { cont ->
                    val c = object : AdvertiseCallback() {
                        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                            if (cont.isActive) cont.resume(true)
                        }

                        override fun onStartFailure(errorCode: Int) {
                            if (errorCode != ADVERTISE_FAILED_DATA_TOO_LARGE || !withTxPower) {
                                Log.w(TAG, "annonce refusée (errorCode=$errorCode)")
                                _state.update {
                                    it.copy(lastError = "annonce refusée (errorCode=$errorCode)")
                                }
                            }
                            if (cont.isActive) cont.resume(false)
                        }
                    }
                    cb = c
                    advertiser.startAdvertising(settings, advertiseData(withTxPower), c)
                    cont.invokeOnCancellation { runCatching { advertiser.stopAdvertising(c) } }
                }
            } ?: false
            if (!ok) cb?.let { runCatching { advertiser.stopAdvertising(it) } }
            return if (ok) cb else null
        }

        var callback = attempt(withTxPower = true)
        if (callback == null) {
            Log.w(TAG, "annonce sans puissance d'émission : le budget des 31 octets a refusé")
            callback = attempt(withTxPower = false)
        }
        val started = callback != null

        if (!started) return null
        _state.update { it.copy(advertising = true, advertisedCell4d = cell4d, lastError = null) }
        Log.d(TAG, "annonce démarrée, cell4d=${cell4d?.toString(16) ?: "inconnue"}")
        return callback
    }

    @SuppressLint("MissingPermission")
    private fun startScan(scanner: android.bluetooth.le.BluetoothLeScanner): ScanCallback {
        // Le BLE 5 n'est pas un détail de confort ici : voir les réglages plus bas.
        val extended = adapter?.isLeExtendedAdvertisingSupported == true
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) = onPeer(result)

            override fun onBatchScanResults(results: List<ScanResult>) = results.forEach(::onPeer)

            override fun onScanFailed(errorCode: Int) {
                Log.w(TAG, "scan refusé (errorCode=$errorCode)")
                _state.update {
                    it.copy(scanning = false, lastError = "scan refusé (errorCode=$errorCode)")
                }
            }
        }
        scanner.startScan(
            listOf(
                ScanFilter.Builder().setServiceUuid(SERVICE_UUID).build(),
                ScanFilter.Builder().setServiceUuid(SEEK_UUID).build(),
            ),
            // ⚠ `setLegacy(false)` remonte les annonces étendues EN PLUS des
            // legacy : sans lui, les déclarations de recherche — qui voyagent
            // en annonce étendue — seraient invisibles sans qu'aucune erreur ne
            // le dise.
            //
            // ⚠⚠ **Mais il rend AVEUGLE une puce qui ne connaît pas le BLE 5.**
            // Sur l'A5 de 2016 (contrôleur 4.x), ce réglage fait démarrer le
            // scan sans la moindre erreur et ne remonte **plus rien du tout**,
            // legacy comprises. Vu le 19/08 : l'A5 était vu de tous — il annonce
            // en legacy — et ne voyait personne ; « à portée (0) », balise
            // active, même hexagone que les autres, et pas une ligne au journal.
            //
            // On ne le demande donc que là où il existe. Ce qu'on y perd sur les
            // vieux appareils : les déclarations de recherche, qu'ils ne savent
            // de toute façon pas émettre ([seekingSupported] les leur refuse
            // déjà). Ce qu'on y gagne : la présence, c'est-à-dire tout le jeu.
            ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .apply { if (extended) setLegacy(false) }
                .build(),
            callback,
        )
        Log.d(TAG, "scan démarré (filtre $SERVICE_UUID, étendu=$extended)")
        _state.update { it.copy(scanning = true) }
        return callback
    }

    private fun onPeer(result: ScanResult) {
        // Une déclaration de recherche arrive sur son propre service ; elle ne
        // porte ni cellule ni signature, et n'a donc rien à faire dans le
        // registre des voisins. On la traite à part et on s'arrête là.
        SeekingPayload.decode(result.scanRecord?.getServiceData(SEEK_UUID))?.let { seeking ->
            onSeeking(seeking)
            return
        }
        val payload = ProximityPayload.decode(
            result.scanRecord?.getServiceData(SERVICE_UUID),
        ) ?: return
        // ⚠ **Deux champs portent ce nom, et ce ne sont pas les mêmes.**
        //
        // - `ScanRecord.getTxPowerLevel()` lit l'AD type **0x0A**, celui que
        //   `setIncludeTxPowerLevel` écrit dans la charge d'une annonce legacy.
        //   Absent = `Integer.MIN_VALUE`. **C'est le nôtre.**
        // - `ScanResult.getTxPower()` lit l'entête d'une annonce **étendue**
        //   (Bluetooth 5), que nous n'émettons pas. Absent = 127.
        //
        // Mesuré le 15/08 : nos deux appareils rendaient `tx=absent` alors que
        // l'annonce partait bien avec le champ — on interrogeait le mauvais des
        // deux. On lit donc le premier, et le second en repli pour les piles qui
        // annoncent en étendu.
        val txPower = result.scanRecord?.txPowerLevel?.takeIf { it != Int.MIN_VALUE }
            ?: result.txPower.takeIf { it != ScanResult.TX_POWER_NOT_PRESENT }
        Log.d(TAG, "pair ${result.device.address} rssi=${result.rssi} " +
            "tx=${txPower?.let { "$it dBm" } ?: "absent"} " +
            "cell4d=${payload.cell4d?.toString(16) ?: "inconnue"}")
        registry.report(
            result.device.address, payload.cell4d, payload.token, result.rssi,
            payload.signature, txPower,
        )
    }

    /**
     * Quelqu'un déclare chercher des cartes. On ne retient que le cas qui nous
     * regarde : **est-ce nous ?**
     *
     * Les autres déclarations passent sans être notées. Tenir la liste de qui
     * cherche qui dans la salle ferait de chaque téléphone un observatoire des
     * intentions des autres — ce n'est pas parce que l'information passe dans
     * l'air qu'on a une raison de la collectionner.
     */
    private fun onSeeking(seeking: SeekingPayload.Seeking) {
        val mine = advertisedToken ?: return
        if (!seeking.seeks(mine)) return
        Log.d(TAG, "une carte nous cherche (jeton ${seeking.from})")
        registry.reportSeeker(seeking.from)
    }
}
