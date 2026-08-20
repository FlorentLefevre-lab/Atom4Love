package one.astroport.atom4love

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import one.astroport.atom4love.ui.AppVisibility
import one.astroport.atom4love.ui.A4LApp
import one.astroport.atom4love.ui.ChatHost
import one.astroport.atom4love.ui.AppTheme
import one.astroport.atom4love.ui.theme.A4L
import one.astroport.atom4love.ui.theme.A4LDark
import one.astroport.atom4love.ui.theme.A4LLight
import one.astroport.atom4love.ui.theme.Atom4LoveTheme

class MainActivity : ComponentActivity() {

    /**
     * ⚠ **C'est l'activité qui dit si l'application est sous les yeux, et non
     * la composition.**
     *
     * L'écran le savait déjà, par `repeatOnLifecycle` — mais ce qu'il en
     * faisait mourait avec lui : la notification de message se décidait dans la
     * composition, qui est en pause exactement quand il faudrait prévenir.
     * `onStart`/`onStop` sont les deux seuls endroits qui parlent encore à ce
     * moment-là, et [ChatHost] vit assez longtemps pour les entendre.
     *
     * ⚠ Même magasin de ViewModels que `viewModel()` côté composition : c'est
     * bien la même instance des deux côtés, et il ne faut pas en fabriquer une
     * seconde ici.
     */
    private val chatHost: ChatHost by viewModels()

    override fun onStart() {
        super.onStart()
        chatHost.foreground(true)
        // ⚠ Deux destinataires, deux durées de vie : `chatHost` meurt avec
        // l'activité, [AppVisibility] est lu par la balise, qui lui survit.
        AppVisibility.onScreen = true
    }

    override fun onStop() {
        super.onStop()
        chatHost.foreground(false)
        AppVisibility.onScreen = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // La préférence se lit d'un bloc, avant le premier frame : ouvrir dans
        // la nuit pour basculer au jour ensuite serait un clignotement.
        AppTheme.load(this)
        setContent {
            val dark = AppTheme.dark
            // Les barres système ne sont pas dans la composition : on les
            // repeint à chaque bascule, avec la palette nommée puisqu'il n'y a
            // pas de thème là où cet appel a lieu.
            LaunchedEffect(dark) {
                if (dark) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.dark(A4LDark.void.toArgb()),
                        navigationBarStyle = SystemBarStyle.dark(A4LDark.void.toArgb()),
                    )
                } else {
                    // en clair, les icônes doivent virer au noir : c'est ce que
                    // `light` demande, avec son voile de repli pour les vieux
                    // Android qui ne savent pas assombrir les leurs
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.light(
                            A4LLight.void.toArgb(),
                            A4LDark.void.toArgb(),
                        ),
                        navigationBarStyle = SystemBarStyle.light(
                            A4LLight.void.toArgb(),
                            A4LDark.void.toArgb(),
                        ),
                    )
                }
            }
            Atom4LoveTheme(dark = dark) {
                A4LApp(Modifier.fillMaxSize().background(A4L.Deep))
            }
        }
    }
}
