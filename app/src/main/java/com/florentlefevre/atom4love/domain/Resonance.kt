package com.florentlefevre.atom4love.domain

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** Une carte du plateau « Qui est-ce ? » : un noyau réduit à son KIN et à son onde. */
data class AtomCard(
    val kin: Int,
    val wave: Wave,
    val holder: String? = null,
    val npub: String? = null,
) {
    /** Ton du Tzolkin, 1..13. */
    val tone: Int get() = ((kin - 1) % 13) + 1

    /** Sceau du Tzolkin, 1..20. */
    val seal: Int get() = ((kin - 1) % 20) + 1
}

/** Résultat de l'analyse en direct du plateau. */
data class Resonance(val harmony: Int, val friction: Int)

/**
 * Analyse de deux cartes déposées sur le plateau.
 *
 * ⚠ Heuristique de démonstration : l'affinité est calculée sur les distances de ton
 * et de sceau du Tzolkin, la friction sur le complément pondéré par la polarité des
 * ondes (deux ondes identiques frottent davantage). À remplacer par la formule Phi2X
 * dès qu'elle est arrêtée — l'écran ne dépend que de la signature de cette fonction.
 */
fun resonanceBetween(a: AtomCard, b: AtomCard): Resonance {
    val toneGap = abs(a.tone - b.tone)                       // 0..12
    val toneAffinity = 100f - toneGap * (100f / 12f)

    val rawSealGap = abs(a.seal - b.seal)
    val sealGap = min(rawSealGap, 20 - rawSealGap)            // 0..10, le sceau boucle
    val sealAffinity = 100f - sealGap * (100f / 10f)

    val harmony = (toneAffinity * 0.45f + sealAffinity * 0.55f).coerceIn(0f, 100f)
    val sameWave = a.wave == b.wave
    val friction = ((100f - harmony) * if (sameWave) 1f else 0.7f).coerceIn(0f, 100f)

    return Resonance(harmony.roundToInt(), friction.roundToInt())
}
