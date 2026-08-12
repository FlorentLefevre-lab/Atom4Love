package one.astroport.atom4love.multipass

import android.util.Log
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import one.astroport.atom4love.data.MultipassAccount
import one.astroport.atom4love.data.AccountVault
import one.astroport.atom4love.domain.BirthData

/**
 * L'inscription vue depuis l'app : une seule intention — « ouvrir un compte » —
 * là où la station demande deux appels distincts.
 *
 * Le premier crée le MULTIPASS et ses portefeuilles, sans rien savoir de qui
 * vous êtes. Le second lui confie la naissance et en reçoit la clé LOVE. Entre
 * les deux, le compte est déjà réel : si l'activation échoue, on n'a pas perdu
 * le MULTIPASS, seulement la clé LOVE — et [retryActivation] la redemande sans
 * recréer quoi que ce soit.
 */
class Enrollment(
    private val scope: CoroutineScope,
    private val service: MultipassService,
    private val store: AccountVault,
) {

    companion object {
        private const val TAG = "Multipass"
    }

    /** Où en est la demande. Un seul chemin, et ses embranchements d'échec. */
    sealed interface Step {
        /** Rien en cours : l'écran attend une adresse email. */
        data object Idle : Step

        /** La station forge le compte — jusqu'à 40 s, c'est normal. */
        data object Creating : Step

        /** Cet email a déjà un MULTIPASS : son code PASS est nécessaire. */
        data class NeedPass(val email: String) : Step

        /** Le compte est là ; la naissance part chercher la clé LOVE. */
        data object Activating : Step

        /** Compte ouvert et clé LOVE en main. */
        data class Done(val account: MultipassAccount) : Step

        /**
         * @param recoverable le MULTIPASS existe (seule l'activation a échoué) :
         *   il n'y a que la clé LOVE à redemander, jamais un compte à recréer.
         */
        data class Failed(val reason: String, val recoverable: Boolean = false) : Step
    }

    private val _step = MutableStateFlow<Step>(Step.Idle)
    val step: StateFlow<Step> = _step.asStateFlow()

    private var job: Job? = null

    /** Le compte connu de l'appareil, rechargé au démarrage. */
    suspend fun restore(): MultipassAccount? = store.load()

    /**
     * Ouvre le compte et va au bout : création puis activation.
     *
     * @param passCode à fournir seulement après un [Step.NeedPass].
     */
    fun enroll(
        email: String,
        birth: BirthData,
        lat: Double?,
        lon: Double?,
        passCode: String? = null,
    ) {
        job?.cancel()
        job = scope.launch {
            val account = create(email, lat, lon, passCode) ?: return@launch
            activate(account, birth)
        }
    }

    /**
     * Le MULTIPASS est créé mais la clé LOVE manque : on ne repasse pas par la
     * création — l'email existe désormais, elle réclamerait un code PASS.
     */
    fun retryActivation(birth: BirthData) {
        job?.cancel()
        job = scope.launch {
            val account = store.load()
            if (account == null) {
                _step.value = Step.Failed("aucun compte enregistré sur cet appareil")
                return@launch
            }
            activate(account, birth)
        }
    }

    /** Repart d'une page blanche après un échec ou un abandon. */
    fun reset() {
        job?.cancel()
        _step.value = Step.Idle
    }

    private suspend fun create(
        email: String,
        lat: Double?,
        lon: Double?,
        passCode: String?,
    ): MultipassAccount? {
        _step.value = Step.Creating
        return try {
            val response = service.createMultipass(
                email = email.trim().lowercase(),
                lang = "fr",
                // La position du moment sert à rattacher le compte à une UMAP ;
                // sans localisation, la station accepte des coordonnées nulles.
                lat = coord(lat),
                lon = coord(lon),
                passCode = passCode,
            )
            store.save(service.stationUrl, response)
            store.load()
        } catch (e: MultipassError.Exists) {
            _step.value = Step.NeedPass(email.trim().lowercase())
            null
        } catch (e: MultipassError.InvalidPass) {
            _step.value = Step.NeedPass(email.trim().lowercase())
            null
        } catch (e: Exception) {
            Log.w(TAG, "création refusée", e)
            _step.value = Step.Failed(humanReadable(e))
            null
        }
    }

    private suspend fun activate(account: MultipassAccount, birth: BirthData) {
        if (!birth.complete) {
            _step.value = Step.Failed("fiche d'incarnation incomplète", recoverable = true)
            return
        }
        _step.value = Step.Activating
        try {
            val activation = service.activateAtom4Love(
                email = account.email,
                primaryNsec = account.nsec,
                birthDatetime = birthDatetime(birth),
                birthLat = coord(birth.lat),
                birthLon = coord(birth.lon),
                birthWeight = String.format(Locale.US, "%.1f", birth.weightKg!!),
                polarity = birth.wave!!.sex.toString(),
                birthPlace = birth.placeName.ifBlank { null },
                // conception : rien n'est envoyé. La station applique sa propre
                // convention (naissance − 280 jours) pour le PEPPER, et notre
                // gestation calculée depuis le poids n'entre nulle part dans la
                // clé — lui envoyer notre date ne ferait que semer un désaccord.
            )
            val updated = store.saveLove(activation)
            _step.value = if (updated != null) {
                Step.Done(updated)
            } else {
                Step.Failed("compte introuvable au moment d'enregistrer la clé", recoverable = true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "activation ATOM4LOVE refusée", e)
            _step.value = Step.Failed(humanReadable(e), recoverable = true)
        }
    }

    /** « AAAA-MM-JJTHH:MM » — l'heure d'horloge du lieu de naissance. */
    private fun birthDatetime(b: BirthData): String = String.format(
        Locale.US, "%04d-%02d-%02dT%02d:%02d",
        b.year!!, b.month!!, b.day!!, b.hour!!, b.minute!!,
    )

    /** Coordonnée en notation US ; « 0.00 » quand elle manque. */
    private fun coord(value: Double?): String =
        if (value == null) "0.00" else String.format(Locale.US, "%.6f", value)

    private fun humanReadable(e: Exception): String = when (e) {
        is MultipassError.IdentityConflict ->
            "ces données sont déjà rattachées à un autre compte"
        is MultipassError.PassUnavailable ->
            "cette station ne détient pas le code PASS de ce compte"
        is MultipassError.PrimaryAccountNotFound ->
            "aucun MULTIPASS derrière cette adresse"
        is MultipassError -> e.message ?: "la station a refusé la demande"
        else -> "station injoignable"
    }
}
