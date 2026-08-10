package one.astroport.atom4love.nostr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayMessageTest {

    @Test
    fun `parse OK`() {
        val msg = RelayMessage.parse("""["OK","abc123",true,""]""")
        assertEquals(RelayMessage.Ok("abc123", true, ""), msg)
    }

    @Test
    fun `parse OK refusé avec raison`() {
        val msg = RelayMessage.parse("""["OK","abc",false,"blocked: spam"]""")
        assertEquals(RelayMessage.Ok("abc", false, "blocked: spam"), msg)
    }

    @Test
    fun `parse EOSE et NOTICE et CLOSED`() {
        assertEquals(RelayMessage.Eose("sub1"), RelayMessage.parse("""["EOSE","sub1"]"""))
        assertEquals(RelayMessage.Notice("nope"), RelayMessage.parse("""["NOTICE","nope"]"""))
        assertEquals(
            RelayMessage.Closed("sub1", "auth-required: x"),
            RelayMessage.parse("""["CLOSED","sub1","auth-required: x"]"""),
        )
    }

    @Test
    fun `parse EVENT`() {
        val eventJson = """{"id":"00","pubkey":"11","created_at":5,"kind":1,""" +
            """"tags":[],"content":"c","sig":"22"}"""
        val msg = RelayMessage.parse("""["EVENT","sub1",$eventJson]""")
        val event = (msg as RelayMessage.Event).event
        assertEquals("sub1", msg.subscriptionId)
        assertEquals("c", event.content)
        assertEquals(5L, event.createdAt)
    }

    @Test
    fun `JSON invalide ou type inconnu - Unknown, jamais d'exception`() {
        assertTrue(RelayMessage.parse("pas du json") is RelayMessage.Unknown)
        assertTrue(RelayMessage.parse("""["AUTH","challenge"]""") is RelayMessage.Unknown)
    }

    @Test
    fun `REQ - les champs null du filtre sont omis`() {
        val req = RelayMessage.subscribe("s1", NostrFilter(kinds = listOf(1), limit = 10))
        assertEquals("""["REQ","s1",{"kinds":[1],"limit":10}]""", req)
    }
}
