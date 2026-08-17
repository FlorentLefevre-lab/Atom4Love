package one.astroport.atom4love.nostr

import one.astroport.atom4love.domain.A4lAddress
import one.astroport.atom4love.domain.KinMaya
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les trois fautes qu'une bienvenue peut commettre, et la quatrième qui la
 * ferait grossir sans fin.
 *
 * Rien ici ne touche au réseau : c'est exactement le but de [Welcome], et c'est
 * la seule partie de la veille qu'on puisse éprouver sans relais ni appareil.
 */
class WelcomeTest {

    private val now = 1_800_000_000_000L
    private val jour = 24 * 60 * 60 * 1000L

    private fun atom(key: String, agedMs: Long) = Constellation.Atom(
        pubkey = key.padEnd(64, '0'),
        place = A4lAddress.Place(latDeg = 48.85, lonDeg = 2.35, pentagonId = 0, q = 0, r = 0),
        phase = 1.0,
        kin = KinMaya.ofNumber(119),
        createdAt = (now - agedMs) / 1000,
    )

    @Test
    fun `un arrivant frais est fete`() {
        val due = Welcome.toCelebrate(listOf(atom("aa", 0)), emptySet(), null, now)
        assertEquals(1, due.size)
    }

    @Test
    fun `la premiere lecture ne fete pas toute la constellation`() {
        // Le relais rejoue son stock à chaque souscription. Sans le filtre
        // « récent », ouvrir l'application posterait deux mille notifications.
        val vieux = (1..50).map { atom("c$it", 30 * jour) }
        val frais = atom("neuf", 2 * jour)
        val due = Welcome.toCelebrate(vieux + frais, emptySet(), null, now)
        assertEquals(1, due.size)
        assertTrue(due.single().pubkey.startsWith("neuf"))
    }

    @Test
    fun `on ne fete jamais deux fois la meme cle`() {
        val a = atom("aa", jour)
        val memory = Welcome.remember(emptySet(), listOf(a), now)
        val due = Welcome.toCelebrate(listOf(a), Welcome.keysOf(memory), null, now)
        assertTrue("refêté : $due", due.isEmpty())
    }

    @Test
    fun `une reactivation refait une arrivee, mais une seule fois`() {
        // Le 30078 est remplaçable : republier change `createdAt`, donc la clé
        // repasse « récente ». C'est assumé — mais elle ne doit pas se refêter
        // à chaque rejeu du relais pour autant.
        val premier = atom("aa", 3 * jour)
        var memory = Welcome.remember(emptySet(), listOf(premier), now)

        val republie = premier.copy(createdAt = now / 1000)
        val due = Welcome.toCelebrate(listOf(republie), Welcome.keysOf(memory), null, now)
        assertTrue("une réactivation ne repasse pas", due.isEmpty())

        memory = Welcome.remember(memory, due, now)
        assertEquals(
            "la clé ne doit rester qu'une fois en mémoire",
            1,
            Welcome.keysOf(memory).size,
        )
    }

    @Test
    fun `on ne se souhaite pas la bienvenue a soi-meme`() {
        // Le jour où l'on active sa propre clé est justement celui où l'on
        // ouvre l'application pour regarder.
        val moi = atom("moi", 0)
        val due = Welcome.toCelebrate(listOf(moi), emptySet(), moi.pubkey, now)
        assertTrue(due.isEmpty())
    }

    @Test
    fun `les arrivees sortent dans l'ordre ou elles se sont produites`() {
        val due = Welcome.toCelebrate(
            listOf(atom("c", 1 * jour), atom("a", 5 * jour), atom("b", 3 * jour)),
            emptySet(),
            null,
            now,
        )
        assertEquals(listOf("a", "b", "c"), due.map { it.pubkey.first().toString() })
    }

    @Test
    fun `la memoire se purge au-dela de la fenetre`() {
        val ancien = atom("vieux", 10 * jour)
        val recent = atom("neuf", jour)
        val memory = Welcome.remember(emptySet(), listOf(ancien, recent), now)
        // `remember` garde ce qu'on vient de fêter, y compris hors fenêtre —
        // c'est au tour SUIVANT que la purge doit s'appliquer.
        assertEquals(2, memory.size)

        val purgee = Welcome.remember(memory, emptyList(), now)
        assertEquals(setOf("neuf".padEnd(64, '0')), Welcome.keysOf(purgee))
    }

    @Test
    fun `une entree malformee est jetee plutot que retenue pour toujours`() {
        val purgee = Welcome.remember(setOf("sans-date", "aussi:pasunnombre"), emptyList(), now)
        assertTrue("entrées illisibles conservées : $purgee", purgee.isEmpty())
    }

    @Test
    fun `la memoire ne grossit pas avec le temps`() {
        // Mille arrivées étalées sur trois mois, une mémoire relue à chaque
        // fois : elle doit se stabiliser sur la fenêtre, pas sur l'historique.
        var memory = emptySet<String>()
        var instant = now
        repeat(1000) { i ->
            instant += 2 * 60 * 60 * 1000L
            val due = Welcome.toCelebrate(
                listOf(atom("k$i", 0).copy(createdAt = instant / 1000)),
                Welcome.keysOf(memory),
                null,
                instant,
            )
            memory = Welcome.remember(memory, due, instant)
        }
        // 7 jours de fenêtre, une arrivée toutes les 2 h → 84 au plus.
        assertTrue("mémoire non bornée : ${memory.size}", memory.size <= 90)
    }
}
