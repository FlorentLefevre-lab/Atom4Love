package one.astroport.atom4love.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import one.astroport.atom4love.BuildConfig
import one.astroport.atom4love.R
import one.astroport.atom4love.domain.BirthData
import one.astroport.atom4love.domain.KinMaya
import one.astroport.atom4love.domain.Phi2X
import one.astroport.atom4love.nostr.Certificate
import one.astroport.atom4love.nostr.Constellation
import one.astroport.atom4love.nostr.NostrKeys
import one.astroport.atom4love.ui.components.A4LChip
import one.astroport.atom4love.ui.components.Basemap
import one.astroport.atom4love.ui.components.BasemapPicker
import one.astroport.atom4love.ui.components.DataBadge
import one.astroport.atom4love.ui.components.CONTACT_SCALE
import one.astroport.atom4love.ui.components.LatLon
import one.astroport.atom4love.ui.components.MapFocus
import one.astroport.atom4love.ui.components.RECENTRE_SCALE
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
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
    /**
     * Notre propre certificat, une fois publié. Il reste sur la carte — s'y voir
     * est tout l'intérêt — mais sa ligne ne porte ni résonance ni distance :
     * « 🤝 100 % · 1 km » n'apprend rien, c'est soi comparé à soi, et le
     * kilomètre n'est que l'arrondi de la maille.
     */
    val isSelf: Boolean,
    /**
     * Fraîchement entré dans la constellation — sa clé LOVE vient d'être
     * activée par la station. La carte lui fait honneur : il passe devant tout
     * le monde, juste derrière nous, quelle que soit sa résonance.
     *
     * C'est le seul endroit du jeu où l'ordre ne dépend pas de ce qu'on a en
     * commun avec quelqu'un. Une semaine durant, arriver suffit.
     */
    val newcomer: Boolean,
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
    /** Le noyau qui signerait notre certificat. Null tant qu'il n'y en a pas. */
    keys: NostrKeys? = null,
    /**
     * L'instance que l'application tient déjà pour la veille des bienvenues.
     * Null en aperçu et dans les tests, où cet écran fait la sienne : la Carte
     * doit pouvoir s'afficher toute seule.
     */
    shared: Constellation? = null,
) {
    val scope = rememberCoroutineScope()
    val constellation = shared ?: remember(scope) { Constellation(scope) }
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
    // Le fond de carte. Le trait de côte par défaut : c'est le seul qui ne dise
    // à personne ce qu'on regarde, et changer d'avis coûte un appui.
    var basemap by rememberSaveable { mutableStateOf(Basemap.Coastline) }
    // Où la carte doit se porter. Un numéro d'ordre plutôt qu'un booléen : deux
    // demandes de suite vers le même point doivent toutes les deux partir.
    var focus by remember { mutableStateOf<MapFocus?>(null) }
    var ticket by remember { mutableIntStateOf(0) }

    // ── La sélection va dans les deux sens ────────────────────────────────
    // Toucher un point ouvre la liste et y fait défiler jusqu'à la ligne ;
    // toucher une ligne porte la carte sur le point. `selected` est le fil
    // commun, ces deux-là ne font qu'y répondre.
    val scroll = rememberScrollState()
    val rowTops = remember { mutableStateMapOf<String, Int>() }
    var reveal by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    // Repliée par défaut : la carte prend la place, la liste se déplie quand on
    // la demande. Une fois ouverte, elle le reste — y compris à la rotation.
    var resonancesOpen by rememberSaveable { mutableStateOf(false) }
    val chevron by animateFloatAsState(if (resonancesOpen) 0f else -90f, label = "chevron")
    // Le battement de la ligne désignée. Calculé une fois ici et prêté à la
    // liste : une transition infinie par ligne tournerait pour cent lignes dont
    // une seule s'en sert.
    val heartbeat by rememberInfiniteTransition(label = "sélection").animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(620, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "battement",
    )
    /**
     * Faire venir la ligne sous les yeux.
     *
     * Elle n'existe pas encore au moment où on la demande — l'accordéon vient
     * de s'ouvrir, la mise en page n'a pas eu lieu. On attend donc qu'elle
     * s'annonce (`onGloballyPositioned`) plutôt que de deviner un délai, et on
     * défile de ce qu'il faut pour l'amener à hauteur de lecture.
     */
    LaunchedEffect(reveal) {
        if (reveal == 0) return@LaunchedEffect
        val key = selected ?: return@LaunchedEffect
        resonancesOpen = true
        val top = withTimeoutOrNull(REVEAL_TIMEOUT_MS) {
            snapshotFlow { rowTops[key] }.filterNotNull().first()
        } ?: return@LaunchedEffect
        val target = with(density) { REVEAL_FROM_TOP.toPx() }
        scroll.animateScrollBy(top - target)
    }

    val atoms = (state as? Constellation.State.Loaded)?.atoms.orEmpty()
    // L'instant du calcul est celui de la lecture du relais, pas l'heure
    // courante : sans ça, la nouveauté se recalculerait à chaque recomposition
    // et la liste pourrait se réordonner sous le doigt.
    val readAtMs = (state as? Constellation.State.Loaded)?.readAtMs ?: 0L
    val sightings = remember(atoms, myPhase, home, keys, readAtMs) {
        atoms
            .map { atom ->
                val theirs = atom.phase
                val self = atom.pubkey == keys?.publicKeyHex
                Sighting(
                    atom = atom,
                    resonance = if (!self && myPhase != null && theirs != null) {
                        Phi2X.classifyResonance(myPhase, theirs)
                    } else {
                        null
                    },
                    distanceKm = if (self) {
                        null
                    } else {
                        home?.let {
                            Phi2X.haversineKm(it.lat, it.lon, atom.place.latDeg, atom.place.lonDeg)
                        }
                    },
                    isSelf = self,
                    newcomer = !self && atom.isNewcomer(readAtMs),
                )
            }
            // Nous d'abord, puis **ceux qui viennent d'arriver** — l'honneur
            // passe avant l'affinité, et c'est tout le propos : on ne fait pas
            // fête à quelqu'un parce qu'il nous ressemble. Ensuite seulement la
            // résonance quand notre φ est connue, comme sur son radar, puis la
            // proximité, puis les derniers scellés.
            .sortedWith(
                compareByDescending<Sighting> { it.isSelf }
                    .thenByDescending { it.newcomer }
                    .thenByDescending { it.resonance?.percent ?: -1 }
                    .thenBy { it.distanceKm ?: Double.MAX_VALUE }
                    .thenByDescending { it.atom.createdAt },
            )
    }

    Column(
        modifier
            .fillMaxSize()
            .screenBackground(A4L.GlowRadar, A4L.Deep, centerY = 0.05f, radiusFactor = 1.3f)
            .statusBarsPadding()
            .verticalScroll(scroll),
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
            onSelect = { pubkey ->
                selected = pubkey
                // Un point touché ne se contente pas de s'entourer de blanc :
                // il déplie la liste et s'y fait rejoindre.
                if (pubkey != null) reveal++
            },
            basemap = basemap,
            focus = focus,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp),
        )

        // Le fond au choix, sous la carte : ce n'est pas un réglage qu'on range
        // dans un menu, c'est une façon de regarder qui change avec ce qu'on
        // cherche — la côte pour situer, la rue pour reconnaître.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasemapPicker(current = basemap, onSelect = { basemap = it })
            if (home != null) {
                A4LChip(
                    label = stringResource(R.string.map_recentre),
                    accent = A4L.Mint,
                    modifier = Modifier.clickable {
                        home?.let { focus = MapFocus(it, RECENTRE_SCALE, ++ticket) }
                    },
                )
            }
        }

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
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // ⚠ Une puce 🏠 précédait celle-ci : le calque des résidences,
                // lu sur `d=atom4love-home`. Retirée le 15/08 avec le calque —
                // voir le mot dans `Constellation`.
                A4LChip(
                    label = stringResource(R.string.map_refresh),
                    accent = A4L.Cyan,
                    modifier = Modifier.clickable(
                        enabled = state !is Constellation.State.Loading,
                    ) { constellation.refresh() },
                )
            }
        }

        // ── Notre place dans la constellation, ou son absence ──────────────
        if (keys != null) {
            CertificateCard(
                keys = keys,
                birth = birth,
                onPublished = { constellation.refresh() },
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp),
            )
        }

        // ── Les résonances, repliées ──────────────────────────────────────
        // La carte est ce qu'on vient voir ; la liste, ce qu'on vient
        // consulter. Elle attend donc qu'on la demande — et son compte, dans
        // l'en-tête, dit qu'elle a quelque chose à dire.
        // ── L'honneur aux nouveaux ────────────────────────────────────────
        //
        // Il se dit **avant** la liste et hors du pli : c'est la seule chose de
        // cet écran qui ne parle pas de soi, et elle ne doit pas attendre qu'on
        // déplie quoi que ce soit pour être vue. Le pli, lui, s'ouvre déjà tout
        // seul quand on arrive par la carte.
        val newcomers = sightings.count { it.newcomer }
        if (newcomers > 0) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 18.dp)
                    .glass(14.dp, A4L.Gold.tint(0.10f), A4L.Gold.tint(0.34f))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("✨", fontSize = 18.sp)
                Text(
                    pluralStringResource(R.plurals.map_newcomers_banner, newcomers, newcomers),
                    style = A4LText.Caption.copy(fontSize = 11.5.sp),
                    color = A4L.Gold,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (sightings.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { resonancesOpen = !resonancesOpen }
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // À gauche, devant le titre : c'est là qu'on lit le sens d'un
                // pli, comme les puces d'une arborescence.
                Text(
                    "▾",
                    style = A4LText.Data.copy(fontSize = 17.sp),
                    color = A4L.TextMuted,
                    modifier = Modifier.rotate(chevron),
                )
                // Le compte contre le titre, et non à l'autre bout de la ligne :
                // il dit combien il y a **derrière ce pli-là**, pas combien il y
                // a sur l'écran. Collé, il se lit comme une précision ; isolé à
                // droite, il se lisait comme un chiffre de plus.
                SectionLabel(
                    stringResource(
                        if (myPhase != null) R.string.map_by_resonance else R.string.map_by_arrival,
                    ) + " (${sightings.size})",
                )
            }
            AnimatedVisibility(visible = resonancesOpen) {
                Column(
                    Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    sightings.forEach { sighting ->
                        val place = sighting.atom.place
                        SightingRow(
                            sighting = sighting,
                            selected = sighting.atom.pubkey == selected,
                            heartbeat = heartbeat,
                            onClick = {
                                val wasSelected = sighting.atom.pubkey == selected
                                selected = sighting.atom.pubkey.takeUnless { wasSelected }
                                // Toucher une ligne montre où elle est. La
                                // reboucler ferme la sélection sans bouger la
                                // carte : on n'a rien demandé de nouveau.
                                if (!wasSelected) {
                                    focus = MapFocus(
                                        LatLon(place.latDeg, place.lonDeg),
                                        CONTACT_SCALE,
                                        ++ticket,
                                    )
                                }
                            },
                            modifier = Modifier.onGloballyPositioned {
                                rowTops[sighting.atom.pubkey] = it.positionInRoot().y.toInt()
                            },
                        )
                    }
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

/**
 * Notre propre place — ou son absence, qui est ce qu'on voit d'abord.
 *
 * Publier son certificat est un geste **irréversible et vers l'extérieur** : un
 * relais public le garde, et d'autres l'auront recopié avant qu'on y repense.
 * D'où la confirmation, qui ne dit pas « êtes-vous sûr » mais montre exactement
 * ce qui partira — l'adresse à la maille du kilomètre, le sceau, les trois
 * nombres, et la clé qui signe.
 */
@Composable
private fun CertificateCard(
    keys: NostrKeys,
    birth: BirthData,
    onPublished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val certificate = remember(scope) { Certificate(scope) }
    val state by certificate.state.collectAsState()
    var confirming by remember { mutableStateOf(false) }

    LaunchedEffect(keys) { certificate.check(keys) }
    LaunchedEffect(state) { if (state is Certificate.State.Published) onPublished() }

    // Ce que le certificat dirait, construit sans rien envoyer.
    val draft = remember(keys, birth) { certificate.build(keys, birth) }

    if (confirming && draft != null) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            containerColor = A4L.Deep,
            title = {
                Text(
                    stringResource(R.string.cert_confirm_title, RELAY_LABEL),
                    style = A4LText.H2,
                    color = A4L.TextHigh,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(
                        stringResource(R.string.cert_confirm_body),
                        style = A4LText.Body,
                        color = A4L.TextBody,
                    )
                    // Les tags tels quels : c'est la seule façon honnête de dire
                    // « voilà ce qui part » — une paraphrase en oublierait un.
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .glass(12.dp)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        draft.tags.forEach { tag ->
                            if (tag.size >= 2) {
                                Text(
                                    "${tag[0]} · ${tag[1]}",
                                    style = A4LText.Data.copy(fontSize = 9.5.sp),
                                    color = A4L.TextDim,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Text(
                            draft.content,
                            style = A4LText.Data.copy(fontSize = 9.5.sp),
                            color = A4L.TextDim,
                        )
                        Text(
                            keys.npub,
                            style = A4LText.Data.copy(fontSize = 9.5.sp),
                            color = A4L.TextFaint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            confirmButton = {
                Text(
                    stringResource(R.string.cert_confirm_ok),
                    style = A4LText.Caption,
                    color = A4L.Mint,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            confirming = false
                            certificate.publish(keys, birth)
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            },
            dismissButton = {
                Text(
                    stringResource(R.string.cert_confirm_cancel),
                    style = A4LText.Caption,
                    color = A4L.TextMuted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { confirming = false }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            },
        )
    }

    val accent = when (state) {
        is Certificate.State.Published -> A4L.Mint
        is Certificate.State.Refused -> A4L.Orange
        else -> A4L.Cyan
    }
    // La station a écrit ce certificat sous un secret qui n'est pas le nôtre :
    // le remplacer effacerait le lien chiffré vers son compte, sans retour.
    val stationOwned = (state as? Certificate.State.Present)?.fromStation == true

    // ⚠ Dans ce cas-là, le panneau **ne paraît plus du tout** (15/08). Il
    // portait un paragraphe — « votre certificat a été publié par une station
    // Astroport, sous un secret qu'elle est seule à détenir… » — et aucun
    // bouton, puisqu'il n'y a précisément rien à faire. Un bloc qui explique
    // longuement pourquoi il ne propose rien est du bruit : tout va bien, on
    // est sur la carte, et la carte le montre déjà.
    if (stationOwned) return

    val actionable = draft != null &&
        state !is Certificate.State.Publishing && state !is Certificate.State.Checking

    Column(
        modifier
            .fillMaxWidth()
            .glass(15.dp, accent.tint(0.05f), accent.tint(0.22f))
            .padding(horizontal = 15.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = when (val s = state) {
                Certificate.State.Unknown, Certificate.State.Checking ->
                    stringResource(R.string.cert_checking)
                Certificate.State.Absent ->
                    if (draft == null) {
                        stringResource(R.string.cert_incomplete)
                    } else {
                        stringResource(R.string.cert_absent)
                    }
                // Le cas `fromStation` ne descend jamais jusqu'ici : la garde
                // du haut a déjà rendu le panneau invisible.
                is Certificate.State.Present -> stringResource(R.string.cert_present)
                Certificate.State.Publishing -> stringResource(R.string.cert_publishing)
                is Certificate.State.Published -> stringResource(R.string.cert_published)
                is Certificate.State.Refused -> stringResource(R.string.cert_refused, s.reason)
            },
            style = A4LText.Body,
            color = if (state is Certificate.State.Refused) A4L.Orange else A4L.TextBody,
        )
        if (actionable) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .glass(12.dp, accent.tint(0.10f), accent.tint(0.34f))
                    .clickable { confirming = true },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(
                        if (state is Certificate.State.Present ||
                            state is Certificate.State.Published
                        ) {
                            R.string.cert_republish
                        } else {
                            R.string.cert_publish
                        },
                    ),
                    style = A4LText.Body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    color = accent,
                )
            }
        }
    }
}

/** Une ligne de la constellation : le sceau, la résonance, la distance. */
@Composable
private fun SightingRow(
    sighting: Sighting,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * L'opacité du liseré quand la ligne est désignée — elle bat.
     *
     * On arrive ici depuis la carte, en ayant touché un point à l'autre bout de
     * l'écran : il faut que l'œil retrouve **où** il a été mené. Un liseré fin
     * et fixe se confondait avec les autres lignes ; celui-ci est épais et il
     * respire.
     */
    heartbeat: Float = 1f,
) {
    val atom = sighting.atom
    val dot = atom.phase?.let { phaseColor(it) } ?: A4L.TextFaint
    val accent = when {
        sighting.isSelf -> A4L.Cyan
        // L'honneur passe devant la résonance dans la couleur comme dans
        // l'ordre : une ligne d'or, qui ne ressemble à aucune autre.
        sighting.newcomer -> A4L.Gold
        sighting.resonance == null -> A4L.TextDim
        sighting.resonance.union -> A4L.Mint
        else -> A4L.Violet
    }

    Row(
        modifier
            .fillMaxWidth()
            .glass(
                radius = 14.dp,
                background = if (selected) accent.tint(0.10f) else A4L.GlassSoft.copy(alpha = 0.035f),
                border = if (selected) accent.copy(alpha = heartbeat) else A4L.StrokeSoft,
                borderWidth = if (selected) 2.5.dp else 1.dp,
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
                    // ⚠ L'onde biologique s'écrivait ici, « · ω 304,53 Hz ».
                    // Partie le 15/08 avec toute la formule de Watson : on ne
                    // la calcule plus, on ne la publie plus, et on ne la lit
                    // même plus dans les certificats des autres.
                },
                style = A4LText.Data.copy(fontSize = 10.sp),
                color = A4L.TextDim,
            )
        }

        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (sighting.isSelf) {
                DataBadge(stringResource(R.string.map_you), A4L.Cyan)
            }
            if (sighting.newcomer) {
                DataBadge(
                    glyph = "✨",
                    label = stringResource(R.string.map_newcomer),
                    color = A4L.Gold,
                )
            }
            sighting.resonance?.let { r ->
                DataBadge(
                    // 🤝 union (Δφ ≈ 0) · ⚡ friction (Δφ ≈ π) — le code de ses
                    // écrans, où k seul ne distingue pas les deux.
                    glyph = if (r.union) "🤝" else "⚡",
                    label = "${r.percent} %",
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

/** À quelle hauteur de l'écran on amène la ligne qu'on vient de désigner. */
private val REVEAL_FROM_TOP = 300.dp

/** Au-delà, la ligne ne viendra pas : inutile de retenir le défilement. */
private const val REVEAL_TIMEOUT_MS = 1_500L

private fun format(value: Double, decimals: Int): String =
    String.format(Locale.getDefault(), "%.${decimals}f", value)

/** Ce que l'écran nomme : l'hôte du relais, sans le `wss://`. */
private val RELAY_LABEL: String = BuildConfig.NOSTR_DEFAULT_RELAY
    .substringAfter("://")
    .substringBefore('/')
