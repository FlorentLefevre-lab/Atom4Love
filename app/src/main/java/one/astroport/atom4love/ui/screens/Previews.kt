package one.astroport.atom4love.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import one.astroport.atom4love.domain.BirthData
import one.astroport.atom4love.multipass.Enrollment
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

/** Le noyau scellé — l'onglet Noyau, avec la porte vers Astroport.ONE. */
@Preview(name = "01b Noyau scellé", device = A4L_DEVICE, showBackground = true)
@Composable
fun SealedNucleusPreview() {
    Atom4LoveTheme {
        IncarnationScreen(
            birth = BirthData.Sample,
            onBirthChange = {},
            forged = true,
            onForge = {},
            npub = "npub1q4v8w2f0s5x7k3m9p6r1t8y4u2i7o5a3d1f9g6h2j4k8l0z7c5x3v",
            onDissolve = {},
            onMultipass = {},
        )
    }
}

/** L'assistant d'inscription, écran d'ouverture. */
@Preview(name = "06 MULTIPASS", device = A4L_DEVICE, showBackground = true)
@Composable
fun MultipassPreview() {
    Atom4LoveTheme {
        MultipassScreen(
            step = Enrollment.Step.Idle,
            account = null,
            onSubmit = { _, _ -> },
            onRetryActivation = {},
            onReset = {},
            onClose = {},
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

