package one.astroport.atom4love.domain

import com.uber.h3core.H3Core
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La projection Goldberg doit être identique sur toute station : les centres
 * figés dans [GoldbergPortal] sont revalidés contre la vraie grille H3
 * (variante desktop), et quelques lieux connus vérifient le plus-proche-sommet.
 */
class GoldbergPortalTest {

    @Test
    fun `les 12 portails sont les 12 pentagones H3 de la résolution 0`() {
        val h3 = H3Core.newInstance()
        val pentagons = h3.getPentagons(0).sortedBy { h3.getBaseCellNumber(it) }
        assertEquals(12, GoldbergPortal.All.size)
        assertEquals(12, pentagons.size)
        GoldbergPortal.All.zip(pentagons).forEach { (portal, cell) ->
            val center = h3.cellToLatLng(cell)
            assertEquals("lat du portail ${portal.code}", center.lat, portal.latDeg, 1e-5)
            assertEquals("lon du portail ${portal.code}", center.lng, portal.lonDeg, 1e-5)
        }
    }

    @Test
    fun `chaque centre de portail se projette sur lui-même`() {
        GoldbergPortal.All.forEach { portal ->
            assertEquals(portal, GoldbergPortal.nearest(portal.latDeg, portal.lonDeg))
        }
    }

    @Test
    fun `l'indexation est stable - P01 Sirius reste P01 Sirius`() {
        val p1 = GoldbergPortal.All.first()
        assertEquals(1, p1.index)
        assertEquals("Sirius", p1.star)
        assertEquals("a4l:P01", p1.code)
        assertEquals("a4l:P01 · Sirius", p1.label)
    }

    @Test
    fun `lieux connus - le portail est le sommet le plus proche`() {
        // Paris → sommet de la mer de Norvège.
        assertEquals("a4l:P01 · Sirius", GoldbergPortal.nearest(48.86, 2.35).label)
        // Tokyo → sommet de la mer de Bohai.
        assertEquals("a4l:P03 · Vega", GoldbergPortal.nearest(35.68, 139.69).label)
        // Le sample de la maquette passe par le même chemin que l'UI.
        val sample = BirthData.Sample
        assertTrue(sample.lat != null && sample.lon != null)
        assertEquals(
            "a4l:P01 · Sirius",
            GoldbergPortal.nearest(sample.lat!!, sample.lon!!).label,
        )
    }
}
