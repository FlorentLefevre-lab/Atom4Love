package one.astroport.atom4love.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les vecteurs viennent de `geoTagA4L()` de `phi2x.js`, exécuté sur cinq lieux
 * connus : c'est lui qui écrit les adresses que le relais porte, il fait donc
 * foi. La maille est celle de l'hexagone d'un kilomètre — on ne revient jamais
 * au point de départ, seulement dans sa case.
 */
class A4lAddressTest {

    private data class Vector(
        val name: String,
        val tag: String,
        val q: Int,
        val r: Int,
        val latDeg: Double,
        val lonDeg: Double,
    )

    private val vectors = listOf(
        Vector("Toulouse", "a4l:P00H8AC9804D", 2761, 77, 43.6047, 1.4442),
        Vector("Paris", "a4l:P00H8C078073", 3079, 115, 48.8566, 2.3522),
        Vector("Sydney", "a4l:P00H6554A45B", -6828, 9307, -33.8688, 151.2093),
        Vector("Quito", "a4l:P00H8B516947", 2897, -5817, -0.1807, -78.4678),
        Vector("Reykjavik", "a4l:P00H91797D3B", 4473, -709, 64.1466, -21.9426),
    )

    @Test
    fun `les coordonnées axiales sont celles de geoTagA4L`() {
        vectors.forEach { v ->
            val place = requireNotNull(A4lAddress.decode(v.tag)) { v.name }
            assertEquals(v.name, v.q, place.q)
            assertEquals(v.name, v.r, place.r)
        }
    }

    @Test
    fun `le lieu retrouvé tient dans sa maille`() {
        vectors.forEach { v ->
            val place = requireNotNull(A4lAddress.decode(v.tag)) { v.name }
            val ecartKm = Phi2X.haversineKm(v.latDeg, v.lonDeg, place.latDeg, place.lonDeg)
            // Un hexagone de 1 km d'arête : son centre est à moins d'un
            // kilomètre de n'importe quel point qu'il contient.
            assertTrue("${v.name} : $ecartKm km", ecartKm < A4lAddress.HEX_SIZE_KM)
        }
    }

    @Test
    fun `le pentagone se lit dans l'adresse`() {
        assertEquals(7, requireNotNull(A4lAddress.decode("a4l:P07H8C078073")).pentagonId)
    }

    @Test
    fun `l'adresse se trouve dans les tags g`() {
        val tags = listOf(
            listOf("d", "atom4love"),
            listOf("g", "a4l:P00H8C078073"),
            listOf("phase", "3.1416"),
        )
        assertEquals(3079, requireNotNull(A4lAddress.fromTags(tags)).q)
    }

    @Test
    fun `un tag qui n'est pas une adresse ne donne rien`() {
        assertNull(A4lAddress.decode("a4l:P00"))
        assertNull(A4lAddress.decode("u51tgkq"))
        assertNull(A4lAddress.fromTags(listOf(listOf("g"), listOf("t", "zicmama_demo"))))
    }

    @Test
    fun `un q r absurde ne rend pas un lieu absurde`() {
        // Le maximum encodable, 0xFFFF : très au-delà du globe.
        assertNull(A4lAddress.decode("a4l:P00HFFFFFFFF"))
    }
}
