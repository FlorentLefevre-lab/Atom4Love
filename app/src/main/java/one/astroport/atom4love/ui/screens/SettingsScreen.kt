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
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import one.astroport.atom4love.BuildConfig
import one.astroport.atom4love.R
import one.astroport.atom4love.domain.BodyMetrics
import one.astroport.atom4love.domain.LoveKey
import one.astroport.atom4love.ui.components.HeightWheels
import one.astroport.atom4love.ui.components.LanguageChoice
import one.astroport.atom4love.ui.components.SectionLabel
import one.astroport.atom4love.ui.components.ThemeChoice
import one.astroport.atom4love.ui.components.UpdateDialog
import one.astroport.atom4love.ui.components.WeightWheels
import one.astroport.atom4love.ui.components.glass
import one.astroport.atom4love.ui.components.screenBackground
import one.astroport.atom4love.ui.theme.A4L
import one.astroport.atom4love.ui.theme.A4LText
import one.astroport.atom4love.ui.theme.tint

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
 * ⚠ La langue se choisissait **aussi** à la première étape de la forge. Cette
 * étape n'existe plus depuis le 15/08 : Android a déjà résolu la langue au
 * premier lancement, l'assistant la suit et le dit dans un encart, et cet écran
 * est devenu le seul endroit où l'on en change. Le rouage de l'en-tête de
 * l'assistant y mène — sans lui, la promesse pointerait dans le vide.
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
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    /**
     * Le corps d'aujourd'hui. Il a rejoint cet écran le 15/08 : c'est la seule
     * donnée de la station qui change avec le temps sans rien casser, et
     * l'assistant promet désormais qu'on la met à jour ici. Il vivait sur le
     * Noyau, qui reste ce qu'on relit — pas ce qu'on règle.
     */
    body: BodyMetrics = BodyMetrics.Empty,
    onBodyChange: (BodyMetrics) -> Unit = {},
) {
    var showHeightPicker by rememberSaveable { mutableStateOf(false) }
    var showWeightPicker by rememberSaveable { mutableStateOf(false) }
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

            // Le corps d'aujourd'hui. Les deux seules mesures de la station qui
            // ont le droit de changer : elles n'entrent dans aucune clé, et
            // l'assistant renvoie ici en toutes lettres.
            Column(
                Modifier.padding(top = 22.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                SectionLabel(
                    stringResource(R.string.settings_body_label),
                    modifier = Modifier.padding(start = 3.dp),
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .glass(14.dp)
                        .padding(horizontal = 15.dp, vertical = 15.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        MeasureBox(
                            value = body.heightCm?.let {
                                stringResource(R.string.format_height, it)
                            } ?: stringResource(R.string.inc_height_placeholder),
                            unset = body.heightCm == null,
                            onClick = { showHeightPicker = true },
                        )
                        MeasureBox(
                            value = body.weightKg?.let {
                                LoveKey.formatWeight(LocalResources.current, it)
                            } ?: stringResource(R.string.inc_weight_placeholder),
                            unset = body.weightKg == null,
                            onClick = { showWeightPicker = true },
                        )
                    }
                    Text(
                        stringResource(R.string.settings_body_note),
                        style = A4LText.Caption,
                        color = A4L.TextMuted,
                    )
                }
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

    // ── Les deux rouleaux du corps ───────────────────────────────────────
    // Mêmes rouleaux, mêmes bornes et même troisième bouton qu'à la forge :
    // « effacer » retire une mesure, sans quoi une taille saisie par erreur
    // resterait pour toujours.
    if (showHeightPicker) {
        var picked by remember { mutableIntStateOf(body.heightCm ?: BodyMetrics.DEFAULT_HEIGHT_CM) }
        MeasureDialog(
            title = stringResource(R.string.inc_height_title),
            onConfirm = {
                onBodyChange(body.copy(heightCm = picked))
                showHeightPicker = false
            },
            onClear = {
                onBodyChange(body.copy(heightCm = null))
                showHeightPicker = false
            },
            onDismiss = { showHeightPicker = false },
        ) {
            HeightWheels(
                heightCm = picked,
                range = BodyMetrics.HEIGHT_RANGE_CM,
                onChange = { picked = it },
            )
        }
    }

    if (showWeightPicker) {
        var picked by remember { mutableStateOf(body.weightKg ?: BodyMetrics.DEFAULT_WEIGHT_KG) }
        MeasureDialog(
            title = stringResource(R.string.inc_weight_title),
            onConfirm = {
                onBodyChange(body.copy(weightKg = picked))
                showWeightPicker = false
            },
            onClear = {
                onBodyChange(body.copy(weightKg = null))
                showWeightPicker = false
            },
            onDismiss = { showWeightPicker = false },
        ) {
            WeightWheels(
                weightKg = picked,
                range = BodyMetrics.WEIGHT_RANGE_KG,
                onChange = { picked = it },
            )
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

    // ── Mettre à jour : chercher, vérifier, puis passer la main ──────────
    if (confirmUpdate) {
        UpdateDialog(onDismiss = { confirmUpdate = false })
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
 * Le désinstalleur du système. `ACTION_DELETE` n'efface rien de lui-même : il
 * pose la question une seconde fois, hors de nous — c'est la seule main qui
 * peut vraiment retirer l'app, et il faut qu'elle soit visible.
 */
private fun uninstallIntent(context: Context): Intent =
    Intent(Intent.ACTION_DELETE, Uri.fromParts("package", context.packageName, null))

/** Une mesure du corps, dans une case qu'on touche pour ouvrir son rouleau. */
@Composable
private fun RowScope.MeasureBox(value: String, unset: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .weight(1f)
            .height(46.dp)
            .glass(10.dp, A4L.Indigo.tint(if (unset) 0.04f else 0.08f), A4L.Indigo.tint(0.26f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            value,
            style = A4LText.Data.copy(fontSize = if (unset) 11.sp else 15.sp),
            color = if (unset) A4L.TextGhost else A4L.TextHigh,
        )
    }
}

/**
 * Le dialogue d'une mesure qu'on a le droit de retirer.
 *
 * Le troisième bouton est le point : sans lui, « effacer » n'existe pas et une
 * valeur fausse ne peut plus que se corriger, jamais s'enlever. C'est la même
 * forme qu'à la forge — les deux écrans montrent le même rouleau.
 */
@Composable
private fun MeasureDialog(
    title: String,
    onConfirm: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    wheels: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = A4LText.Title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Text(
                    stringResource(R.string.inc_body_dialog_body),
                    style = A4LText.Caption,
                    color = A4L.TextMuted,
                )
                wheels()
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.inc_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onClear) {
                Text(stringResource(R.string.inc_measure_clear), color = A4L.TextBody)
            }
        },
    )
}
