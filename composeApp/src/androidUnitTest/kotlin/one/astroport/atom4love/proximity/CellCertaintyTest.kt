package one.astroport.atom4love.proximity

import com.uber.h3core.H3Core
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La règle tranchée par Florent le 20/08 : **on ne nomme un portail que si le
 * cercle d'incertitude de la position tient tout entier dedans**. Se tromper
 * d'hexagone est pire que ne pas en avoir — le jeton de présence en est dérivé,
 * et deux voisins qui n'ont pas la même cellule ne se reconnaissent plus.
 *
 * Ces cas-là tiennent la géométrie de [CellLocator.fitsInCell] contre la vraie
 * grille H3, sans Android autour.
 */
class CellCertaintyTest {

    private val h3 = H3Core.newInstance()
    private val resolution = 8

    /** Toulouse, quelque part dans la ville — le lieu n'a aucune importance. */
    private val lat = 43.6045
    private val lon = 1.4440

    @Test
    fun `un fix GNSS au centre d'une cellule la nomme`() {
        val cell = h3.latLngToCell(lat, lon, resolution)
        val center = h3.cellToLatLng(cell)
        assertTrue(
            "± 20 m au centre d'un hexagone de 920 m doit suffire",
            CellLocator.fitsInCell(h3, center.lat, center.lng, 20.0, resolution),
        )
    }

    @Test
    fun `une position approximative ne nomme rien`() {
        val cell = h3.latLngToCell(lat, lon, resolution)
        val center = h3.cellToLatLng(cell)
        // Ce que rend « Approximative » depuis Android 12 : le kilomètre.
        assertFalse(
            "± 1 500 m déborde forcément d'un hexagone de 920 m",
            CellLocator.fitsInCell(h3, center.lat, center.lng, 1_500.0, resolution),
        )
    }

    @Test
    fun `au bord, même un bon fix refuse de trancher`() {
        val cell = h3.latLngToCell(lat, lon, resolution)
        val center = h3.cellToLatLng(cell)
        val vertex = h3.cellToBoundary(cell).first()
        // Un pas à l'intérieur depuis un sommet : on est bien dans la cellule,
        // mais à quelques mètres du bord.
        val inside = 0.02
        val nearEdgeLat = vertex.lat + (center.lat - vertex.lat) * inside
        val nearEdgeLon = vertex.lng + (center.lng - vertex.lng) * inside
        assertEqualsCell(cell, nearEdgeLat, nearEdgeLon)
        assertFalse(
            "à quelques mètres du bord, ± 50 m peut désigner le voisin",
            CellLocator.fitsInCell(h3, nearEdgeLat, nearEdgeLon, 50.0, resolution),
        )
    }

    @Test
    fun `la certitude ne dépend pas de l'hémisphère`() {
        // Le décalage en longitude divise par le cosinus de la latitude : une
        // erreur de signe ou d'unité s'y verrait tout de suite.
        for (place in listOf(-33.45 to -70.66, 64.14 to -21.94, 1.29 to 103.85)) {
            val cell = h3.latLngToCell(place.first, place.second, resolution)
            val center = h3.cellToLatLng(cell)
            assertTrue(
                "± 20 m au centre doit suffire à ${place.first}, ${place.second}",
                CellLocator.fitsInCell(h3, center.lat, center.lng, 20.0, resolution),
            )
            assertFalse(
                "± 1 500 m ne doit jamais suffire à ${place.first}, ${place.second}",
                CellLocator.fitsInCell(h3, center.lat, center.lng, 1_500.0, resolution),
            )
        }
    }

    private fun assertEqualsCell(expected: Long, lat: Double, lon: Double) {
        assertTrue(
            "le point d'essai doit être dans la cellule",
            h3.latLngToCell(lat, lon, resolution) == expected,
        )
    }
}
