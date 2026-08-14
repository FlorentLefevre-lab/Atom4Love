package one.astroport.atom4love.domain

/**
 * Le corps d'aujourd'hui — taille et poids, tels qu'ils sont maintenant.
 *
 * **Rien de tout cela n'entre dans le SALT.** Le noyau se dérive de la date, de
 * l'heure et du lieu de naissance ; un corps change, une clé non. Ces deux
 * mesures vivent donc à part de [BirthData], se modifient après la forge, et ne
 * servent qu'à une chose : l'onde biologique ω_bio ([Phi2X.omegaBio]), qui
 * n'existe pas sans elles.
 *
 * C'est exactement ce que collecte `atomic.html` sous `current-height` et
 * `current-weight`, avec les mêmes valeurs d'ouverture — 170 cm et 70 kg.
 *
 * Les deux champs restent facultatifs : sans eux, il n'y a pas de fréquence, et
 * c'est tout. La station marche pareil, et la clé est la même.
 */
data class BodyMetrics(
    val heightCm: Int?,
    val weightKg: Float?,
) {
    /** De quoi calculer une onde biologique : les deux mesures, pas une. */
    val complete: Boolean get() = heightCm != null && weightKg != null

    companion object {
        /** Rien de saisi — l'état de départ, et celui d'après la dissolution. */
        val Empty = BodyMetrics(heightCm = null, weightKg = null)

        /**
         * Les bornes des rouleaux de saisie. Larges à dessein : elles écartent
         * la faute de frappe, pas les corps réels.
         */
        val HEIGHT_RANGE_CM = 100..250
        val WEIGHT_RANGE_KG = 30f..250f

        /**
         * Là où s'ouvrent les rouleaux quand rien n'a encore été saisi. Ce sont
         * les valeurs de repli d'`atomic.html` (`_getAdultHeight`,
         * `_getAdultWeight`) : on ne s'invente pas d'autres chiffres que ceux
         * du formulaire de référence.
         */
        const val DEFAULT_HEIGHT_CM = 170
        const val DEFAULT_WEIGHT_KG = 70f
    }
}
