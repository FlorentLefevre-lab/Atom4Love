package one.astroport.atom4love.chat.net

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Le ramassage d'un groupe Wi-Fi Direct laissé ouvert. Il faut une vraie radio :
 * `WifiP2pManager` n'existe pas sur la JVM, et c'est justement la survie du
 * groupe au-delà de l'application qui est en cause.
 */
@RunWith(AndroidJUnit4::class)
class P2pGroupTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun grantNearby() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        P2pGroup.RUNTIME_PERMISSIONS.forEach { permission ->
            runCatching {
                instrumentation.uiAutomation.grantRuntimePermission(
                    context.packageName,
                    permission,
                )
            }
        }
        assumeTrue(
            "permission Wi-Fi Direct refusée par l'appareil",
            P2pGroup.permissionsGranted(context),
        )
    }

    @After
    fun tearDown() = runBlocking {
        // qu'un échec en cours de route ne laisse pas le banc avec un groupe :
        // ce qu'on a tracé part par reclaim, le reste en le reprenant d'abord
        // (host adopte un groupe dont on est propriétaire) pour le refermer.
        val cabin = P2pGroup(context)
        cabin.reclaim()
        if (cabin.currentGroupName() != null && cabin.host() != null) cabin.release()
        Unit
    }

    /**
     * Le scénario tel qu'il arrive : une cabine ouvre un groupe, l'application
     * meurt sans passer par `stop()`, et le lancement suivant part d'une
     * instance neuve — qui n'a plus que la trace sur le disque pour savoir
     * qu'il y a quelque chose à refermer.
     */
    @Test
    fun un_groupe_laisse_ouvert_est_referme_au_lancement_suivant() = runBlocking {
        val cabin = P2pGroup(context)
        assumeTrue("appareil sans Wi-Fi Direct utilisable", cabin.usable())

        val credentials = cabin.host()
        assumeTrue("le groupe n'a pas pu s'ouvrir sur cet appareil", credentials != null)
        assertNotNull(cabin.network())

        // l'instance qui a ouvert le groupe disparaît sans release() : c'est
        // exactement ce que fait un balayage dans les récents
        val afterRestart = P2pGroup(context)
        assertTrue("le groupe aurait dû être refermé", afterRestart.reclaim())

        // le constater sur l'appareil, pas seulement dans notre trace
        assertNull(P2pGroup(context).currentGroupName())
        // et une fois refermé, il n'y a plus rien à reprendre
        assertFalse(P2pGroup(context).reclaim())
    }

    /**
     * Une fermeture propre emporte vraiment le groupe. Le piège, vu au banc le
     * 2026-08-12 : `release()` effaçait sa trace et rendait la main alors que
     * le retrait n'avait pas abouti — plus rien ne disait qu'un réseau restait
     * ouvert. Vérifier la trace ne suffit donc pas, il faut regarder la radio.
     */
    @Test
    fun une_fermeture_propre_emporte_le_groupe() = runBlocking {
        val cabin = P2pGroup(context)
        assumeTrue("appareil sans Wi-Fi Direct utilisable", cabin.usable())
        val credentials = cabin.host()
        assumeTrue("le groupe n'a pas pu s'ouvrir sur cet appareil", credentials != null)

        cabin.release()

        val gone = withTimeoutOrNull(RELEASE_TIMEOUT_MS) {
            while (P2pGroup(context).currentGroupName() != null) delay(200)
            true
        }
        assertTrue("le groupe ${credentials?.networkName} a survécu à sa fermeture", gone == true)
        assertFalse(P2pGroup(context).reclaim())
    }

    private companion object {
        /** Le retrait passe par le système : quelques centaines de ms en pratique. */
        const val RELEASE_TIMEOUT_MS = 10_000L
    }
}
