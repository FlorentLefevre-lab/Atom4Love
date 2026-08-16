package one.astroport.atom4love.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les trois formules de la planche `tzolkin_oracle.svg`, épinglées une par une
 * — sceau+10 même ton, sceau±10 ton retourné, K+K′ = 261 — puis les propriétés
 * qui découlent de leur combinaison sur les 260 cases de la grille.
 *
 * Ce qui compte le plus ici est le **partage du sceau** entre le défi et
 * l'alternance : c'est ce que dit la planche, c'est contre-intuitif quand on
 * connaît d'autres traditions, et c'est ce que le Plateau exploite pour lire un
 * lien depuis un sceau seul.
 */
class OracleTest {

    private val all = (1..260).mapNotNull { KinMaya.ofNumber(it) }

    @Test
    fun `le defi garde le ton et decale le sceau de dix`() {
        all.forEach { k ->
            val a = Oracle.antipode(k)!!
            assertEquals("ton du défi de ${k.kin}", k.tone, a.tone)
            assertEquals("sceau du défi de ${k.kin}", (k.glyph + 10) % 20, a.glyph)
        }
    }

    @Test
    fun `l'alternance partage le sceau du defi et retourne le ton`() {
        all.forEach { k ->
            val defi = Oracle.antipode(k)!!
            val alt = Oracle.analogue(k)!!
            // Le point de la planche : ±10 et +10 sont la même opération.
            assertEquals("sceau partagé sur ${k.kin}", defi.glyph, alt.glyph)
            // « 14 − ton », sur des tons comptés de 1 à 13 comme chez Fred.
            assertEquals("somme des tons sur ${k.kin}", 14, (k.tone + 1) + (alt.tone + 1))
        }
    }

    @Test
    fun `au ton 7 le defi et l'alternance sont le meme KIN`() {
        // 14 − 7 = 7 : le ton Résonnant est son propre complément. Les deux
        // relations partagent déjà le sceau ; là, le ton cesse aussi de les
        // séparer et elles se referment sur un seul KIN. Vingt cas sur 260.
        val resonants = all.filter { it.tone == 6 }
        assertEquals(20, resonants.size)
        resonants.forEach { k ->
            assertEquals("sur ${k.kin}", Oracle.antipode(k)!!.kin, Oracle.analogue(k)!!.kin)
        }
        // Partout ailleurs, le ton les sépare.
        all.filter { it.tone != 6 }.forEach { k ->
            assertNotEquals("sur ${k.kin}", Oracle.antipode(k)!!.kin, Oracle.analogue(k)!!.kin)
        }
    }

    @Test
    fun `l'occulte somme a 261, mire le sceau et retourne le ton`() {
        all.forEach { k ->
            val o = Oracle.occult(k)!!
            assertEquals("somme sur ${k.kin}", 261, k.kin + o.kin)
            // Déduit, non posé : la définition est le nombre seul.
            assertEquals("sceau miroir sur ${k.kin}", 19 - k.glyph, o.glyph)
            assertEquals("ton retourné sur ${k.kin}", 12 - k.tone, o.tone)
        }
    }

    @Test
    fun `les trois relations sont involutives`() {
        all.forEach { k ->
            assertEquals(k.kin, Oracle.antipode(Oracle.antipode(k)!!)!!.kin)
            assertEquals(k.kin, Oracle.analogue(Oracle.analogue(k)!!)!!.kin)
            assertEquals(k.kin, Oracle.occult(Oracle.occult(k)!!)!!.kin)
        }
    }

    @Test
    fun `les complements ne retombent jamais sur soi`() {
        all.forEach { k ->
            val r = Oracle.of(k)
            val kins = listOf(k.kin, r.antipode!!.kin, r.analogue!!.kin, r.occult!!.kin)
            // Quatre KIN distincts — sauf au ton 7, où défi et alternance se
            // confondent et il n'en reste que trois. Jamais soi, jamais moins.
            val attendu = if (k.tone == 6) 3 else 4
            assertEquals("KIN distincts pour ${k.kin}", attendu, kins.toSet().size)
            assertTrue("un complément retombe sur soi (${k.kin})", kins.drop(1).none { it == k.kin })
        }
    }

    @Test
    fun `un sceau seul designe le defi ou l'occulte, jamais les deux`() {
        all.forEach { k ->
            val defiSeal = (k.glyph + 10) % 20
            val hiddenSeal = 19 - k.glyph
            assertNotEquals("sceaux confondus sur ${k.kin}", defiSeal, hiddenSeal)
            assertEquals(Oracle.Bond.Challenge, Oracle.sealBond(k.glyph, defiSeal))
            assertEquals(Oracle.Bond.Hidden, Oracle.sealBond(k.glyph, hiddenSeal))
            // Le sien ne dit rien : on n'est ni son propre défi ni son occulte.
            assertNull(Oracle.sealBond(k.glyph, k.glyph))
            // Un pair sans sceau connu non plus.
            assertNull(Oracle.sealBond(k.glyph, KinMaya.GLYPH_UNKNOWN))
        }
    }

    @Test
    fun `un sceau seul ne tranche pas entre defi et alternance`() {
        // La conséquence à retenir pour le Plateau : les deux répondent au même
        // sceau, donc [sealBond] ne peut pas les distinguer — et c'est correct.
        all.forEach { k ->
            val defi = Oracle.antipode(k)!!
            val alt = Oracle.analogue(k)!!
            assertEquals(Oracle.Bond.Challenge, Oracle.sealBond(k.glyph, defi.glyph))
            assertEquals(Oracle.Bond.Challenge, Oracle.sealBond(k.glyph, alt.glyph))
        }
    }

    @Test
    fun `un KIN hors grille n'a pas d'occulte`() {
        // La station rend 263 pour le 28/09/1944 — voir KinMaya.of. Sans case,
        // pas de 261 − K : on ne montre rien plutôt qu'un nombre inventé.
        val horsGrille = KinMaya.of(1944, 9, 28)!!
        assertTrue("KIN attendu au-delà de 260", horsGrille.kin > 260)
        assertNull(Oracle.occult(horsGrille))
    }

    @Test
    fun `le KIN d'un sceau et d'un ton est unique et se relit`() {
        all.forEach { k ->
            assertEquals(k.kin, KinMaya.ofSealAndTone(k.glyph, k.tone)!!.kin)
        }
        assertNull(KinMaya.ofSealAndTone(20, 0))
        assertNull(KinMaya.ofSealAndTone(0, 13))
    }

    @Test
    fun `l'oracle du KIN 119, l'exemple de la planche`() {
        // Kin 119 : sceau 18 (Tempête), ton index 1 — le « T3 » de sa légende
        // compte autrement, ce qui ne change aucune des trois formules.
        val k = KinMaya.ofNumber(119)!!
        assertEquals(18, k.glyph)
        assertEquals(1, k.tone)

        val r = Oracle.of(k)
        // Défi : sceau 8 (Muluc / Lune), même ton.
        assertEquals(8, r.antipode!!.glyph)
        assertEquals(1, r.antipode.tone)
        // Alternance : même sceau 8, ton 11 — 2 + 13 ne font pas 14, mais les
        // tons affichés, 2 et 12, oui.
        assertEquals(8, r.analogue!!.glyph)
        assertEquals(11, r.analogue.tone)
        assertEquals(14, (k.tone + 1) + (r.analogue.tone + 1))
        // Occulte : 261 − 119 = 142.
        assertEquals(142, r.occult!!.kin)
    }
}
