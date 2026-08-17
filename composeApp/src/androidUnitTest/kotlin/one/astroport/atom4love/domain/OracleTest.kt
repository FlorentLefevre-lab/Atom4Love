package one.astroport.atom4love.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les quatre formules de Fred, épinglées une par une sur les 260 cases de la
 * grille — celles de `kin_oracle.sh`, en production chez lui, et non celles du
 * texte de `tzolkin_oracle.svg` qui **intervertissait les tons du défi et de
 * l'alternance** (corrigé le 16/08/2026, cf. [Oracle]).
 *
 * Deux choses comptent plus que les autres ici :
 *
 * - le **partage du sceau** entre le défi et l'alternance, contre-intuitif
 *   quand on connaît d'autres traditions, et que le Plateau exploite pour lire
 *   un lien depuis un sceau seul ;
 * - le fait que le **guide n'est pas une relation comme les trois autres** :
 *   cinq KIN se partagent le même, et il ne se renverse pas.
 */
class OracleTest {

    private val all = (1..260).mapNotNull { KinMaya.ofNumber(it) }

    @Test
    fun `le defi decale le sceau de dix et retourne le ton`() {
        all.forEach { k ->
            val a = Oracle.antipode(k)!!
            assertEquals("sceau du défi de ${k.kin}", (k.glyph + 10) % 20, a.glyph)
            // « 14 − ton », sur des tons comptés de 1 à 13 comme chez Fred.
            assertEquals("somme des tons sur ${k.kin}", 14, (k.tone + 1) + (a.tone + 1))
        }
    }

    @Test
    fun `l'alternance partage le sceau du defi et garde le ton`() {
        all.forEach { k ->
            val defi = Oracle.antipode(k)!!
            val alt = Oracle.analogue(k)!!
            // Le point de la planche : ±10 et +10 sont la même opération.
            assertEquals("sceau partagé sur ${k.kin}", defi.glyph, alt.glyph)
            assertEquals("ton de l'alternance de ${k.kin}", k.tone, alt.tone)
        }
    }

    @Test
    fun `le ton range les cinq pouvoirs en deux camps`() {
        // L'identité qui nous avait échappé : ce sont le DÉFI et l'occulte qui
        // retournent le ton — pas l'alternance, qui le garde comme le guide.
        all.forEach { k ->
            assertEquals("défi ↔ occulte sur ${k.kin}", Oracle.occult(k)!!.tone, Oracle.antipode(k)!!.tone)
            assertEquals("alternance sur ${k.kin}", k.tone, Oracle.analogue(k)!!.tone)
            assertEquals("guide sur ${k.kin}", k.tone, Oracle.guide(k)!!.tone)
        }
    }

    @Test
    fun `au ton 7 le defi et l'alternance sont le meme KIN`() {
        // 14 − 7 = 7 : le ton Résonnant est son propre complément. Les deux
        // relations partagent déjà le sceau ; là, le ton cesse aussi de les
        // séparer et elles se referment sur un seul KIN. Vingt cas sur 260,
        // que Fred confirme ne traiter nulle part autrement.
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
    fun `les trois relations de rencontre sont involutives`() {
        // Le guide n'y est pas : il ne se renverse pas, voir plus bas.
        all.forEach { k ->
            assertEquals(k.kin, Oracle.antipode(Oracle.antipode(k)!!)!!.kin)
            assertEquals(k.kin, Oracle.analogue(Oracle.analogue(k)!!)!!.kin)
            assertEquals(k.kin, Oracle.occult(Oracle.occult(k)!!)!!.kin)
        }
    }

    @Test
    fun `le guide reste dans la famille de sceaux et garde le ton`() {
        all.forEach { k ->
            val g = Oracle.guide(k)!!
            assertEquals("famille sur ${k.kin}", k.glyph % 4, g.glyph % 4)
            assertEquals("ton sur ${k.kin}", k.tone, g.tone)
            // La position dans la famille est celle du ton, pas la nôtre.
            assertEquals("position sur ${k.kin}", k.tone % 5, g.glyph / 4)
        }
    }

    @Test
    fun `le guide est cinq-vers-un et idempotent`() {
        // Ce que Fred annonce en disant « bijectivité non requise » : le guide
        // efface le rang dans la famille au lieu de le décaler. Cinq KIN ont
        // donc le même guide, et l'image ne compte que 4 × 13 = 52 KIN.
        val fibres = all.groupBy { Oracle.guide(it)!!.kin }
        assertEquals("KIN atteints", 52, fibres.size)
        fibres.forEach { (kin, sources) ->
            assertEquals("antécédents du guide $kin", 5, sources.size)
        }
        // Idempotent : le guide d'un guide est ce même guide. Les 52 atteints
        // sont donc leurs propres guides, et eux seuls.
        all.forEach { k ->
            val g = Oracle.guide(k)!!
            assertEquals("guide du guide de ${k.kin}", g.kin, Oracle.guide(g)!!.kin)
        }
        assertEquals(52, all.count { Oracle.guide(it)!!.kin == it.kin })
    }

    @Test
    fun `le guide sort du jeu des sceaux, les autres en sont`() {
        // Pourquoi le guide n'est pas un [Oracle.Bond] : son sceau est toujours
        // dans notre famille, quand ceux du défi et de l'occulte en sortent
        // toujours (+10 et le miroir déplacent tous deux la famille de 2).
        all.forEach { k ->
            assertNotEquals("défi sur ${k.kin}", k.glyph % 4, Oracle.antipode(k)!!.glyph % 4)
            assertNotEquals("occulte sur ${k.kin}", k.glyph % 4, Oracle.occult(k)!!.glyph % 4)
            assertNull("le guide ne fait pas lien", Oracle.sealBond(k.glyph, Oracle.guide(k)!!.glyph))
        }
    }

    @Test
    fun `les complements ne retombent jamais sur soi, sauf le guide`() {
        all.forEach { k ->
            val r = Oracle.of(k)
            val kins = listOf(k.kin, r.antipode!!.kin, r.analogue!!.kin, r.occult!!.kin)
            // Quatre KIN distincts — sauf au ton 7, où défi et alternance se
            // confondent et il n'en reste que trois. Jamais soi, jamais moins.
            val attendu = if (k.tone == 6) 3 else 4
            assertEquals("KIN distincts pour ${k.kin}", attendu, kins.toSet().size)
            assertTrue("un complément retombe sur soi (${k.kin})", kins.drop(1).none { it == k.kin })
            // Le guide, lui, peut être soi — c'est le cas des 52 points fixes.
            assertTrue(r.guide!!.kin in 1..260)
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
        // compte autrement, ce qui ne change aucune des quatre formules.
        val k = KinMaya.ofNumber(119)!!
        assertEquals(18, k.glyph)
        assertEquals(1, k.tone)

        val r = Oracle.of(k)
        // Défi : sceau 8 (Muluc / Lune), ton 12 — 2 et 12 somment à 14.
        assertEquals(8, r.antipode!!.glyph)
        assertEquals(11, r.antipode.tone)
        assertEquals(14, (k.tone + 1) + (r.antipode.tone + 1))
        // Alternance : même sceau 8, ton 2 — le nôtre.
        assertEquals(8, r.analogue!!.glyph)
        assertEquals(1, r.analogue.tone)
        // Occulte : 261 − 119 = 142.
        assertEquals(142, r.occult!!.kin)
        // Guide : famille 18 % 4 = 2 (Bleue), position (2−1) % 5 = 1,
        // donc sceau 2 + 4 = 6 (Manik / Main), ton inchangé → KIN 67.
        assertEquals(67, r.guide!!.kin)
        assertEquals(6, r.guide.glyph)
        assertEquals(1, r.guide.tone)
    }

    @Test
    fun `l'oracle du KIN 83, celui du Pixel`() {
        // Akbal ton 5. ⚠ Les deux Ben ont ÉCHANGÉ leurs noms le 16/08 : l'écran
        // du Pixel montrait « ⚡ Ben 213 · 🌀 Ben 113 », c'est l'inverse.
        val r = Oracle.of(KinMaya.ofNumber(83)!!)
        assertEquals(113, r.antipode!!.kin) // sceau 12 (Ben), ton 9 = 14 − 5
        assertEquals(213, r.analogue!!.kin) // sceau 12 (Ben), ton 5 — le nôtre
        assertEquals(178, r.occult!!.kin) // 261 − 83
        assertEquals(239, r.guide!!.kin) // famille 2, position 4 → sceau 18
    }
}
