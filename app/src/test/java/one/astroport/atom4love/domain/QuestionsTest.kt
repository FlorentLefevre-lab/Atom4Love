package one.astroport.atom4love.domain

import one.astroport.atom4love.chat.wire.ChatFrame
import one.astroport.atom4love.chat.wire.ChatFrames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les règles du jeu des questions, et la trame qui les porte.
 *
 * Ce qui est épinglé ici n'est pas du calcul, ce sont des **engagements pris
 * envers quelqu'un** : qu'un identifiant d'attribut ne bouge jamais, qu'une
 * valeur donnée ne se reprenne pas, qu'un refus ferme la question, et qu'une
 * valeur venue du réseau soit vérifiée avant d'être crue.
 */
class QuestionsTest {

    private val fiche = BirthData(
        year = 1982, month = 9, day = 18, hour = 11, minute = 59,
        placeName = "Saint-Quentin", lat = 49.5626957, lon = 3.6366783,
        wave = Wave.entries.first(), weightKg = 3.2f,
    )

    // ── Le catalogue ──────────────────────────────────────────────────────

    /**
     * Les identifiants voyagent : les changer casserait silencieusement les
     * échanges avec un appareil resté à une version antérieure, qui lirait
     * « tonalité » là où on aurait écrit « KIN ».
     */
    @Test
    fun `les identifiants de trait sont gelés`() {
        assertEquals(1, Questions.Trait.Tone.id)
        assertEquals(2, Questions.Trait.Color.id)
        assertEquals(3, Questions.Trait.Decade.id)
        assertEquals(4, Questions.Trait.BirthHour.id)
        assertEquals(5, Questions.Trait.Kin.id)
        assertEquals(
            "deux traits partagent un identifiant",
            Questions.Trait.entries.size,
            Questions.Trait.entries.map { it.id }.toSet().size,
        )
    }

    /**
     * La fiche répond à tout **sauf à l'onde biologique**, et c'est voulu :
     * celle-ci se calcule sur le corps d'aujourd'hui, qui n'est pas dans une
     * fiche de naissance. C'est la cabine qui la joint, depuis ce qu'on lui a
     * lié séparément.
     */
    @Test
    fun `une fiche complète répond à tout sauf au corps, et dans les bornes`() {
        Questions.Trait.entries.filter { it != Questions.Trait.Bio }.forEach { trait ->
            val value = trait.read(fiche)
            assertNotNull("$trait ne répond rien", value)
            assertTrue("$trait rend $value, hors de ses propres bornes", trait.accepts(value!!))
        }
    }

    @Test
    fun `sans heure, l'heure ne se propose pas — le reste tient`() {
        val sansHeure = fiche.copy(hour = null, minute = null)
        assertNull(Questions.Trait.BirthHour.read(sansHeure))
        assertNotNull(Questions.Trait.Tone.read(sansHeure))
        assertNotNull(Questions.Trait.Kin.read(sansHeure))
    }

    @Test
    fun `sans date, il n'y a rien à demander du KIN`() {
        val vide = BirthData.Empty
        assertNull(Questions.Trait.Tone.read(vide))
        assertNull(Questions.Trait.Color.read(vide))
        assertNull(Questions.Trait.Kin.read(vide))
    }

    /** Une valeur reçue du réseau n'est jamais crue sur parole. */
    @Test
    fun `les valeurs hors bornes sont refusées`() {
        assertFalse(Questions.Trait.Tone.accepts(0))
        assertFalse(Questions.Trait.Tone.accepts(14))
        assertFalse(Questions.Trait.Color.accepts(5))
        assertFalse(Questions.Trait.Kin.accepts(0))
        assertFalse(Questions.Trait.Kin.accepts(261))
        assertFalse(Questions.Trait.BirthHour.accepts(24))
        // une décennie est une dizaine ronde, pas une année quelconque
        assertFalse(Questions.Trait.Decade.accepts(1982))
        assertTrue(Questions.Trait.Decade.accepts(1980))
    }

    // ── Ce qu'on peut proposer ────────────────────────────────────────────

    @Test
    fun `une question déjà jouée ne se repropose pas`() {
        val all = Questions.Trait.entries.toSet()
        val history = listOf(Questions.Exchange(Questions.Trait.Tone, mine = 5, theirs = 9))
        assertFalse(Questions.Trait.Tone in Questions.offerable(all, history))
        assertEquals(all.size - 1, Questions.offerable(all, history).size)
    }

    /**
     * **Un refus ferme la question.** Pouvoir reproposer transformerait le
     * refus en délai, et l'insistance en mécanique de jeu.
     */
    @Test
    fun `une question refusée ne se repropose pas non plus`() {
        val all = Questions.Trait.entries.toSet()
        val history = listOf(Questions.Exchange(Questions.Trait.Kin, mine = 83, declined = true))
        assertFalse(Questions.Trait.Kin in Questions.offerable(all, history))
    }

    @Test
    fun `on ne propose que ce que la cabine sait répondre`() {
        val partiel = setOf(Questions.Trait.Tone, Questions.Trait.Color)
        assertEquals(partiel.toList(), Questions.offerable(partiel, emptyList()))
    }

    // ── L'état d'un échange ───────────────────────────────────────────────

    @Test
    fun `l'état se lit dans les deux valeurs, sans automate`() {
        val neuf = Questions.Exchange(Questions.Trait.Tone)
        assertFalse(neuf.settled); assertFalse(neuf.owed); assertFalse(neuf.pending)

        val donne = neuf.copy(mine = 5)
        assertTrue("on attend sa réponse", donne.pending)
        assertFalse(donne.owed)

        val recu = neuf.copy(theirs = 9)
        assertTrue("on nous demande", recu.owed)

        val joue = neuf.copy(mine = 5, theirs = 9)
        assertTrue(joue.settled); assertFalse(joue.owed); assertFalse(joue.pending)

        // refusé : on a donné pour rien, et on n'attend plus
        val refuse = neuf.copy(mine = 5, declined = true)
        assertFalse("un refus n'est pas une attente", refuse.pending)
    }

    // ── La trame ──────────────────────────────────────────────────────────

    @Test
    fun `la trame fait cinq octets et revient à l'identique`() {
        val bytes = ChatFrames.encodeQuestion(ChatFrames.QUESTION_OFFER, 5, 260)
        assertEquals(5, bytes.size)
        val frame = ChatFrames.decode(bytes) as ChatFrame.Question
        assertEquals(ChatFrames.QUESTION_OFFER, frame.step)
        assertEquals(5, frame.traitId)
        assertEquals(260, frame.value)
    }

    /**
     * **Un refus ne doit pas emporter la valeur qu'il refuse de donner.** Une
     * trame qui la porterait quand même la livrerait à un client modifié — le
     * refus deviendrait une réponse à retardement.
     */
    @Test
    fun `un refus part à zéro, quelle que soit la valeur passée`() {
        val bytes = ChatFrames.encodeQuestion(ChatFrames.QUESTION_DECLINE, 5, 83)
        val frame = ChatFrames.decode(bytes) as ChatFrame.Question
        assertEquals(ChatFrames.QUESTION_DECLINE, frame.step)
        assertEquals(0, frame.value)
    }

    @Test
    fun `une étape inconnue ne se décode pas`() {
        assertNull(ChatFrames.decode(byteArrayOf(0x0B, 0, 1, 0, 5)))
        assertNull(ChatFrames.decode(byteArrayOf(0x0B, 9, 1, 0, 5)))
    }

    /** 260 dépasse un octet signé : le KIN se lit non signé, ou il devient négatif. */
    @Test
    fun `les grandes valeurs passent sans changer de signe`() {
        listOf(1, 127, 128, 255, 260, 2100).forEach { value ->
            val frame = ChatFrames.decode(
                ChatFrames.encodeQuestion(ChatFrames.QUESTION_ANSWER, 3, value),
            ) as ChatFrame.Question
            assertEquals(value, frame.value)
        }
    }

    @Test
    fun `la trame de question ne se confond pas avec celle de l'onde`() {
        val question = ChatFrames.encodeQuestion(ChatFrames.QUESTION_OFFER, 1, 5)
        assertTrue(ChatFrames.decode(question) is ChatFrame.Question)
        assertTrue(ChatFrames.decode(ChatFrames.encodeResonance(202.18f)) is ChatFrame.Resonance)
    }

    /** Un trait qu'on ne connaît pas — version plus récente d'en face. */
    @Test
    fun `un identifiant de trait inconnu ne devient pas un trait`() {
        assertNull(Questions.Trait.of(0))
        assertNull(Questions.Trait.of(99))
        assertEquals(Questions.Trait.Kin, Questions.Trait.of(5))
    }

    // ── L'onde biologique ─────────────────────────────────────────────────

    /**
     * **Le cœur de la bascule du 15/08.** ω_bio partait toute seule à chaque
     * pair attesté ; elle se demande maintenant. Le piège tenait au fait que
     * deux chemins peuvent encore l'apporter — la question, et la vieille trame
     * de résonance d'un appareil resté en arrière. S'ils ne tombaient pas sur
     * le même entier, la même personne s'afficherait avec deux ondes selon
     * l'ordre d'arrivée, et le battement changerait de note.
     */
    @Test
    fun `les deux chemins d'une onde donnent le même entier`() {
        listOf(202.18f, 1.04f, 429.62f, 300.0f, 12.15f).forEach { hz ->
            val parLaQuestion = Questions.encodeBio(hz)
            // ce que ferait la vieille trame : un float qu'on ramène au dixième
            val parLaResonance = Questions.encodeBio(
                ChatFrames.decode(ChatFrames.encodeResonance(hz))
                    .let { (it as ChatFrame.Resonance).omegaBio },
            )
            assertEquals("$hz", parLaQuestion, parLaResonance)
        }
    }

    @Test
    fun `l'onde tient dans les deux octets du jeu, et revient en hertz`() {
        val value = Questions.encodeBio(202.18f)
        assertEquals(2022, value)
        assertTrue(Questions.Trait.Bio.accepts(value!!))
        assertEquals(202.2f, Questions.decodeBio(value), 0.001f)
    }

    @Test
    fun `une onde impossible ne se propose pas`() {
        assertNull(Questions.encodeBio(0f))
        assertNull(Questions.encodeBio(-1f))
        assertNull(Questions.encodeBio(Float.NaN))
        assertNull(Questions.encodeBio(Float.POSITIVE_INFINITY))
        // au-delà de 6553,5 Hz on déborde les deux octets : rien ne part
        assertNull(Questions.encodeBio(7000f))
    }

    /** L'onde vient du corps d'aujourd'hui : la fiche de naissance l'ignore. */
    @Test
    fun `l'onde ne se lit pas dans la fiche`() {
        assertNull(Questions.Trait.Bio.read(fiche))
    }

    /** Elle est la dernière du catalogue — c'est la seule qui parle du corps. */
    @Test
    fun `l'onde est proposée en dernier`() {
        assertEquals(Questions.Trait.Bio, Questions.Trait.entries.last())
        assertEquals(6, Questions.Trait.Bio.id)
    }
}
