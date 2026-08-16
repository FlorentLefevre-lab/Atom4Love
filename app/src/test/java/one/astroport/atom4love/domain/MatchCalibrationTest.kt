package one.astroport.atom4love.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.random.Random

/**
 * **La pertinence des seuils, mesurée — pas supposée.**
 *
 * [Match] annonce ses taux (un match pour ~6 personnes, un super match pour
 * ~300) en supposant φ **uniforme** sur le tour. L'hypothèse est loin d'être
 * gratuite : l'autre grandeur de terrain de Fred, le champ Ψ de
 * [Phi2X.resonanceField], est si tassée sur des coordonnées réelles — [0,50 ;
 * 0,545] — qu'il a retiré de son projecteur la teinte qui en dépendait. Une φ
 * qui se comporterait pareil ferait matcher la Terre entière avec elle-même, et
 * les seuils de [Match] ne vaudraient rien.
 *
 * Ce test construit donc une population de naissances plausibles — dates
 * étalées sur soixante ans, lieux répartis sur les terres habitées — calcule
 * leur φ par [Phi2X.personalPhase], et mesure ce que les seuils produisent
 * **vraiment**. La graine est fixe : le résultat ne dépend pas du jour où on
 * lance la suite.
 *
 * ✅ **Relevé du 2026-08-16, sur 79 800 paires** : match **16,38 %**, super
 * match **0,335 %**, lien de sceau **10,17 %** — contre 16,4 / 0,335 / 10,0
 * prédits en supposant φ uniforme. L'hypothèse tient à la décimale : **φ ne se
 * comporte pas comme Ψ**, elle couvre le tour entier. Les fourchettes des
 * assertions sont larges autour de ces valeurs, pour qu'un changement de seuil
 * les fasse céder franchement plutôt qu'au bord.
 */
class MatchCalibrationTest {

    private companion object {
        const val POPULATION = 400

        /** Douze villes, cinq continents — de quoi ne pas mesurer une seule longitude. */
        val CITIES = listOf(
            48.8566 to 2.3522, // Paris
            43.6047 to 1.4442, // Toulouse
            51.5074 to -0.1278, // Londres
            40.4168 to -3.7038, // Madrid
            55.7558 to 37.6173, // Moscou
            -33.8688 to 151.2093, // Sydney
            -23.5505 to -46.6333, // São Paulo
            40.7128 to -74.0060, // New York
            35.6762 to 139.6503, // Tokyo
            -1.2921 to 36.8219, // Nairobi
            19.4326 to -99.1332, // Mexico
            28.6139 to 77.2090, // New Delhi
        )
    }

    private data class Person(val phase: Double, val glyph: Int)

    private val people: List<Person> = run {
        val rng = Random(20260816)
        (0 until POPULATION).map {
            val year = 1950 + rng.nextInt(60)
            val month = 1 + rng.nextInt(12)
            val day = 1 + rng.nextInt(28)
            val hour = rng.nextInt(24)
            val minute = rng.nextInt(60)
            val (lat, lon) = CITIES[rng.nextInt(CITIES.size)]
            val unix = LocalDateTime.of(year, month, day, hour, minute)
                .toEpochSecond(ZoneOffset.UTC)
            Person(
                phase = Phi2X.personalPhase(unix, lat, lon),
                glyph = KinMaya.of(year, month, day)!!.glyph,
            )
        }
    }

    @Test
    fun `la phase couvre bien tout le tour`() {
        // Le test qui décide de tous les autres. Douze secteurs de 30° : si φ
        // se tassait comme Ψ, plusieurs seraient vides.
        val buckets = IntArray(12)
        people.forEach { buckets[(it.phase / Phi2X.TAU * 12).toInt().coerceIn(0, 11)]++ }
        val vides = buckets.count { it == 0 }
        assertEquals("des secteurs de phase sont vides : ${buckets.toList()}", 0, vides)

        // Et aucun ne doit rafler la mise : à l'uniforme chacun tient 8,3 %.
        val maxPart = 100.0 * buckets.max() / people.size
        assertTrue("phase concentrée à $maxPart % sur un secteur", maxPart < 20.0)
    }

    @Test
    fun `les vingt sceaux sont tous representes`() {
        val seen = people.map { it.glyph }.toSet()
        assertEquals("sceaux manquants", 20, seen.size)
    }

    @Test
    fun `les taux annonces par Match se verifient sur une population reelle`() {
        var match = 0
        var superb = 0
        var bonds = 0
        var total = 0
        for (i in people.indices) {
            for (j in i + 1 until people.size) {
                val a = people[i]
                val b = people[j]
                total++
                if (Oracle.sealBond(a.glyph, b.glyph) != null) bonds++
                when (Match.read(a.phase, a.glyph, b.phase, b.glyph).level) {
                    Match.Level.Super -> superb++
                    Match.Level.Match -> match++
                    Match.Level.None -> Unit
                }
            }
        }
        val matchPct = 100.0 * (match + superb) / total
        val superPct = 100.0 * superb / total
        val bondPct = 100.0 * bonds / total
        println(
            "MESURE sur $total paires : match ${"%.2f".format(matchPct)} %, " +
                "super ${"%.3f".format(superPct)} %, sceau ${"%.2f".format(bondPct)} %",
        )

        // Le lien de sceau ne dépend d'aucun réglage : 2 sur 20, et la grille
        // du Tzolkin répartit les sceaux également. C'est le repère qui dit que
        // la population n'est pas biaisée.
        assertTrue("liens de sceau à $bondPct %, attendu ≈ 10 %", abs(bondPct - 10.0) < 2.5)

        // Les deux annonces de [Match], vérifiées à leur ordre de grandeur.
        assertTrue("match à $matchPct %, annoncé ≈ 16 %", matchPct in 11.0..22.0)
        assertTrue("super match à $superPct %, annoncé ≈ 0,34 %", superPct in 0.05..1.2)

        // Et la hiérarchie tient : un super match est au moins dix fois plus
        // rare qu'un match, sinon les deux mots disent la même chose.
        assertTrue("échelons trop proches ($matchPct % / $superPct %)", matchPct > 10 * superPct)
    }

    @Test
    fun `le quart de tour reste le vrai fond, sur cette population aussi`() {
        // Rien ne doit descendre sous 0,5, et le minimum observé doit s'en
        // approcher — sinon la population ne couvre pas les écarts moyens et
        // les taux mesurés plus haut ne voudraient rien dire.
        var min = 1.0
        for (i in people.indices) {
            for (j in i + 1 until people.size) {
                val k = Phi2X.resonanceK(people[i].phase, people[j].phase)
                assertTrue("k sous 0,5 : $k", k >= 0.5)
                if (k < min) min = k
            }
        }
        assertTrue("minimum observé trop haut : $min", min < 0.51)
    }
}
