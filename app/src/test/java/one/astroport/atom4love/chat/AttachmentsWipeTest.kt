package one.astroport.atom4love.chat

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * « Tout s'efface en fermant » — ce que la cabine promet à qui y parle.
 *
 * La conversation partait bien avec l'instance, mais les fichiers restaient :
 * treize pièces relevées sur la tablette du banc le 13/08, la plus ancienne
 * datant de la veille. Ces tests tiennent la porte fermée.
 */
class AttachmentsWipeTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun contextOn(filesDir: File): Context =
        mockk<Context>().also { every { it.filesDir } returns filesDir }

    private fun chatDir(root: File) = File(root, "chat").apply { mkdirs() }

    @Test
    fun `la cabine fermee ne laisse aucune piece derriere elle`() {
        val root = temp.newFolder()
        val chat = chatDir(root)
        File(chat, "abc-photo.jpg").writeBytes(ByteArray(64))
        File(chat, "def-note.pdf").writeBytes(ByteArray(128))
        File(chat, "ghi-bip.mp3").writeBytes(ByteArray(32))

        assertEquals(3, Attachments.wipe(contextOn(root)))
        assertEquals(0, chat.listFiles()!!.size)
    }

    /** Le dossier survit à son contenu : un transfert qui démarre le retrouve. */
    @Test
    fun `le dossier lui-meme reste en place`() {
        val root = temp.newFolder()
        val chat = chatDir(root)
        File(chat, "abc-photo.jpg").writeBytes(ByteArray(8))

        Attachments.wipe(contextOn(root))

        assertTrue(chat.isDirectory)
    }

    /**
     * Le ramassage au démarrage tombe sur une application qui n'a jamais ouvert
     * de cabine : il n'y a pas de dossier, et ce n'est pas une anomalie.
     */
    @Test
    fun `sans cabine passee il n'y a rien a effacer`() {
        val root = temp.newFolder()

        assertEquals(0, Attachments.wipe(contextOn(root)))
    }

    /**
     * Ce que quelqu'un a choisi de garder lui appartient. La copie vers
     * Téléchargements vit hors de files/chat/ — la fermeture ne doit pas la
     * suivre, sinon sortir de la cabine deviendrait un piège.
     */
    @Test
    fun `ce qui vit hors du dossier n'est pas emporte`() {
        val root = temp.newFolder()
        chatDir(root).also { File(it, "abc-photo.jpg").writeBytes(ByteArray(8)) }
        val garde = File(root, "telechargements-photo.jpg").apply { writeBytes(ByteArray(8)) }
        val voisin = File(root, "datastore").apply { mkdirs() }

        assertEquals(1, Attachments.wipe(contextOn(root)))
        assertTrue(garde.exists())
        assertTrue(voisin.isDirectory)
    }

    /** Une cabine à trois médiums peut ranger des sous-dossiers un jour. */
    @Test
    fun `un sous-dossier part avec le reste`() {
        val root = temp.newFolder()
        val chat = chatDir(root)
        File(chat, "partiels").apply { mkdirs() }
            .also { File(it, "en-cours.tmp").writeBytes(ByteArray(16)) }

        assertEquals(1, Attachments.wipe(contextOn(root)))
        assertFalse(File(chat, "partiels").exists())
    }
}
