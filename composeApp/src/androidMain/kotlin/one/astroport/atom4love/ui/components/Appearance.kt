package one.astroport.atom4love.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import one.astroport.atom4love.R
import one.astroport.atom4love.ui.AppLanguage
import one.astroport.atom4love.ui.AppLocale
import one.astroport.atom4love.ui.AppTheme
import one.astroport.atom4love.ui.theme.A4L
import one.astroport.atom4love.ui.theme.A4LText
import one.astroport.atom4love.ui.theme.tint

/**
 * Le choix de langue, en lignes plutôt qu'en drapeaux serrés.
 *
 * Il vit à deux endroits, et c'est voulu : à la **première étape de la forge**,
 * parce qu'on ne remplit pas une fiche d'état civil dans une langue qu'on ne lit
 * pas, et dans les **Réglages** ensuite, parce qu'on change d'avis.
 *
 * Le nom reste l'endonyme — on cherche sa propre langue dans une liste, pas la
 * traduction de son nom. La ligne cochée est celle **réellement affichée** : au
 * premier lancement c'est celle du téléphone, résolue par Android, et il n'y a
 * rien à deviner. Une langue de téléphone qu'on ne parle pas retombe sur le
 * français, la langue source — d'où l'intérêt de poser la question tôt.
 */
@Composable
fun LanguageChoice(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val current = AppLocale.shown(LocalResources.current.configuration.locales)

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!AppLocale.selectable) {
            // En dessous d'Android 13 le choix par application n'existe pas :
            // mieux vaut dire pourquoi que montrer trois lignes sans effet.
            Text(
                stringResource(R.string.language_follows_phone),
                style = A4LText.Caption,
                color = A4L.TextMuted,
            )
            return@Column
        }
        AppLanguage.entries.forEach { language ->
            val active = language == current
            val accent = if (active) A4L.Cyan else A4L.TextBody
            Row(
                Modifier
                    .fillMaxWidth()
                    .glass(
                        radius = 12.dp,
                        background = if (active) A4L.Cyan.tint(0.10f) else A4L.GlassSoft,
                        border = if (active) A4L.Cyan.tint(0.34f) else A4L.StrokeSoft,
                    )
                    .clickable(enabled = !active) { AppLocale.choose(context, language) }
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(language.flag, fontSize = 18.sp)
                Spacer(Modifier.width(12.dp))
                Text(
                    language.endonym,
                    style = A4LText.Body,
                    fontWeight = if (active) FontWeight.SemiBold else null,
                    color = accent,
                    modifier = Modifier.weight(1f),
                )
                if (active) Text("●", style = A4LText.Data.copy(fontSize = 10.sp), color = A4L.Cyan)
            }
        }
    }
}

/**
 * Le jour et la nuit, en deux lignes du même dessin que les langues.
 *
 * L'interrupteur compact de l'en-tête montrait **ce qu'il proposait** ; ici on
 * montre les deux états et lequel est en cours — c'est ce qu'attend un écran de
 * réglages, où l'on vient lire autant que changer.
 */
@Composable
fun ThemeChoice(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dark = A4L.IsDark

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(false to R.string.theme_light, true to R.string.theme_dark).forEach { (wantsDark, labelRes) ->
            val active = wantsDark == dark
            Row(
                Modifier
                    .fillMaxWidth()
                    .glass(
                        radius = 12.dp,
                        background = if (active) A4L.Cyan.tint(0.10f) else A4L.GlassSoft,
                        border = if (active) A4L.Cyan.tint(0.34f) else A4L.StrokeSoft,
                    )
                    .clickable(enabled = !active) { AppTheme.toggle(context) }
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // U+FE0E force la présentation texte : sans lui, la police rend
                // le soleil en emoji coloré et la lune en glyphe, deux registres
                // pour un seul couple.
                Text(
                    if (wantsDark) "☾︎" else "☀︎",
                    style = A4LText.Data.copy(fontSize = if (wantsDark) 15.sp else 18.sp),
                    color = if (wantsDark) A4L.TextStrong else A4L.Amber,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(labelRes),
                    style = A4LText.Body,
                    fontWeight = if (active) FontWeight.SemiBold else null,
                    color = if (active) A4L.Cyan else A4L.TextBody,
                    modifier = Modifier.weight(1f),
                )
                if (active) Text("●", style = A4LText.Data.copy(fontSize = 10.sp), color = A4L.Cyan)
            }
        }
    }
}
