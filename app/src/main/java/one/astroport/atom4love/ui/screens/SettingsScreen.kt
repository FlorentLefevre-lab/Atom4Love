package one.astroport.atom4love.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import one.astroport.atom4love.BuildConfig
import one.astroport.atom4love.R
import one.astroport.atom4love.ui.components.LanguageChoice
import one.astroport.atom4love.ui.components.SectionLabel
import one.astroport.atom4love.ui.components.ThemeChoice
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
 * ⚠ Ce commentaire disait le contraire jusqu'au 15/08 : que rien n'y serait
 * déménagé, et que le thème et la langue resteraient dans l'en-tête. **Florent
 * a tranché l'inverse** — ce sont bien des réglages, et une ligne d'en-tête qui
 * les porte sur tous les écrans les met plus haut qu'ils ne valent.
 *
 * Ce qui n'y vient toujours pas : la balise et la cabine, sur la Carte, et le
 * MULTIPASS, sur le Noyau. Ces gestes-là engagent la radio ou l'identité — les
 * enfouir dans un menu leur ferait perdre ce qu'ils disent là où ils agissent.
 *
 * La langue se choisit **aussi** à la première étape de la forge : avant les
 * réglages il y a l'assistant, et on ne remplit pas une fiche dans une langue
 * qu'on ne lit pas.
 *
 * Il **a quitté la barre du bas** pour la poignée ⚙️ de l'en-tête : une place
 * tenue et vide n'est pas une destination, et une barre à six entrées se lisait
 * moins bien qu'elle ne menait quelque part.
 *
 * **La bulle de version, tout en bas, s'ouvre** : mettre à jour et désinstaller
 * partent de là, et de nulle part ailleurs. Un numéro de build est ce qu'on
 * regarde quand on se demande si on a la bonne version — les deux gestes qui
 * répondent à cette question se tiennent donc au même endroit qu'elle. Chacun
 * passe par sa propre confirmation ; la désinstallation en a trois, la dernière
 * étant celle du système, qui seul retire vraiment l'application.
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier, onClose: (() -> Unit)? = null) {
    // La bulle de version et ses deux gestes. Aucun des deux ne part sur un
    // simple appui : la bulle ouvre un choix, le choix ouvre une confirmation,
    // et la désinstallation en a même une troisième — celle du système.
    var showVersion by rememberSaveable { mutableStateOf(false) }
    var confirmUpdate by rememberSaveable { mutableStateOf(false) }
    var confirmUninstall by rememberSaveable { mutableStateOf(false) }

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
                Modifier.padding(top = 22.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                SectionLabel(
                    stringResource(R.string.settings_language_label),
                    modifier = Modifier.padding(start = 3.dp),
                )
                LanguageChoice()
            }

            Column(
                Modifier.padding(top = 22.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                SectionLabel(
                    stringResource(R.string.settings_theme_label),
                    modifier = Modifier.padding(start = 3.dp),
                )
                ThemeChoice()
            }

            // Tout en bas de la page, et la dernière chose qu'on y lit : la
            // version installée, et les deux gestes qui la font changer.
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
                        .clickable { showVersion = true }
                        .padding(horizontal = 15.dp, vertical = 15.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        stringResource(R.string.settings_version),
                        style = A4LText.Body.copy(fontWeight = FontWeight.SemiBold),
                        color = A4L.TextHigh,
                    )
                    // Le numéro de l'APK réellement posé sur l'appareil : le
                    // nom de version, qui porte le commit sur un build debug,
                    // et le versionCode qui départage deux APK de même nom.
                    Text(
                        stringResource(
                            R.string.settings_build,
                            BuildConfig.VERSION_NAME,
                            BuildConfig.VERSION_CODE,
                        ),
                        style = A4LText.Data,
                        color = A4L.Cyan,
                    )
                    Text(
                        buildStamp(),
                        style = A4LText.Data,
                        color = A4L.TextMuted,
                    )
                    Text(
                        stringResource(R.string.settings_maker),
                        style = A4LText.Data,
                        color = A4L.TextGhost,
                    )
                    // Une bulle qui s'ouvre le dit : sans ça, rien ne
                    // distingue cette carte des deux blocs au-dessus.
                    Text(
                        stringResource(R.string.settings_version_hint),
                        style = A4LText.Caption,
                        color = A4L.TextMuted,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    // ── La bulle ouverte : deux liens, et rien qui parte d'ici ───────────
    if (showVersion) {
        AlertDialog(
            onDismissRequest = { showVersion = false },
            title = { Text(stringResource(R.string.settings_version), style = A4LText.Title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        stringResource(
                            R.string.settings_build,
                            BuildConfig.VERSION_NAME,
                            BuildConfig.VERSION_CODE,
                        ),
                        style = A4LText.Data,
                        color = A4L.Cyan,
                    )
                    Text(
                        buildStamp(),
                        style = A4LText.Data,
                        color = A4L.TextMuted,
                    )
                    Text(
                        stringResource(R.string.settings_update_link),
                        style = A4LText.Body.copy(fontWeight = FontWeight.SemiBold),
                        color = A4L.Green,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showVersion = false
                                confirmUpdate = true
                            }
                            .padding(vertical = 6.dp),
                    )
                    Text(
                        stringResource(R.string.settings_uninstall_link),
                        style = A4LText.Body.copy(fontWeight = FontWeight.SemiBold),
                        color = A4L.Red,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showVersion = false
                                confirmUninstall = true
                            }
                            .padding(vertical = 6.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showVersion = false }) {
                    Text(stringResource(R.string.settings_close), color = A4L.TextBody)
                }
            },
        )
    }

    // ── Mettre à jour : la page s'ouvre, l'app ne s'installe pas seule ───
    if (confirmUpdate) {
        val context = LocalContext.current
        val noBrowser = stringResource(R.string.settings_update_unavailable)
        AlertDialog(
            onDismissRequest = { confirmUpdate = false },
            title = { Text(stringResource(R.string.settings_update_title), style = A4LText.Title) },
            text = {
                Text(
                    stringResource(R.string.settings_update_body),
                    style = A4LText.Body,
                    color = A4L.TextBody,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmUpdate = false
                    // Un appareil sans navigateur existe : mieux vaut le dire
                    // que laisser le geste tomber dans le vide.
                    runCatching { context.startActivity(releasesIntent()) }.onFailure {
                        Toast.makeText(context, noBrowser, Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text(stringResource(R.string.settings_update_confirm), color = A4L.Green)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmUpdate = false }) {
                    Text(stringResource(R.string.settings_cancel), color = A4L.TextBody)
                }
            },
        )
    }

    // ── Désinstaller : notre confirmation, puis celle du système ─────────
    if (confirmUninstall) {
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { confirmUninstall = false },
            title = {
                Text(stringResource(R.string.settings_uninstall_title), style = A4LText.Title)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(
                        stringResource(R.string.settings_uninstall_warning),
                        style = A4LText.Body,
                        color = A4L.TextBody,
                    )
                    Text(
                        stringResource(R.string.settings_uninstall_body),
                        style = A4LText.Caption,
                        color = A4L.Amber.copy(alpha = 0.85f),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmUninstall = false
                    runCatching { context.startActivity(uninstallIntent(context)) }
                }) {
                    Text(stringResource(R.string.settings_uninstall_confirm), color = A4L.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmUninstall = false }) {
                    Text(stringResource(R.string.settings_cancel), color = A4L.TextBody)
                }
            },
        )
    }
}

/**
 * L'instant de la compilation, écrit en toutes lettres : jour de la semaine,
 * date complète, heure à la seconde.
 *
 * Mis en forme ici et non dans le build : `BuildConfig` ne porte qu'un nombre
 * de millisecondes, donc la date se dit dans la langue choisie dans cet
 * écran-là — une chaîne figée à la compilation parlerait la langue de la
 * machine qui a compilé, ce qui n'est celle de personne.
 *
 * La seconde compte : deux APK d'une même minute existent quand on corrige à
 * la volée, et c'est précisément là qu'on se demande lequel tourne.
 */
@Composable
private fun buildStamp(): String {
    val locale = LocalConfiguration.current.locales[0]
    val stamp = remember(locale) {
        DateTimeFormatter
            .ofPattern("EEEE d MMMM yyyy '·' HH:mm:ss", locale)
            .format(
                Instant.ofEpochMilli(BuildConfig.BUILD_TIME_MS)
                    .atZone(ZoneId.systemDefault()),
            )
    }
    return stringResource(R.string.settings_built_on, stamp)
}

/**
 * La page des versions publiées, dans le navigateur. Atom4Love ne se met pas à
 * jour toute seule : l'AGPL s'accorde mal avec le Play Store, et installer un
 * APK depuis l'app demanderait `REQUEST_INSTALL_PACKAGES` — une permission qui
 * vaut bien plus que le service rendu. On ouvre la page, la personne décide.
 */
private fun releasesIntent(): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.RELEASES_URL))

/**
 * Le désinstalleur du système. `ACTION_DELETE` n'efface rien de lui-même : il
 * pose la question une seconde fois, hors de nous — c'est la seule main qui
 * peut vraiment retirer l'app, et il faut qu'elle soit visible.
 */
private fun uninstallIntent(context: Context): Intent =
    Intent(Intent.ACTION_DELETE, Uri.fromParts("package", context.packageName, null))
