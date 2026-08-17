package one.astroport.atom4love.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Quand la cabine cesse d'appeler une adresse BLE — et quand elle recommence. */
class BleIdentitiesTest {

    private val alice = "aa11"
    private val bob = "bb22"

    @Test
    fun `une adresse inconnue se compose toujours`() {
        val known = BleIdentities()
        assertTrue(known.shouldDial("AA:BB:CC:DD:EE:FF", setOf(alice, bob)))
    }

    @Test
    fun `un pair deja joignable autrement n'est plus appele`() {
        val known = BleIdentities()
        known.learn("AA:BB:CC:DD:EE:FF", alice)
        assertFalse(known.shouldDial("AA:BB:CC:DD:EE:FF", setOf(alice)))
    }

    /** Le médium rapide est tombé : la porte redevient la porte. */
    @Test
    fun `le pair redevient appelable des qu'il n'est plus joignable ailleurs`() {
        val known = BleIdentities()
        known.learn("AA:BB:CC:DD:EE:FF", alice)
        assertFalse(known.shouldDial("AA:BB:CC:DD:EE:FF", setOf(alice)))
        assertTrue(known.shouldDial("AA:BB:CC:DD:EE:FF", setOf(bob)))
        assertTrue(known.shouldDial("AA:BB:CC:DD:EE:FF", emptySet()))
    }

    /** Les adresses tournent : la nouvelle est un inconnu, et le reste. */
    @Test
    fun `une adresse neuve du meme pair est un inconnu`() {
        val known = BleIdentities()
        known.learn("AA:BB:CC:DD:EE:FF", alice)
        assertTrue(known.shouldDial("11:22:33:44:55:66", setOf(alice)))
    }

    @Test
    fun `la memoire est bornee - la plus ancienne adresse sort`() {
        val known = BleIdentities(capacity = 2)
        known.learn("a", alice)
        known.learn("b", bob)
        known.learn("c", alice)
        assertNull(known.peerAt("a"))
        assertEquals(bob, known.peerAt("b"))
        assertEquals(alice, known.peerAt("c"))
    }

    /** Ré-apprendre rafraîchit : une adresse encore vue ne doit pas sortir la première. */
    @Test
    fun `revoir une adresse la remet au bout de la file`() {
        val known = BleIdentities(capacity = 2)
        known.learn("a", alice)
        known.learn("b", bob)
        known.learn("a", alice)
        known.learn("c", bob)
        assertNull(known.peerAt("b"))
        assertEquals(alice, known.peerAt("a"))
    }
}
