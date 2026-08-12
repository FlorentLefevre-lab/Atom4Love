package one.astroport.atom4love.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Le coffre sur appareil réel — l'Android Keystore n'existe pas sur la JVM,
 * ces vérifications-là ne peuvent se faire qu'ici.
 */
@RunWith(AndroidJUnit4::class)
class DeviceVaultTest {

    private val secret = """{"nsec":"nsec1exemple","pass":"1234"}"""

    @Test
    fun sceller_puis_ouvrir_rend_le_texte_intact() {
        assertEquals(secret, DeviceVault.open(DeviceVault.seal(secret)))
    }

    /** Deux scellements du même texte ne doivent jamais se ressembler :
     *  c'est le vecteur d'initialisation tiré au sort qui le garantit. */
    @Test
    fun deux_scellements_du_meme_texte_different() {
        assertNotEquals(DeviceVault.seal(secret), DeviceVault.seal(secret))
    }

    /** GCM authentifie : un octet retourné doit faire échouer l'ouverture,
     *  jamais rendre un contenu approximatif. */
    @Test
    fun un_message_altere_ne_s_ouvre_pas() {
        val sealed = DeviceVault.seal(secret)
        val tampered = sealed.toCharArray().also { chars ->
            val i = chars.lastIndex - 3
            chars[i] = if (chars[i] == 'A') 'B' else 'A'
        }.concatToString()
        assertNull(DeviceVault.open(tampered))
    }

    @Test
    fun un_message_tronque_ne_s_ouvre_pas() {
        assertNull(DeviceVault.open("AAAA"))
    }
}
