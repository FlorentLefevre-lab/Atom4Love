package one.astroport.atom4love.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import one.astroport.atom4love.R
import one.astroport.atom4love.chat.Attachments
import one.astroport.atom4love.ui.theme.A4L
import one.astroport.atom4love.ui.theme.A4LText

/**
 * **L'appareil photo du selfie de reconnaissance — le nôtre.**
 *
 * ## Pourquoi nous et pas celui du système
 *
 * `ACTION_IMAGE_CAPTURE` délègue à une autre application : on lui donne une
 * destination, elle rend une photo, et **on ne peut rien dessiner sur son
 * écran**. Or le cadre de visée est ici la moitié du geste — la lanterne
 * d'en face rogne un cercle, et cadrer à l'aveugle donne des plafonds et des
 * murs (mes trois premiers essais du 20/08). Demandé par Florent : « est-il
 * possible de mettre un masque pour centrer le visage ? » Oui, à cette
 * condition-là.
 *
 * Ce qu'on gagne en le tenant : **le cercle affiché EST celui que l'autre
 * verra**, la caméra frontale s'ouvre d'elle-même, et la capture est déjà
 * petite — plus de photo de quatre mégaoctets à recompresser.
 *
 * ⚠ La permission `CAMERA` n'est demandée qu'ici, au moment du geste. Une
 * application de rencontre qui réclame l'appareil photo au démarrage a déjà
 * perdu.
 */
@Composable
fun SelfieCamera(
    onCancel: () -> Unit,
    onTaken: (Uri) -> Unit,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(cameraGranted(context)) }
    val ask = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { ok ->
        granted = ok
        if (!ok) onCancel()
    }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (granted) Viewfinder(onCancel = onCancel, onTaken = onTaken)
        // Sans la permission, l'écran reste noir une fraction de seconde, le
        // temps du dialogue système — puis `onCancel` referme si l'on refuse.
    }
}

private fun cameraGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

@Composable
private fun Viewfinder(onCancel: () -> Unit, onTaken: (Uri) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_FRONT) }
    var taking by remember { mutableStateOf(false) }
    val capture = remember {
        ImageCapture.Builder()
            // Le déclencheur d'un selfie doit répondre au doigt : on préfère la
            // latence à la dernière goutte de qualité, que la recompression à
            // 448 px effacerait de toute façon.
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    val preview = remember { Preview.Builder().build() }
    val view = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    // ⚠ Le fournisseur arrive par un `ListenableFuture` : on l'attend sur le
    // fil principal, et on **délie tout** avant de relier — sans quoi changer
    // de caméra empile deux usages du même capteur.
    DisposableEffect(lensFacing) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching {
                val provider = future.get()
                provider.unbindAll()
                preview.surfaceProvider = view.surfaceProvider
                provider.bindToLifecycle(
                    owner,
                    CameraSelector.Builder().requireLensFacing(lensFacing).build(),
                    preview,
                    capture,
                )
            }.onFailure { Log.w("SelfieCamera", "caméra indisponible", it) }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        }
    }

    AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())

    // ── Le masque : tout est sombre, sauf le cercle ────────────────────────
    //
    // ⚠ Il n'est pas décoratif : **c'est exactement le disque que la lanterne
    // d'en face affichera**. Ce qui déborde sera rogné, et le savoir avant de
    // déclencher est toute la différence entre un visage et un plafond.
    // ⚠ La couleur se lit DANS la composition, jamais dans le `DrawScope` : la
    // palette passe par un CompositionLocal, et `A4L.Mint` est un accès
    // composable. Lu là-dedans, il ne compile pas — et c'est tant mieux.
    val ring = A4L.Mint.copy(alpha = 0.85f)
    Canvas(
        Modifier
            .fillMaxSize()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
    ) {
        // ⚠ Mesuré sur l'A5 (16:9, 1080×1600 utiles) : à 0,36 de large et
        // centré à 0,42 de haut, le bas du cercle tombait **sur le
        // déclencheur** — le bouton mordait le menton. Le cercle remonte et
        // maigrit un peu ; le déclencheur garde sa marge en bas.
        val radius = size.minDimension * 0.32f
        val center = Offset(size.width / 2f, size.height * 0.37f)
        drawRect(Color.Black.copy(alpha = 0.62f))
        drawCircle(Color.Transparent, radius = radius, center = center, blendMode = BlendMode.Clear)
        drawCircle(ring, radius = radius, center = center, style = Stroke(width = 2.dp.toPx()))
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.10f))
                    .clickable(onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) { Text("✕", fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f)) }
            Text(
                stringResource(R.string.selfie_aim),
                style = A4LText.Body,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.10f))
                    .clickable {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                            CameraSelector.LENS_FACING_BACK
                        } else {
                            CameraSelector.LENS_FACING_FRONT
                        }
                    },
                contentAlignment = Alignment.Center,
            ) { Text("🔄", fontSize = 15.sp) }
        }

        Box(Modifier.weight(1f))

        Box(
            Modifier
                .padding(bottom = 44.dp)
                .size(74.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (taking) 0.35f else 0.92f))
                .border(3.dp, A4L.Mint.copy(alpha = 0.9f), CircleShape)
                .clickable(enabled = !taking) {
                    taking = true
                    val (file, uri) = Attachments.newPhoto(context)
                    val options = ImageCapture.OutputFileOptions.Builder(file).build()
                    capture.takePicture(
                        options,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                                onTaken(uri)
                            }

                            override fun onError(error: ImageCaptureException) {
                                Log.w("SelfieCamera", "capture impossible", error)
                                taking = false
                                onCancel()
                            }
                        },
                    )
                },
        )
    }
}
