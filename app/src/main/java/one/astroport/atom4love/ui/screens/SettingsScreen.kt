package one.astroport.atom4love.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import one.astroport.atom4love.BuildConfig
import one.astroport.atom4love.R
import one.astroport.atom4love.ui.components.SectionLabel
import one.astroport.atom4love.ui.components.glass
import one.astroport.atom4love.ui.components.screenBackground
import one.astroport.atom4love.ui.theme.A4L
import one.astroport.atom4love.ui.theme.A4LText

/**
 * Réglages — ce qui appartient à la personne, pas à la station.
 *
 * L'écran n'a encore rien à régler : il ne porte que le numéro de version, et
 * **il est le seul à le porter**. Le pied de l'Aide l'affichait, ce qui n'a
 * jamais eu de sens — un numéro de build n'est pas une réponse à une question,
 * et deux endroits qui l'écrivent finissent par se contredire.
 *
 * C'est donc une place tenue, volontairement vide en attendant ce qu'on y
 * mettra. Rien n'y a été déménagé : le thème et la langue restent en haut,
 * la balise et la cabine sur la Carte, le MULTIPASS sur le Noyau. Ces
 * gestes-là engagent la radio ou l'identité — les enfouir dans un menu leur
 * ferait perdre ce qu'ils disent là où ils agissent.
 *
 * Il **a quitté la barre du bas** pour la poignée ⚙️ de l'en-tête : une place
 * tenue et vide n'est pas une destination, et une barre à six entrées se lisait
 * moins bien qu'elle ne menait quelque part.
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier, onClose: (() -> Unit)? = null) {
    Column(
        modifier
            .fillMaxSize()
            .screenBackground(A4L.GlowBond, A4L.DeepAlt, radiusFactor = 1.4f)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⚛", color = A4L.Cyan, fontSize = 13.sp)
                Spacer(Modifier.width(7.dp))
                Text(
                    "ATOM4LOVE",
                    style = A4LText.Data.copy(letterSpacing = 1.7.sp),
                    color = A4L.TextMuted,
                )
            }
            if (onClose != null) {
                Box(
                    Modifier
                        .size(30.dp)
                        .background(A4L.Glass, CircleShape)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Text("✕", fontSize = 13.sp, color = A4L.TextStrong) }
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                // Plus de barre de menus dessous : l'encoche du système est à
                // nous.
                .navigationBarsPadding(),
        ) {
            Text(
                stringResource(R.string.settings_title),
                style = A4LText.H1,
                color = A4L.TextHigh,
                modifier = Modifier.padding(top = 18.dp),
            )

            Column(
                Modifier.padding(top = 22.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                SectionLabel(
                    stringResource(R.string.settings_about_label),
                    modifier = Modifier.padding(start = 3.dp),
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .glass(14.dp)
                        .padding(horizontal = 15.dp, vertical = 15.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                        style = A4LText.Body,
                        color = A4L.TextHigh,
                    )
                    Text(
                        stringResource(R.string.settings_maker),
                        style = A4LText.Data,
                        color = A4L.TextGhost,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
