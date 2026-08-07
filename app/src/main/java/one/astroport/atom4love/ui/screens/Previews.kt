package one.astroport.atom4love.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import one.astroport.atom4love.domain.BirthData
import one.astroport.atom4love.ui.theme.Atom4LoveTheme

/** Cadre de la maquette : 428 × 908, densité d'un grand téléphone. */
private const val A4L_DEVICE = "spec:width=428dp,height=908dp,dpi=440"

@Preview(name = "01 Incarnation", device = A4L_DEVICE, showBackground = true)
@Composable
fun IncarnationPreview() {
    Atom4LoveTheme {
        var birth by remember { mutableStateOf(BirthData.Sample) }
        IncarnationScreen(
            birth = birth,
            onBirthChange = { birth = it },
            forged = false,
            onForge = {},
        )
    }
}

@Preview(name = "02 Radar", device = A4L_DEVICE, showBackground = true)
@Composable
fun RadarPreview() {
    Atom4LoveTheme { RadarScreen() }
}

@Preview(name = "03 Plateau", device = A4L_DEVICE, showBackground = true)
@Composable
fun BoardPreview() {
    Atom4LoveTheme { BoardScreen() }
}

@Preview(name = "04 Résonance", device = A4L_DEVICE, showBackground = true)
@Composable
fun ResonancePreview() {
    Atom4LoveTheme { ResonanceScreen() }
}
