package one.astroport.atom4love.chat.wire

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Le battement de vie — un octet, et ce qu'il ne doit pas devenir.
 *
 * ⚠ Ce que ces essais protègent n'est pas le battement lui-même (il n'a aucun
 * contenu à préserver) mais **sa place dans le protocole** : un octet de tête
 * qui ne collisionne avec rien, une trame d'un seul octet qui ne se confond pas
 * avec l'adieu, et un décodeur qui ne prend pas un battement pour du contenu.
 */
class PingFrameTest {

    @Test
    fun `un octet, et il fait l'aller-retour`() {
        val bytes = ChatFrames.encodePing()
        assertEquals("un battement ne porte rien : il tient en un octet", 1, bytes.size)
        assertSame(ChatFrame.Ping, ChatFrames.decode(bytes))
    }

    @Test
    fun `il ne se confond pas avec l'adieu`() {
        // Les deux tiennent en un octet et voyagent sur la même file de
        // contrôle : les confondre ferait fermer une cabine à chaque battement.
        val ping = ChatFrames.encodePing()
        val bye = ChatFrames.encodeBye()
        assertEquals(1, bye.size)
        assertSame(ChatFrame.Bye, ChatFrames.decode(bye))
        assertSame(ChatFrame.Ping, ChatFrames.decode(ping))
        assertFalse(bye.contentEquals(ping))
    }

    @Test
    fun `l'octet 0x0A reste brûlé`() {
        // Il portait la trame VERSION retirée le 17/08, et le champ BLE standard
        // AD 0x0A le réutilise dans les airs. Rien ne doit le décoder.
        assertNull(ChatFrames.decode(byteArrayOf(0x0A)))
    }

    @Test
    fun `un battement suivi de quoi que ce soit n'est pas un battement`() {
        // La longueur EXACTE fait la trame : sans ça, un fragment tronqué d'une
        // autre trame commençant par 0x0D passerait pour un signe de vie.
        assertNull(ChatFrames.decode(byteArrayOf(0x0D, 0x00)))
    }
}
