package one.astroport.atom4love.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.offset
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlin.math.round
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import one.astroport.atom4love.domain.BirthData
import one.astroport.atom4love.domain.GoldbergPortal
import one.astroport.atom4love.geo.CommuneApi
import one.astroport.atom4love.geo.PlaceResolver
import one.astroport.atom4love.nostr.RelayStation
import one.astroport.atom4love.domain.LoveKey
import one.astroport.atom4love.domain.Wave
import one.astroport.atom4love.ui.components.ComputedRow
import one.astroport.atom4love.ui.components.SectionLabel
import one.astroport.atom4love.ui.components.StatusDot
import one.astroport.atom4love.ui.components.glass
import one.astroport.atom4love.ui.components.screenBackground
import one.astroport.atom4love.ui.theme.A4L
import one.astroport.atom4love.ui.theme.A4LText
import one.astroport.atom4love.ui.theme.tint

private const val WEIGHT_MIN = 2.5f
private const val WEIGHT_MAX = 4.5f

/**
 * 01 · Incarnation — la forge du noyau.
 *
 * Les cinq données (instant, lieu, onde, poids) sont modifiables tant que la clé
 * n'est pas forgée ; le SALT, la gestation et la date de conception se recalculent
 * à chaque frappe. Une fois forgée, tout se verrouille : c'est le contrat annoncé
 * par l'avertissement ambre.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncarnationScreen(
    birth: BirthData,
    onBirthChange: (BirthData) -> Unit,
    forged: Boolean,
    onForge: () -> Unit,
    modifier: Modifier = Modifier,
    npub: String? = null,
    onDissolve: (() -> Unit)? = null,
    relay: RelayStation.Status? = null,
    onHelp: (() -> Unit)? = null,
    /** La porte vers Astroport.ONE, en bas de l'écran : on peut y revenir
     *  quand on veut, y compris après avoir dit « plus tard ». */
    onMultipass: (() -> Unit)? = null,
    /** Le compte est ouvert et sa clé LOVE en place : le bouton change de rôle. */
    multipassActive: Boolean = false,
) {
    // L'étape courante de l'assistant, et la plus lointaine qu'on ait le droit
    // d'atteindre : on revient où l'on veut, on n'avance que sur du renseigné.
    var step by rememberSaveable { mutableIntStateOf(0) }
    val furthest = remember(birth, step) {
        val firstOpen = ForgeStep.entries.indexOfFirst { !it.isSatisfied(birth) }
        maxOf(step, if (firstOpen < 0) ForgeStep.entries.lastIndex else firstOpen)
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
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
                onDissolve = { showDissolveWarning = true },
                modifier = Modifier.weight(1f),
            )
        } else {
            // L'assistant, calqué sur celui d'ATOM4LOVE : cinq stations, une
            // seule question à la fois, et rien à faire défiler pour la voir.
            val stepTitle = ForgeStep.entries[step]

            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${stepTitle.glyph}  ${stepTitle.title}",
                            style = A4LText.H2,
                            color = A4L.TextHigh,
                        )
                        Text(
                            stepTitle.subtitle,
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
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    // `fillMaxSize` impose au Column la hauteur du viewport tant
                    // que l'étape y tient : l'arrangement la centre alors dans
                    // l'écran. Dès qu'elle déborde, le Column reprend la taille
                    // de son contenu et le filet de défilement s'active.
                    verticalArrangement = Arrangement.Center,
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
                            )
                            // L'avertissement qu'ATOM4LOVE porte à cette étape : ces
                            // coordonnées sont la seule chose à recopier quelque part.
                            Text(
                                "Coordonnées de récupération, à noter précieusement : " +
                                    "précision 0,01° — environ 1 km. C'est ce couple de " +
                                    "nombres qui rouvre votre clé si vous perdez cet appareil.",
                                style = A4LText.Caption,
                                color = A4L.Amber.copy(alpha = 0.75f),
                            )
                        }

                    ForgeStep.Vessel -> Column(
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        WaveSection(birth = birth, editable = true, onBirthChange = onBirthChange)
                        WeightSection(birth = birth, editable = true, onBirthChange = onBirthChange)
                        Text(
                            "L'onde et le poids donnent sa fréquence à votre corps — " +
                                "l'onde biologique. Ils entrent tous deux dans la clé.",
                            style = A4LText.Caption,
                            color = A4L.TextMuted,
                        )
                    }

                    ForgeStep.Singularity -> Column(
                        verticalArrangement = Arrangement.spacedBy(13.dp),
                    ) {
                        SingularityCard(birth)
                        Text(
                            "Votre conception est l'Esprit, votre naissance la Matière. " +
                                "Rien à saisir : la station la déduit de votre naissance, " +
                                "et le portail est le sommet du pavage le plus proche de " +
                                "votre lieu.",
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
                                    "${ForgeStep.entries[step + 1].title}  →",
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
            title = { Text("Vérifiez votre incarnation", style = A4LText.Title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(
                        "Votre noyau va être scellé avec ces cinq données. " +
                            "Après, plus rien n'est modifiable.",
                        style = A4LText.Body,
                        color = A4L.TextBody,
                    )
                    Spacer(Modifier.height(2.dp))
                    ComputedRow(
                        "Naissance",
                        "%02d/%02d/%04d à %02d:%02d".format(
                            birth.day, birth.month, birth.year, birth.hour, birth.minute,
                        ),
                    )
                    ComputedRow("Lieu", birth.placeName.ifBlank { "—" })
                    ComputedRow("Coordonnées", LoveKey.formatCoords(birth))
                    ComputedRow("Onde", "${birth.wave?.symbol} ${birth.wave?.label}")
                    ComputedRow("Poids", LoveKey.formatWeight(birth.weightKg))
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "⚠ Une erreur ici, et la clé dérivée ne sera jamais la vôtre. " +
                            "Prenez le temps de relire chaque ligne.",
                        style = A4LText.Caption,
                        color = A4L.Amber.copy(alpha = 0.85f),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showForgeConfirm = false
                    onForge()
                }) { Text("Forger définitivement", color = A4L.Gold) }
            },
            dismissButton = {
                TextButton(onClick = { showForgeConfirm = false }) {
                    Text("Vérifier encore", color = A4L.TextBody)
                }
            },
        )
    }

    // ── Dissolution : deux verrous avant l'oubli ─────────────────────────
    if (showDissolveWarning) {
        AlertDialog(
            onDismissRequest = { showDissolveWarning = false },
            title = { Text("Dissoudre le noyau ?", style = A4LText.Title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(
                        "Votre fiche d'incarnation et la clé qui en dérive seront " +
                            "effacées de cette station. Rien ne part sur le réseau : en " +
                            "ressaisissant exactement les cinq mêmes données, la même clé " +
                            "renaîtra.",
                        style = A4LText.Body,
                        color = A4L.TextBody,
                    )
                    Text(
                        "⚠ Une seule donnée différente produira une clé entièrement différente.",
                        style = A4LText.Caption,
                        color = A4L.Amber.copy(alpha = 0.85f),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showDissolveWarning = false
                    showDissolveFinal = true
                }) { Text("Continuer", color = A4L.Red.copy(alpha = 0.85f)) }
            },
            dismissButton = {
                TextButton(onClick = { showDissolveWarning = false }) {
                    Text("Annuler", color = A4L.TextBody)
                }
            },
        )
    }

    if (showDissolveFinal) {
        AlertDialog(
            onDismissRequest = { showDissolveFinal = false },
            title = { Text("Dernière confirmation", style = A4LText.Title) },
            text = {
                Text(
                    "La station va oublier votre noyau, immédiatement et sans retour " +
                        "en arrière. Vous reviendrez à l'écran de forge, fiche vierge.",
                    style = A4LText.Body,
                    color = A4L.TextBody,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDissolveFinal = false
                    onDissolve?.invoke()
                }) { Text("Dissoudre définitivement", color = A4L.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showDissolveFinal = false }) {
                    Text("Annuler", color = A4L.TextBody)
                }
            },
        )
    }

    // ── Sélecteurs ────────────────────────────────────────────────────────
    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = LoveKey.birthUtcMillis(birth),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val (y, m, d) = LoveKey.utcDateParts(millis)
                        onBirthChange(birth.copy(year = y, month = m, day = d))
                    }
                    showDatePicker = false
                }) { Text("Valider") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Annuler") }
            },
        ) { DatePicker(state = state) }
    }

    if (showTimePicker) {
        val state = rememberTimePickerState(
            initialHour = birth.hour ?: 12,
            initialMinute = birth.minute ?: 0,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onBirthChange(birth.copy(hour = state.hour, minute = state.minute))
                    showTimePicker = false
                }) { Text("Valider") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Annuler") }
            },
            title = { Text("Heure de naissance", style = A4LText.Title) },
            text = { TimePicker(state = state) },
        )
    }
}

/**
 * Les cinq stations de l'assistant, dans l'ordre d'ATOM4LOVE : on ne demande
 * qu'une chose à la fois, et chacune tient dans l'écran sans rien faire défiler.
 */
private enum class ForgeStep(
    val glyph: String,
    val title: String,
    val subtitle: String,
) {
    Identity("🪪", "Identité", "ce que la station fabrique, et pour qui"),
    Anchor("⚓", "Ancrage", "date · heure · lieu de naissance"),
    Vessel("🧬", "Le vaisseau", "onde biologique · poids de naissance"),
    Singularity("🌀", "Singularité", "la trace originelle, calculée pour vous"),
    Forge("⚛", "Forger", "dernière relecture avant le scellement");

    /** Ce que l'étape exige pour qu'on ait le droit de passer à la suivante. */
    fun isSatisfied(b: BirthData): Boolean = when (this) {
        Identity, Singularity -> true
        Anchor -> b.dateComplete && b.timeComplete && b.lat != null && b.lon != null
        Vessel -> b.wave != null && b.weightKg != null
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
            "Au moment exact de votre naissance, la Terre occupait une position " +
                "précise dans l'espace-temps. Votre noyau s'en dérive : le même " +
                "instant donne toujours la même clé, sur n'importe quelle station.",
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
                "Ni compte, ni mot de passe",
                style = A4LText.Body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = A4L.TextHigh,
            )
            Text(
                "Quatre écrans suffisent : quand et où vous êtes né, votre onde et " +
                    "votre poids de naissance. Rien de tout cela ne part sur le réseau.",
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
                "Une clé temporaire, pour commencer",
                style = A4LText.Body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = A4L.Gold,
            )
            Text(
                "Elle vous suffit pour tout ce qui se passe à portée d'antenne. " +
                    "Votre clé LOVE, elle, sera dérivée par une station Astroport.ONE " +
                    "le jour où vous ouvrirez un MULTIPASS — ce sera proposé juste après.",
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
        SectionLabel("Date et heure de naissance")
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Les cases vides s'affichent en fantôme « JJ MM AAAA » :
            // l'utilisateur voit d'un coup d'œil ce qui reste à définir.
            DigitBox(
                birth.day?.let { "%02d".format(it) } ?: "JJ",
                42.dp, enabled = editable, placeholder = birth.day == null,
                onClick = onPickDate,
            )
            Spacer(Modifier.width(6.dp))
            DigitBox(
                birth.month?.let { "%02d".format(it) } ?: "MM",
                42.dp, enabled = editable, placeholder = birth.month == null,
                onClick = onPickDate,
            )
            Spacer(Modifier.width(6.dp))
            DigitBox(
                birth.year?.toString() ?: "AAAA",
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
                birth.hour?.let { "%02d".format(it) } ?: "HH",
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
                birth.minute?.let { "%02d".format(it) } ?: "MN",
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
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        SectionLabel("Lieu de naissance")
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
                            "Ville, pays…",
                            style = A4LText.Body.copy(fontSize = 14.sp),
                            color = A4L.TextGhost,
                        )
                    }
                    inner()
                },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                LoveKey.formatCoords(birth),
                style = A4LText.Data.copy(fontSize = 10.sp),
                color = A4L.TextMuted,
                modifier = Modifier
                    .background(A4L.Glass, RoundedCornerShape(7.dp))
                    .padding(horizontal = 7.dp, vertical = 4.dp),
            )
            Spacer(Modifier.width(8.dp))
            // 📍 : remplit lieu et coordonnées depuis la position GPS
            // de l'appareil (demande la permission au premier appui).
            Box(
                Modifier
                    .size(30.dp)
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
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
        SectionLabel("Onde biologique")
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

/** Étape 3 — le poids de naissance, au dixième de kilo. */
@Composable
private fun WeightSection(birth: BirthData, editable: Boolean, onBirthChange: (BirthData) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel("Poids de naissance")
            Text(
                LoveKey.formatWeight(birth.weightKg),
                style = A4LText.Data.copy(fontSize = 13.sp),
                color = A4L.TextHigh.copy(alpha = 0.75f),
            )
        }
        WeightSlider(
            value = birth.weightKg,
            enabled = editable,
            onValueChange = { onBirthChange(birth.copy(weightKg = it)) },
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("2,5", style = A4LText.Data.copy(fontSize = 9.sp), color = A4L.TextGhost)
            Text("4,5", style = A4LText.Data.copy(fontSize = 9.sp), color = A4L.TextGhost)
        }
    }
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
        SectionLabel("Singularité · calculée", color = A4L.Cyan.copy(alpha = 0.6f))
        ComputedRow(
            "Conception",
            if (birth.dateComplete && birth.timeComplete && birth.weightKg != null) {
                LoveKey.formatDate(LoveKey.conception(birth))
            } else "—",
        )
        ComputedRow(
            "Gestation",
            birth.weightKg?.let { LoveKey.formatDays(LoveKey.gestationDays(it)) } ?: "—",
        )
        // Le vrai portail : le sommet du polyèdre de Goldberg le plus
        // proche du lieu de naissance (les 12 pentagones de la grille H3).
        ComputedRow(
            "Portail Goldberg",
            if (birth.lat != null && birth.lon != null) {
                GoldbergPortal.nearest(birth.lat, birth.lon).label
            } else "—",
            valueColor = A4L.Cyan,
        )
    }
}

/** Étape 5 — les cinq données, relues d'un bloc avant le scellement. */
@Composable
private fun RecapCard(birth: BirthData) {
    Column(
        Modifier
            .fillMaxWidth()
            .glass(12.dp, A4L.Glass, A4L.Stroke)
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionLabel("Votre incarnation")
        ComputedRow(
            "Naissance",
            if (birth.dateComplete && birth.timeComplete) {
                "%02d/%02d/%04d à %02d:%02d".format(
                    birth.day, birth.month, birth.year, birth.hour, birth.minute,
                )
            } else "—",
        )
        ComputedRow("Lieu", birth.placeName.ifBlank { "—" })
        ComputedRow("Coordonnées", LoveKey.formatCoords(birth))
        ComputedRow("Onde", birth.wave?.let { "${it.symbol} ${it.label}" } ?: "—")
        ComputedRow("Poids", LoveKey.formatWeight(birth.weightKg))
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
                "Ces cinq données ne seront plus jamais modifiables. Ceux qui vous " +
                    "connaissent peuvent les redire : vos proches sont votre phrase de récupération.",
                style = A4LText.Caption,
                color = A4L.Amber.copy(alpha = 0.85f),
            )
            // Dit avant la forge, pas après : la clé d'ici n'est pas encore la
            // clé LOVE, et le jour où une station la dérivera vraiment, le npub
            // d'aujourd'hui sera périmé.
            Text(
                "La clé forgée ici est provisoire : elle vous suffit à portée " +
                    "d'antenne. Votre clé LOVE, elle, sera dérivée par une station " +
                    "Astroport.ONE le jour où vous ouvrirez un MULTIPASS.",
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
            if (birth.complete) "salt ${LoveKey.salt(birth)}" else "salt — fiche incomplète",
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
            if (!birth.dateComplete) add("date")
            if (!birth.timeComplete) add("heure")
            if (birth.lat == null || birth.lon == null) {
                add(
                    if (birth.placeName.isBlank()) "lieu"
                    else "lieu (choisissez dans la liste ou 📍)",
                )
            }
        }
        if (only == null || only == ForgeStep.Vessel) {
            if (birth.wave == null) add("onde")
            if (birth.weightKg == null) add("poids")
        }
    }
    if (missing.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("ⓘ", fontSize = 11.sp, color = A4L.Cyan.copy(alpha = 0.7f))
        Spacer(Modifier.width(7.dp))
        Text(
            "À renseigner : ${missing.joinToString(" · ")}",
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
    onDissolve: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.verticalScroll(rememberScrollState()),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp)) {
            Text("Votre noyau", style = A4LText.H1, color = A4L.TextHigh)
            Spacer(Modifier.height(8.dp))
            Text(
                "Scellé. Ces cinq données ne changent plus : elles redérivent votre " +
                    "clé à chaque démarrage, sur n'importe quelle station.",
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
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 18.dp)
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
                    if (multipassActive) {
                        "MULTIPASS actif · clé LOVE en place"
                    } else {
                        "Ouvrir un MULTIPASS sur AstroPort.ONE"
                    },
                    style = A4LText.Body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    color = accent,
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
                "Dissoudre le noyau",
                style = A4LText.Body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = A4L.Red.copy(alpha = 0.85f),
            )
        }
        // Le launcher garde le nom court ; la filiation s'affiche ici.
        Text(
            "Atom4Love · by AstroPort.ONE",
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
            wave.label,
            style = A4LText.Body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            color = if (selected) A4L.Mint else A4L.TextBody.copy(alpha = 0.55f),
        )
        Text(
            "sexe ${wave.sex}",
            style = A4LText.Data.copy(fontSize = 9.sp),
            color = if (selected) A4L.Mint.copy(alpha = 0.55f) else A4L.TextGhost,
        )
    }
}

/**
 * Curseur du poids de naissance — rail de 3 px, pastille cyan halonée.
 * Tant qu'aucun poids n'est saisi (value null), le rail reste vide : le premier
 * toucher fixe la valeur.
 */
@Composable
private fun WeightSlider(
    value: Float?,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
) {
    val fraction = value?.let { ((it - WEIGHT_MIN) / (WEIGHT_MAX - WEIGHT_MIN)).coerceIn(0f, 1f) }

    // Le poids entre dans le SALT au dixième de kilo près : on arrondit à la saisie.
    fun updateFromFraction(f: Float) {
        val raw = WEIGHT_MIN + f.coerceIn(0f, 1f) * (WEIGHT_MAX - WEIGHT_MIN)
        onValueChange((round(raw * 10f) / 10f).coerceIn(WEIGHT_MIN, WEIGHT_MAX))
    }

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(20.dp)
            .semantics {
                contentDescription = "Poids de naissance"
                progressBarRangeInfo =
                    ProgressBarRangeInfo(value ?: WEIGHT_MIN, WEIGHT_MIN..WEIGHT_MAX)
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset -> updateFromFraction(offset.x / size.width) }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures { change, _ ->
                    updateFromFraction(change.position.x / size.width)
                }
            },
    ) {
        val trackWidth = maxWidth

        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(3.dp)
                .background(A4L.Stroke.copy(alpha = 0.10f), RoundedCornerShape(2.dp)),
        )
        if (fraction != null) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(fraction)
                    .height(3.dp)
                    .background(
                        Brush.horizontalGradient(listOf(A4L.Cyan.tint(0.30f), A4L.Cyan)),
                        RoundedCornerShape(2.dp),
                    ),
            )
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = trackWidth * fraction - 7.5.dp)
                    .size(15.dp)
                    .background(A4L.Cyan, CircleShape),
            )
        }
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
            when {
                forged -> "Clé LOVE scellée"
                complete -> "Forger mon noyau"
                else -> "Complétez votre fiche pour forger"
            },
            style = A4LText.Body.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
            color = if (ready) accent else accent.copy(alpha = 0.45f),
        )
    }
}
