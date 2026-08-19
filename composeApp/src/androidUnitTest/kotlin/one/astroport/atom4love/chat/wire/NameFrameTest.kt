package one.astroport.atom4love.chat.wire

import one.astroport.atom4love.data.Pseudo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La trame qui porte le nom, et les règles du nom lui-même.
 *
 * ⚠ Ce qui est épinglé ici tient à une contrainte de radio : le nom voyage dans
 * une trame scellée dont la charge utile tombe à une douzaine d'octets au MTU
 * plancher. Un nom d'accents ou d'idéogrammes vaut deux ou trois fois sa
 * longueur en caractères — la troncature doit donc couper à la frontière UTF-8,
 * jamais au milieu d'un point de code, sous peine de produire des « ? » chez le
 * pair.
 */
class NameFrameTest {

    @Test
    fun `un nom fait l'aller-retour intact`() {
        val frame = ChatFrames.decode(ChatFrames.encodeName("Marie-Ange"))
        assertTrue(frame is ChatFrame.Name)
        assertEquals("Marie-Ange", (frame as ChatFrame.Name).pseudo)
    }

    @Test
    fun `les accents traversent`() {
        val frame = ChatFrames.decode(ChatFrames.encodeName("Zoé Ünal")) as ChatFrame.Name
        assertEquals("Zoé Ünal", frame.pseudo)
    }

    /**
     * Un nom vide n'est pas un nom : le taire vaut mieux que d'installer une
     * étiquette invisible à la place de celle qu'on avait.
     */
    @Test
    fun `un nom vide n'est pas une trame`() {
        assertNull(ChatFrames.decode(ChatFrames.encodeName("")))
        assertNull(ChatFrames.decode(ChatFrames.encodeName("   ")))
    }

    @Test
    fun `une trame tronquée s'ignore au lieu de planter`() {
        val bytes = ChatFrames.encodeName("Marie")
        assertNull(ChatFrames.decode(bytes.copyOfRange(0, 3)))
    }

    /**
     * ⚠ Le type **0x0A** reste brûlé : il portait l'onde biologique, partie le
     * 15/08 avec la formule de Watson. Le nom a pris 0x0C, pas 0x0A — un
     * appareil resté à une ancienne version peut encore émettre l'ancien type,
     * et le réutiliser ferait lire une onde pour un nom.
     */
    @Test
    fun `le type brûlé de l'onde ne redevient pas un nom`() {
        assertNull(ChatFrames.decode(byteArrayOf(0x0A, 4, 1, 2, 3, 4)))
    }

    @Test
    fun `les blancs de tête et de queue partent, les retours à la ligne deviennent des espaces`() {
        assertEquals("Marie Ange", Pseudo.clean("  Marie\nAnge  "))
    }

    @Test
    fun `un nom trop long est coupé, un nom trop court est refusé`() {
        assertEquals(Pseudo.MAX_LENGTH, Pseudo.clean("a".repeat(80)).length)
        assertFalse(Pseudo.isValid("Z"))
        assertTrue(Pseudo.isValid("Zo"))
    }
}
