package one.astroport.atom4love.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.annotation.StringRes
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.util.Locale
import kotlin.math.round
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import one.astroport.atom4love.R
import one.astroport.atom4love.domain.BirthData
import one.astroport.atom4love.domain.BodyMetrics
import one.astroport.atom4love.domain.DateProblem
import one.astroport.atom4love.domain.GoldbergPortal
import one.astroport.atom4love.geo.CommuneApi
import one.astroport.atom4love.geo.PlaceResolver
import one.astroport.atom4love.nostr.RelayStation
import one.astroport.atom4love.domain.LoveKey
import one.astroport.atom4love.domain.Phi2X
import one.astroport.atom4love.domain.KinMaya
import one.astroport.atom4love.domain.Wave
import one.astroport.atom4love.ui.components.AtomLogo
import one.astroport.atom4love.ui.components.BirthDateWheels
import one.astroport.atom4love.ui.components.BirthTimeWheels
import one.astroport.atom4love.ui.components.ComputedRow
import one.astroport.atom4love.ui.components.HeightWheels
import one.astroport.atom4love.ui.components.WeightWheels
import one.astroport.atom4love.ui.components.SectionLabel
import one.astroport.atom4love.ui.components.StatusDot
import one.astroport.atom4love.ui.components.glass
import one.astroport.atom4love.ui.components.screenBackground
import one.astroport.atom4love.ui.theme.A4L
import one.astroport.atom4love.ui.theme.A4LText
import one.astroport.atom4love.ui.theme.tint

/**
 * 01 · Incarnation — la forge du noyau.
 *
 * Les données de naissance (date, heure facultative, lieu, onde, poids
 * facultatif) sont modifiables tant que la clé n'est pas forgée ; le SALT et la
 * date de conception se recalculent à chaque frappe. Une fois forgée, tout se
 * verrouille : c'est le contrat annoncé par l'avertissement ambre.
 *
 * Les mesures du corps ([body]) échappent à ce contrat, et c'est le seul
 * endroit de l'écran où deux régimes cohabitent : elles n'entrent dans aucune
 * clé, se modifient après le scellement, et ne servent qu'à ω_bio. L'étape du
 * vaisseau les tient donc dans un bloc séparé, de couleur différente, avec la
 * raison écrite dessous — la confusion des deux poids serait autrement
 * inévitable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncarnationScreen(
    birth: BirthData,
    onBirthChange: (BirthData) -> Unit,
    forged: Boolean,
    onForge: () -> Unit,
    modifier: Modifier = Modifier,
    body: BodyMetrics = BodyMetrics.Empty,
    onBodyChange: (BodyMetrics) -> Unit = {},
    npub: String? = null,
    onDissolve: (() -> Unit)? = null,
    relay: RelayStation.Status? = null,
    onHelp: (() -> Unit)? = null,
    /** La porte vers Astroport.ONE, en bas de l'écran : on peut y revenir
     *  quand on veut, y compris après avoir dit « plus tard ». */
    onMultipass: (() -> Unit)? = null,
    /** Le compte est ouvert et sa clé LOVE en place : le bouton change de rôle. */
    multipassActive: Boolean = false,
    /**
     * Le Plateau, qui a quitté la barre du bas faute de partie à jouer. Il entre
     * ici parce que c'est d'ici que vient sa carte — le noyau scellé.
     */
    onBoard: (() -> Unit)? = null,
) {
    // L'étape courante de l'assistant, et la plus lointaine qu'on ait le droit
    // d'atteindre : on revient où l'on veut, on n'avance que sur du renseigné.
    var step by rememberSaveable { mutableIntStateOf(0) }
    val furthest = remember(birth, step) {
        val firstOpen = ForgeStep.entries.indexOfFirst { !it.isSatisfied(birth) }
        maxOf(step, if (firstOpen < 0) ForgeStep.entries.lastIndex else firstOpen)
    }

    var showCoordsEditor by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showBirthWeightPicker by remember { mutableStateOf(false) }
    var showHeightPicker by remember { mutableStateOf(false) }
    var showWeightPicker by remember { mutableStateOf(false) }
    var showForgeConfirm by remember { mutableStateOf(false) }
    var showDissolveWarning by remember { mutableStateOf(false) }
    var showDissolveFinal by remember { mutableStateOf(false) }
    val editable = !forged

    // ── Lieu de naissance : autocomplétion data.gouv.fr + repérage GPS ────
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var locating by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<CommuneApi.Commune>>(emptyList()) }
    // true tant que le nom affiché ne vient pas d'une frappe (restauration,
    // sélection dans la liste, GPS) : pas de recherche dans ce cas.
    var placeSettled by remember { mutableStateOf(true) }
    // La fiche vue par les retours asynchrones (géocodage, GPS) : toujours la
    // version À JOUR. Sans cela, un résultat qui arrive après coup écraserait
    // les champs saisis entre-temps — l'heure notamment — avec sa copie périmée.
    val currentBirth by rememberUpdatedState(birth)

    // Anti-rebond : la liste des communes se rafraîchit 350 ms après la
    // dernière frappe, jamais pendant qu'on tape.
    LaunchedEffect(birth.placeName, placeSettled, editable) {
        if (!editable || placeSettled) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(350)
        suggestions = CommuneApi.search(birth.placeName)
    }

    fun chooseCommune(c: CommuneApi.Commune) {
        placeSettled = true
        suggestions = emptyList()
        focusManager.clearFocus()
        onBirthChange(currentBirth.copy(placeName = c.placeName, lat = c.lat, lon = c.lon))
    }

    // Repli hors France : géocodeur Android au « Terminé » du clavier.
    fun resolveTypedPlace() {
        val query = birth.placeName
        placeSettled = true
        scope.launch {
            PlaceResolver.search(context, query)?.let { p ->
                onBirthChange(currentBirth.copy(placeName = p.name, lat = p.lat, lon = p.lon))
            }
        }
    }

    // Position GPS → nom (bouton 📍).
    fun locateFromGps() {
        if (locating) return
        locating = true
        scope.launch {
            PlaceResolver.current(context)?.let { p ->
                placeSettled = true
                onBirthChange(currentBirth.copy(placeName = p.name, lat = p.lat, lon = p.lon))
            }
            locating = false
        }
    }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) locateFromGps() }

    Column(
        modifier
            .fillMaxSize()
            .screenBackground(A4L.GlowNucleus, A4L.DeepAlt, radiusFactor = 1.4f)
            .statusBarsPadding()
            .imePadding(),
    ) {

        // ── Barre d'état applicative ──────────────────────────────────────
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
            // L'état réel de l'antenne : vert dès qu'un relais répond,
            // éteint tant que le noyau n'est pas forgé ou que rien ne passe.
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(if (relay?.online == true) A4L.Green else A4L.TextGhost)
                Text(relay?.label ?: "relay · —", style = A4LText.Data, color = A4L.TextDim)
            }
        }

        // ── Le corps ──────────────────────────────────────────────────────
        if (forged) {
            SealedNucleus(
                birth = birth,
                npub = npub,
                onMultipass = onMultipass,
                multipassActive = multipassActive,
                onBoard = onBoard,
                onDissolve = { showDissolveWarning = true },
                modifier = Modifier.weight(1f),
                body = body,
                onPickHeight = { showHeightPicker = true },
                onPickWeight = { showWeightPicker = true },
            )
        } else {
            // L'assistant, calqué sur celui d'ATOM4LOVE : cinq stations, une
            // seule question à la fois, et rien à faire défiler pour la voir.
            val stepTitle = ForgeStep.entries[step]

            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(
                                R.string.forge_step_header,
                                stepTitle.glyph,
                                stringResource(stepTitle.titleRes),
                            ),
                            style = A4LText.H2,
                            color = A4L.TextHigh,
                        )
                        Text(
                            stringResource(stepTitle.subtitleRes),
                            style = A4LText.Caption,
                            color = A4L.TextMuted,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    // Avant la forge, la barre de menus n'existe pas encore :
                    // l'aide vit à côté du titre, là où le regard se pose.
                    if (onHelp != null) {
                        Box(
                            Modifier
                                .size(36.dp)
                                .glass(
                                    radius = 18.dp,
                                    background = A4L.Indigo.tint(0.10f),
                                    border = A4L.Indigo.tint(0.40f),
                                )
                                .clickable(onClick = onHelp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "?",
                                style = A4LText.Body.copy(
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = A4L.Indigo,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                // Revenir sur ses pas se fait d'un doigt ; avancer, non : on ne
                // saute pas une étape dont les données manquent encore.
                StepBar(current = step, reached = { it <= furthest }) { step = it }
            }

            // Le contenu d'une étape est taillé pour tenir : le défilement ici
            // n'est pas une page qui scrolle, c'est un filet — il ne s'active
            // que sur les petits écrans, clavier ouvert et liste de communes
            // déployée, là où la troncature serait pire.
            Box(Modifier.weight(1f)) {
                // Le logo en filigrane, dans le vide que laisse l'étape sous
                // elle — et il tourne : c'est l'atome vectoriel du splash
                // ([AtomLogo]), pas l'icône figée du launcher. Le GIF d'origine
                // n'aurait rien donné de mieux, Compose ne l'anime pas sans
                // décodeur tiers, et le dessin est le même.
                AtomLogo(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 28.dp)
                        .fillMaxWidth(0.55f)
                        .aspectRatio(1f)
                        .alpha(0.40f),
                )
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp),
                    // L'étape commence en haut : elle se lit du même endroit
                    // d'un écran à l'autre, au lieu de flotter à une hauteur
                    // qui dépend de sa longueur. `fillMaxSize` ne sert plus
                    // qu'au filet de défilement, qui s'active dès que le
                    // contenu déborde.
                    verticalArrangement = Arrangement.Top,
                ) {
                    when (ForgeStep.entries[step]) {
                        ForgeStep.Identity -> IdentityStep()

                        ForgeStep.Anchor -> Column(
                            verticalArrangement = Arrangement.spacedBy(13.dp),
                        ) {
                            BirthDateTimeSection(
                                birth = birth,
                                editable = true,
                                onPickDate = { showDatePicker = true },
                                onPickTime = { showTimePicker = true },
                            )
                            BirthPlaceSection(
                                birth = birth,
                                editable = true,
                                locating = locating,
                                suggestions = suggestions,
                                onNameChange = {
                                    placeSettled = false
                                    onBirthChange(birth.copy(placeName = it))
                                },
                                onChoose = ::chooseCommune,
                                onDone = {
                                    focusManager.clearFocus()
                                    resolveTypedPlace()
                                },
                                onLocate = {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.ACCESS_FINE_LOCATION,
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        locateFromGps()
                                    } else {
                                        locationPermission.launch(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                        )
                                    }
                                },
                                onEditCoords = { showCoordsEditor = true },
                            )
                            // L'avertissement qu'ATOM4LOVE porte à cette étape : ces
                            // coordonnées sont la seule chose à recopier quelque part.
                            birth.dateProblem()?.let { problem ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("⚠", fontSize = 12.sp, color = A4L.Red)
                                    Spacer(Modifier.width(9.dp))
                                    Text(
                                        stringResource(
                                            R.string.inc_date_problem,
                                            problemText(problem),
                                        ),
                                        style = A4LText.Caption,
                                        color = A4L.Red.copy(alpha = 0.9f),
                                    )
                                }
                            }
                            Text(
                                stringResource(R.string.inc_coords_warning),
                                style = A4LText.Caption,
                                color = A4L.Amber.copy(alpha = 0.75f),
                            )
                        }

                    ForgeStep.Vessel -> Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        WaveSection(birth = birth, editable = true, onBirthChange = onBirthChange)
                        BirthWeightSection(
                            birth = birth,
                            editable = true,
                            onPick = { showBirthWeightPicker = true },
                        )
                        Text(
                            stringResource(R.string.inc_vessel_note),
                            style = A4LText.Caption,
                            color = A4L.TextMuted,
                        )
                        BodySection(
                            body = body,
                            wave = birth.wave,
                            onPickHeight = { showHeightPicker = true },
                            onPickWeight = { showWeightPicker = true },
                        )
                    }

                    ForgeStep.Singularity -> Column(
                        verticalArrangement = Arrangement.spacedBy(13.dp),
                    ) {
                        SingularityCard(birth)
                        Text(
                            stringResource(R.string.inc_singularity_note),
                            style = A4LText.Caption,
                            color = A4L.TextMuted,
                        )
                    }

                    ForgeStep.Forge -> Column(
                        verticalArrangement = Arrangement.spacedBy(13.dp),
                    ) {
                        RecapCard(birth)
                        ImmutableWarning()
                        if (!birth.complete) MissingLine(birth)
                    }
                    }
                }
            }

            // ── Navigation ────────────────────────────────────────────────
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 18.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                SaltLine(birth = birth, npub = npub)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (step > 0) {
                        Box(
                            Modifier
                                .width(56.dp)
                                .height(if (step == ForgeStep.entries.lastIndex) 52.dp else 46.dp)
                                .glass(12.dp, A4L.Glass, A4L.Stroke)
                                .clickable { step-- },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("←", style = A4LText.Body.copy(fontSize = 16.sp), color = A4L.TextBody)
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        if (step == ForgeStep.entries.lastIndex) {
                            ForgeButton(
                                forged = false,
                                complete = birth.complete,
                                onClick = { showForgeConfirm = true },
                            )
                        } else {
                            val ready = ForgeStep.entries[step].isSatisfied(birth)
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .glass(
                                        radius = 12.dp,
                                        background = A4L.Cyan.tint(if (ready) 0.12f else 0.04f),
                                        border = A4L.Cyan.tint(if (ready) 0.38f else 0.14f),
                                    )
                                    .clickable(enabled = ready) {
                                        focusManager.clearFocus()
                                        step++
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    stringResource(
                                        R.string.forge_next,
                                        stringResource(ForgeStep.entries[step + 1].titleRes),
                                    ),
                                    style = A4LText.Body.copy(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = if (ready) A4L.Cyan else A4L.TextGhost,
                                )
                            }
                        }
                    }
                }
                if (step < ForgeStep.entries.lastIndex &&
                    !ForgeStep.entries[step].isSatisfied(birth)
                ) {
                    MissingLine(birth, only = ForgeStep.entries[step])
                }
            }
        }
    }

    // ── Confirmation de forge : dernier regard avant l'irréversible ──────
    if (showForgeConfirm && birth.complete) {
        AlertDialog(
            onDismissRequest = { showForgeConfirm = false },
            title = { Text(stringResource(R.string.inc_confirm_title), style = A4LText.Title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(
                        stringResource(R.string.inc_confirm_body),
                        style = A4LText.Body,
                        color = A4L.TextBody,
                    )
                    Spacer(Modifier.height(2.dp))
                    // L'heure n'est plus exigée pour forger : la lire sans la
                    // vérifier faisait tomber l'écran juste avant le scellement.
                    ComputedRow(
                        stringResource(R.string.inc_row_birth),
                        if (birth.timeComplete) {
                            stringResource(
                                R.string.inc_birth_datetime,
                                birth.day!!, birth.month!!, birth.year!!,
                                birth.hour!!, birth.minute!!,
                            )
                        } else {
                            stringResource(
                                R.string.inc_birth_date,
                                birth.day!!, birth.month!!, birth.year!!,
                            )
                        },
                    )
                    ComputedRow(
                        stringResource(R.string.inc_row_place),
                        birth.placeName.ifBlank { "—" },
                    )
                    ComputedRow(
                        stringResource(R.string.inc_row_coords),
                        LoveKey.formatCoords(birth),
                    )
                    ComputedRow(
                        stringResource(R.string.inc_row_wave),
                        birth.wave?.let {
                            stringResource(
                                R.string.inc_wave_value,
                                it.symbol,
                                stringResource(it.labelRes),
                            )
                        } ?: "—",
                    )
                    // Comme au récapitulatif : le poids paraît toujours, saisi
                    // ou non — c'est celui-là qui va être scellé dans la clé.
                    ComputedRow(
                        stringResource(R.string.inc_row_weight),
                        LoveKey.formatWeight(LocalResources.current, birth.saltWeightKg) +
                            if (birth.weightKg == null) {
                                stringResource(R.string.inc_weight_default_suffix)
                            } else {
                                ""
                            },
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.inc_confirm_warning),
                        style = A4LText.Caption,
                        color = A4L.Amber.copy(alpha = 0.85f),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showForgeConfirm = false
                    onForge()
                }) { Text(stringResource(R.string.inc_confirm_forge), color = A4L.Gold) }
            },
            dismissButton = {
                TextButton(onClick = { showForgeConfirm = false }) {
                    Text(stringResource(R.string.inc_confirm_recheck), color = A4L.TextBody)
                }
            },
        )
    }

    // ── Dissolution : deux verrous avant l'oubli ─────────────────────────
    if (showDissolveWarning) {
        AlertDialog(
            onDismissRequest = { showDissolveWarning = false },
            title = { Text(stringResource(R.string.inc_dissolve_title), style = A4LText.Title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(
                        stringResource(R.string.inc_dissolve_body),
                        style = A4LText.Body,
                        color = A4L.TextBody,
                    )
                    Text(
                        stringResource(R.string.inc_dissolve_warning),
                        style = A4LText.Caption,
                        color = A4L.Amber.copy(alpha = 0.85f),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showDissolveWarning = false
                    showDissolveFinal = true
                }) { Text(stringResource(R.string.inc_continue), color = A4L.Red.copy(alpha = 0.85f)) }
            },
            dismissButton = {
                TextButton(onClick = { showDissolveWarning = false }) {
                    Text(stringResource(R.string.inc_cancel), color = A4L.TextBody)
                }
            },
        )
    }

    if (showDissolveFinal) {
        AlertDialog(
            onDismissRequest = { showDissolveFinal = false },
            title = { Text(stringResource(R.string.inc_dissolve_last_title), style = A4LText.Title) },
            text = {
                Text(
                    stringResource(R.string.inc_dissolve_last_body),
                    style = A4LText.Body,
                    color = A4L.TextBody,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDissolveFinal = false
                    onDissolve?.invoke()
                }) { Text(stringResource(R.string.inc_dissolve_final), color = A4L.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showDissolveFinal = false }) {
                    Text(stringResource(R.string.inc_cancel), color = A4L.TextBody)
                }
            },
        )
    }

    // ── Saisie directe des coordonnées ───────────────────────────────────
    // La contrepartie de l'avertissement ambre : ce qu'on demande de noter
    // doit pouvoir se retaper. Ni la liste des communes ni le GPS ne visent le
    // centième — un couple noté sur un carnet est le seul chemin fidèle vers
    // une clé déjà forgée un jour.
    if (showCoordsEditor) {
        CoordinatesDialog(
            lat = birth.lat,
            lon = birth.lon,
            onDismiss = { showCoordsEditor = false },
            onConfirm = { lat, lon ->
                showCoordsEditor = false
                onBirthChange(birth.copy(lat = lat, lon = lon))
            },
        )
    }

    // ── Sélecteurs ────────────────────────────────────────────────────────
    if (showDatePicker) {
        val today = remember { LocalDate.now() }
        val oldest = remember(today) { today.minusYears(BirthData.MAX_AGE_YEARS.toLong()) }
        val youngest = remember(today) { today.minusYears(BirthData.MIN_AGE_YEARS.toLong()) }
        // Trois rouleaux plutôt qu'un calendrier : une date de naissance se
        // cherche par l'année, pas par la semaine. Ils ouvrent sur ce qui est
        // déjà saisi, ou sur la borne des 18 ans — la seule valeur que l'app
        // connaisse sans rien supposer de qui la remplit.
        var pickedYear by remember { mutableIntStateOf(birth.year ?: youngest.year) }
        var pickedMonth by remember { mutableIntStateOf(birth.month ?: 1) }
        var pickedDay by remember { mutableIntStateOf(birth.day ?: 1) }
        AlertDialog(
            onDismissRequest = { showDatePicker = false },
            title = { Text(stringResource(R.string.inc_date_title), style = A4LText.Title) },
            text = {
                BirthDateWheels(
                    year = pickedYear,
                    month = pickedMonth,
                    day = pickedDay,
                    yearRange = oldest.year..youngest.year,
                    onChange = { y, m, d ->
                        pickedYear = y
                        pickedMonth = m
                        pickedDay = d
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onBirthChange(
                        birth.copy(year = pickedYear, month = pickedMonth, day = pickedDay),
                    )
                    showDatePicker = false
                }) { Text(stringResource(R.string.inc_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.inc_cancel)) }
            },
        )
    }

    if (showTimePicker) {
        // Deux rouleaux plutôt que le cadran de Material : une heure de
        // naissance se recopie d'un acte au chiffre près, elle ne s'approche
        // pas au doigt comme une heure de rendez-vous.
        var pickedHour by remember { mutableIntStateOf(birth.hour ?: BirthData.DEFAULT_HOUR) }
        var pickedMinute by remember { mutableIntStateOf(birth.minute ?: BirthData.DEFAULT_MINUTE) }
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onBirthChange(birth.copy(hour = pickedHour, minute = pickedMinute))
                    showTimePicker = false
                }) { Text(stringResource(R.string.inc_confirm)) }
            },
            // L'heure est facultative : il faut donc pouvoir la retirer, pas
            // seulement renoncer à la changer.
            dismissButton = {
                TextButton(onClick = {
                    onBirthChange(birth.copy(hour = null, minute = null))
                    showTimePicker = false
                }) { Text(stringResource(R.string.inc_time_unknown)) }
            },
            title = { Text(stringResource(R.string.inc_time_title), style = A4LText.Title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    Text(
                        stringResource(R.string.inc_time_optional_body),
                        style = A4LText.Caption,
                        color = A4L.TextMuted,
                    )
                    BirthTimeWheels(
                        hour = pickedHour,
                        minute = pickedMinute,
                        onChange = { h, m ->
                            pickedHour = h
                            pickedMinute = m
                        },
                    )
                }
            },
        )
    }

    // ── Le poids de naissance : dans la clé, mais facultatif ─────────────
    // Comme l'heure : peu de gens le connaissent, et la station sait quoi
    // mettre à sa place — 3,5 kg, exactement ce que retient
    // `atom4love_publish.py` quand son cinquième argument arrive vide.
    if (showBirthWeightPicker) {
        var picked by remember { mutableStateOf(birth.saltWeightKg) }
        OptionalMeasureDialog(
            title = stringResource(R.string.inc_birth_weight_title),
            body = stringResource(R.string.inc_birth_weight_body),
            unknownLabel = stringResource(R.string.inc_weight_unknown),
            onConfirm = {
                onBirthChange(birth.copy(weightKg = picked))
                showBirthWeightPicker = false
            },
            onUnknown = {
                onBirthChange(birth.copy(weightKg = null))
                showBirthWeightPicker = false
            },
            onDismiss = { showBirthWeightPicker = false },
        ) {
            WeightWheels(
                weightKg = picked,
                range = BirthData.BIRTH_WEIGHT_RANGE_KG,
                onChange = { picked = it },
            )
        }
    }

    // ── Le corps d'aujourd'hui : hors clé, modifiable à jamais ───────────
    if (showHeightPicker) {
        var picked by remember {
            mutableIntStateOf(body.heightCm ?: BodyMetrics.DEFAULT_HEIGHT_CM)
        }
        OptionalMeasureDialog(
            title = stringResource(R.string.inc_height_title),
            body = stringResource(R.string.inc_body_dialog_body),
            unknownLabel = stringResource(R.string.inc_measure_clear),
            onConfirm = {
                onBodyChange(body.copy(heightCm = picked))
                showHeightPicker = false
            },
            onUnknown = {
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
        var picked by remember {
            mutableStateOf(body.weightKg ?: BodyMetrics.DEFAULT_WEIGHT_KG)
        }
        OptionalMeasureDialog(
            title = stringResource(R.string.inc_weight_title),
            body = stringResource(R.string.inc_body_dialog_body),
            unknownLabel = stringResource(R.string.inc_measure_clear),
            onConfirm = {
                onBodyChange(body.copy(weightKg = picked))
                showWeightPicker = false
            },
            onUnknown = {
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
}

/**
 * Le dialogue d'une mesure qu'on a le droit de ne pas donner.
 *
 * Les trois mesures facultatives partagent la même forme, et surtout le même
 * troisième bouton : « annuler » ne retire pas une valeur déjà là. Sans lui,
 * une taille saisie par erreur ne pourrait plus jamais s'effacer — c'est la
 * leçon du sélecteur d'heure, qui a dû apprendre le même geste.
 */
@Composable
private fun OptionalMeasureDialog(
    title: String,
    body: String,
    unknownLabel: String,
    onConfirm: () -> Unit,
    onUnknown: () -> Unit,
    onDismiss: () -> Unit,
    wheels: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = A4LText.Title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Text(body, style = A4LText.Caption, color = A4L.TextMuted)
                wheels()
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.inc_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onUnknown) { Text(unknownLabel, color = A4L.TextBody) }
        },
    )
}

/**
 * Saisie directe des coordonnées de naissance, au centième de degré.
 *
 * Les valeurs sont arrondies à deux décimales à la validation : c'est la
 * précision qui entre dans le SALT, et ce que l'utilisateur tape doit être
 * exactement ce que la clé verra — sans quoi « notez vos coordonnées » resterait
 * un vœu pieux.
 */
@Composable
private fun CoordinatesDialog(
    lat: Double?,
    lon: Double?,
    onDismiss: () -> Unit,
    onConfirm: (Double, Double) -> Unit,
) {
    // La virgule décimale française est acceptée à la saisie ; le point reste la
    // seule forme qui entre dans le SALT (cf. LoveKey.salt, Locale.US).
    fun parse(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

    var latText by rememberSaveable {
        mutableStateOf(lat?.let { String.format(Locale.US, "%.2f", it) } ?: "")
    }
    var lonText by rememberSaveable {
        mutableStateOf(lon?.let { String.format(Locale.US, "%.2f", it) } ?: "")
    }

    val parsedLat = parse(latText)?.takeIf { it in -90.0..90.0 }
    val parsedLon = parse(lonText)?.takeIf { it in -180.0..180.0 }
    val valid = parsedLat != null && parsedLon != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.inc_coords_title), style = A4LText.Title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Text(
                    stringResource(R.string.inc_coords_body),
                    style = A4LText.Body,
                    color = A4L.TextBody,
                )
                CoordinateField(
                    label = stringResource(R.string.inc_latitude),
                    value = latText,
                    hint = "48.86",
                    invalid = latText.isNotBlank() && parsedLat == null,
                    onValueChange = { latText = it },
                )
                CoordinateField(
                    label = stringResource(R.string.inc_longitude),
                    value = lonText,
                    hint = "2.35",
                    invalid = lonText.isNotBlank() && parsedLon == null,
                    onValueChange = { lonText = it },
                )
                Text(
                    stringResource(R.string.inc_coords_south_west),
                    style = A4LText.Caption,
                    color = A4L.TextMuted,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    // Arrondi ici, pas seulement à l'affichage : la fiche garde
                    // ce qui a été tapé, au centième près.
                    onConfirm(
                        round(parsedLat!! * 100.0) / 100.0,
                        round(parsedLon!! * 100.0) / 100.0,
                    )
                },
            ) { Text(stringResource(R.string.inc_confirm), color = if (valid) A4L.Cyan else A4L.TextGhost) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.inc_cancel), color = A4L.TextBody) }
        },
    )
}

/** Un champ de coordonnée : chiffres, point ou virgule, signe moins. */
@Composable
private fun CoordinateField(
    label: String,
    value: String,
    hint: String,
    invalid: Boolean,
    onValueChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        SectionLabel(label, color = if (invalid) A4L.Red else A4L.TextFaint)
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .glass(
                    radius = 11.dp,
                    background = A4L.Glass,
                    border = if (invalid) A4L.Red.tint(0.40f) else A4L.Stroke,
                )
                .padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = { typed ->
                    // On ne laisse entrer que ce qui peut former un nombre :
                    // une frappe refusée vaut mieux qu'un « Valider » éteint
                    // sans qu'on sache pourquoi.
                    if (typed.all { it.isDigit() || it in "-,." }) onValueChange(typed)
                },
                singleLine = true,
                textStyle = A4LText.Data.copy(fontSize = 15.sp, color = A4L.TextHigh),
                cursorBrush = SolidColor(A4L.Cyan),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            hint,
                            style = A4LText.Data.copy(fontSize = 15.sp),
                            color = A4L.TextGhost,
                        )
                    }
                    inner()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Le texte d'un [DateProblem], première lettre en capitale. Le domaine dit
 * lequel des trois cas ; c'est ici qu'on l'écrit, dans la langue de l'appareil.
 */
@Composable
private fun problemText(problem: DateProblem): String =
    (problem.arg?.let { stringResource(problem.messageRes, it) }
        ?: stringResource(problem.messageRes))
        .replaceFirstChar { it.uppercase() }

/**
 * Les cinq stations de l'assistant, dans l'ordre d'ATOM4LOVE : on ne demande
 * qu'une chose à la fois, et chacune tient dans l'écran sans rien faire défiler.
 */
private enum class ForgeStep(
    /** Le glyphe reste en dur : un pictogramme n'est d'aucune langue. */
    val glyph: String,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
) {
    Identity("🪪", R.string.forge_step_identity, R.string.forge_step_identity_sub),
    Anchor("⚓", R.string.forge_step_anchor, R.string.forge_step_anchor_sub),
    Vessel("🧬", R.string.forge_step_vessel, R.string.forge_step_vessel_sub),
    Singularity("🌀", R.string.forge_step_singularity, R.string.forge_step_singularity_sub),
    Forge("⚛", R.string.forge_step_forge, R.string.forge_step_forge_sub);

    /**
     * Ce que l'étape exige pour qu'on ait le droit de passer à la suivante.
     * L'heure de naissance n'en fait plus partie : peu de gens la connaissent,
     * et la fiche sait quoi mettre à sa place ([BirthData.saltHour]).
     */
    fun isSatisfied(b: BirthData): Boolean = when (this) {
        Identity, Singularity -> true
        Anchor -> b.dateComplete && b.isPlausible() && b.lat != null && b.lon != null
        Vessel -> b.wave != null
        Forge -> b.complete
    }
}

/** Le chemin parcouru : cinq pastilles, reliées, cliquables vers l'arrière. */
@Composable
private fun StepBar(current: Int, reached: (Int) -> Boolean, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ForgeStep.entries.forEachIndexed { index, _ ->
            val done = index < current
            val active = index == current
            val open = reached(index)
            Box(
                Modifier
                    .size(26.dp)
                    .glass(
                        radius = 13.dp,
                        background = when {
                            active -> A4L.Cyan.tint(0.16f)
                            done -> A4L.Mint.tint(0.10f)
                            else -> A4L.GlassSoft
                        },
                        border = when {
                            active -> A4L.Cyan.tint(0.45f)
                            done -> A4L.Mint.tint(0.30f)
                            else -> A4L.Stroke.copy(alpha = 0.10f)
                        },
                    )
                    .clickable(enabled = open) { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (done) "✓" else "${index + 1}",
                    style = A4LText.Data.copy(fontSize = 10.sp),
                    color = when {
                        active -> A4L.Cyan
                        done -> A4L.Mint
                        else -> A4L.TextGhost
                    },
                )
            }
            if (index < ForgeStep.entries.lastIndex) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(if (done) A4L.Mint.tint(0.30f) else A4L.Stroke.copy(alpha = 0.10f)),
                )
            }
        }
    }
}

/** Étape 1 — ce qu'on s'apprête à faire, et avec quelle clé. */
@Composable
private fun IdentityStep() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.inc_lead),
            style = A4LText.Body,
            color = A4L.TextBody,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .glass(12.dp, A4L.Glass, A4L.Stroke)
                .padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                stringResource(R.string.inc_no_account_title),
                style = A4LText.Body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = A4L.TextHigh,
            )
            Text(
                stringResource(R.string.inc_no_account_body),
                style = A4LText.Caption,
                color = A4L.TextMuted,
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .glass(12.dp, A4L.Gold.tint(0.05f), A4L.Gold.tint(0.22f))
                .padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                stringResource(R.string.inc_temp_key_title),
                style = A4LText.Body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = A4L.Gold,
            )
            Text(
                stringResource(R.string.inc_temp_key_body),
                style = A4LText.Caption,
                color = A4L.TextMuted,
            )
        }
    }
}

/** Étape 2 — la ligne des six cases : JJ MM AAAA · HH MN. */
@Composable
private fun BirthDateTimeSection(
    birth: BirthData,
    editable: Boolean,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        // La mention passe sous le titre plutôt qu'à sa droite : elle est trop
        // longue pour tenir sur la même ligne dans les trois langues, et une
        // ligne de plus coûte moins cher qu'un mot coupé.
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SectionLabel(stringResource(R.string.inc_section_datetime))
            // L'heure reste facultative — qui ne la connaît pas n'est pas
            // bloqué — mais elle affine la phase personnelle : le dire ici
            // évite qu'on la saute par simple inattention.
            Text(
                stringResource(R.string.inc_time_optional),
                style = A4LText.Caption,
                color = A4L.TextGhost,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Les cases vides s'affichent en fantôme « JJ MM AAAA » :
            // l'utilisateur voit d'un coup d'œil ce qui reste à définir.
            DigitBox(
                birth.day?.let { "%02d".format(it) } ?: stringResource(R.string.inc_dd),
                42.dp, enabled = editable, placeholder = birth.day == null,
                onClick = onPickDate,
            )
            Spacer(Modifier.width(6.dp))
            DigitBox(
                birth.month?.let { "%02d".format(it) } ?: stringResource(R.string.inc_mm),
                42.dp, enabled = editable, placeholder = birth.month == null,
                onClick = onPickDate,
            )
            Spacer(Modifier.width(6.dp))
            DigitBox(
                birth.year?.toString() ?: stringResource(R.string.inc_yyyy),
                60.dp, enabled = editable, placeholder = birth.year == null,
                onClick = onPickDate,
            )
            Text(
                "·",
                color = A4L.TextGhost,
                fontSize = 15.sp,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            DigitBox(
                birth.hour?.let { "%02d".format(it) } ?: stringResource(R.string.inc_hh),
                42.dp, accent = true, enabled = editable, placeholder = birth.hour == null,
                onClick = onPickTime,
            )
            Text(
                ":",
                style = A4LText.Data,
                color = A4L.TextGhost,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            DigitBox(
                birth.minute?.let { "%02d".format(it) } ?: stringResource(R.string.inc_mn),
                42.dp, accent = true, enabled = editable, placeholder = birth.minute == null,
                onClick = onPickTime,
            )
        }
    }
}

/** Étape 2 — le lieu : saisie libre, autocomplétion des communes, ou GPS. */
@Composable
private fun BirthPlaceSection(
    birth: BirthData,
    editable: Boolean,
    locating: Boolean,
    suggestions: List<CommuneApi.Commune>,
    onNameChange: (String) -> Unit,
    onChoose: (CommuneApi.Commune) -> Unit,
    onDone: () -> Unit,
    onLocate: () -> Unit,
    onEditCoords: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        SectionLabel(stringResource(R.string.inc_section_place))
        Row(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
                .glass(11.dp, A4L.Glass, A4L.Stroke)
                .padding(start = 14.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Saisie libre : le nom est géocodé en (lat, lon) à la
            // validation clavier. Verrouillée une fois la clé forgée.
            BasicTextField(
                value = birth.placeName,
                onValueChange = onNameChange,
                enabled = editable,
                singleLine = true,
                textStyle = A4LText.Body.copy(fontSize = 14.sp, color = A4L.TextHigh),
                cursorBrush = SolidColor(A4L.Cyan),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                decorationBox = { inner ->
                    if (birth.placeName.isEmpty()) {
                        Text(
                            stringResource(R.string.inc_place_placeholder),
                            style = A4LText.Body.copy(fontSize = 14.sp),
                            color = A4L.TextGhost,
                        )
                    }
                    inner()
                },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            // Le badge n'est pas qu'un affichage : c'est la seule façon de
            // ressaisir un couple de coordonnées noté ailleurs. Sans lui, dire
            // « notez-les, elles rouvrent votre clé » serait une promesse en
            // l'air — ni la commune ni le GPS ne redonnent un centième précis.
            Text(
                LoveKey.formatCoords(birth),
                style = A4LText.Data.copy(fontSize = 10.sp),
                color = if (editable) A4L.Cyan.copy(alpha = 0.75f) else A4L.TextMuted,
                modifier = Modifier
                    .background(A4L.Glass, RoundedCornerShape(7.dp))
                    .clickable(enabled = editable, onClick = onEditCoords)
                    .padding(horizontal = 7.dp, vertical = 4.dp),
            )
            Spacer(Modifier.width(8.dp))
            // 📍 : remplit lieu et coordonnées depuis la position GPS
            // de l'appareil (demande la permission au premier appui).
            Box(
                Modifier
                    .size(30.dp)
                    .background(A4L.Glass, RoundedCornerShape(8.dp))
                    .clickable(enabled = editable && !locating, onClick = onLocate),
                contentAlignment = Alignment.Center,
            ) {
                if (locating) {
                    CircularProgressIndicator(
                        color = A4L.Cyan,
                        strokeWidth = 1.5.dp,
                        modifier = Modifier.size(14.dp),
                    )
                } else {
                    Text("📍", fontSize = 13.sp)
                }
            }
        }

        // Propositions data.gouv.fr : villes et villages de France. Quatre au
        // plus — la liste ne doit pas repousser le reste de l'étape hors écran.
        if (suggestions.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .glass(11.dp, A4L.Glass, A4L.Cyan.tint(0.20f)),
            ) {
                suggestions.take(4).forEach { commune ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onChoose(commune) }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    ) {
                        Text(
                            commune.name,
                            style = A4LText.Body.copy(fontSize = 13.sp),
                            color = A4L.TextHigh,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            commune.context,
                            style = A4LText.Data.copy(fontSize = 9.sp),
                            color = A4L.TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** Étape 3 — l'onde biologique, Φ ou Octave. */
@Composable
private fun WaveSection(birth: BirthData, editable: Boolean, onBirthChange: (BirthData) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        SectionLabel(stringResource(R.string.inc_section_wave))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Wave.entries.forEach { wave ->
                WaveCard(
                    wave = wave,
                    selected = birth.wave == wave,
                    enabled = editable,
                    onClick = { onBirthChange(birth.copy(wave = wave)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Étape 3 — le poids de naissance. Facultatif, mais il entre dans la clé :
 * la case montre donc toujours ce qui y entrera, défaut compris.
 */
@Composable
private fun BirthWeightSection(birth: BirthData, editable: Boolean, onPick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SectionLabel(stringResource(R.string.inc_section_birth_weight))
            Text(
                stringResource(R.string.inc_birth_weight_optional),
                style = A4LText.Caption,
                color = A4L.TextGhost,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            DigitBox(
                LoveKey.formatWeight(LocalResources.current, birth.saltWeightKg),
                84.dp,
                enabled = editable,
                // Rien n'a été saisi : la valeur affichée est celle de la
                // station, pas la sienne — elle se montre donc en fantôme.
                placeholder = birth.weightKg == null,
                onClick = onPick,
            )
            if (birth.weightKg == null) {
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.inc_weight_is_default),
                    style = A4LText.Caption,
                    color = A4L.TextGhost,
                )
            }
        }
    }
}

/**
 * Étape 3 — le corps d'aujourd'hui, et l'onde biologique qui s'en déduit.
 *
 * Séparé du reste, dans son propre cadre indigo : tout ce qui est cyan ou or
 * dans cet écran entre dans la clé, ces deux mesures non. Elles se modifient
 * après la forge, et la note le dit.
 */
@Composable
private fun BodySection(
    body: BodyMetrics,
    wave: Wave?,
    onPickHeight: () -> Unit,
    onPickWeight: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .glass(12.dp, A4L.Indigo.tint(0.05f), A4L.Indigo.tint(0.22f))
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionLabel(
            stringResource(R.string.inc_section_body),
            color = A4L.Indigo.copy(alpha = 0.75f),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            DigitBox(
                body.heightCm?.let { stringResource(R.string.format_height, it) }
                    ?: stringResource(R.string.inc_height_placeholder),
                84.dp,
                placeholder = body.heightCm == null,
                onClick = onPickHeight,
            )
            Spacer(Modifier.width(8.dp))
            DigitBox(
                body.weightKg?.let { LoveKey.formatWeight(LocalResources.current, it) }
                    ?: stringResource(R.string.inc_weight_placeholder),
                84.dp,
                placeholder = body.weightKg == null,
                onClick = onPickWeight,
            )
        }
        // ω_bio ne paraît que lorsqu'il existe vraiment : sans les deux mesures
        // et la polarité, la station n'a rien à en dire, et un zéro serait faux.
        OmegaBioRow(body = body, wave = wave)
        Text(
            stringResource(R.string.inc_body_note),
            style = A4LText.Caption,
            color = A4L.TextMuted,
        )
    }
}

/** L'onde biologique et l'eau qui la porte — les deux nombres d'`atomic.html`. */
@Composable
private fun OmegaBioRow(body: BodyMetrics, wave: Wave?) {
    val omega = Phi2X.omegaBio(body, wave)
    ComputedRow(
        stringResource(R.string.inc_row_omega_bio),
        if (omega == null) {
            "—"
        } else {
            stringResource(
                R.string.inc_omega_bio_value,
                String.format(Locale.getDefault(), "%.2f", omega),
                String.format(
                    Locale.getDefault(),
                    "%.1f",
                    Phi2X.waterKg(body.heightCm!!, body.weightKg!!, wave!!.sex),
                ),
            )
        },
        valueColor = if (omega == null) A4L.TextMuted else A4L.Indigo,
    )
}

/** Étape 4 — ce que la station calcule sans rien demander. */
@Composable
private fun SingularityCard(birth: BirthData) {
    Column(
        Modifier
            .fillMaxWidth()
            .glass(12.dp, A4L.Cyan.tint(0.05f), A4L.Cyan.tint(0.16f))
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionLabel(
            stringResource(R.string.inc_section_singularity),
            color = A4L.Cyan.copy(alpha = 0.6f),
        )
        val res = LocalResources.current
        ComputedRow(
            stringResource(R.string.inc_row_conception),
            if (birth.dateComplete) {
                LoveKey.formatDate(LoveKey.conception(birth), res.configuration.locales[0])
            } else "—",
        )
        ComputedRow(
            stringResource(R.string.inc_row_gestation),
            if (birth.dateComplete) LoveKey.formatGestation(res) else "—",
        )
        // Le vrai portail : le sommet du polyèdre de Goldberg le plus
        // proche du lieu de naissance (les 12 pentagones de la grille H3).
        ComputedRow(
            stringResource(R.string.inc_row_portal),
            if (birth.lat != null && birth.lon != null) {
                GoldbergPortal.nearest(birth.lat, birth.lon).label
            } else "—",
            valueColor = A4L.Cyan,
        )
        // Le KIN ne demande que la date — il paraît donc avant le lieu, et
        // avant la forge. La station le calcule elle-même depuis le portage de
        // la table de Fred ; celui que rend le MULTIPASS est le même nombre.
        ComputedRow(
            stringResource(R.string.inc_row_kin),
            KinMaya.of(birth)?.let { kin ->
                stringResource(
                    R.string.inc_kin_value,
                    kin.kin,
                    KinMaya.glyphName(kin.glyph) ?: "—",
                )
            } ?: "—",
        )
        // La phase, elle, attend le lieu : c'est le nombre sur lequel le Radar
        // calcule ses résonances, et le montrer ici évite qu'il ne vive que
        // dans la comparaison avec les autres.
        ComputedRow(
            stringResource(R.string.inc_row_phase),
            // La virgule française ici, le point pour les coordonnées juste
            // au-dessus : celles-là se recopient à l'identique dans une autre
            // station, une phase se lit seulement — comme le k du Radar.
            Phi2X.personalPhase(birth)?.let {
                stringResource(
                    R.string.inc_phase_value,
                    String.format(Locale.getDefault(), "%.3f", it),
                )
            } ?: "—",
        )
    }
}

/** Étape 5 — la fiche entière, relue d'un bloc avant le scellement. */
@Composable
private fun RecapCard(birth: BirthData) {
    Column(
        Modifier
            .fillMaxWidth()
            .glass(12.dp, A4L.Glass, A4L.Stroke)
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionLabel(stringResource(R.string.inc_section_incarnation))
        ComputedRow(
            stringResource(R.string.inc_row_birth),
            when {
                !birth.dateComplete -> "—"
                // L'heure ne s'affiche que si elle a été donnée : montrer le
                // midi par défaut ferait croire à une heure de naissance.
                birth.timeComplete -> stringResource(
                    R.string.inc_birth_datetime,
                    birth.day!!, birth.month!!, birth.year!!, birth.hour!!, birth.minute!!,
                )
                else -> stringResource(
                    R.string.inc_birth_date,
                    birth.day!!, birth.month!!, birth.year!!,
                )
            },
        )
        ComputedRow(stringResource(R.string.inc_row_place), birth.placeName.ifBlank { "—" })
        ComputedRow(stringResource(R.string.inc_row_coords), LoveKey.formatCoords(birth))
        ComputedRow(
            stringResource(R.string.inc_row_wave),
            birth.wave?.let {
                stringResource(R.string.inc_wave_value, it.symbol, stringResource(it.labelRes))
            } ?: "—",
        )
        // Le poids paraît toujours, saisi ou non : c'est celui-là qui entre
        // dans la clé, et une ligne absente laisserait croire le contraire.
        ComputedRow(
            stringResource(R.string.inc_row_weight),
            LoveKey.formatWeight(LocalResources.current, birth.saltWeightKg) +
                if (birth.weightKg == null) {
                    stringResource(R.string.inc_weight_default_suffix)
                } else {
                    ""
                },
        )
    }
}


/** L'avertissement d'irréversibilité, et la nature provisoire de la clé. */
@Composable
private fun ImmutableWarning() {
    Row(
        Modifier
            .fillMaxWidth()
            .glass(11.dp, A4L.Amber.tint(0.06f), A4L.Amber.tint(0.24f))
            .padding(horizontal = 13.dp, vertical = 11.dp),
    ) {
        Text("⚠", fontSize = 12.sp, color = A4L.Amber)
        Spacer(Modifier.width(9.dp))
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                stringResource(R.string.inc_sealed_note),
                style = A4LText.Caption,
                color = A4L.Amber.copy(alpha = 0.85f),
            )
            // Dit avant la forge, pas après : la clé d'ici n'est pas encore la
            // clé LOVE, et le jour où une station la dérivera vraiment, le npub
            // d'aujourd'hui sera périmé.
            Text(
                stringResource(R.string.inc_provisional_note),
                style = A4LText.Caption,
                color = A4L.TextMuted,
            )
        }
    }
}

/** La ligne du SALT, et le npub dès qu'une clé existe. */
@Composable
private fun SaltLine(birth: BirthData, npub: String?) {
    Column {
        Text(
            if (birth.complete) {
                stringResource(R.string.inc_salt, LoveKey.salt(birth))
            } else {
                stringResource(R.string.inc_salt_incomplete)
            },
            style = A4LText.Data.copy(fontSize = 9.sp),
            color = A4L.TextGhost,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // L'identité NOSTR réelle, dès que la clé est forgée.
        if (npub != null) {
            Text(
                npub,
                style = A4LText.Data.copy(fontSize = 9.sp),
                color = A4L.TextGhost,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Ce qui manque encore, nommé — jamais un bouton éteint sans explication.
 * [only] restreint la liste à ce que l'étape courante réclame.
 */
@Composable
private fun MissingLine(birth: BirthData, only: ForgeStep? = null) {
    val missing = buildList {
        if (only == null || only == ForgeStep.Anchor) {
            if (!birth.dateComplete) add(stringResource(R.string.inc_missing_date))
            if (birth.lat == null || birth.lon == null) {
                add(
                    stringResource(
                        if (birth.placeName.isBlank()) {
                            R.string.inc_missing_place
                        } else {
                            R.string.inc_missing_place_pick
                        },
                    ),
                )
            }
        }
        if (only == null || only == ForgeStep.Vessel) {
            if (birth.wave == null) add(stringResource(R.string.inc_missing_wave))
        }
    }
    if (missing.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("ⓘ", fontSize = 11.sp, color = A4L.Cyan.copy(alpha = 0.7f))
        Spacer(Modifier.width(7.dp))
        Text(
            stringResource(
                R.string.inc_missing,
                missing.joinToString(stringResource(R.string.inc_missing_separator)),
            ),
            style = A4LText.Caption,
            color = A4L.TextMuted,
        )
    }
}

/**
 * Le noyau scellé, dans l'onglet Noyau : plus d'assistant, plus rien à saisir —
 * la fiche entière d'un seul tenant, et les deux portes qui restent ouvertes,
 * le MULTIPASS et la dissolution.
 */
@Composable
private fun ColumnScope.SealedNucleus(
    birth: BirthData,
    npub: String?,
    onMultipass: (() -> Unit)?,
    multipassActive: Boolean,
    onBoard: (() -> Unit)?,
    onDissolve: () -> Unit,
    modifier: Modifier = Modifier,
    body: BodyMetrics = BodyMetrics.Empty,
    onPickHeight: () -> Unit = {},
    onPickWeight: () -> Unit = {},
) {
    Column(
        modifier.verticalScroll(rememberScrollState()),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp)) {
            Text(
                stringResource(R.string.inc_sealed_title),
                style = A4LText.H1,
                color = A4L.TextHigh,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.inc_sealed_body),
                style = A4LText.Body,
                color = A4L.TextBody,
            )
        }
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            RecapCard(birth)
            SingularityCard(birth)
            // La seule carte encore vivante sous un noyau scellé : un corps
            // change, ces deux nombres doivent pouvoir suivre sans qu'on
            // dissolve quoi que ce soit.
            BodySection(
                body = body,
                wave = birth.wave,
                onPickHeight = onPickHeight,
                onPickWeight = onPickWeight,
            )
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            // Le pied ne touche pas le contenu qui défile au-dessus : sans cette
            // marge, le sel se colle au bord où la dernière carte se fait
            // couper, et les deux se lisent comme un seul bloc.
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 18.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        SaltLine(birth = birth, npub = npub)
        // La porte vers Astroport.ONE. Elle n'existe qu'une fois le noyau
        // scellé : sans les cinq données, il n'y a pas de clé LOVE à dériver.
        if (onMultipass != null) {
            val accent = if (multipassActive) A4L.Mint else A4L.Indigo
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .glass(
                        radius = 12.dp,
                        background = accent.tint(0.10f),
                        border = accent.tint(0.34f),
                    )
                    .clickable(onClick = onMultipass),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(
                        if (multipassActive) {
                            R.string.inc_multipass_active
                        } else {
                            R.string.inc_multipass_open
                        },
                    ),
                    style = A4LText.Body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    color = accent,
                )
            }
        }
        // Le Plateau. Discret, et en toutes lettres une maquette : il n'a pas
        // de partie, et un bouton qui promet un jeu doit dire ce qu'il tient.
        if (onBoard != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .glass(
                        radius = 12.dp,
                        background = A4L.Glass.copy(alpha = 0.05f),
                        border = A4L.Stroke,
                    )
                    .clickable(onClick = onBoard),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.inc_board_open),
                    style = A4LText.Body.copy(fontSize = 12.5.sp),
                    color = A4L.TextMuted,
                )
            }
        }
        // La porte de sortie propre : dissoudre le noyau plutôt que
        // désinstaller l'app quand on s'est trompé dans sa fiche.
        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .glass(
                    radius = 12.dp,
                    background = A4L.Red.tint(0.06f),
                    border = A4L.Red.tint(0.30f),
                )
                .clickable(onClick = onDissolve),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.inc_dissolve),
                style = A4LText.Body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = A4L.Red.copy(alpha = 0.85f),
            )
        }
        // Le launcher garde le nom court ; la filiation s'affiche ici.
        Text(
            stringResource(R.string.inc_footer),
            style = A4LText.Data.copy(fontSize = 9.sp),
            color = A4L.TextGhost,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Une case de la ligne date/heure. Les cases d'heure sont cyan ; une case
 * [placeholder] (champ pas encore défini) s'affiche en fantôme.
 */
@Composable
private fun DigitBox(
    text: String,
    width: Dp,
    accent: Boolean = false,
    enabled: Boolean = true,
    placeholder: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .width(width)
            .height(42.dp)
            .glass(
                radius = 9.dp,
                background = if (accent) A4L.Cyan.tint(0.07f) else A4L.Glass,
                border = if (accent) A4L.Cyan.tint(0.34f) else A4L.Stroke,
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = A4LText.Data.copy(fontSize = if (placeholder) 11.sp else 15.sp),
            color = when {
                placeholder && accent -> A4L.Cyan.copy(alpha = 0.30f)
                placeholder -> A4L.TextGhost
                accent -> A4L.Cyan
                else -> A4L.TextHigh
            },
        )
    }
}

/** Carte de choix d'onde. L'onde retenue passe en vert menthe. */
@Composable
private fun WaveCard(
    wave: Wave,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .glass(
                radius = 12.dp,
                background = if (selected) A4L.Mint.tint(0.10f) else A4L.GlassSoft,
                border = if (selected) A4L.Mint.tint(0.40f) else A4L.Stroke.copy(alpha = 0.10f),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            wave.symbol,
            style = A4LText.Data.copy(fontSize = 19.sp),
            color = if (selected) A4L.Mint else A4L.TextMuted,
        )
        Text(
            stringResource(wave.labelRes),
            style = A4LText.Body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            color = if (selected) A4L.Mint else A4L.TextBody.copy(alpha = 0.55f),
        )
        Text(
            stringResource(R.string.wave_sex, wave.sex),
            style = A4LText.Data.copy(fontSize = 9.sp),
            color = if (selected) A4L.Mint.copy(alpha = 0.55f) else A4L.TextGhost,
        )
    }
}

/**
 * Bouton primaire — éteint tant que la fiche est incomplète, or quand la clé
 * peut être forgée, menthe une fois scellée.
 */
@Composable
private fun ForgeButton(forged: Boolean, complete: Boolean, onClick: () -> Unit) {
    val accent = if (forged) A4L.Mint else A4L.Gold
    val ready = forged || complete
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .glass(
                radius = 14.dp,
                background = accent.tint(if (!ready) 0.05f else if (forged) 0.12f else 0.18f),
                border = accent.tint(if (ready) 0.55f else 0.18f),
            )
            .clickable(enabled = !forged && complete, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(
                when {
                    forged -> R.string.inc_key_sealed
                    complete -> R.string.inc_forge_action
                    else -> R.string.inc_forge_incomplete
                },
            ),
            style = A4LText.Body.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
            color = if (ready) accent else accent.copy(alpha = 0.45f),
        )
    }
}
