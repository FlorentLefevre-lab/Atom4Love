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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import one.astroport.atom4love.geo.CommuneApi
import one.astroport.atom4love.geo.PlaceResolver
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
 * 01 · Incarnation — dérivation de la clé LOVE.
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
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
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
        onBirthChange(birth.copy(placeName = c.placeName, lat = c.lat, lon = c.lon))
    }

    // Repli hors France : géocodeur Android au « Terminé » du clavier.
    fun resolveTypedPlace() {
        val query = birth.placeName
        placeSettled = true
        scope.launch {
            PlaceResolver.search(context, query)?.let { p ->
                onBirthChange(birth.copy(placeName = p.name, lat = p.lat, lon = p.lon))
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
                onBirthChange(birth.copy(placeName = p.name, lat = p.lat, lon = p.lon))
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
            .statusBarsPadding(),
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(A4L.Green)
                Text("relay · 1 / 3", style = A4LText.Data, color = A4L.TextDim)
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {

            // ── Titre ─────────────────────────────────────────────────────
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp)) {
                Text("Forger votre noyau", style = A4LText.H1, color = A4L.TextHigh)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Votre clé LOVE se dérive de l'instant où vous êtes né. Le même " +
                        "instant produit toujours la même clé, sur n'importe quelle station.",
                    style = A4LText.Body,
                    color = A4L.TextBody,
                )
            }

            Column(
                Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {

                // ── Date et heure de naissance ────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    SectionLabel("Date et heure de naissance")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DigitBox(birth.day?.let { "%02d".format(it) } ?: "--", 42.dp, enabled = editable) {
                            showDatePicker = true
                        }
                        Spacer(Modifier.width(6.dp))
                        DigitBox(birth.month?.let { "%02d".format(it) } ?: "--", 42.dp, enabled = editable) {
                            showDatePicker = true
                        }
                        Spacer(Modifier.width(6.dp))
                        DigitBox(birth.year?.toString() ?: "----", 60.dp, enabled = editable) {
                            showDatePicker = true
                        }
                        Text(
                            "·",
                            color = A4L.TextGhost,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                        DigitBox(birth.hour?.let { "%02d".format(it) } ?: "--", 42.dp, accent = true, enabled = editable) {
                            showTimePicker = true
                        }
                        Text(
                            ":",
                            style = A4LText.Data,
                            color = A4L.TextGhost,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                        DigitBox(birth.minute?.let { "%02d".format(it) } ?: "--", 42.dp, accent = true, enabled = editable) {
                            showTimePicker = true
                        }
                    }
                }

                // ── Lieu de naissance ─────────────────────────────────────
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
                            onValueChange = {
                                placeSettled = false
                                onBirthChange(birth.copy(placeName = it))
                            },
                            enabled = editable,
                            singleLine = true,
                            textStyle = A4LText.Body.copy(fontSize = 14.sp, color = A4L.TextHigh),
                            cursorBrush = SolidColor(A4L.Cyan),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                focusManager.clearFocus()
                                resolveTypedPlace()
                            }),
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
                                .clickable(enabled = editable && !locating) {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.ACCESS_FINE_LOCATION,
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        locateFromGps()
                                    } else {
                                        locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                    }
                                },
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

                    // Propositions data.gouv.fr : villes et villages de France.
                    if (suggestions.isNotEmpty()) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .glass(11.dp, A4L.Glass, A4L.Cyan.tint(0.20f)),
                        ) {
                            suggestions.forEach { commune ->
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { chooseCommune(commune) }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
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

                // ── Onde biologique ───────────────────────────────────────
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

                // ── Poids de naissance ────────────────────────────────────
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

                // ── Calculé pour vous ─────────────────────────────────────
                Column(
                    Modifier
                        .fillMaxWidth()
                        .glass(12.dp, A4L.Cyan.tint(0.05f), A4L.Cyan.tint(0.16f))
                        .padding(horizontal = 15.dp, vertical = 13.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SectionLabel("Calculé pour vous", color = A4L.Cyan.copy(alpha = 0.6f))
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
                    // TODO : projeter (lat, lon) sur le polyèdre de Goldberg pour obtenir
                    // la vraie tuile. En attendant, le portail est celui de la maquette.
                    ComputedRow("Portail Goldberg", "a4l:P02 · Sirius", valueColor = A4L.Cyan)
                }

                // ── Avertissement ─────────────────────────────────────────
                Row(
                    Modifier
                        .fillMaxWidth()
                        .glass(11.dp, A4L.Amber.tint(0.06f), A4L.Amber.tint(0.24f))
                        .padding(horizontal = 13.dp, vertical = 11.dp),
                ) {
                    Text("⚠", fontSize = 12.sp, color = A4L.Amber)
                    Spacer(Modifier.width(9.dp))
                    Text(
                        "Ces cinq données ne seront plus jamais modifiables. Ceux qui vous " +
                            "connaissent peuvent les redire : vos proches sont votre phrase de récupération.",
                        style = A4LText.Caption,
                        color = A4L.Amber.copy(alpha = 0.85f),
                    )
                }
            }
        }

        // ── SALT + action ─────────────────────────────────────────────────
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 22.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
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
            ForgeButton(forged = forged, complete = birth.complete, onClick = onForge)
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

/** Une case de la ligne date/heure. Les cases d'heure sont cyan. */
@Composable
private fun DigitBox(
    text: String,
    width: Dp,
    accent: Boolean = false,
    enabled: Boolean = true,
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
            style = A4LText.Data.copy(fontSize = 15.sp),
            color = if (accent) A4L.Cyan else A4L.TextHigh,
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
                complete -> "Forger ma clé LOVE"
                else -> "Complétez votre fiche pour forger"
            },
            style = A4LText.Body.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
            color = if (ready) accent else accent.copy(alpha = 0.45f),
        )
    }
}
