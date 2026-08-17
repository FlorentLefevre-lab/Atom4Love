package one.astroport.atom4love.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import one.astroport.atom4love.ui.components.ATOM_BEAT_MS
import one.astroport.atom4love.ui.components.AtomLogo
import one.astroport.atom4love.ui.components.screenBackground
import one.astroport.atom4love.ui.theme.A4L
import one.astroport.atom4love.ui.theme.A4LText
import one.astroport.atom4love.ui.theme.Ornament

/**
 * Durée d'affichage minimale du splash.
 *
 * 4 s, et non les 2,4 s d'origine : l'émission met à elle seule un peu plus de
 * deux secondes à se poser, et un écran qui s'efface pendant que son animation
 * finit ne donne rien à voir. Ce qui reste après — presque deux secondes de
 * noyau qui bat et de nom qui respire — est du temps rendu au regard, pas du
 * temps perdu. Le prix se paie à chaque démarrage à froid : c'est la seule
 * constante à baisser si le lancement devient une attente.
 */
const val SPLASH_HOLD_MS = 4000L

/** Le noyau paraît seul un instant : sans ce silence, rien n'a l'air d'en sortir. */
private const val TITLE_EMIT_DELAY_MS = 400L

/** L'écart entre deux paquets : les lettres partent du milieu, deux par deux. */
private const val TITLE_EMIT_STAGGER_MS = 120L

/**
 * 00 · Splash — l'atome au cœur battant dessiné en vectoriel ([AtomLogo] :
 * cadence et nombre d'électrons maîtrisés, rendu net à toute taille), le temps
 * que la station restaure l'incarnation depuis le DataStore.
 *
 * **L'atome émet son nom** — voir [EmittedTitle]. Les neuf lettres sortent du
 * noyau et se rangent dessous, le milieu du mot le premier.
 *
 * Elles portent **la couleur des électrons** : `A4L.Cyan`, celui du halo et du
 * sillage qui tournent au-dessus. Ce qui sort du noyau a la couleur de ce qui
 * l'entoure, et la palette n'a rien eu à inventer pour ça — la ligne qui définit
 * ce cyan porte depuis le début le commentaire « ATOM4LOVE ».
 *
 * ⚠ Deux états antérieurs, le même jour. Le nom **tombait du haut de l'écran**,
 * en bleu ; c'était joli et ça ne disait rien — une chute vient d'ailleurs, une
 * émission vient de l'atome. Puis il fut **orange**, l'énergie rendue en
 * arrivant. L'orange disait quelque chose de vrai mais d'une autre famille : il
 * n'existait nulle part ailleurs sur cet écran.
 *
 * L'atome et le nom tiennent le centre ; la filiation — *Designed, Made, and
 * Powered by Astroport.ONE* — se pose **tout en bas de l'écran**, en grand. Le
 * pied de page qu'on a chassé de partout ailleurs revient ici, et c'est le seul
 * endroit où il a sa place : rien ne se lit sur cet écran, on le regarde. La
 * signature peut donc occuper le bord sans voler de la place à quoi que ce soit.
 *
 * En anglais, et non traduite : c'est une signature, pas une phrase. La bulle de
 * version des Réglages porte la même, en petit, là où l'on cherche qui publie.
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .screenBackground(A4L.GlowNucleus, A4L.Void, centerY = 0.42f),
    ) {
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AtomLogo(Modifier.size(220.dp))
            Spacer(Modifier.height(18.dp))
            EmittedTitle()
        }

        // La signature, posée sur le bord bas. La marge latérale porte la
        // coupure : sur un écran large la phrase tient d'un trait, sur un
        // téléphone étroit elle passe à deux lignes et « Astroport.ONE » tombe
        // seule sur la seconde — c'est le mot qu'on veut voir en dernier.
        Text(
            "Designed, Made, and Powered by Astroport.ONE",
            style = A4LText.Body.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 22.sp,
            ),
            color = A4L.TextBody,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 32.dp, end = 32.dp, bottom = 30.dp),
        )
    }
}

/** Le style du nom — un seul endroit, la mesure et le dessin devant s'accorder. */
private val TitleStyle
    @Composable get() = A4LText.Data.copy(
        fontFamily = Ornament,
        fontSize = 30.sp,
        fontWeight = FontWeight.Bold,
        // Moins d'interlettrage qu'au grotesque : les empattements espacent déjà
        // l'œil, et 5,2 sp faisaient tomber le mot en lettres détachées.
        letterSpacing = 2.4.sp,
    )

/**
 * **L'émission.** Les neuf lettres sortent du noyau de l'atome et se rangent.
 *
 * C'est ce que fait un atome excité : il rend son énergie, et ce qui part du
 * noyau part par paquets. D'où l'ordre — le `4`, qui tient le milieu du mot,
 * s'échappe le premier, puis ses voisins deux par deux vers l'extérieur. Le nom
 * ne tombe pas dessus, il en sort.
 *
 * Chaque lettre est son propre `Text` dans une `Row` : c'est la seule façon de
 * les bouger séparément, et ça rend la mesure exacte — les largeurs additionnées
 * *sont* la mise en page, aucun crénage ne court d'une composable à l'autre.
 * [TextMeasurer] donne donc la position de chacune dès la première image, sans
 * attendre un `onGloballyPositioned` qui arriverait une image trop tard.
 *
 * Aucune bibliothèque : `Animatable` et `graphicsLayer` suffisent, et une
 * animation qu'on lit dans le fichier vaut mieux qu'un binaire qu'on ne diffe
 * pas. [nucleusRise] est la distance du cœur de l'atome à la ligne du nom —
 * 110 dp de demi-logo, 18 dp d'espace, une demi-hauteur de titre.
 */
@Composable
private fun EmittedTitle(nucleusRise: Dp = 148.dp) {
    val word = "ATOM4LOVE"
    val base = TitleStyle
    val measurer = rememberTextMeasurer()

    // Le décalage de chaque lettre par rapport au centre du mot : à l'instant
    // zéro on l'annule, et les neuf se superposent sur le noyau.
    //
    // Mesuré sur [base], jamais sur le style qui porte la lueur : celle-là
    // change à chaque image, et la mesure se referait soixante fois par seconde
    // pour un résultat identique — une ombre ne déplace aucune lettre.
    val fromCentre = remember(base) {
        val widths = word.map { measurer.measure(it.toString(), base).size.width.toFloat() }
        val total = widths.sum()
        var run = 0f
        widths.map { w -> (run + w / 2f - total / 2f).also { run += w } }
    }

    // La respiration, accrochée au battement du cœur du logo : même durée, même
    // courbe, même aller-retour. Le nom brille quand le noyau enfle.
    val beat by rememberInfiniteTransition(label = "titre").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(ATOM_BEAT_MS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "titre-lueur",
    )
    // Une ombre sans décalage n'est pas une ombre : c'est un halo, la copie
    // floue de la lettre posée exactement dessous. C'est ce que les électrons
    // portent aussi, en dégradé radial — ici le flou du texte fait le même
    // travail sans un Canvas de plus.
    val style = base.copy(
        shadow = Shadow(
            color = A4L.Cyan.copy(alpha = 0.30f + 0.50f * beat),
            offset = Offset.Zero,
            blurRadius = with(LocalDensity.current) { (5.dp + 8.dp * beat).toPx() },
        ),
    )

    val emitted = remember { word.map { Animatable(0f) } }
    LaunchedEffect(Unit) {
        val centre = word.length / 2
        word.indices.forEach { i ->
            launch {
                delay(TITLE_EMIT_DELAY_MS + abs(i - centre) * TITLE_EMIT_STAGGER_MS)
                emitted[i].animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        // Un dépassement court : la lettre arrive, hésite, se pose.
                        dampingRatio = 0.68f,
                        // 40, après deux corrections faites en filmant l'écran :
                        // à 110 tout était posé en 250 ms — l'effet existait
                        // sans qu'on ait le temps de le voir ; à 65 il se
                        // regardait ; à 40 il se contemple. Le mot entier met
                        // ~2 s à se ranger, sur les 4 s du splash.
                        stiffness = 40f,
                        // Le seuil par défaut couperait la pose à quelques points
                        // de la place.
                        visibilityThreshold = 0.0005f,
                    ),
                )
            }
        }
    }

    Row {
        word.forEachIndexed { i, c ->
            Text(
                c.toString(),
                style = style,
                // La couleur des électrons, pas une couleur choisie : le nom
                // sort du noyau, il prend la teinte de ce qui gravite autour.
                color = A4L.Cyan,
                modifier = Modifier.graphicsLayer {
                    val p = emitted[i].value
                    val back = 1f - p
                    translationX = -fromCentre[i] * back
                    translationY = -nucleusRise.toPx() * back
                    // Sortie du noyau : un point qui grandit jusqu'à sa taille.
                    scaleX = 0.12f + 0.88f * p
                    scaleY = scaleX
                    // L'opacité monte plus vite que la course, sinon la lettre
                    // reste fantôme pendant la moitié du trajet.
                    alpha = (p * 2.4f).coerceAtMost(1f)
                },
            )
        }
    }
}
