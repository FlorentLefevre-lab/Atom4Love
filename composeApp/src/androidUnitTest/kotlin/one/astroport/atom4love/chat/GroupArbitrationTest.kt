package one.astroport.atom4love.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La règle vaut par une seule propriété : **les deux côtés doivent tomber sur
 * des réponses opposées sans se parler**. Un test qui vérifierait un seul côté
 * ne dirait rien — c'est l'accord qui compte, et c'est lui qu'on éprouve ici.
 */
class GroupArbitrationTest {

    private val alice = "aa11bb22"
    private val bob = "ff99ee88"

    /** Exactement un des deux cède. Ni zéro (deux groupes), ni deux (aucun). */
    private fun exactlyOneYields(a: String?, b: String?): Boolean =
        GroupArbitration.shouldYield(a, b) != GroupArbitration.shouldYield(b, a)

    @Test
    fun `des deux cotes, un seul cede`() {
        assertTrue(exactlyOneYields(alice, bob))
        assertTrue(exactlyOneYields(bob, alice))
    }

    @Test
    fun `le plus petit npub garde son groupe`() {
        // alice < bob : c'est bob qui cède
        assertFalse(GroupArbitration.shouldYield(mine = alice, peer = bob))
        assertTrue(GroupArbitration.shouldYield(mine = bob, peer = alice))
    }

    /**
     * Le même noyau des deux côtés, ce ne sont pas deux appareils en collision.
     * Si les deux cédaient, chacun fermerait son groupe et il n'en resterait
     * aucun — la boucle exacte que la règle doit interdire.
     */
    @Test
    fun `a clefs egales personne ne cede`() {
        assertFalse(GroupArbitration.shouldYield(alice, alice))
    }

    /** Une clé manquante ne tranche rien : on garde ce qu'on a. */
    @Test
    fun `sans clef on ne lache pas son groupe`() {
        assertFalse(GroupArbitration.shouldYield(null, bob))
        assertFalse(GroupArbitration.shouldYield(alice, null))
        assertFalse(GroupArbitration.shouldYield("", bob))
        assertFalse(GroupArbitration.shouldYield(alice, "   "))
    }

    /** La règle doit tenir sur de vraies clés, pas seulement sur deux jetons. */
    @Test
    fun `la regle tient sur des clefs de 64 caracteres`() {
        val low = "0".repeat(63) + "1"
        val high = "f".repeat(64)

        assertTrue(exactlyOneYields(low, high))
        assertTrue(GroupArbitration.shouldYield(mine = high, peer = low))
        assertFalse(GroupArbitration.shouldYield(mine = low, peer = high))
    }

    /**
     * Quelle que soit la paire tirée, l'accord tient. Sans horloge ni hasard :
     * un balayage déterministe de toutes les paires distinctes d'un jeu.
     */
    @Test
    fun `l'accord tient sur toutes les paires distinctes`() {
        val keys = (0 until 40).map { "%064x".format(it * 7919) }
        keys.forEachIndexed { i, a ->
            keys.drop(i + 1).forEach { b ->
                assertTrue("désaccord entre $a et $b", exactlyOneYields(a, b))
            }
        }
    }
}
