package one.astroport.atom4love.journal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Ce que le journal écrit, et surtout **ce qu'il n'écrit pas deux fois**.
 *
 * Le défaut que ces tests épinglent n'est pas théorique : la mémoire des
 * transitions vivait dans la composition, et une rotation d'écran réinscrivait
 * toutes les cartes à portée comme si elles venaient d'arriver. Trois voisins
 * dans un bar suffisaient à noyer la fenêtre à chaque fois qu'on tournait le
 * téléphone.
 */
class JournalTest {

    @Before
    fun clean() = Journal.clear()

    @Test
    fun `la balise ne s'inscrit que sur un vrai changement`() {
        Journal.noteBeacon(true)
        Journal.noteBeacon(true)
        Journal.noteBeacon(true)
        assertEquals(1, Journal.entries.value.size)
        Journal.noteBeacon(false)
        assertEquals(2, Journal.entries.value.size)
    }

    @Test
    fun `une carte qui reste ne s'inscrit qu'une fois, et son départ une fois`() {
        val alice = mapOf("jeton-1" to Journal.Card(glyph = 4, percent = 92))
        Journal.noteCards(alice)
        Journal.noteCards(alice)
        assertEquals(1, Journal.entries.value.size)

        Journal.noteCards(emptyMap())
        assertEquals(2, Journal.entries.value.size)
        val gone = Journal.entries.value.first()
        assertTrue(gone is Journal.Entry.CardGone)
        // ⚠ Le sceau de celle qui part vient de la mémoire, pas de la liste
        // courante — elle n'y est justement plus.
        assertEquals(4, (gone as Journal.Entry.CardGone).glyph)
    }

    /**
     * ⚠ Deux cartes qui paraissent dans le même balayage sont inscrites à la
     * même milliseconde, avec le même sceau et le même pourcentage si elles se
     * ressemblent. La liste paresseuse de l'écran veut une clé unique : sans le
     * numéro d'ordre, Compose lève exactement quand la salle se remplit.
     */
    @Test
    fun `deux lignes identiques gardent des clés distinctes`() {
        Journal.noteCards(
            mapOf(
                "jeton-1" to Journal.Card(glyph = 4, percent = 92),
                "jeton-2" to Journal.Card(glyph = 4, percent = 92),
            ),
        )
        val seqs = Journal.entries.value.map { it.seq }
        assertEquals(2, seqs.size)
        assertEquals(2, seqs.toSet().size)
    }

    @Test
    fun `une rencontre mutuelle ne s'inscrit qu'une fois`() {
        Journal.noteMeetings(setOf(77)) { 4 }
        Journal.noteMeetings(setOf(77)) { 4 }
        assertEquals(1, Journal.entries.value.size)
        assertTrue(Journal.entries.value.single() is Journal.Entry.Meeting)
    }

    @Test
    fun `le plus récent est en tête`() {
        Journal.noteBeacon(true)
        Journal.noteRelay(true)
        assertTrue(Journal.entries.value.first() is Journal.Entry.Relay)
    }

    @Test
    fun `le journal est borné`() {
        repeat(Journal.CAPACITY + 50) { i ->
            Journal.record(Journal.Entry.CardSeen(glyph = i, percent = null))
        }
        assertEquals(Journal.CAPACITY, Journal.entries.value.size)
    }

    /**
     * Dissoudre le noyau efface le journal **et sa mémoire** : sans le second,
     * la balise resterait « déjà allumée » pour un noyau tout neuf, et sa
     * première ligne manquerait.
     */
    @Test
    fun `l'effacement remet la mémoire à zéro, pas seulement les lignes`() {
        Journal.noteBeacon(true)
        Journal.clear()
        Journal.noteBeacon(true)
        assertEquals(1, Journal.entries.value.size)
    }
}
