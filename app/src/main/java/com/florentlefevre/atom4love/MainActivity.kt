package com.florentlefevre.atom4love

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import com.florentlefevre.atom4love.ui.A4LApp
import com.florentlefevre.atom4love.ui.theme.A4L
import com.florentlefevre.atom4love.ui.theme.Atom4LoveTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // La station est toujours sombre : barres transparentes, icônes claires.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(A4L.Void.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(A4L.Void.toArgb()),
        )
        setContent {
            Atom4LoveTheme {
                A4LApp(Modifier.fillMaxSize().background(A4L.Deep))
            }
        }
    }
}
