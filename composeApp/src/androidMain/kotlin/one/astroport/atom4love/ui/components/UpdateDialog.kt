package one.astroport.atom4love.ui.components

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import java.io.File
import kotlinx.coroutines.launch
import one.astroport.atom4love.BuildConfig
import one.astroport.atom4love.R
import one.astroport.atom4love.update.ApkInstall
import one.astroport.atom4love.update.ChecksumMismatch
import one.astroport.atom4love.update.UpdateManifest
import one.astroport.atom4love.update.UpdateService
import one.astroport.atom4love.ui.theme.A4L
import one.astroport.atom4love.ui.theme.A4LText

/**
 * La mise à jour, de bout en bout, dans une seule bulle.
 *
 * Le parcours ne saute aucune étape et n'en cache aucune : on cherche, on
 * annonce ce qu'on a trouvé, on télécharge en montrant l'avancée, on vérifie
 * l'empreinte, et **c'est le système qui installe** — jamais nous. Chaque
 * passage demande un geste ; rien ne s'enchaîne tout seul.
 *
 * ⚠ L'autorisation d'installer des applications ne se demande pas comme les
 * autres : elle vit dans les réglages du système, pas dans une fenêtre qu'on
 * ouvre. On y conduit, on ne l'arrache pas.
 */
@Composable
fun UpdateDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val service = remember { UpdateService.forApp(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf<UpdateStep>(UpdateStep.Checking) }
    var progress by remember { mutableFloatStateOf(0f) }

    // Un APK vérifié attend l'installeur : on ne le lance qu'une fois
    // l'autorisation donnée, sinon le système ouvre une fenêtre vide.
    fun handOver(apk: File) {
        runCatching { context.startActivity(ApkInstall.installIntent(context, apk)) }
            .onFailure { step = UpdateStep.Failed(corrupted = false) }
    }

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        // Le résultat de cet écran-là ne dit rien d'utile : c'est l'état de
        // l'autorisation qu'il faut relire, pas le code de retour.
        val ready = step as? UpdateStep.Ready ?: return@rememberLauncherForActivityResult
        if (ApkInstall.allowed(context)) handOver(ready.apk)
    }

    LaunchedEffect(Unit) {
        val manifest = service.latest()
        step = when {
            manifest == null -> UpdateStep.Offline
            !manifest.isWorthOffering() -> {
                // Rien à proposer : si un APK traînait dans le cache, c'est
                // qu'il a été installé (ou abandonné). Trente mégaoctets ne
                // dorment pas là pour rien.
                service.forget()
                UpdateStep.UpToDate
            }
            else -> UpdateStep.Available(manifest)
        }
    }

    fun download(manifest: UpdateManifest) {
        progress = 0f
        step = UpdateStep.Downloading(manifest)
        scope.launch {
            val result = service.download(manifest) { progress = it }
            step = result.fold(
                onSuccess = { UpdateStep.Ready(manifest, it) },
                onFailure = { UpdateStep.Failed(corrupted = it is ChecksumMismatch) },
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_title), style = A4LText.Title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (val current = step) {
                    UpdateStep.Checking -> Line(R.string.update_checking, A4L.TextMuted)

                    UpdateStep.UpToDate -> {
                        Line(R.string.update_uptodate, A4L.Green)
                        Text(
                            stringResource(
                                R.string.settings_build,
                                BuildConfig.VERSION_NAME,
                                BuildConfig.VERSION_CODE,
                            ),
                            style = A4LText.Data,
                            color = A4L.TextMuted,
                        )
                    }

                    UpdateStep.Offline -> Line(R.string.update_offline, A4L.Amber)

                    is UpdateStep.Available -> {
                        Text(
                            stringResource(
                                R.string.update_available,
                                current.manifest.versionName,
                            ),
                            style = A4LText.Data.copy(fontWeight = FontWeight.SemiBold),
                            color = A4L.Cyan,
                        )
                        if (current.manifest.notes.isNotBlank()) {
                            Text(
                                current.manifest.notes,
                                style = A4LText.Body,
                                color = A4L.TextBody,
                            )
                        }
                        Text(
                            stringResource(R.string.update_weight, megabytes(current.manifest)),
                            style = A4LText.Caption,
                            color = A4L.TextMuted,
                        )
                    }

                    is UpdateStep.Downloading -> {
                        Line(R.string.update_downloading, A4L.TextBody)
                        // La barre est indéterminée quand la source ne dit pas
                        // son poids : une barre figée à 0 % ferait croire à un
                        // téléchargement bloqué.
                        if (progress >= 0f) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(),
                                color = A4L.Green,
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = A4L.Green,
                            )
                        }
                    }

                    is UpdateStep.Ready ->
                        if (ApkInstall.allowed(context)) {
                            Line(R.string.update_ready, A4L.Green)
                        } else {
                            Line(R.string.update_permission_needed, A4L.Amber)
                        }

                    is UpdateStep.Failed ->
                        if (current.corrupted) {
                            Line(R.string.update_corrupt, A4L.Red)
                        } else {
                            Line(R.string.update_failed, A4L.Amber)
                        }
                }
            }
        },
        confirmButton = {
            when (val current = step) {
                is UpdateStep.Available -> TextButton(onClick = { download(current.manifest) }) {
                    Text(stringResource(R.string.update_download), color = A4L.Green)
                }

                is UpdateStep.Ready -> TextButton(onClick = {
                    if (ApkInstall.allowed(context)) {
                        handOver(current.apk)
                    } else {
                        permission.launch(ApkInstall.permissionIntent(context))
                    }
                }) {
                    Text(
                        stringResource(
                            if (ApkInstall.allowed(context)) {
                                R.string.update_install
                            } else {
                                R.string.update_permission_open
                            },
                        ),
                        color = A4L.Green,
                    )
                }

                // Ni manifeste, ni téléchargement : il reste la page des
                // versions, à faire à la main. C'est le seul chemin qui ne
                // dépend de rien de ce qui vient d'échouer.
                UpdateStep.Offline, is UpdateStep.Failed -> TextButton(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, BuildConfig.RELEASES_URL.toUri()),
                        )
                    }
                }) {
                    Text(stringResource(R.string.update_open_page), color = A4L.Cyan)
                }

                else -> Unit
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_close), color = A4L.TextBody)
            }
        },
    )
}

@Composable
private fun Line(text: Int, color: androidx.compose.ui.graphics.Color) {
    Text(stringResource(text), style = A4LText.Body, color = color)
}

/** Le poids annoncé, en mégaoctets — « 27 » se lit, « 28311552 » non. */
private fun megabytes(manifest: UpdateManifest): Int =
    ((manifest.sizeBytes + 524_288L) / 1_048_576L).toInt()

/** Là où en est la mise à jour. Un état, un écran, pas de demi-teinte. */
private sealed interface UpdateStep {
    /** On interroge le dépôt et son miroir. */
    data object Checking : UpdateStep

    /** Ni l'un ni l'autre n'a répondu. */
    data object Offline : UpdateStep

    /** Le manifeste est là, et il ne dit rien de plus récent. */
    data object UpToDate : UpdateStep

    data class Available(val manifest: UpdateManifest) : UpdateStep

    data class Downloading(val manifest: UpdateManifest) : UpdateStep

    /** Téléchargé ET vérifié — sans quoi on n'arrive jamais ici. */
    data class Ready(val manifest: UpdateManifest, val apk: File) : UpdateStep

    /** [corrupted] : l'empreinte n'y était pas, ce qui ne se retente pas. */
    data class Failed(val corrupted: Boolean) : UpdateStep
}
