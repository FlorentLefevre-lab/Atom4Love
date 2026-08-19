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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import one.astroport.atom4love.R
import one.astroport.atom4love.ui.components.glass
import one.astroport.atom4love.ui.components.screenBackground
import one.astroport.atom4love.ui.theme.A4L
import one.astroport.atom4love.ui.theme.A4LText

/**
 * 05 · Aide — le mode d'emploi de la station, dans ses mots à elle.
 * Accessible par l'onglet « Aide » une fois le noyau forgé, et par le « ? »
 * de l'écran de forge avant (c'est là qu'on en a le plus besoin).
 */
@Composable
fun HelpScreen(
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    atWindowBottom: Boolean = false,
) {
    Column(
        modifier
            .fillMaxSize()
            .screenBackground(A4L.GlowBond, A4L.DeepAlt, radiusFactor = 1.4f)
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

        // Deux entrées de plain-pied. « Aide » n'est plus le titre de la page
        // avec la F.A.Q. rangée dessous : ce sont deux façons d'arriver, et la
        // seconde n'est pas un appendice de la première. Le titre est donc
        // devenu l'un des deux onglets, à la même hauteur et au même corps.
        var tab by rememberSaveable { mutableStateOf(HelpTab.Help) }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                // L'encoche du bas n'est à nous que si nous touchons le bas.
                // Sous l'onglet Aide, la barre de menus la porte un cran plus
                // bas ; en plein écran avant la forge, personne d'autre.
                .then(if (atWindowBottom) Modifier.navigationBarsPadding() else Modifier),
        ) {
            // La barre d'onglets de Material, habillée de la palette : deux
            // titres écrits côte à côte ne se lisaient que comme des liens,
            // rien ne disait qu'en toucher un changeait la page dessous.
            //
            // Le composant plutôt qu'un dessin à nous : il apporte l'indicateur
            // animé, la navigation au clavier, et surtout les sémantiques que
            // TalkBack attend — « onglet 2 sur 2, sélectionné ». Un onglet
            // fabriqué à la main n'annonce qu'un texte cliquable.
            //
            // Fond transparent pour laisser passer le dégradé de l'écran ;
            // l'indicateur prend `primary`, qui est le cyan de la station dans
            // les deux lumières.
            PrimaryTabRow(
                selectedTabIndex = tab.ordinal,
                modifier = Modifier.padding(top = 18.dp),
                containerColor = Color.Transparent,
                contentColor = A4L.TextHigh,
                divider = {
                    HorizontalDivider(thickness = 1.dp, color = A4L.StrokeSoft)
                },
            ) {
                HelpTab.entries.forEach { entry ->
                    Tab(
                        selected = entry == tab,
                        onClick = { tab = entry },
                        selectedContentColor = A4L.TextHigh,
                        unselectedContentColor = A4L.TextDim,
                        text = {
                            Text(
                                stringResource(entry.title),
                                style = A4LText.Title.copy(fontSize = 15.sp),
                            )
                        },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(tab.lead),
                style = A4LText.Body,
                color = A4L.TextBody,
            )

            // Le socle du discours vient d'ATOM4LOVE, l'interface web de Fred
            // (u.copylaradio.com/earth/atomic.html) : mêmes notions, mêmes
            // mots — une seule idée du monde des deux côtés.
            //
            // Trois thèmes, dans l'ordre où la question se pose : ce qu'est ce
            // monde, ce qu'on y est, ce qu'on y fait. Les dix réponses étaient
            // dix cartes à la file, chacune cerclée d'une couleur qui ne
            // codait rien — le Violet servait « vibration » et « singularité »,
            // le Doré « clé temporaire » et « forger ». L'œil croyait à des
            // familles qui n'existaient pas. Ce sont les thèmes qui les font,
            // et ils se lisent : trois panneaux au lieu de dix boîtes.
            Column(
                Modifier.padding(top = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                when (tab) {
                    HelpTab.Faq -> HelpPanel(
                        listOf(
                            answer(R.string.faq_network_label, R.string.faq_network_body),
                            answer(R.string.faq_chat_label, R.string.faq_chat_body),
                            answer(R.string.faq_weight_label, R.string.faq_weight_body),
                            answer(R.string.faq_nobody_label, R.string.faq_nobody_body),
                            answer(R.string.faq_change_label, R.string.faq_change_body),
                        ),
                    )

                    HelpTab.Help -> {
                    HelpGroup(
                        stringResource(R.string.help_group_world),
                        listOf(
                            answer(R.string.help_vibration_label, R.string.help_vibration_body),
                            answer(R.string.help_imprint_label, R.string.help_imprint_body),
                            answer(R.string.help_resonance_label, R.string.help_resonance_body),
                            answer(R.string.help_singularity_label, R.string.help_singularity_body),
                            answer(R.string.help_waves_label, R.string.help_waves_body),
                            // Juste après la polarité : c'est la question que
                            // pose la réponse d'au-dessus — d'où vient la
                            // fréquence, si ce n'est pas de l'onde.
                            // ⚠ « L'onde biologique · ω_bio » se lisait ici :
                            // l'eau de Watson ramenée en hertz. Retirée le
                            // 15/08 avec la fonctionnalité — une aide qui
                            // explique ce que l'app ne fait plus est pire que
                            // pas d'aide du tout.
                        ),
                    )
                    // Le vocabulaire maya a son propre panneau : ce sont des
                    // définitions, pas la doctrine. On y dit aussi ce que la
                    // station calcule et ce qu'elle se contente de recevoir —
                    // le KIN vient du MULTIPASS, il ne naît pas ici.
                    HelpGroup(
                        stringResource(R.string.help_group_maya),
                        listOf(
                            answer(R.string.help_tzolkin_label, R.string.help_tzolkin_body),
                            answer(R.string.help_kin_label, R.string.help_kin_body),
                        ),
                    )
                    // « Retrouver sa clé » est ici et non parmi les gestes : elle
                    // ne dit pas une manœuvre mais ce que cette clé EST — qu'elle
                    // se redérive de ce que vos proches savent déjà de vous.
                    HelpGroup(
                        stringResource(R.string.help_group_identity),
                        listOf(
                            answer(R.string.help_temp_key_label, R.string.help_temp_key_body),
                            answer(R.string.help_multipass_label, R.string.help_multipass_body),
                            answer(R.string.help_recover_label, R.string.help_recover_body),
                        ),
                    )
                    HelpGroup(
                        stringResource(R.string.help_group_gestures),
                        listOf(
                            answer(R.string.help_forge_label, R.string.help_forge_body),
                            answer(R.string.help_redo_label, R.string.help_redo_body),
                            answer(R.string.help_station_label, R.string.help_station_body),
                        ),
                    )
                    }

                    HelpTab.Zion -> ZionTab()
                }
            }
        }

    }
}

/**
 * Un thème : son nom au-dessus, ses réponses dans un seul panneau.
 *
 * Le nom est dehors et le verre dedans — c'est ce qui fait qu'on voit trois
 * ensembles et non trois boîtes de plus. Le panneau garde le verre neutre du
 * reste de la station : plus de couleur qui prétendrait classer.
 */
/**
 * Trois façons d'arriver. Pas un titre et ses sous-chapitres : trois pairs.
 * La première explique la station à qui la découvre, la deuxième répond à qui
 * bute sur quelque chose — et personne n'arrive jamais avec les deux besoins à
 * la fois. La troisième ne parle pas de la station du tout : elle donne le
 * monde d'où viennent ses nombres, dans les mots et les planches de Made In
 * Zion. Elle vient en dernier parce qu'on n'en a jamais besoin pour se servir
 * de l'application — seulement pour comprendre pourquoi elle est ainsi.
 */
private enum class HelpTab(val title: Int, val lead: Int) {
    Help(R.string.help_title, R.string.help_lead),
    Faq(R.string.faq_title, R.string.faq_lead),
    Zion(R.string.zion_title, R.string.zion_lead),
}

@Composable
internal fun HelpGroup(title: String, answers: List<Answer>) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        // Pas le [SectionLabel] partagé : il sert cinq écrans, et ce qu'on veut
        // ici ne vaut qu'ici. Un nom de thème n'est pas une étiquette de plus
        // dans une colonne — c'est ce qui découpe la page, et ça doit se voir
        // de loin. Mêmes capitales très espacées, mais gras et à la taille du
        // texte courant.
        Text(
            title.uppercase(),
            style = A4LText.SectionLabel.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
                lineHeight = 16.sp,
                letterSpacing = 1.75.sp,
            ),
            color = A4L.TextMuted,
            modifier = Modifier.padding(start = 3.dp),
        )
        HelpPanel(answers)
    }
}

/**
 * Un panneau de réponses. Séparé de son intitulé parce que la F.A.Q. n'en a
 * pas : une question porte déjà son propre nom, la coiffer d'un thème
 * reviendrait à la ranger deux fois.
 */
@Composable
internal fun HelpPanel(answers: List<Answer>) {
    Column(
        Modifier
            .fillMaxWidth()
            .glass(14.dp)
            .padding(horizontal = 15.dp),
    ) {
        answers.forEachIndexed { index, answer ->
            // Le filet ne sépare que ce qui a un dessus : c'est le panneau qui
            // le sait, pas la réponse.
            if (index > 0) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = A4L.StrokeSoft.copy(alpha = 0.5f),
                )
            }
            Column(
                Modifier.padding(vertical = 13.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(answer.label, style = A4LText.Body, color = A4L.TextHigh)
                Text(
                    answer.body,
                    style = A4LText.Body,
                    color = A4L.TextBody.copy(alpha = 0.8f),
                )
            }
        }
    }
}

/** Une question du lecteur et ce que la station y répond. */
internal data class Answer(val label: String, val body: String)

@Composable
internal fun answer(label: Int, body: Int) = Answer(stringResource(label), stringResource(body))
