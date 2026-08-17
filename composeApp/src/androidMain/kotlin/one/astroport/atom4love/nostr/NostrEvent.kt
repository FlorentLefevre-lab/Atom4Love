package one.astroport.atom4love.nostr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonArray

/**
 * Événement NIP-01. L'id est le SHA-256 de la sérialisation canonique
 * `[0, pubkey, created_at, kind, tags, content]`, la signature un Schnorr
 * BIP-340 de cet id.
 */
@Serializable
data class NostrEvent(
    val id: String,
    val pubkey: String,
    @SerialName("created_at") val createdAt: Long,
    val kind: Int,
    val tags: List<List<String>>,
    val content: String,
    val sig: String,
) {

    /** Recalcule l'id et vérifie qu'il correspond (intégrité, pas signature). */
    fun hasValidId(): Boolean =
        id == Hex.encode(computeId(pubkey, createdAt, kind, tags, content))

    companion object {

        /** Json compact partagé par tout le module (émission et parsing). */
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false // les champs null d'un filtre sont omis (NIP-01)
        }

        /**
         * Construit et signe un événement. [createdAt] en secondes epoch —
         * paramètre explicite pour rester testable et compatible avec la
         * temporalisation D2 le jour venu.
         */
        fun create(
            keys: NostrKeys,
            kind: Int,
            content: String,
            tags: List<List<String>> = emptyList(),
            createdAt: Long = System.currentTimeMillis() / 1000,
        ): NostrEvent {
            val pubkey = keys.publicKeyHex
            val idBytes = computeId(pubkey, createdAt, kind, tags, content)
            return NostrEvent(
                id = Hex.encode(idBytes),
                pubkey = pubkey,
                createdAt = createdAt,
                kind = kind,
                tags = tags,
                content = content,
                sig = Hex.encode(keys.sign(idBytes)),
            )
        }

        /**
         * Sérialisation canonique NIP-01. kotlinx produit le JSON compact
         * attendu (échappement minimal, UTF-8 brut) — verrouillé par test.
         */
        fun canonicalJson(
            pubkey: String,
            createdAt: Long,
            kind: Int,
            tags: List<List<String>>,
            content: String,
        ): String = json.encodeToString(
            buildJsonArray {
                add(JsonPrimitive(0))
                add(pubkey)
                add(createdAt)
                add(kind)
                addJsonArray { tags.forEach { tag -> addJsonArray { tag.forEach { add(it) } } } }
                add(content)
            },
        )

        fun computeId(
            pubkey: String,
            createdAt: Long,
            kind: Int,
            tags: List<List<String>>,
            content: String,
        ): ByteArray = LoveKeyForge.sha256(
            canonicalJson(pubkey, createdAt, kind, tags, content).toByteArray(Charsets.UTF_8),
        )
    }
}
