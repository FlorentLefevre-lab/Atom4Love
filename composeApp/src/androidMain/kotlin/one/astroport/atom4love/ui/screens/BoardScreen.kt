package one.astroport.atom4love.ui.screens

import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Image
import android.graphics.BitmapFactory
import one.astroport.atom4love.chat.ChatKind
import android.net.Uri
import java.io.File
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.annotation.StringRes
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import one.astroport.atom4love.R
import one.astroport.atom4love.domain.BirthData
import one.astroport.atom4love.domain.KinMaya
import one.astroport.atom4love.domain.Match
import one.astroport.atom4love.domain.Oracle
import one.astroport.atom4love.domain.Phi2X
import one.astroport.atom4love.proximity.NeighborRegistry
import one.astroport.atom4love.proximity.ProximityPayload
import one.astroport.atom4love.proximity.SeekingBeacon
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
 *
 * ## ⚠ La règle de lisibilité de cet écran
 *
 * Signalé par Florent le 16/08 : « la taille et le contraste de certaines
 * écritures du plateau sont trop illisibles ». Le diagnostic n'était pas dans
 * cet écran mais dans la palette, dont le commentaire l'écrivait déjà — les sept
 * crans du jour font `15,1 · 8,7 · 6,5 · 4,8 · 3,4 · 2,6 · 2,1`, et
 * `A4L.TextFaint` et `A4L.TextGhost` « n'ont rien à porter qu'on doive lire ».
 * Le Plateau leur donnait des phrases entières, à 10 sp.
 *
 * Trois règles, à tenir :
 *
 * 1. **Rien qui se lit ne descend sous [A4LText.Caption]** (12,5 sp). Cet écran
 *    la réduisait partout à 9, 9,5, 10, 10,5 et 11 sp — la taille de base est la
 *    bonne, c'est chaque `copy(fontSize = …)` qui était l'erreur.
 * 2. **Ni [A4L.TextGhost] ni [A4L.TextFaint] sous une phrase.** Ils sont là pour
 *    les filets et les fantômes ; une légende prend [A4L.TextMuted] (4,8:1).
 * 3. **Une couleur d'accent ne fait pas une encre.** Les accents du jour sont
 *    autour de 3:1 sur blanc, et posés sur un lavis de leur propre teinte, bien
 *    moins — la couleur reste sur le pictogramme, le filet et le fond. Seules
 *    exceptions assumées : les deux mots en capitales grasses du match, où la
 *    couleur **est** l'information et où le mot se lit de toute façon.
 */
@Composable
fun BoardScreen(
    modifier: Modifier = Modifier,
    npub: String? = null,
    /** Non nul quand le Plateau s'ouvre en plein écran depuis le Noyau. */
    onClose: (() -> Unit)? = null,
    birth: BirthData = BirthData.Empty,
    /**
     * L'état de la radio, greffé en tête — voir [RadioSection].
     *
     * Passé en composable et non en données : cette section demande des
     * permissions, tient un lanceur de réglages et sonde la position toutes les
     * trente secondes. Lui faire traverser le Plateau sous forme d'une douzaine
     * de paramètres aurait fait du Plateau le porteur d'un état qui ne le
     * regarde pas. Nul en aperçu, où le Plateau doit pouvoir s'afficher seul.
     */
    radio: (@Composable (title: @Composable () -> Unit) -> Unit)? = null,
    /**
     * Les pseudos de ceux à qui l'on parle déjà, par **jeton de présence**.
     *
     * ⚠ Une annonce de proximité ne nomme personne, et ne le fera jamais : le
     * nom vient du lien attesté de la cabine. Reste à savoir quelle carte est
     * quelle personne, et c'est le **jeton** qui le dit —
     * `SHA-256(clé NOSTR ‖ cellule)`, que l'on sait recalculer pour un pair
     * dont on connaît déjà la clé ([ProximityPayload.token], dont le KDoc
     * nomme précisément cette propriété).
     *
     * ⚠ **Ce n'est pas l'adresse radio.** Premier essai, et il ne marche pas :
     * la balise annonce sous une adresse tirée au sort par le jeu d'annonce,
     * qui n'est pas celle du lien GATT. Aucun nom n'apparaissait, en silence.
     * Vu sur le Pixel le 19/08.
     *
     * Une carte sans lien attesté reste anonyme, ce qui est exactement le jeu :
     * on cherche d'abord un sceau dans la salle, on apprend un nom en se
     * parlant. Et sans localisation il n'y a pas de jeton du tout, donc pas de
     * nom non plus — même règle que partout ailleurs.
     */
    names: Map<Int, String> = emptyMap(),
    /** Les visages reçus, par jeton de présence — voir [ChatKind.SELFIE]. */
    selfies: Map<Int, File> = emptyMap(),
    /** Un visage à envoyer à la carte qu'on regarde dans la lanterne. */
    onSelfie: (Int, Uri) -> Unit = { _, _ -> },
    /** Où en est le visage qu'on envoie, par jeton de présence. */
    sendings: Map<Int, SelfieSend> = emptyMap(),
    /** Remettre le même visage sur le fil après un échec. */
    onRetrySelfie: (Int) -> Unit = {},
    /**
     * Ouvrir la lanterne sur ce jeton, demandé de l'extérieur.
     *
     * ⚠ C'est le « Voir » du bandeau du visage. Il menait au Plateau et rien de
     * plus : quand on y était déjà, le bouton paraissait mort. Ce qu'on veut
     * voir, c'est la lanterne — là où le visage bat.
     */
    openToken: Int? = null,
    /** La demande est honorée : à l'appelant de l'oublier. */
    onOpened: () -> Unit = {},
) {
    val neighbors by ProximityService.neighbors.collectAsStateWithLifecycle()
    val own by ProximityService.signature.collectAsStateWithLifecycle()
    val beaconRunning by ProximityService.running.collectAsStateWithLifecycle()
    /**
     * ⚠ **La main vide a trois causes, et une seule veut dire « il n'y a
     * personne ».** Sans Bluetooth la balise ne tourne pas ; avant Android 12,
     * sans position, elle tourne et le scan est aveugle
     * ([ProximityService.scanBlind]). Dans les deux premiers cas, écrire
     * « Personne ne montre sa carte » est un mensonge — celui qui a coûté une
     * soirée à l'A5 le 19/08, où l'écran concluait sur une salle qu'il n'avait
     * pas regardée.
     */
    val scanBlind by ProximityService.scanBlind.collectAsStateWithLifecycle()
    /** Les jetons de ceux qui nous cherchent — cf. [SeekingPayload]. */
    val seekers by ProximityService.seekers.collectAsStateWithLifecycle()

    // Une carte par **personne**, pas par adresse : deux annonces d'un même
    // jeton sont un seul appareil qui vient de changer de visage. Et sans
    // signature il n'y a pas de carte — le pair est là, il n'a rien montré.
    // ⚠ Le tri se fait sur [Phi2X.resonanceK] SEUL, et c'est délibéré : ne pas
    // le « corriger » en remontant les 🤝 avant les ⚡. k vaut 1 aux DEUX bouts,
    // en phase et en opposition, parce que |sin Δφ| s'annule aux deux — la
    // singularité optique de Fred les traite à égalité stricte. L'opposable
    // n'est pas le contraire, c'est le complémentaire : il attire.
    //
    // Le vrai fond de classement n'est pas l'opposition mais le QUART DE TOUR,
    // où k atteint son minimum de 0,5 — deux phases qui n'ont rien à se dire.
    // Trier par k met donc en tête ce qui résonne fort, dans les deux sens, et
    // laisse en bas ce qui ne résonne pas. C'est la seule lecture cohérente
    // avec la formule ; le sens du lien se lit sur le badge 🤝/⚡ juste après.
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
    LaunchedEffect(openToken, hand) {
        val token = openToken ?: return@LaunchedEffect
        hand.firstOrNull { it.token == token }?.let { seekingId = it.identity }
        onOpened()
    }
    // Chercher plusieurs cartes d'un coup : la première n'est réciproque que
    // deux fois sur trois, mais le rang moyen chez l'autre est de 0,5 — couvrir
    // les trois premières couvre presque tout le monde. Les fenêtres de
    // [Rendezvous] gardent les deux appareils en phase.
    var seekingMany by rememberSaveable { mutableStateOf(false) }
    val live = hand.firstOrNull { it.identity == seekingId }
    // La dernière carte connue survit à un balayage manqué — sinon l'écran de
    // reconnaissance clignoterait à chaque trou de la radio, au pire moment.
    var lastKnown by remember(seekingId) { mutableStateOf(live) }
    LaunchedEffect(live) { if (live != null) lastKnown = live }

    // ── Se déclarer ───────────────────────────────────────────────────────
    //
    // Toucher une carte allume l'annonce étendue qui dit « je cherche ces
    // jetons-là » ([SeekingBeacon]). C'est ce qui fait passer le jeu de « deux
    // fois sur trois » à « à tous les coups » : sans elle, celui d'en face
    // devait deviner qu'on le cherchait.
    //
    // ⚠ Elle s'éteint dès qu'on ferme — la liste vide coupe l'annonce au
    // balayage suivant. Une déclaration qui survivrait au geste continuerait de
    // parler pour quelqu'un qui a rangé son téléphone.
    val seekingTokens = remember(lastKnown, seekingMany, hand) {
        val card = lastKnown ?: return@remember emptyList()
        val many = if (seekingMany) hand.take(SEEK_MANY) else listOf(card)
        (listOf(card) + many).distinctBy { it.identity }.mapNotNull { it.token }
    }
    DisposableEffect(seekingTokens) {
        SeekingBeacon.seek(seekingTokens)
        onDispose { SeekingBeacon.stop() }
    }

    lastKnown?.let { card ->
        // ⚠ Le geste de retour referme la lanterne, il ne quitte pas la
        // station. Sans ce `BackHandler` il sortait de l'application — vu sur
        // le Pixel le 16/08, où il a rendu la main à Firefox : le geste le plus
        // naturel du téléphone, fait au pire moment, quand on cherche
        // justement quelqu'un dans une salle. Les autres plein-écrans le
        // faisaient déjà ; celui-ci avait été oublié.
        BackHandler { seekingId = null; seekingMany = false }
        RendezvousScreen(
            card = card,
            alsoSeeking = if (seekingMany) hand.take(SEEK_MANY).filter { it.identity != card.identity } else emptyList(),
            own = own,
            inRange = live != null,
            onClose = { seekingId = null; seekingMany = false },
            pseudo = card.token?.let { names[it] },
            selfie = card.token?.let { selfies[it] },
            onSelfie = { uri -> card.token?.let { onSelfie(it, uri) } },
            sending = card.token?.let { sendings[it] },
            onRetry = { card.token?.let { onRetrySelfie(it) } },
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
        //
        // ⚠ **Le titre n'est plus en tête de l'écran : il est en tête du JEU.**
        // La balise passe au-dessus de lui — sans elle il n'y a pas de voisin,
        // donc pas de carte, donc pas de « Qui est-ce ? » du tout. La ligne qui
        // dit « il manque le Bluetooth » se lit donc avant le nom de ce qu'elle
        // empêche. Ce titre est confié à [RadioSection], qui le rend entre sa
        // balise et ses compteurs : voir son KDoc pour la raison — le lanceur de
        // permissions est unique et ne peut pas se couper en deux.
        val title: @Composable () -> Unit = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // ⚠ **Ce n'était pas cette ligne-là.** Elle portait le nom du
                    // jeu — « Qui est-ce ? » — au-dessus de trois carreaux qui
                    // ne parlent pas du jeu mais de l'étendue de ce que la
                    // station touche. Retiré par Florent le 20/08 : la ligne
                    // annonce désormais ce qui la suit immédiatement, et le jeu
                    // se nomme là où il commence, à « votre carte ».
                    Text("📊", fontSize = 13.sp)
                    Spacer(Modifier.width(7.dp))
                    Text(
                        stringResource(R.string.board_counters_title),
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
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .then(if (onClose != null) Modifier.navigationBarsPadding() else Modifier),
        ) {
            Spacer(Modifier.height(12.dp))
            // En aperçu il n'y a pas de radio : le titre reprend alors sa place
            // ordinaire, en haut, plutôt que de disparaître avec elle.
            if (radio != null) radio(title) else title()

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
                            when {
                                !beaconRunning -> R.string.board_beacon_off
                                scanBlind -> R.string.board_blind_no_location
                                else -> R.string.board_nobody
                            },
                        ),
                        style = A4LText.Caption,
                        color = A4L.TextMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                if (hand.size > 1) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .glass(13.dp, A4L.Mint.tint(0.10f), A4L.Mint.tint(0.34f))
                            .clickable {
                                seekingMany = true
                                seekingId = hand.first().identity
                            }
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(
                                R.string.board_seek_many,
                                minOf(hand.size, SEEK_MANY),
                            ),
                            style = A4LText.Body.copy(
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = A4L.Mint,
                        )
                    }
                    Spacer(Modifier.height(11.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    hand.forEachIndexed { index, neighbor ->
                        DealtCard(
                            neighbor = neighbor,
                            own = own,
                            // Elle s'est déclarée : elle nous cherche, et elle
                            // le dit. Toucher la sienne suffit alors — les deux
                            // écrans battront.
                            seeksUs = neighbor.token != null && neighbor.token in seekers,
                            pseudo = neighbor.token?.let { names[it] },
                            face = neighbor.token?.let { selfies[it] },
                            onSeek = { seekingId = neighbor.identity },
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
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
    Column(
        Modifier
            .fillMaxWidth()
            .glass(16.dp, A4L.Cyan.tint(0.09f), A4L.Cyan.tint(0.30f))
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
                    style = A4LText.Data.copy(fontSize = 12.sp),
                    color = A4L.TextMuted,
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
        kin?.let { OracleBlock(it) }
    }
}

/** Une des quatre cases de l'Oracle — le sceau visé, sous le nom que Fred lui donne. */
private data class OracleCell(
    /** Le pictogramme de sa planche, repris tel quel. */
    val mark: String,
    @StringRes val labelRes: Int,
    val kin: KinMaya.Kin,
    val color: Color,
)

/**
 * Les quatre compléments de votre KIN — [Oracle], donc les formules de Fred.
 *
 * Ils tiennent sur votre carte et nulle part ailleurs : ce sont des sceaux à
 * **chercher** dans la salle, et la carte est ce qu'on lève au-dessus d'une
 * table. Rien n'en sort par la radio, tout se calcule de la seule date.
 *
 * ⚠ Le **guide** n'est pas de la même nature que les trois autres : il reste
 * dans votre propre famille de sceaux, il peut être vous-même, et cinq KIN le
 * partagent ([Oracle.guide]). Il est là pour être lu, pas pour être cherché —
 * d'où sa place en tête et son pictogramme d'orientation.
 *
 * ⚠ Au ton 7 le défi et l'alternance sont le même KIN : les deux cases se
 * répètent alors, et une ligne le dit plutôt que d'en cacher une — la
 * coïncidence est le fait remarquable, pas un doublon à masquer.
 */
@Composable
private fun OracleBlock(kin: KinMaya.Kin) {
    val reading = Oracle.of(kin)
    val cells = listOfNotNull(
        reading.guide?.let {
            OracleCell("🧭", R.string.board_oracle_guide, it, A4L.Amber)
        },
        reading.antipode?.let {
            OracleCell("⚡", R.string.board_oracle_antipode, it, A4L.Violet)
        },
        reading.analogue?.let {
            OracleCell("🌀", R.string.board_oracle_analogue, it, A4L.Mint)
        },
        reading.occult?.let {
            OracleCell("🌙", R.string.board_oracle_occult, it, A4L.Cyan)
        },
    )
    if (cells.isEmpty()) return

    Column(
        Modifier.padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionLabel(stringResource(R.string.board_oracle_title))
        // ⚠ **Deux par deux, jamais quatre de front.** À quatre colonnes, une
        // case fait le quart de la carte et « Alternance » n'y tient qu'en
        // dessous de 11 sp — c'est-à-dire que la largeur décidait de la taille
        // du texte. On inverse : la légende garde sa taille, la grille s'adapte.
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            cells.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    row.forEach { cell -> OracleTile(cell, Modifier.weight(1f)) }
                    // Une rangée dépareillée ne s'étale pas sur toute la largeur
                    // (cas d'un KIN hors grille, qui n'a pas d'occulte).
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Une case de l'Oracle : le pictogramme du sceau, son nom, son KIN.
 *
 * ⚠ **La couleur du pouvoir vit sur le lavis et le filet, jamais sur l'encre.**
 * Les accents du thème du jour tournent autour de 3:1 sur blanc — et posés sur
 * un lavis de leur propre teinte, moins encore. Un libellé de 12,5 sp écrit
 * dans sa couleur était donc illisible, alors que le même libellé en encre
 * ordinaire garde tout son code couleur : le cadre et le pictogramme le
 * portent. Ne pas « recolorer » ces trois lignes.
 */
@Composable
private fun OracleTile(cell: OracleCell, modifier: Modifier = Modifier) {
    Row(
        modifier
            .glass(12.dp, cell.color.tint(0.07f), cell.color.tint(0.24f))
            .padding(horizontal = 11.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(KinMaya.glyphEmoji(cell.kin.glyph), fontSize = 26.sp)
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                "${cell.mark} ${stringResource(cell.labelRes)}",
                style = A4LText.Caption,
                color = A4L.TextHigh,
            )
            KinMaya.glyphName(cell.kin.glyph)?.let {
                Text(it, style = A4LText.Caption, color = A4L.TextBody)
            }
            Text(
                stringResource(R.string.board_oracle_kin, cell.kin.kin),
                style = A4LText.Data.copy(fontSize = 11.5.sp),
                color = A4L.TextMuted,
            )
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
    /**
     * Cette carte a déclaré nous chercher.
     *
     * ⚠ C'est le seul endroit du jeu où l'on apprend qu'on a été choisi avant
     * d'avoir choisi. Le silence d'origine tenait le consentement des deux
     * côtés ; ici celui qui cherche **se déclare**, en connaissance de cause.
     * Ce qui ne change pas : rien ne bat tant qu'on n'a pas touché à son tour.
     */
    seeksUs: Boolean = false,
    /** Son pseudo, quand un lien attesté nous l'a appris. */
    pseudo: String? = null,
    /**
     * Le visage qu'elle a envoyé pour se faire reconnaître.
     *
     * ⚠ **Il se montre ICI, sans qu'on ait à ouvrir sa lanterne** (Florent,
     * 20/08). Quelqu'un qui vous cherche dans une salle ne peut pas attendre
     * que vous pensiez à ouvrir un écran : sa photo prend la place du sceau sur
     * la carte, là où l'œil passe de toute façon.
     */
    face: File? = null,
) {
    val theirs = neighbor.signature
    val classification = own.phase?.let { mine ->
        theirs.phase?.let { Phi2X.classifyResonance(mine, it) }
    }
    val match = Match.read(
        myPhase = own.phase,
        myGlyph = own.glyph,
        theirPhase = theirs.phase,
        theirGlyph = theirs.glyph,
    )
    val bond = match.bond
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

    // Un match habille la carte entière plutôt que d'y ajouter une pastille de
    // plus : c'est un état de la rencontre, pas une donnée à côté des autres.
    val frame = when (match.level) {
        Match.Level.Super -> A4L.Gold
        Match.Level.Match -> accent
        Match.Level.None -> null
    }
    Row(
        Modifier
            .fillMaxWidth()
            .glass(
                radius = 15.dp,
                background = frame?.tint(0.07f) ?: A4L.GlassSoft.copy(alpha = 0.04f),
                border = frame?.tint(0.40f) ?: A4L.StrokeSoft,
                borderWidth = if (match.level == Match.Level.Super) 2.dp else 1.dp,
            )
            .clickable(onClick = onSeek)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val faceBitmap = face?.let { file ->
            remember(file.path, file.lastModified()) {
                BitmapFactory.decodeFile(file.path)?.asImageBitmap()
            }
        }
        if (faceBitmap != null) {
            Image(
                bitmap = faceBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape),
            )
        } else {
            Text(KinMaya.glyphEmoji(theirs.glyph), fontSize = 34.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // ⚠ **Le pseudo d'abord, en gras ; le sceau après, entre
                // parenthèses** (Florent, 20/08). C'était l'inverse — le sceau
                // en tête, au motif que c'est lui qu'on cherche dans la salle.
                // Mais des quatre mots d'une carte, celui qui désigne quelqu'un
                // est le pseudo : un sceau se partage à vingt personnes dans un
                // bar, un pseudo non. La même règle tient le journal.
                val seal = KinMaya.glyphName(theirs.glyph)
                    ?: stringResource(R.string.board_no_seal)
                pseudo?.let {
                    Text(
                        it,
                        style = A4LText.ItemTitle,
                        color = A4L.TextHigh,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
                Text(
                    "($seal)",
                    style = if (pseudo == null) {
                        A4LText.ItemTitle
                    } else {
                        A4LText.Body.copy(fontSize = 12.5.sp)
                    },
                    color = if (pseudo == null) A4L.TextHigh else A4L.TextBody,
                    fontWeight = if (pseudo == null) FontWeight.SemiBold else null,
                )
                if (seeksUs) {
                    Text(
                        stringResource(R.string.board_seeks_you),
                        style = A4LText.Caption.copy(fontWeight = FontWeight.Bold),
                        color = A4L.Gold,
                    )
                }
                if (match.level != Match.Level.None) {
                    val superb = match.level == Match.Level.Super
                    Text(
                        stringResource(
                            if (superb) R.string.board_super_match else R.string.board_match,
                        ),
                        style = A4LText.Caption.copy(fontWeight = FontWeight.Bold),
                        color = if (superb) A4L.Gold else accent,
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(warmth.glyph, fontSize = 12.sp)
                Text(
                    stringResource(warmth.labelRes),
                    style = A4LText.Caption,
                    color = warmth.color,
                )
            }
            // Le lien de l'Oracle, quand il y en a un. Le sceau suffit à le
            // lire — c'est une relation de colonne, le ton n'y entre pas — et
            // c'est heureux : l'annonce ne porte jamais le ton. Revers assumé,
            // dit par le libellé : un sceau seul ne tranche pas entre le défi
            // et l'alternance, qui le partagent.
            bond?.let {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (it == Oracle.Bond.Challenge) "⚡" else "🌙", fontSize = 11.sp)
                    Text(
                        stringResource(
                            if (it == Oracle.Bond.Challenge) {
                                R.string.board_bond_challenge
                            } else {
                                R.string.board_bond_hidden
                            },
                        ),
                        style = A4LText.Caption,
                        color = A4L.TextBody,
                    )
                }
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

/**
 * Combien de cartes on cherche d'un coup.
 *
 * Trois, parce que le rang moyen d'une carte chez son porteur est de 0,5 —
 * mesuré sur 400 salles simulées — et que couvrir les trois premières couvre
 * donc l'immense majorité des réciprocités. Au-delà, chaque carte de plus
 * espace le retour de toutes les autres sans presque rien ajouter.
 */
private const val SEEK_MANY = 3
