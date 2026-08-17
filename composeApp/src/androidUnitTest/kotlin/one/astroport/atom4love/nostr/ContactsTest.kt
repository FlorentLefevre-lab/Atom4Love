package one.astroport.atom4love.nostr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * [Contacts.merge] est toute la logique qui ne dépend pas d'un relais : le
 * reste ([Contacts.follow]) n'est qu'un aller-retour réseau autour d'elle,
 * comme [Certificate.existing]/[Certificate.send] pour le certificat.
 */
class ContactsTest {

    @Test
    fun `ajoute un pair absent en fin de liste`() {
        val existing = listOf(listOf("p", "aaaa"), listOf("p", "bbbb"))
        val merged = Contacts.merge(existing, "cccc")
        assertEquals(listOf(listOf("p", "aaaa"), listOf("p", "bbbb"), listOf("p", "cccc")), merged)
    }

    @Test
    fun `un pair deja suivi ne se duplique pas`() {
        val existing = listOf(listOf("p", "aaaa"), listOf("p", "bbbb"))
        assertEquals(existing, Contacts.merge(existing, "bbbb"))
    }

    @Test
    fun `la comparaison ignore la casse de l'hexadecimal`() {
        val existing = listOf(listOf("p", "AAAA"))
        assertEquals(existing, Contacts.merge(existing, "aaaa"))
    }

    @Test
    fun `une liste vide donne un seul pair`() {
        assertEquals(listOf(listOf("p", "aaaa")), Contacts.merge(emptyList(), "aaaa"))
    }

    @Test
    fun `les tags qui ne sont pas des p restent en place`() {
        val existing = listOf(listOf("p", "aaaa"), listOf("t", "atom4love"))
        val merged = Contacts.merge(existing, "bbbb")
        assertEquals(listOf(listOf("p", "aaaa"), listOf("t", "atom4love"), listOf("p", "bbbb")), merged)
    }

    @Test
    fun `rien a fusionner renvoie la meme liste, pas une copie`() {
        val existing = listOf(listOf("p", "aaaa"))
        // Pas d'allocation inutile quand il n'y a rien à ajouter.
        assertSame(existing, Contacts.merge(existing, "aaaa"))
    }
}
