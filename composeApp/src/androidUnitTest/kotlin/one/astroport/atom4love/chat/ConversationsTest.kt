package one.astroport.atom4love.chat

import one.astroport.atom4love.nostr.Bech32
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le passage d'une **salle** à des **conversations**.
 *
 * C'est le cœur de la refonte, et c'est du calcul pur : le transport n'a pas
 * bougé d'un octet, seule la façon de relire la même liste de messages a changé.
 * Ce qui est épinglé ici est ce qu'un utilisateur remarquerait immédiatement si
 * ça cassait — un message rangé dans le mauvais fil, une conversation qui
 * disparaît parce que quelqu'un a franchi une porte, ou deux étrangers réunis
 * dans le même fil « inconnu ».
 */
class ConversationsTest {

    private fun key(byte: Int) = ByteArray(32) { byte.toByte() }
    private fun hex(byte: Int) = "%02x".format(byte).repeat(32)
    private fun npub(byte: Int) = Bech32.encode("npub", key(byte))

    private fun peer(byte: Int, name: String? = null) =
        ChatEngine.Peer(nostrKey = key(byte), npub = npub(byte), name = name)

    private fun message(peerHex: String?, text: String, mine: Boolean, atMs: Long) =
        ChatMessage(
            id = text.hashCode(), mine = mine, from = if (mine) "moi" else "elle",
            fromAttested = !mine, peer = peerHex,
            kind = ChatKind.TEXT, status = ChatStatus.SENT, text = text, atMs = atMs,
        )

    @Test
    fun `chaque message tombe dans le fil de son correspondant`() {
        val conversations = Conversations.of(
            peers = listOf(peer(1, "Alice"), peer(2, "Bob")),
            messages = listOf(
                message(hex(1), "salut Alice", mine = true, atMs = 10),
                message(hex(2), "salut Bob", mine = true, atMs = 20),
                message(hex(1), "coucou", mine = false, atMs = 30),
            ),
        )
        assertEquals(2, conversations.size)
        val alice = conversations.first { it.peerHex == hex(1) }
        val bob = conversations.first { it.peerHex == hex(2) }
        assertEquals(listOf("salut Alice", "coucou"), alice.messages.map { it.text })
        assertEquals(listOf("salut Bob"), bob.messages.map { it.text })
    }

    @Test
    fun `un pair présent sans un mot a tout de même son fil, vide`() {
        val conversations = Conversations.of(listOf(peer(1, "Alice")), emptyList())
        assertEquals(1, conversations.size)
        assertTrue(conversations.single().empty)
        assertTrue(conversations.single().inRange)
    }

    /**
     * ⚠ Le cas qui décide de tout le confort de l'écran : en salle, un pair
     * disparaît et revient plusieurs fois par minute. Retirer son fil à chaque
     * fois effacerait sous les yeux ce qui vient d'être dit.
     */
    @Test
    fun `un pair qui s'éloigne garde son fil, hors de portée`() {
        val conversations = Conversations.of(
            peers = emptyList(),
            messages = listOf(message(hex(1), "à tout à l'heure", mine = true, atMs = 10)),
        )
        val alone = conversations.single()
        assertFalse(alone.inRange)
        assertEquals(1, alone.messages.size)
    }

    /**
     * ⚠ Deux étrangers ne cohabitent pas. Un message sans correspondant vient
     * d'un lien que personne n'a signé : le ranger dans un fil « inconnu »
     * commun reconstituerait la salle qu'on vient de défaire.
     */
    @Test
    fun `un message sans correspondant ne crée aucun fil`() {
        val conversations = Conversations.of(
            peers = emptyList(),
            messages = listOf(message(null, "d'où ça vient", mine = false, atMs = 10)),
        )
        assertTrue(conversations.isEmpty())
    }

    @Test
    fun `le fil qui vient de bouger passe devant`() {
        val conversations = Conversations.of(
            peers = listOf(peer(1, "Alice"), peer(2, "Bob")),
            messages = listOf(
                message(hex(1), "ancien", mine = true, atMs = 10),
                message(hex(2), "récent", mine = true, atMs = 99),
            ),
        )
        assertEquals(hex(2), conversations.first().peerHex)
    }

    @Test
    fun `à silence égal, ce qui est joignable passe devant`() {
        val conversations = Conversations.of(
            peers = listOf(peer(2, "Bob")),
            // Alice n'est plus là et ne nous a jamais rien dit : elle n'existe
            // que si un message la mentionne. On en pose un daté à zéro pour
            // qu'elle ait un fil, aussi muet que celui de Bob.
            messages = listOf(
                ChatMessage(
                    id = 1, mine = true, from = "moi", peer = hex(1),
                    kind = ChatKind.TEXT, status = ChatStatus.SENT, text = "", atMs = 0,
                ),
            ),
        )
        assertEquals(hex(2), conversations.first().peerHex)
    }

    @Test
    fun `le nom vient du pair présent, et survit à son départ par les messages`() {
        val present = Conversations.of(listOf(peer(1, "Alice")), emptyList()).single()
        assertEquals("Alice", present.name)

        val gone = Conversations.of(
            peers = emptyList(),
            messages = listOf(
                ChatMessage(
                    id = 1, mine = false, from = "Alice", fromAttested = true, peer = hex(1),
                    kind = ChatKind.TEXT, status = ChatStatus.RECEIVED, text = "coucou", atMs = 5,
                ),
            ),
        ).single()
        assertEquals("Alice", gone.name)
    }

    @Test
    fun `un pair qui ne s'est pas nommé n'a pas de nom, jamais sa clé`() {
        val conversation = Conversations.of(listOf(peer(1)), emptyList()).single()
        assertNull(conversation.name)
        // La clé reste disponible pour le journal technique, mais elle n'est
        // jamais ce que l'écran affiche à sa place.
        assertEquals(npub(1), conversation.npub)
    }

    /**
     * ⚠ **Deux personnes peuvent choisir le même pseudo, et rien ne l'empêche.**
     * Un pseudo se déclare, il ne s'attribue pas : pas de registre, pas de
     * première arrivée. Ce qui doit tenir, c'est l'écran — deux lignes « Marie »
     * dans une liste, et l'on écrit à la mauvaise personne.
     *
     * La règle : quand le pseudo ne suffit plus, l'identité reparaît **pour
     * ceux-là seulement**, et juste assez pour les séparer.
     */
    @Test
    fun `deux homonymes se séparent par la queue de leur clé, les autres non`() {
        val conversations = Conversations.of(
            peers = listOf(peer(1, "Marie"), peer(2, "Marie"), peer(3, "Bob")),
            messages = emptyList(),
        )
        val marie1 = conversations.first { it.peerHex == hex(1) }
        val marie2 = conversations.first { it.peerHex == hex(2) }
        val bob = conversations.first { it.peerHex == hex(3) }

        // Bob est seul de son espèce : son pseudo reste nu.
        assertEquals("Bob", bob.name)

        // Les deux Marie portent chacune la fin de sa propre clé…
        assertTrue(marie1.name!!.startsWith("Marie · "))
        assertTrue(marie2.name!!.startsWith("Marie · "))
        // …et ces deux fins diffèrent, sinon on n'aurait rien séparé.
        assertNotEquals(marie1.name, marie2.name)
        assertTrue(marie1.name!!.endsWith(marie1.npub.takeLast(4)))
    }

    @Test
    fun `une seule Marie garde son pseudo nu`() {
        val conversations = Conversations.of(listOf(peer(1, "Marie")), emptyList())
        assertEquals("Marie", conversations.single().name)
    }
}
