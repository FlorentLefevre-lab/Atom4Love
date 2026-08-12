package one.astroport.atom4love.nostr

import fr.acinq.secp256k1.Secp256k1
import one.astroport.atom4love.domain.BirthData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NostrEventTest {

    private val keys = LoveKeyForge.forge(BirthData.Sample)

    /**
     * Le noyau provisoire de la fiche d'exemple, figé.
     *
     * Ce npub n'a aucune valeur de protocole — la clé LOVE vient de la station.
     * Mais il vaut engagement : toute retouche à la forge provisoire déplacerait
     * l'identité de chaque appareil déjà en service, et les cabines ouvertes la
     * veille ne reconnaîtraient plus personne. Si ce test tombe, c'est une
     * décision à prendre, jamais un effet de bord à entériner.
     */
    @Test
    fun `la forge provisoire ne bouge pas sous les pieds des appareils en service`() {
        assertEquals(
            "npub17c7fnw5jrmhurkn09k8j842sr9md3sdhr94zg34xdru08gqs2hhswxqdp6",
            keys.npub,
        )
    }

    @Test
    fun `sérialisation canonique NIP-01 - forme exacte`() {
        val canonical = NostrEvent.canonicalJson(
            pubkey = "ab".repeat(32),
            createdAt = 1_700_000_000,
            kind = 1,
            tags = listOf(listOf("t", "phix2")),
            content = "hello",
        )
        assertEquals(
            """[0,"${"ab".repeat(32)}",1700000000,1,[["t","phix2"]],"hello"]""",
            canonical,
        )
    }

    @Test
    fun `échappement des guillemets et sauts de ligne`() {
        val canonical = NostrEvent.canonicalJson(
            pubkey = "ab".repeat(32),
            createdAt = 0,
            kind = 1,
            tags = emptyList(),
            content = "l1\n\"l2\"",
        )
        assertEquals("""[0,"${"ab".repeat(32)}",0,1,[],"l1\n\"l2\""]""", canonical)
    }

    @Test
    fun `événement créé - id valide et signature Schnorr vérifiable`() {
        val event = NostrEvent.create(keys, kind = 1, content = "premier battement")
        assertTrue(event.hasValidId())
        assertTrue(
            Secp256k1.verifySchnorr(
                Hex.decode(event.sig),
                Hex.decode(event.id),
                keys.publicKey,
            ),
        )
    }

    @Test
    fun `aller-retour JSON du wire format`() {
        val event = NostrEvent.create(keys, kind = 1, content = "aller-retour")
        val decoded = NostrEvent.json.decodeFromString(
            NostrEvent.serializer(),
            NostrEvent.json.encodeToString(NostrEvent.serializer(), event),
        )
        assertEquals(event, decoded)
    }

    @Test
    fun `forge LOVE - déterministe, et sensible aux données d'incarnation`() {
        assertEquals(keys.npub, LoveKeyForge.forge(BirthData.Sample).npub)
        val other = LoveKeyForge.forge(BirthData.Sample.copy(weightKg = 3.3f))
        assertNotEquals(keys.npub, other.npub)
    }
}
