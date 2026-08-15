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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import one.astroport.atom4love.R
import one.astroport.atom4love.domain.BirthData
import one.astroport.atom4love.domain.KinMaya
import one.astroport.atom4love.domain.Phi2X
import one.astroport.atom4love.proximity.NeighborRegistry
import one.astroport.atom4love.proximity.ProximityPayload
import one.astroport.atom4love.proximity.ProximityService
import one.astroport.atom4love.ui.components.DataBadge
import one.astroport.atom4love.ui.components.SectionLabel
import one.astroport.atom4love.ui.components.dashedGlass
import one.astroport.atom4love.ui.components.glass
import one.astroport.atom4love.ui.components.screenBackground
import one.astroport.atom4love.ui.theme.A4L
import one.astroport.atom4love.ui.theme.A4LText
import one.astroport.atom4love.ui.theme.tint

/**
 * 🎴 Le tirage — la première main du « Qui est-ce ? ».
 *
 * Le radar dit qu'un 92 % est à dix mètres ; il ne dit pas **lequel**, et c'est
 * voulu — l'annonce de proximité ne porte aucun npub. Entre « quelqu'un ici te
 * répond » et « bonjour », il y a un trou que personne ne traverse seul dans un
 * bar. Le jeu est ce qui le fait traverser.
 *
 * Ce premier coup ne demande rien à personne : **le sceau est déjà dans l'air**.
 * L'annonce BLE porte la polarité, le sceau et φ, sans compte et sans réseau. À
 * vingt sceaux pour une salle de trente, « cherche le Dragon » réduit déjà la
 * pièce à deux personnes — la partie commence avant qu'on ait joué.
 *
 * Toucher une carte ouvre le second coup, [RendezvousScreen] : la chaleur mène
 * au bon mètre carré et s'arrête là, le rythme partagé fait le dernier mètre.
 *
 * Le troisième coup, [one.astroport.atom4love.domain.Questions], ne vit pas ici
 * mais dans la cabine : une carte à portée n'a pas de npub, et une question a
 * besoin d'un canal. C'est là que l'ordre du jeu compte — on se reconnaît
 * d'abord, on se demande ensuite.
 *
 * Le principe qui tient les trois : **le jeu ne révèle jamais une identité, il
 * permet de devenir trouvable.**
 */
@Composable
fun BoardScreen(
    modifier: Modifier = Modifier,
    npub: String? = null,
    /** Non nul quand le Plateau s'ouvre en plein écran depuis le Noyau. */
    onClose: (() -> Unit)? = null,
    birth: BirthData = BirthData.Empty,
) {
    val neighbors by ProximityService.neighbors.collectAsStateWithLifecycle()
    val own by ProximityService.signature.collectAsStateWithLifecycle()
    val beaconRunning by ProximityService.running.collectAsStateWithLifecycle()

    // Une carte par **personne**, pas par adresse : deux annonces d'un même
    // jeton sont un seul appareil qui vient de changer de visage. Et sans
    // signature il n'y a pas de carte — le pair est là, il n'a rien montré.
    val hand = neighbors
        .distinctBy { it.identity }
        .filter { it.signature != ProximityPayload.Signature.Unknown }
        .sortedByDescending { neighbor ->
            val theirs = neighbor.signature.phase
            if (own.phase != null && theirs != null) Phi2X.resonanceK(own.phase!!, theirs) else -1.0
        }

    // ── La carte qu'on est parti chercher ─────────────────────────────────
    // On retient l'identité, pas la carte : une carte est un instantané, et
    // c'est justement quand on marche vers quelqu'un que sa chaleur change.
    var seekingId by rememberSaveable { mutableStateOf<String?>(null) }
    val live = hand.firstOrNull { it.identity == seekingId }
    // La dernière carte connue survit à un balayage manqué — sinon l'écran de
    // reconnaissance clignoterait à chaque trou de la radio, au pire moment.
    var lastKnown by remember(seekingId) { mutableStateOf(live) }
    LaunchedEffect(live) { if (live != null) lastKnown = live }

    lastKnown?.let { card ->
        RendezvousScreen(
            card = card,
            own = own,
            inRange = live != null,
            onClose = { seekingId = null },
        )
        return
    }

    Column(
        modifier
            .fillMaxSize()
            .screenBackground(A4L.GlowBoard, A4L.Deep, centerY = 0.08f, radiusFactor = 1.3f)
            .statusBarsPadding(),
    ) {

        // ── En-tête ───────────────────────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎴", fontSize = 13.sp)
                Spacer(Modifier.width(7.dp))
                Text(
                    stringResource(R.string.board_title),
                    style = A4LText.Title,
                    color = A4L.TextHigh,
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
                .then(if (onClose != null) Modifier.navigationBarsPadding() else Modifier),
        ) {
            Text(
                stringResource(R.string.board_deal_intro),
                style = A4LText.Caption,
                color = A4L.TextMuted,
                modifier = Modifier.padding(top = 10.dp),
            )

            // ── Notre carte ───────────────────────────────────────────────
            SectionLabel(
                stringResource(R.string.board_your_card),
                modifier = Modifier.padding(top = 20.dp, bottom = 9.dp),
            )
            OwnCard(birth = birth, npub = npub)

            // ── Les cartes à portée ───────────────────────────────────────
            SectionLabel(
                stringResource(R.string.board_in_range, hand.size),
                modifier = Modifier.padding(top = 24.dp, bottom = 9.dp),
            )
            if (hand.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .dashedGlass(15.dp)
                        .padding(horizontal = 16.dp, vertical = 22.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(
                            if (beaconRunning) R.string.board_nobody else R.string.board_beacon_off,
                        ),
                        style = A4LText.Caption,
                        color = A4L.TextDim,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Text(
                    stringResource(R.string.board_hint_seek),
                    style = A4LText.Caption.copy(fontSize = 11.sp),
                    color = A4L.TextDim,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    hand.forEach { neighbor ->
                        DealtCard(
                            neighbor = neighbor,
                            own = own,
                            onSeek = { seekingId = neighbor.identity },
                        )
                    }
                }
            }

            Text(
                stringResource(R.string.board_rule),
                style = A4LText.Caption.copy(fontSize = 10.sp),
                color = A4L.TextGhost,
                modifier = Modifier.padding(top = 22.dp, bottom = 20.dp),
            )
        }
    }
}

/**
 * La carte qu'on montre — celle qu'un autre cherche dans la salle.
 *
 * Le pictogramme domine tout le reste, et c'est le sujet : c'est lui qu'on lève
 * au-dessus d'une table. Le npub n'y figure pas ; il n'est jamais diffusé.
 */
@Composable
private fun OwnCard(birth: BirthData, npub: String?) {
    val kin = KinMaya.of(birth)
    Row(
        Modifier
            .fillMaxWidth()
            .glass(16.dp, A4L.Cyan.tint(0.09f), A4L.Cyan.tint(0.30f))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(KinMaya.glyphEmoji(kin?.glyph), fontSize = 44.sp)
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                KinMaya.glyphName(kin?.glyph) ?: stringResource(R.string.board_no_seal),
                style = A4LText.H2,
                color = A4L.TextHigh,
            )
            Text(
                text = if (kin == null) {
                    stringResource(R.string.board_no_date)
                } else {
                    stringResource(R.string.board_kin_tone, kin.kin, kin.tone + 1)
                },
                style = A4LText.Data.copy(fontSize = 10.5.sp),
                color = A4L.TextDim,
            )
            birth.wave?.let {
                Text(
                    stringResource(it.labelRes),
                    style = A4LText.Caption.copy(fontSize = 11.sp),
                    color = A4L.TextMuted,
                )
            }
        }
    }
}

/**
 * Une carte distribuée par la salle : ce qu'un voisin montre de lui, sans se
 * nommer, plus la résonance et la chaleur.
 *
 * La toucher, c'est partir la chercher — et c'est le seul geste de tout le jeu
 * qui engage quelque chose. Il ne prévient personne : l'autre ne saura qu'on
 * l'a choisi que s'il nous a choisi aussi, et alors les deux écrans battront
 * ensemble. Un choix non partagé ne se dénonce pas.
 */
@Composable
private fun DealtCard(
    neighbor: NeighborRegistry.Neighbor,
    own: ProximityPayload.Signature,
    onSeek: () -> Unit,
) {
    val theirs = neighbor.signature
    val classification = own.phase?.let { mine ->
        theirs.phase?.let { Phi2X.classifyResonance(mine, it) }
    }
    // Le signal LISSÉ, comme dans la lanterne du rendez-vous — sur du brut, la
    // carte clignoterait pendant que la lanterne reste stable, et deux écrans
    // du même jeu ne diraient pas la même chose du même voisin. La mémoire est
    // clée sur l'identité : dans un Column, un `remember` suit la position, et
    // la main se réordonne quand les voisins vont et viennent.
    var last by remember(neighbor.identity) { mutableStateOf<Warmth?>(null) }
    val warmth = Warmth.of(neighbor.rssiSmoothed, neighbor.txPowerDbm, last)
    LaunchedEffect(warmth) { last = warmth }
    val accent = when {
        classification == null -> A4L.TextDim
        classification.union -> A4L.Mint
        else -> A4L.Violet
    }

    Row(
        Modifier
            .fillMaxWidth()
            .glass(15.dp, A4L.GlassSoft.copy(alpha = 0.04f), A4L.StrokeSoft)
            .clickable(onClick = onSeek)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(KinMaya.glyphEmoji(theirs.glyph), fontSize = 34.sp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                KinMaya.glyphName(theirs.glyph) ?: stringResource(R.string.board_no_seal),
                style = A4LText.ItemTitle,
                color = A4L.TextHigh,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(warmth.glyph, fontSize = 12.sp)
                Text(
                    stringResource(warmth.labelRes),
                    style = A4LText.Caption.copy(fontSize = 11.sp),
                    color = warmth.color,
                )
            }
        }
        classification?.let {
            DataBadge(
                glyph = if (it.union) "🤝" else "⚡",
                label = "${it.percent} %",
                color = accent,
            )
        }
        Spacer(Modifier.width(9.dp))
        Text("◎", fontSize = 15.sp, color = A4L.TextFaint)
    }
}
