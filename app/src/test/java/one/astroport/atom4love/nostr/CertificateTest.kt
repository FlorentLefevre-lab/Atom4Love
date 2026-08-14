package one.astroport.atom4love.nostr

import java.security.MessageDigest
import kotlinx.coroutines.test.TestScope
import one.astroport.atom4love.domain.BirthData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le certificat que nous publions doit être **indiscernable** de celui que la
 * station écrirait pour la même fiche : c'est `atomic_map.html` qui le relit, et
 * il n'a pas à savoir qui l'a signé.
 *
 * Les valeurs attendues viennent de `tools/phi2x.py` et de
 * `tools/atom4love_publish.py` exécutés sur [BirthData.Sample] — Paris,
 * 17/04/1985 15 h 30, onde Φ, 3,2 kg. Elles ne sont pas recalculées ici : si ce
 * test tombe, c'est notre portage qui a bougé, pas la référence.
 */
class CertificateTest {

    private val keys = LoveKeyForge.forge(BirthData.Sample)
    private val certificate = Certificate(TestScope())
    private val event = certificate.build(keys, BirthData.Sample, createdAt = 1_700_000_000L)!!

    private fun tag(name: String): String? =
        event.tags.firstOrNull { it.size >= 2 && it[0] == name }?.get(1)

    @Test
    fun `c'est un kind 30078 d=atom4love`() {
        assertEquals(30078, event.kind)
        assertEquals("atom4love", tag("d"))
    }

    /**
     * `compute_a4l_proof()` : `sha256("<pubkey hex>:ATOM4LOVE_v1")`. C'est la
     * porte d'écriture du relais — sans elle, rien ne passe.
     */
    @Test
    fun `le a4l_proof est celui de la station`() {
        val expected = MessageDigest.getInstance("SHA-256")
            .digest("${keys.publicKeyHex}:${Certificate.APP_ID}".toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals(expected, tag("a4l_proof"))
    }

    /**
     * Les deux tags `g`, dans l'ordre où la station les pose : le pentagone
     * seul, puis la maille. La carte ne lit que le second, mais on écrit ce
     * qu'elle écrit.
     */
    @Test
    fun `l'adresse a4l est celle de geo_tag_a4l`() {
        val gs = event.tags.filter { it.size >= 2 && it[0] == "g" }.map { it[1] }
        assertEquals(listOf("a4l:P03", "a4l:P03H8C078073"), gs)
    }

    @Test
    fun `l'amplitude cymatique est celle de encode_a5l_tag`() {
        assertEquals("a5l:81E5", tag("a5l"))
    }

    /** KIN, sceau et couleur sous les noms français de `phi2x.py`. */
    @Test
    fun `le sceau est écrit comme la station l'écrit`() {
        assertEquals("244", tag("kin"))
        assertEquals("Graine", tag("glyph"))
        assertEquals("10", tag("tone"))
        assertEquals("Jaune", tag("color"))
    }

    /**
     * Le contenu, au chiffre près — arrondis compris. ω_bio y est celui du
     * **poids de naissance**, la formule d'`atom4love_publish.py` et non celle
     * de Watson : voir `Phi2X.omegaBioAsPublished`.
     */
    @Test
    fun `le contenu est celui d'atom4love_publish`() {
        assertEquals(
            """{"personal_phase":1.435899,"omega_bio":12.7659,""" +
                """"a5l_amplitude":0.507414,"biological_sex":0,"kin_num":244,"version":1}""",
            event.content,
        )
    }

    /**
     * L'`email_enc` de la station est chiffré sous `$UPLANETNAME`, un secret que
     * nous n'avons pas et n'aurons pas. C'est aussi la seule donnée du
     * certificat qui désigne la personne : son absence est un acquis, pas un
     * manque à combler.
     */
    @Test
    fun `rien n'identifie la personne`() {
        assertNull(tag("email_enc"))
        assertNull(tag("email"))
        assertTrue(!event.content.contains("email"))
        // Le lieu n'est présent qu'encodé : aucun degré en clair.
        assertTrue(!event.content.contains("lat"))
        assertTrue(event.tags.none { it.size >= 2 && (it[0] == "lat" || it[0] == "lon") })
    }

    @Test
    fun `l'événement est signé et son id se recalcule`() {
        assertTrue(event.hasValidId())
        assertEquals(keys.publicKeyHex, event.pubkey)
        assertEquals(128, event.sig.length)
    }

    /** Sans lieu de naissance il n'y a ni phase ni adresse : rien à publier. */
    @Test
    fun `une fiche sans lieu ne produit pas de certificat`() {
        assertNull(certificate.build(keys, BirthData.Sample.copy(lat = null, lon = null)))
    }

    /**
     * L'adresse est ancrée à l'instant de **naissance**, pas à celui de la
     * publication : la grille pentagonale tourne toutes les 14,83 h, et un
     * certificat republié le lendemain doit tomber dans la même case.
     */
    @Test
    fun `republier ne déplace pas l'adresse`() {
        val later = certificate.build(keys, BirthData.Sample, createdAt = 1_800_000_000L)!!
        assertEquals(
            event.tags.filter { it[0] == "g" },
            later.tags.filter { it[0] == "g" },
        )
    }
}
