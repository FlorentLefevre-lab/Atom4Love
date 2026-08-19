package one.astroport.atom4love.journal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    /**
     * ⚠ Vu à l'écran le 19/08 : au lancement, le journal s'ouvrait sur « Balise
     * éteinte », « Relais perdu », « Présence annoncée sans position », puis les
     * trois lignes inverses deux secondes plus tard. Six lignes pour dire que
     * l'application démarre — et les trois premières n'étaient pas des
     * évènements, c'étaient les valeurs par défaut des flux.
     */
    @Test
    fun `la première observation ensemence sans écrire`() {
        Journal.noteBeacon(false)
        Journal.noteRelay(false)
        assertTrue(Journal.entries.value.isEmpty())
    }

    @Test
    fun `la balise ne s'inscrit que sur un vrai changement`() {
        Journal.noteBeacon(false) // l'état de départ : muet
        Journal.noteBeacon(true)
        Journal.noteBeacon(true)
        Journal.noteBeacon(true)
        assertEquals(1, Journal.entries.value.size)
        Journal.noteBeacon(false)
        assertEquals(2, Journal.entries.value.size)
    }

    /**
     * L'exception à l'ensemencement muet, et la seule : une cellule **connue**
     * dès la première observation est une information — voilà l'hexagone où
     * vous êtes —, là où son absence n'est que l'état de départ de tout le monde.
     */
    @Test
    fun `une cellule connue d'emblée s'inscrit, son absence non`() {
        Journal.noteCell(null)
        assertTrue(Journal.entries.value.isEmpty())

        Journal.clear()
        Journal.noteCell(0x881fb5b861fffffL)
        assertEquals(1, Journal.entries.value.size)
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
        Journal.noteBeacon(false)
        Journal.noteRelay(false)
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
        Journal.noteBeacon(false)
        Journal.noteBeacon(true)
        assertEquals(1, Journal.entries.value.size)
        Journal.clear()
        // La mémoire est vide : « allumée » redevient un état de départ, donc
        // muet. Sans la remise à zéro, elle serait restée « déjà allumée » et
        // la première ligne du noyau suivant aurait manqué.
        Journal.noteBeacon(true)
        assertTrue(Journal.entries.value.isEmpty())
        Journal.noteBeacon(false)
        assertEquals(1, Journal.entries.value.size)
    }

    /**
     * ⚠ **Le nom arrive TOUJOURS après l'attestation** — il voyage dans une
     * trame scellée, donc après le handshake qui fait apparaître le pair. Sans
     * cette reprise, le journal aurait dit « sans pseudo rejoint la radio »
     * pour tout le monde, à chaque fois, pendant que la liste des conversations
     * affichait le bon nom deux écrans plus loin. Vu à l'écran le 19/08.
     */
    @Test
    fun `la ligne d'arrivée se complète quand le nom arrive`() {
        Journal.notePeers(mapOf("aa" to null))
        assertEquals(1, Journal.entries.value.size)
        assertNull((Journal.entries.value.single() as Journal.Entry.Peer).name)

        Journal.notePeers(mapOf("aa" to "Flow_tab"))
        // Une seule ligne, toujours : on complète, on n'en ajoute pas une.
        assertEquals(1, Journal.entries.value.size)
        val amended = Journal.entries.value.single() as Journal.Entry.Peer
        assertEquals("Flow_tab", amended.name)
        assertTrue(amended.joined)
    }

    @Test
    fun `changer de nom en cours de présence ne réécrit pas l'arrivée deux fois`() {
        Journal.notePeers(mapOf("aa" to "Flow_tab"))
        Journal.notePeers(mapOf("aa" to "Flower"))
        assertEquals(1, Journal.entries.value.size)
        assertEquals("Flower", (Journal.entries.value.single() as Journal.Entry.Peer).name)
    }

    /** Le départ garde le dernier nom connu, et ferme la reprise. */
    @Test
    fun `le départ porte le nom, et rien ne se retouche après`() {
        Journal.notePeers(mapOf("aa" to "Flow_tab"))
        Journal.notePeers(emptyMap())
        assertEquals(2, Journal.entries.value.size)
        val left = Journal.entries.value.first() as Journal.Entry.Peer
        assertEquals("Flow_tab", left.name)
        assertFalse(left.joined)
    }
}
