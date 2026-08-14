package one.astroport.atom4love.nostr

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * scrypt contre les vecteurs de la RFC 7914.
 *
 * Une fonction de dérivation écrite à la main ne vaut que par ces vecteurs :
 * une erreur d'un bit dans Salsa20 ou dans le ré-entrelacement de BlockMix
 * produit un résultat parfaitement déterministe, parfaitement faux, et qui
 * ressemble à du hasard. C'est le seul contrôle qui distingue les deux.
 */
class ScryptTest {

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    /** RFC 7914 §12 — scrypt("", "", N=16, r=1, p=1, dkLen=64). */
    @Test
    fun `vecteur RFC 7914 numero 1`() {
        assertEquals(
            "77d6576238657b203b19ca42c18a0497f16b4844e3074ae8dfdffa3fede21442" +
                "fcd0069ded0948f8326a753a0fc81f17e8d3e0fb2e0d3628cf35e20c38d18906",
            hex(Scrypt.generate(ByteArray(0), ByteArray(0), n = 16, r = 1, p = 1, dkLen = 64)),
        )
    }

    /**
     * RFC 7914 §12 — scrypt("password", "NaCl", N=1024, r=8, p=16, dkLen=64).
     * Celui-ci exerce `p > 1`, que Duniter n'utilise pas mais que la fonction
     * doit tenir : les `p` blocs se dérivent indépendamment avant le second
     * PBKDF2, et les intervertir passerait inaperçu sans ce vecteur.
     */
    @Test
    fun `vecteur RFC 7914 numero 2`() {
        assertEquals(
            "fdbabe1c9d3472007856e7190d01e9fe7c6ad7cbc8237830e77376634b373162" +
                "2eaf30d92e22a3886ff109279d9830dac727afb94a83ee6d8360cbdfa2cc0640",
            hex(
                Scrypt.generate(
                    "password".toByteArray(), "NaCl".toByteArray(),
                    n = 1024, r = 8, p = 16, dkLen = 64,
                ),
            ),
        )
    }

    /**
     * PBKDF2-HMAC-SHA256, RFC 7914 §11 : le premier appel de scrypt se fait à
     * une seule itération, et c'est celui qui étire le SALT de la station qui
     * en fait 600 000. Les deux passent par le même code.
     */
    @Test
    fun `PBKDF2-HMAC-SHA256 suit son vecteur`() {
        assertEquals(
            "55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc" +
                "49ca9cccf179b645991664b39d77ef317c71b845b1e30bd509112041d3a19783",
            hex(
                Scrypt.pbkdf2(
                    "passwd".toByteArray(), "salt".toByteArray(),
                    iterations = 1, dkLen = 64,
                ),
            ),
        )
    }
}
