package one.astroport.atom4love.nostr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Filtre de souscription NIP-01 — le sous-ensemble dont on a besoin.
 * Les champs null sont omis du JSON (`explicitNulls = false`).
 */
@Serializable
data class NostrFilter(
    val ids: List<String>? = null,
    val authors: List<String>? = null,
    val kinds: List<Int>? = null,
    /** Tag `h` (NIP-01 `#h`) : la cellule H3 du salon de cabine. */
    @SerialName("#h") val hexagons: List<String>? = null,
    val since: Long? = null,
    val until: Long? = null,
    val limit: Int? = null,
)

/** Messages du protocole relais NIP-01, dans les deux sens. */
object RelayMessage {

    private val json = NostrEvent.json

    // ── Client → relais ───────────────────────────────────────────────────

    fun publish(event: NostrEvent): String = json.encodeToString(
        buildJsonArray {
            add("EVENT")
            add(json.encodeToJsonElement(event))
        },
    )

    fun subscribe(subscriptionId: String, filter: NostrFilter): String = json.encodeToString(
        buildJsonArray {
            add("REQ")
            add(subscriptionId)
            add(json.encodeToJsonElement(filter))
        },
    )

    fun unsubscribe(subscriptionId: String): String = json.encodeToString(
        buildJsonArray {
            add("CLOSE")
            add(subscriptionId)
        },
    )

    // ── Relais → client ───────────────────────────────────────────────────

    sealed interface Inbound
    data class Event(val subscriptionId: String, val event: NostrEvent) : Inbound
    data class Eose(val subscriptionId: String) : Inbound
    data class Ok(val eventId: String, val accepted: Boolean, val message: String) : Inbound
    data class Notice(val message: String) : Inbound
    data class Closed(val subscriptionId: String, val message: String) : Inbound
    /** Type inconnu ou JSON invalide : conservé brut pour le diagnostic. */
    data class Unknown(val raw: String) : Inbound

    fun parse(text: String): Inbound = runCatching {
        val arr: JsonArray = json.parseToJsonElement(text).jsonArray
        when (arr[0].jsonPrimitive.content) {
            "EVENT" -> Event(
                subscriptionId = arr[1].jsonPrimitive.content,
                event = json.decodeFromJsonElement(NostrEvent.serializer(), arr[2]),
            )
            "EOSE" -> Eose(arr[1].jsonPrimitive.content)
            "OK" -> Ok(
                eventId = arr[1].jsonPrimitive.content,
                accepted = arr[2].jsonPrimitive.boolean,
                message = arr.getOrNull(3)?.jsonPrimitive?.content ?: "",
            )
            "NOTICE" -> Notice(arr[1].jsonPrimitive.content)
            "CLOSED" -> Closed(
                subscriptionId = arr[1].jsonPrimitive.content,
                message = arr.getOrNull(2)?.jsonPrimitive?.content ?: "",
            )
            else -> Unknown(text)
        }
    }.getOrElse { Unknown(text) }

    private fun JsonArray.getOrNull(i: Int): JsonPrimitive? =
        if (i < size) this[i].jsonPrimitive else null
}
