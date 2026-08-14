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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import one.astroport.atom4love.BuildConfig
import one.astroport.atom4love.R
import one.astroport.atom4love.domain.BirthData
import one.astroport.atom4love.domain.KinMaya
import one.astroport.atom4love.domain.Phi2X
import one.astroport.atom4love.nostr.Constellation
import one.astroport.atom4love.ui.components.A4LChip
import one.astroport.atom4love.ui.components.DataBadge
import one.astroport.atom4love.ui.components.LatLon
import one.astroport.atom4love.ui.components.SectionLabel
import one.astroport.atom4love.ui.components.StatusDot
import one.astroport.atom4love.ui.components.WorldMap
import one.astroport.atom4love.ui.components.dashedGlass
import one.astroport.atom4love.ui.components.glass
import one.astroport.atom4love.ui.components.phaseColor
import one.astroport.atom4love.ui.components.screenBackground
import one.astroport.atom4love.ui.theme.A4L
import one.astroport.atom4love.ui.theme.A4LText
import one.astroport.atom4love.ui.theme.tint
import java.util.Locale

/**
 * Un noyau de la constellation, prêt à s'afficher : son certificat, et ce qu'il
 * devient une fois rapporté au nôtre.
 */
private data class Sighting(
    val atom: Constellation.Atom,
    /** null tant que notre propre φ n'est pas calculable. */
    val resonance: Phi2X.Classification?,
    /** À vol d'oiseau depuis notre lieu de naissance, null s'il est inconnu. */
    val distanceKm: Double?,
)

/**
 * 03 · Constellation — la carte de ceux qui ont activé leur clé LOVE.
 *
 * Elle lit le relais **public** et rien d'autre : les certificats ATOM4LOVE
 * n'existent nulle part ailleurs. Un noyau par clé publique, posé à son adresse
 * `a4l:`, c'est-à-dire à son **lieu de naissance** au kilomètre près — pas là où
 * il se trouve. C'est ce que la station scelle au moment de l'activation, et
 * c'est tout ce que le relais sait de la géographie de quiconque.
 *
 * Rien n'est publié depuis cet écran. Nous lisons la constellation ; nous n'y
 * figurons pas encore.
 */
@Composable
fun MapScreen(
    birth: BirthData,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val constellation = remember(scope) { Constellation(scope) }
    val state by constellation.state.collectAsState()

    // Une lecture à l'ouverture. Le certificat est un événement remplaçable :
    // la constellation ne bouge qu'au rythme des activations, un rafraîchi à la
    // demande suffit — inutile de tenir une souscription ouverte.
    LaunchedEffect(Unit) { constellation.refresh() }

    val myPhase = remember(birth) { Phi2X.personalPhase(birth) }
    val home = remember(birth) {
        val lat = birth.lat
        val lon = birth.lon
        if (lat != null && lon != null) LatLon(lat, lon) else null
    }

    var selected by remember { mutableStateOf<String?>(null) }

    val atoms = (state as? Constellation.State.Loaded)?.atoms.orEmpty()
    val sightings = remember(atoms, myPhase, home) {
        atoms
            .map { atom ->
                val theirs = atom.phase
                Sighting(
                    atom = atom,
                    resonance = if (myPhase != null && theirs != null) {
                        Phi2X.classifyResonance(myPhase, theirs)
                    } else {
                        null
                    },
                    distanceKm = home?.let {
                        Phi2X.haversineKm(it.lat, it.lon, atom.place.latDeg, atom.place.lonDeg)
                    },
                )
            }
            // La résonance d'abord quand notre φ est connue — c'est l'ordre de
            // son radar. Sinon la proximité, sinon les derniers arrivés.
            .sortedWith(
                compareByDescending<Sighting> { it.resonance?.percent ?: -1 }
                    .thenBy { it.distanceKm ?: Double.MAX_VALUE }
                    .thenByDescending { it.atom.createdAt },
            )
    }

    Column(
        modifier
            .fillMaxSize()
            .screenBackground(A4L.GlowRadar, A4L.Deep, centerY = 0.05f, radiusFactor = 1.3f)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {

        // ── En-tête ───────────────────────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.map_title),
                style = A4LText.Title,
                color = A4L.TextHigh,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (atoms.isNotEmpty()) {
                    DataBadge(
                        pluralStringResource(R.plurals.map_count, atoms.size, atoms.size),
                        A4L.Cyan,
                        border = A4L.Cyan.tint(0.35f),
                    )
                }
                StatusDot(
                    when (state) {
                        is Constellation.State.Loaded -> A4L.Mint
                        is Constellation.State.Unreachable -> A4L.Orange
                        else -> A4L.TextFaint
                    },
                )
            }
        }

        Text(
            stringResource(R.string.map_birthplaces),
            style = A4LText.Caption,
            color = A4L.TextMuted,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp),
        )

        // ── La carte ──────────────────────────────────────────────────────
        WorldMap(
            atoms = atoms,
            home = home,
            selected = selected,
            onSelect = { selected = it },
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp),
        )

        // ── État de la lecture ────────────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when (state) {
                    Constellation.State.Idle,
                    Constellation.State.Loading,
                    -> stringResource(R.string.map_reading)
                    Constellation.State.Unreachable -> stringResource(R.string.map_unreachable)
                    is Constellation.State.Loaded ->
                        if (atoms.isEmpty()) stringResource(R.string.map_empty)
                        else stringResource(R.string.map_relay, RELAY_LABEL)
                },
                style = A4LText.Caption,
                color = if (state is Constellation.State.Unreachable) A4L.Orange else A4L.TextDim,
                modifier = Modifier.weight(1f, fill = false),
            )
            A4LChip(
                label = stringResource(R.string.map_refresh),
                accent = A4L.Cyan,
                modifier = Modifier.clickable(
                    enabled = state !is Constellation.State.Loading,
                ) { constellation.refresh() },
            )
        }

        // ── La liste ──────────────────────────────────────────────────────
        if (sightings.isNotEmpty()) {
            SectionLabel(
                stringResource(
                    if (myPhase != null) R.string.map_by_resonance else R.string.map_by_arrival,
                ),
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp),
            )
            Column(
                Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sightings.forEach { sighting ->
                    SightingRow(
                        sighting = sighting,
                        selected = sighting.atom.pubkey == selected,
                        onClick = {
                            selected = sighting.atom.pubkey.takeIf { it != selected }
                        },
                    )
                }
            }
        } else if (state is Constellation.State.Loaded) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 18.dp)
                    .dashedGlass(15.dp)
                    .padding(horizontal = 16.dp, vertical = 22.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.map_empty_detail),
                    style = A4LText.Caption,
                    color = A4L.TextDim,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** Une ligne de la constellation : le sceau, la résonance, la distance. */
@Composable
private fun SightingRow(
    sighting: Sighting,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val atom = sighting.atom
    val dot = atom.phase?.let { phaseColor(it) } ?: A4L.TextFaint
    val accent = when {
        sighting.resonance == null -> A4L.TextDim
        sighting.resonance.union -> A4L.Mint
        else -> A4L.Violet
    }

    Row(
        Modifier
            .fillMaxWidth()
            .glass(
                radius = 14.dp,
                background = if (selected) accent.tint(0.08f) else A4L.GlassSoft.copy(alpha = 0.035f),
                border = if (selected) accent.tint(0.32f) else A4L.StrokeSoft,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(dot))

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = atom.kin?.let { kin ->
                    val name = KinMaya.glyphName(kin.glyph)
                    if (name != null) "KIN ${kin.kin} · $name" else "KIN ${kin.kin}"
                } ?: stringResource(R.string.map_no_kin),
                style = A4LText.ItemTitle,
                color = A4L.TextHigh,
                fontWeight = if (selected) FontWeight.SemiBold else null,
            )
            Text(
                text = buildString {
                    append(atom.shortKey)
                    atom.phase?.let { append(" · φ ").append(format(it, 3)) }
                    atom.omegaBio?.let { append(" · ω ").append(format(it, 2)).append(" Hz") }
                },
                style = A4LText.Data.copy(fontSize = 10.sp),
                color = A4L.TextDim,
            )
        }

        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            sighting.resonance?.let { r ->
                DataBadge(
                    // 🤝 union (Δφ ≈ 0) · ⚡ friction (Δφ ≈ π) — le code de ses
                    // écrans, où k seul ne distingue pas les deux.
                    label = (if (r.union) "🤝 " else "⚡ ") + r.percent + " %",
                    color = accent,
                )
            }
            sighting.distanceKm?.let {
                Text(
                    text = String.format(Locale.getDefault(), "%,.0f km", it),
                    style = A4LText.Data.copy(fontSize = 9.5.sp),
                    color = A4L.TextFaint,
                )
            }
        }
    }
}

private fun format(value: Double, decimals: Int): String =
    String.format(Locale.getDefault(), "%.${decimals}f", value)

/** Ce que l'écran nomme : l'hôte du relais, sans le `wss://`. */
private val RELAY_LABEL: String = BuildConfig.NOSTR_DEFAULT_RELAY
    .substringAfter("://")
    .substringBefore('/')
