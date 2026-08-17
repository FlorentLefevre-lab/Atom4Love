package one.astroport.atom4love.domain

/**
 * Le corps d'aujourd'hui — taille et poids, tels qu'ils sont maintenant.
 *
 * **Rien de tout cela n'entre dans le SALT.** Le noyau se dérive de la date, de
 * l'heure et du lieu de naissance ; un corps change, une clé non. Ces deux
 * mesures vivent donc à part de [BirthData] et se modifient dans les Réglages
 * après la forge.
 *
 * Elles ne servent plus qu'à **la silhouette** de la dernière étape de la forge
 * ([one.astroport.atom4love.ui.components.Bmi]) : un dessin qu'on regarde pour
 * vérifier qu'on ne s'est pas trompé de chiffre.
 *
 * ⚠ Elles ont servi à autre chose jusqu'au 15/08 : l'onde biologique ω_bio,
 * calculée par la formule de Watson, affichée sur le Noyau, échangée en cabine
 * et publiée dans le certificat. **Tout cela est parti**, sur décision de
 * Florent. Ces deux champs ont survécu à leur seul usage d'origine.
 *
 * C'est exactement ce que collecte `atomic.html` sous `current-height` et
 * `current-weight`, avec les mêmes valeurs d'ouverture — 170 cm et 70 kg.
 *
 * Les deux champs restent facultatifs : sans eux, la silhouette est neutre, et
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
