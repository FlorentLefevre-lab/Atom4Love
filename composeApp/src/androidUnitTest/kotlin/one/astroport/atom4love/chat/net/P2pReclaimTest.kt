package one.astroport.atom4love.chat.net

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Ce qu'on retire d'un groupe Wi-Fi Direct retrouvé au lancement — et surtout
 * ce qu'on ne retire pas. La règle est étroite exprès : le groupe d'une autre
 * application n'est pas à nous, même quand le nôtre a disparu.
 */
class P2pReclaimTest {

    private val ours = "DIRECT-ab-A4L"

    @Test
    fun `sans trace, on ne touche a rien`() {
        assertEquals(
            P2pGroup.Verdict.Nothing,
            P2pGroup.decide(trace = null, currentGroup = ours),
        )
    }

    @Test
    fun `le groupe qu'on avait engage est referme`() {
        assertEquals(
            P2pGroup.Verdict.Remove(ours),
            P2pGroup.decide(trace = ours, currentGroup = ours),
        )
    }

    /** Redémarrage, Wi-Fi coupé : le groupe est tombé sans nous. */
    @Test
    fun `trace sans groupe vivant - on oublie`() {
        assertEquals(
            P2pGroup.Verdict.Forget,
            P2pGroup.decide(trace = ours, currentGroup = null),
        )
    }

    /** Le cas qui compte : ne pas couper le Wi-Fi Direct de quelqu'un d'autre. */
    @Test
    fun `un groupe qui n'est pas le notre est laisse en place`() {
        assertEquals(
            P2pGroup.Verdict.Forget,
            P2pGroup.decide(trace = ours, currentGroup = "DIRECT-xy-Impression"),
        )
    }
}
