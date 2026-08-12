package one.astroport.atom4love.multipass

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import one.astroport.atom4love.nostr.Bech32
import one.astroport.atom4love.nostr.NostrEvent
import one.astroport.atom4love.nostr.NostrKeys

/**
 * Le guichet d'une station Astroport.ONE (l'API UPassport, dite « uSPOT »).
 *
 * Port fidèle de `zelkova/lib/g1/multipass_service.dart`, le client de
 * référence de Fred : mêmes appels, mêmes champs, mêmes codes d'erreur. Toute
 * la fabrication d'identité — clés NOSTR, portefeuilles, uDRIVE, et la clé
 * LOVE elle-même — appartient à la station. Atom4Love n'est que la porte.
 *
 * Le parcours se fait en deux temps, et l'ordre n'est pas négociable :
 *
 *  1. [createMultipass] — l'email, la langue, la position. **Aucune donnée de
 *     naissance** : l'identité principale du MULTIPASS est toujours tirée au
 *     hasard côté serveur, jamais dérivée de qui vous êtes.
 *  2. [activateAtom4Love] — le profil de naissance, prouvé par une signature
 *     NIP-42 de la clé du compte. C'est là, et seulement là, que la station
 *     dérive la clé LOVE et la rend au client.
 */
class MultipassService(
    private val baseUrl: String,
    client: OkHttpClient? = null,
) {

    companion object {
        /** La création d'un MULTIPASS mobilise la station : IPFS, mails, QR codes. */
        private const val TIMEOUT_SECONDS = 180L

        /** Event d'authentification NIP-42 : la preuve de possession du compte. */
        private const val KIND_NIP42_AUTH = 22242
    }

    /** L'adresse du guichet — retenue avec le compte : c'est cette station-là
     *  qui détient le MULTIPASS, et elle seule peut en révéler le PASS. */
    val stationUrl: String get() = baseUrl

    private val http = client ?: OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val json = NostrEvent.json

    /**
     * Crée un MULTIPASS, ou récupère celui d'un email déjà connu.
     *
     * - email inconnu → la station forge tout et rend les clés.
     * - email connu, sans [passCode] → [MultipassError.Exists] (le serveur
     *   réclame le code PASS reçu par mail à la création).
     * - email connu, avec le bon [passCode] → le MULTIPASS existant.
     *
     * @param lang code de langue à deux lettres, ou un code PASS à quatre
     *   chiffres (la station s'en sert pour choisir le mode d'accueil).
     * @param lat latitude UMAP de l'utilisateur, telle qu'affichée par la
     *   station (grille 0,01°) — jamais la position de naissance.
     */
    suspend fun createMultipass(
        email: String,
        lang: String,
        lat: String,
        lon: String,
        passCode: String? = null,
    ): MultipassResponse = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("email", email)
            .add("lang", lang)
            .add("lat", lat)
            .add("lon", lon)
            .add("format", "json")
            .apply { if (!passCode.isNullOrEmpty()) add("pass_code", passCode) }
            .build()

        val call = Request.Builder().url("$baseUrl/g1nostr").post(form).build()
        http.newCall(call).execute().use { response ->
            val body = response.body?.string().orEmpty()
            when (response.code) {
                200 -> json.decodeFromString(MultipassResponse.serializer(), body)
                401 -> throw MultipassError.InvalidPass()
                409 -> if (errorCode(body) == "IDENTITY_CONFLICT") {
                    throw MultipassError.IdentityConflict()
                } else {
                    throw MultipassError.Exists()
                }
                503 -> throw MultipassError.PassUnavailable()
                else -> throw MultipassError.Failed(
                    detail(body) ?: "création du MULTIPASS refusée (${response.code})",
                )
            }
        }
    }

    /**
     * Active ATOM4LOVE sur un MULTIPASS existant : la station chiffre et range
     * le profil de naissance, dérive la clé LOVE dédiée, publie l'événement de
     * résonance (kind 30078, `d=atom4love`) et rend la clé au client.
     *
     * Aucun second MULTIPASS n'est créé, et [primaryNsec] ne quitte jamais
     * l'appareil : la possession du compte se prouve en signant un challenge à
     * usage unique (NIP-42), comme le fait Zelkova.
     *
     * @param birthDatetime « AAAA-MM-JJTHH:MM », heure d'horloge du lieu de
     *   naissance — la station la traite en heure solaire locale.
     * @param polarity « 0 » (onde Φ) ou « 1 » (onde Octave).
     */
    suspend fun activateAtom4Love(
        email: String,
        primaryNsec: String,
        birthDatetime: String,
        birthLat: String,
        birthLon: String,
        birthWeight: String,
        polarity: String,
        birthPlace: String? = null,
        conceptionDatetime: String? = null,
        conceptionPlace: String? = null,
    ): Atom4LoveActivation = withContext(Dispatchers.IO) {
        val challenge = fetchChallenge(email)

        // La signature se fait ici, avec la clé du compte : le serveur ne
        // reçoit que l'événement signé, jamais le secret qui l'a produit.
        val keys = NostrKeys(nsecToBytes(primaryNsec))
        val authEvent = NostrEvent.create(
            keys = keys,
            kind = KIND_NIP42_AUTH,
            content = "",
            tags = listOf(listOf("challenge", challenge)),
        )

        val form = FormBody.Builder()
            .add("email", email)
            .add("birth_datetime", birthDatetime)
            .add("birth_lat", birthLat)
            .add("birth_lon", birthLon)
            .add("birth_weight", birthWeight)
            .add("polarity", polarity)
            .add("auth_event", json.encodeToString(NostrEvent.serializer(), authEvent))
            .apply {
                if (!birthPlace.isNullOrEmpty()) add("birth_place", birthPlace)
                if (!conceptionDatetime.isNullOrEmpty()) {
                    add("conception_datetime", conceptionDatetime)
                }
                if (!conceptionPlace.isNullOrEmpty()) add("conception_place", conceptionPlace)
            }
            .build()

        val call = Request.Builder().url("$baseUrl/atom4love/activate").post(form).build()
        http.newCall(call).execute().use { response ->
            val body = response.body?.string().orEmpty()
            when (response.code) {
                200 -> json.decodeFromString(Atom4LoveActivation.serializer(), body)
                404 -> throw MultipassError.PrimaryAccountNotFound()
                else -> throw MultipassError.ActivationFailed(
                    detail(body) ?: "HTTP ${response.code}",
                )
            }
        }
    }

    /** Challenge NIP-42 à usage unique, scopé à la clé principale de [email]. */
    private fun fetchChallenge(email: String): String {
        val url = "$baseUrl/atom4love/challenge".toHttpUrl().newBuilder()
            .addQueryParameter("email", email)
            .build()
        http.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            if (response.code == 404) throw MultipassError.PrimaryAccountNotFound()
            val body = response.body?.string().orEmpty()
            if (response.code != 200) {
                throw MultipassError.ActivationFailed("challenge indisponible (${response.code})")
            }
            return json.parseToJsonElement(body).jsonObject["challenge"]?.jsonPrimitive?.content
                ?: throw MultipassError.ActivationFailed("challenge absent de la réponse")
        }
    }

    /**
     * Relit la clé publique LOVE (`HEX_LOVE`) d'un compte déjà activé.
     *
     * null si ATOM4LOVE n'est pas encore activé, ou en cas d'erreur : c'est le
     * signal qu'il faut passer par [activateAtom4Love]. Même point d'entrée que
     * `atomic_chat.html` côté web — les deux clients adressent donc exactement
     * le même destinataire sur le canal LOVE.
     */
    suspend fun fetchLoveHex(email: String): String? = withContext(Dispatchers.IO) {
        val url = "$baseUrl/atom4love/dream".toHttpUrl().newBuilder()
            .addQueryParameter("email", email)
            .build()
        runCatching {
            http.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (response.code != 200) return@use null
                val root = json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
                val hex = root["love_hex"]?.jsonPrimitive?.content
                    ?: root["dream_vector"]?.jsonObject?.get("love_hex")?.jsonPrimitive?.content
                hex?.takeIf { it.length == 64 }
            }
        }.getOrNull()
    }

    /** `nsec1…` → les 32 octets de la clé privée. */
    private fun nsecToBytes(nsec: String): ByteArray {
        val (hrp, bytes) = Bech32.decode(nsec)
        require(hrp == "nsec") { "clé privée attendue au format nsec1…" }
        return bytes
    }

    /** Le champ `error` d'une réponse d'erreur JSON, s'il y en a un. */
    private fun errorCode(body: String): String? = runCatching {
        json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content
    }.getOrNull()

    /** Le message le plus parlant d'une réponse d'erreur (FastAPI dit `detail`). */
    private fun detail(body: String): String? = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        (root["message"] ?: root["error"] ?: root["detail"])?.jsonPrimitive?.content
    }.getOrNull()
}

/** Les urls de contribution ouverte que la station propose après création. */
@Serializable
data class OcUrls(
    val satellite: String = "",
    val constellation: String = "",
    val cloud: String = "",
    val membre: String = "",
)

/**
 * Ce que la station rend une fois le MULTIPASS créé (`.multipass.json`).
 *
 * [pass] est le code court reçu par mail : il récupère le compte depuis
 * n'importe quel terminal UPlanet, et authentifie les appels ATOM4LOVE quand la
 * signature NIP-42 n'est pas disponible. [ssssPlayer] est une part du secret
 * partagé (2 sur 3) — celle du porteur.
 */
@Serializable
data class MultipassResponse(
    val email: String = "",
    val salt: String = "",
    val pepper: String = "",
    val nsec: String = "",
    val pass: String = "",
    val g1pub: String = "",
    val npub: String = "",
    val hex: String = "",
    val nostrns: String = "",
    val lat: String = "",
    val lon: String = "",
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("ssss", "ssss_player")
    val ssss: String = "",
    @SerialName("is_origin") val isOrigin: Boolean = false,
    @SerialName("oc_urls") val ocUrls: OcUrls = OcUrls(),
    @SerialName("uplanet_home") val uplanetHome: String = "",
    @SerialName("uplanetname_g1") val uplanetnameG1: String = "",
)

/**
 * Ce que rend l'activation ATOM4LOVE : la clé LOVE dérivée par la station des
 * données de naissance, distincte de l'identité principale du compte. Elle ne
 * sert qu'au canal LOVE et au profil de résonance — jamais aux paiements ẐEN.
 */
@Serializable
data class Atom4LoveActivation(
    val email: String = "",
    @SerialName("love_nsec") val loveNsec: String = "",
    @SerialName("love_npub") val loveNpub: String = "",
    @SerialName("love_hex") val loveHex: String = "",
    @SerialName("kin_num") val kinNum: Int = 0,
    @SerialName("personal_phase") val personalPhase: Double = 0.0,
)

/** Les refus de la station, tels que Zelkova les distingue. */
sealed class MultipassError(message: String) : Exception(message) {

    /** L'email est déjà enregistré : le code PASS est requis (409). */
    class Exists : MultipassError("MULTIPASS_EXISTS")

    /** Code PASS incorrect (401). */
    class InvalidPass : MultipassError("INVALID_PASS")

    /** Le fichier `.pass` n'est pas sur ce nœud — compte créé ailleurs (503). */
    class PassUnavailable : MultipassError("PASS_UNAVAILABLE")

    /** Ces clés appartiennent déjà à une autre identité (409). */
    class IdentityConflict : MultipassError("IDENTITY_CONFLICT")

    /** Activation demandée sans MULTIPASS derrière l'email (404). */
    class PrimaryAccountNotFound : MultipassError("PRIMARY_ACCOUNT_NOT_FOUND")

    /** L'activation ATOM4LOVE a échoué côté station. */
    class ActivationFailed(detail: String) : MultipassError("ACTIVATION_FAILED: $detail")

    /** Tout autre refus. */
    class Failed(detail: String) : MultipassError(detail)
}
