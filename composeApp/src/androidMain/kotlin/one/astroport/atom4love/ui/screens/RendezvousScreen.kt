package one.astroport.atom4love.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import one.astroport.atom4love.R
import one.astroport.atom4love.domain.KinMaya
import one.astroport.atom4love.domain.Phi2X
import one.astroport.atom4love.proximity.NeighborRegistry
import one.astroport.atom4love.proximity.ProximityPayload
import one.astroport.atom4love.proximity.Rendezvous
import one.astroport.atom4love.ui.theme.A4L
import one.astroport.atom4love.ui.theme.A4LDark
import one.astroport.atom4love.ui.theme.A4LText
import one.astroport.atom4love.ui.theme.LocalA4L
import one.astroport.atom4love.ui.theme.tint

/**
 * 🔦 La reconnaissance — le dernier mètre, franchi par les yeux.
 *
 * La chaleur a mené au bon mètre carré et s'est tue : le RSSI ne désigne pas un
 * corps. Ici l'écran devient une lanterne et bat un rythme que **l'autre
 * téléphone calcule tout seul**, à partir des deux φ qui étaient déjà dans
 * l'air. On lève l'écran, on cherche dans la salle celui qui bat pareil, et
 * c'est fini : la carte a un visage.
 *
 * Ce que ça coûte en vie privée : rien. Aucun octet de plus n'est diffusé, le
 * motif ne circule jamais (cf. [Rendezvous]). Et l'accord des deux est dans le
 * silence : un téléphone ne bat que pour la carte que son porteur a retournée,
 * si bien qu'un seul qui cherche clignote seul, sans que personne l'apprenne.
 *
 * **Le noir est voulu, y compris en thème clair.** Cet écran n'est pas une page
 * qu'on lit, c'est un signal qu'on montre à dix mètres, et un fond clair
 * mangerait le contraste des éclairs. Il ne suffit pas pour autant de peindre
 * un fond sombre : chaque teinte lue ici vient du même
 * [one.astroport.atom4love.ui.theme.LocalA4L] que le reste de l'application, et
 * en thème jour elles reviendraient toutes claires — texte pâle sur noir, ou
 * pire, le fond lui-même clair (vu sur la tablette le 15/08). La lanterne
 * **impose donc la palette de nuit** à tout ce qu'elle contient : une seule
 * décision, et l'accent, la chaleur et les textes suivent ensemble.
 *
 * **L'amplitude aussi est voulue.** Le motif atteint par instants 6 Hz, au-delà
 * des 3 Hz en dessous desquels un clignotement plein cadre est réputé sans
 * risque photosensible. Le fond ne fait donc qu'un lavis discret, et l'éclair
 * franc reste confiné au pictogramme et à ses anneaux — plus sûr, et de toute
 * façon plus lisible de loin qu'un rectangle qui s'allume.
 */
@Composable
fun RendezvousScreen(
    /** La carte qu'on suit — la dernière connue si le pair vient de sauter un balayage. */
    card: NeighborRegistry.Neighbor,
    /**
     * Les autres cartes cherchées en même temps, la première comprise.
     *
     * Chercher à plusieurs multiplie les chances que le geste soit joué en face
     * au même moment : mesuré sur 400 salles, la première carte d'une main est
     * réciproque deux fois sur trois, mais le **rang moyen chez l'autre est de
     * 0,5** — donc couvrir les deux premières cartes couvre presque tout le
     * monde. Les fenêtres de [Rendezvous] font que les deux appareils jouent
     * une paire donnée pendant les mêmes secondes, sans rien s'échanger.
     */
    alsoSeeking: List<NeighborRegistry.Neighbor> = emptyList(),
    /** Notre propre signature, pour la résonance et pour le rythme. */
    own: ProximityPayload.Signature,
    /** Vrai tant que le pair est effectivement entendu à l'instant. */
    inRange: Boolean,
    onClose: () -> Unit,
    /**
     * Son pseudo, quand un lien attesté nous l'a appris — la même règle qu'au
     * Plateau et au journal : **le pseudo en gras, le sceau entre parenthèses**.
     * Des deux noms, celui qui désigne quelqu'un est le pseudo ; le sceau, on
     * l'a déjà sous les yeux, en grand, et il bat.
     */
    pseudo: String? = null,
) {
    CompositionLocalProvider(LocalA4L provides A4LDark) {
        Lantern(
            card = card,
            alsoSeeking = alsoSeeking,
            own = own,
            inRange = inRange,
            onClose = onClose,
            pseudo = pseudo,
        )
    }
}

@Composable
private fun Lantern(
    card: NeighborRegistry.Neighbor,
    alsoSeeking: List<NeighborRegistry.Neighbor>,
    own: ProximityPayload.Signature,
    inRange: Boolean,
    onClose: () -> Unit,
    pseudo: String?,
) {
    // L'horloge murale, relue à chaque image — c'est elle qui décide de la
    // fenêtre en cours autant que du pas du motif.
    val clock by produceState(System.currentTimeMillis()) {
        while (true) withFrameMillis { value = System.currentTimeMillis() }
    }

    // ── Quelle carte se joue en ce moment ─────────────────────────────────
    //
    // La fenêtre appartient à la PAIRE, pas au rang dans la liste : les deux
    // appareils tombent donc sur la même carte commune aux mêmes secondes,
    // même s'ils en cherchent trois chacun dans un ordre différent. Quand deux
    // des cartes cherchées partagent une fenêtre, la plus forte l'emporte —
    // arbitrairement, mais **de la même façon des deux côtés**, la résonance
    // étant symétrique.
    val seeking = remember(card, alsoSeeking) { (listOf(card) + alsoSeeking).distinctBy { it.identity } }
    val current = remember(seeking, own.phase, Rendezvous.windowAt(clock)) {
        val window = Rendezvous.windowAt(clock)
        seeking
            .filter { Rendezvous.windowOf(own.phase, it.signature.phase) == window }
            .maxByOrNull { n ->
                val p = n.signature.phase
                if (own.phase != null && p != null) Phi2X.resonanceK(own.phase, p) else -1.0
            }
            // Une seule carte cherchée : elle se joue tout le temps, sans quoi
            // l'écran serait noir deux tiers du temps pour rien.
            ?: card.takeIf { seeking.size == 1 }
    }
    val shown = current ?: card
    val waiting = current == null
    val theirs = shown.signature
    val beat = remember(own.phase, theirs.phase) { Rendezvous.of(own.phase, theirs.phase) }
    val classification = own.phase?.let { mine ->
        theirs.phase?.let { Phi2X.classifyResonance(mine, it) }
    }
    val accent = when {
        classification == null -> A4L.Cyan
        classification.union -> A4L.Mint
        else -> A4L.Violet
    }

    // Le motif est calé sur le temps Unix absolu, seul repère que deux
    // appareils partagent sans se parler — la même horloge que les fenêtres.
    val now = clock
    // ⚠ Hors fenêtre, on ne bat PAS : battre la figure d'une paire au mauvais
    // moment ferait chercher pour rien celui d'en face, qui joue alors une
    // autre paire. Le noir est le prix de la synchronisation.
    val glow = if (waiting) 0f else beat?.glowAt(now) ?: 0f
    val slot = beat?.slotAt(now) ?: 0

    // L'instant où l'on est parti chercher cette carte-ci. Clé sur l'identité :
    // fermer et rouvrir sur quelqu'un d'autre repart de zéro, un trou de radio
    // sur la même personne ne remet pas le compteur à plat.

    ScreenAsLantern()
    beat?.let { PulseInTheHand(it, slot) }

    Box(
        Modifier
            .fillMaxSize()
            .background(A4L.Void)
            .background(
                Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.22f * glow), Color.Transparent),
                    center = Offset.Unspecified,
                    radius = Float.POSITIVE_INFINITY,
                ),
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {

        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.rendezvous_title),
                style = A4LText.SectionLabel,
                color = accent.copy(alpha = 0.75f),
            )
            Box(
                Modifier
                    .size(30.dp)
                    .background(Color.White.copy(alpha = 0.07f), CircleShape)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Text("✕", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f)) }
        }

        // ⚠ **La colonne défile.** Vu sur l'A5 le 20/08 : la note « les deux
        // téléphones calculent ce rythme tout seuls… » était coupée net par la
        // barre du bas, et l'horloge de recherche qui la suit ne s'affichait
        // pas du tout. Un écran de 1920 px de haut suffit à faire déborder ce
        // qui tient sur un Pixel — et cet écran-là est justement celui qu'on
        // tient à bout de bras dans une salle, sans pouvoir deviner qu'il
        // manque quelque chose. Centrée tant que ça tient, défilante sinon.
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {

            // ── Le signal ─────────────────────────────────────────────────
            Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
                Halo(glow = glow, accent = accent)
                Text(
                    KinMaya.glyphEmoji(theirs.glyph),
                    fontSize = 96.sp,
                    modifier = Modifier
                        .alpha(0.30f + 0.70f * glow)
                        .scale(1f + 0.10f * glow),
                )
            }

            Spacer(Modifier.height(10.dp))
            val seal = "(${KinMaya.glyphName(theirs.glyph) ?: stringResource(R.string.board_no_seal)})"
            if (pseudo != null) {
                Text(
                    pseudo,
                    style = A4LText.H2,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.92f),
                )
                Text(
                    seal,
                    style = A4LText.Body,
                    color = Color.White.copy(alpha = 0.55f),
                )
            } else {
                // Personne ne nous l'a nommée : le sceau tient la place du nom,
                // et garde ses parenthèses pour rester le même mot partout.
                Text(
                    seal,
                    style = A4LText.H2,
                    color = Color.White.copy(alpha = 0.92f),
                )
            }

            // ── La chaleur, toujours vivante ──────────────────────────────
            Spacer(Modifier.height(8.dp))
            if (inRange) {
                // Le signal LISSÉ, jamais le brut : deux appareils posés qui ne
                // bougent pas font 18 dB d'amplitude, de quoi changer d'état
                // une fois sur deux. Et la mémoire de l'état précédent, pour
                // que l'hystérésis ait de quoi mordre.
                var last by remember(card.identity) { mutableStateOf<Warmth?>(null) }
                val warmth = Warmth.of(card.rssiSmoothed, card.txPowerDbm, last)
                LaunchedEffect(warmth) { last = warmth }
                val metres = Warmth.metres(card.rssiSmoothed, card.txPowerDbm)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(warmth.glyph, fontSize = 15.sp)
                    Text(
                        stringResource(warmth.labelRes),
                        style = A4LText.Body,
                        color = warmth.color,
                    )
                    // Une distance qui s'annonce comme approximative, et qui se
                    // tait au-delà de la portée utile mesurée (8 m) : le modèle
                    // log-distance y donne encore des nombres, mais plus aucune
                    // information — on est au plancher du récepteur.
                    Text(
                        if (metres > Warmth.USEFUL_RANGE_METRES) {
                            stringResource(
                                R.string.board_warmth_distance_far,
                                stringResource(
                                    R.string.format_metres,
                                    Warmth.USEFUL_RANGE_METRES.toFloat(),
                                ),
                            )
                        } else {
                            stringResource(
                                R.string.board_warmth_distance,
                                if (metres < 10) {
                                    stringResource(R.string.format_metres_sub, metres.toFloat())
                                } else {
                                    stringResource(R.string.format_metres, metres.toFloat())
                                },
                            )
                        },
                        style = A4LText.Data,
                        color = warmth.color.copy(alpha = 0.75f),
                    )
                    classification?.let {
                        Text(
                            "· ${if (it.union) "🤝" else "⚡"} ${it.percent} %",
                            style = A4LText.Body,
                            color = accent.copy(alpha = 0.85f),
                        )
                    }
                }
            } else {
                Text(
                    stringResource(R.string.rendezvous_out_of_range),
                    style = A4LText.Body,
                    color = A4L.TextDim,
                )
            }

            // ── La figure, pour la comparer d'un coup d'œil ───────────────
            Spacer(Modifier.height(30.dp))
            if (beat != null) {
                RhythmStrip(beat = beat, slot = slot, accent = accent)
                // ⚠ « Levez votre écran. Cherchez dans la salle celui qui bat
                // comme le vôtre. » retirée le 20/08 à la demande de Florent.
                // Elle disait à voix haute ce que l'écran fait déjà voir : un
                // glyphe qui bat, une figure à comparer. Le geste est celui
                // qu'on invente en tenant l'appareil, pas celui qu'on lit.
                Spacer(Modifier.height(26.dp))
                // Ce n'est plus une note en bas de page mais la consigne : elle
                // reprend la place, et le poids, de la phrase retirée au-dessus.
                Text(
                    stringResource(R.string.rendezvous_mutual),
                    style = A4LText.Body,
                    color = Color.White.copy(alpha = 0.86f),
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    stringResource(R.string.rendezvous_no_phase),
                    style = A4LText.Body,
                    color = A4L.TextDim,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Deux anneaux qui partent du pictogramme à chaque éclair : ce qui se voit de
 * loin n'est pas l'éclat, c'est le mouvement.
 */
@Composable
private fun Halo(glow: Float, accent: Color) {
    Canvas(Modifier.fillMaxSize()) {
        if (glow <= 0.001f) return@Canvas
        val spread = 1f - glow
        listOf(0f, 0.35f).forEach { delay ->
            val progress = (spread - delay).coerceAtLeast(0f) / (1f - delay)
            if (progress <= 0f) return@forEach
            drawCircle(
                color = accent.copy(alpha = 0.55f * (1f - progress) * glow.coerceAtLeast(0.15f)),
                radius = size.minDimension * (0.24f + 0.26f * progress),
                style = Stroke(width = 3.dp.toPx() * (1f - progress * 0.6f)),
            )
        }
        drawCircle(
            color = accent.copy(alpha = 0.14f * glow),
            radius = size.minDimension * 0.30f,
        )
    }
}

/**
 * Le motif en clair, seize pas côte à côte, le pas courant marqué.
 *
 * Il sert à deux choses : comparer deux figures posées à plat sur une table
 * quand la salle est trop éclairée pour comparer deux éclairs, et donner à voir
 * que le rythme n'est pas décoratif — c'est un nombre, le même des deux côtés.
 */
@Composable
private fun RhythmStrip(beat: Rendezvous.Beat, slot: Int, accent: Color) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(18.dp),
    ) {
        val step = size.width / Rendezvous.SLOTS
        for (i in 0 until Rendezvous.SLOTS) {
            val here = i == slot
            drawCircle(
                color = when {
                    beat.isLit(i) && here -> accent
                    beat.isLit(i) -> accent.copy(alpha = 0.42f)
                    here -> Color.White.copy(alpha = 0.22f)
                    else -> Color.White.copy(alpha = 0.10f)
                },
                radius = if (beat.isLit(i)) 4.dp.toPx() else 2.dp.toPx(),
                center = Offset(step * (i + 0.5f), size.height / 2f),
            )
        }
    }
}

/**
 * L'écran à fond, et qui ne s'éteint pas : à dix mètres dans un bar, la
 * luminosité **est** la portée du signal.
 *
 * Les icônes système passent en clair avec le reste : la lanterne est noire même
 * en thème jour, et l'heure en gris foncé sur ce noir ne se lirait plus.
 *
 * Les trois réglages sont rendus tels qu'ils étaient en quittant l'écran.
 */
@Composable
private fun ScreenAsLantern() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.activity()?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousBrightness = window?.attributes?.screenBrightness
        val previousLightBars = bars?.isAppearanceLightStatusBars
        window?.apply {
            attributes = attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
            }
            addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        bars?.apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        onDispose {
            window?.apply {
                attributes = attributes.apply {
                    screenBrightness = previousBrightness
                        ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
                clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            previousLightBars?.let {
                bars.isAppearanceLightStatusBars = it
                bars.isAppearanceLightNavigationBars = it
            }
        }
    }
}

/**
 * Le même rythme dans la main. Il sert quand l'écran est tourné vers la salle —
 * on ne voit pas son propre signal, on le sent, et on sait qu'il tourne.
 */
@Composable
private fun PulseInTheHand(beat: Rendezvous.Beat, slot: Int) {
    val context = LocalContext.current
    val vibrator = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }?.takeIf { it.hasVibrator() }
    }
    LaunchedEffect(beat, slot) {
        if (!beat.isLit(slot)) return@LaunchedEffect
        vibrator?.vibrate(VibrationEffect.createOneShot(20L, 130))
    }
}


/** Le contexte d'un composable est un empilement d'enveloppes ; l'activité est dessous. */
private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
