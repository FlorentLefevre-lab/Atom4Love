package one.astroport.atom4love.domain

import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * La phase personnelle φᵢ — portage de `tools/phi2x.py` d'Astroport.ONE.
 *
 * Jusqu'ici la station ne savait pas la calculer : elle la recevait de la
 * station Astroport avec le MULTIPASS, ou pas du tout. Le Radar, lui, marche
 * sans compte : une phase qui n'existe que pour les inscrits ne peut pas entrer
 * dans une annonce de proximité. D'où ce portage.
 *
 * **Trois implémentations existent chez Fred et elles ne s'accordent pas.**
 * C'est `tools/phi2x.py` qui est repris ici, parce que c'est elle qui dérive la
 * clé LOVE et publie le 30078 : notre phase doit être celle que la station dira
 * de nous, pas une autre. Les deux écarts relevés le 2026-08-13, à porter à
 * Fred :
 *
 * 1. **La grille des pentagones.** La station et `phi2x.js` (le web) la font
 *    tourner (« Sphère Temps Phi », un tour en ~14,83 h) ; `Phi2X_Math.gd` de
 *    cabine-33 appelle son offset **sans horodatage**, donc sur la grille figée.
 *    L'écart mesuré sur une naissance réelle est de **2,03 radian** — ce n'est
 *    pas un arrondi, c'est une autre grandeur. Un joueur de cabine-33 diffuse
 *    donc une phase incomparable avec celle que la station lui attribue.
 *    ⚠ Le commentaire d'en-tête de `phi2x.py` affirme l'inverse de ce que fait
 *    son code (il annonce la grille statique, ligne 71) : c'est le code qui
 *    tranche, `_pentagon_offset(lat, lon, birth_unix_utc)`.
 *
 * 2. **Les naissances d'avant 1970.** La station prend son modulo en Python
 *    (résultat toujours positif) ; `phi2x.js` et le GDScript prennent un `fmod`
 *    qui garde le signe du dividende. Pour un horodatage négatif les angles
 *    annuel et journalier partent donc dans des directions opposées — 4,908
 *    contre 4,166 radian sur la fiche de la tablette (1948). Ici, c'est la
 *    règle de la station : [floorMod].
 */
object Phi2X {

    const val PHI = 1.6180339887
    const val F_PHI = 33.17
    const val F_2 = 31.32

    /** Multiplicateur d'onde — **pas** un modulo, l'erreur d'`atomic.html` que Fred a corrigée. */
    const val WAVE_STRETCH = F_PHI / F_2

    const val TAU = 2.0 * PI
    const val ORBITAL_DAY_S = 86400.0

    /** Année sidérale : la Terre revient au même point du ciel, pas au même jour du calendrier. */
    const val ORBITAL_YEAR_S = 365.25636 * ORBITAL_DAY_S

    /** Un tour complet de la grille pentagonale — ~53 406 s, soit 14,83 h. */
    const val GRID_ROT_S = ORBITAL_DAY_S / PHI

    private const val EARTH_RADIUS_KM = 6371.0

    /** Rayon d'influence d'un pentagone dans la moyenne circulaire. */
    private const val PENTAGON_FALLOFF_KM = 1500.0

    /**
     * Les douze pentagones du polyèdre de Goldberg, à l'époque J2000 — deux
     * pôles et deux couronnes de cinq. Les pôles ne tournent pas : une rotation
     * autour de l'axe polaire les laisse sur place.
     */
    private val PENTAGONS = arrayOf(
        90.0 to 0.0, -90.0 to 0.0,
        26.56 to 0.0, 26.56 to 72.0, 26.56 to 144.0,
        26.56 to -72.0, 26.56 to -144.0,
        -26.56 to 36.0, -26.56 to 108.0, -26.56 to 180.0,
        -26.56 to -36.0, -26.56 to -108.0,
    )

    /**
     * Le modulo de Python : le résultat porte le signe du diviseur, donc reste
     * positif. Celui de Kotlin porte le signe du dividende — la différence ne
     * se voit que sur les naissances d'avant 1970, et elle y change tout.
     */
    private fun floorMod(x: Double, m: Double): Double = ((x % m) + m) % m

    /** Distance orthodromique en kilomètres. */
    fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = Math.toRadians(lat2 - lat1)
        val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return EARTH_RADIUS_KM * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /**
     * L'offset des pentagones : une moyenne circulaire des douze directions,
     * chacune pesée par `exp(−d/1500)` — les pentagones lointains comptent
     * encore un peu, ce qui lisse le passage de l'un à l'autre.
     *
     * `atan2(Σsin, Σcos)` plutôt qu'une moyenne d'angles : additionner des
     * angles qui bouclent à 2π donnerait un résultat faux au passage du zéro.
     *
     * [unixTs] null rend la grille figée de J2000 ; sinon la grille est tournée
     * à l'instant demandé. La station appelle toujours avec l'instant.
     */
    fun pentagonOffset(lat: Double, lon: Double, unixTs: Double? = null): Double {
        val rotationDeg = unixTs?.let { Math.toDegrees(floorMod(it, GRID_ROT_S) / GRID_ROT_S * TAU) }
        var sumSin = 0.0
        var sumCos = 0.0
        for (i in PENTAGONS.indices) {
            val (plat, plon0) = PENTAGONS[i]
            // Les deux pôles restent en place : les faire tourner autour de
            // l'axe polaire ne veut rien dire.
            val plon = if (rotationDeg == null || i <= 1) {
                plon0
            } else {
                val turned = floorMod(plon0 + rotationDeg, 360.0)
                if (turned > 180.0) turned - 360.0 else turned
            }
            val weight = exp(-haversineKm(lat, lon, plat, plon) / PENTAGON_FALLOFF_KM)
            val angle = i / 12.0 * TAU
            sumSin += sin(angle) * weight
            sumCos += cos(angle) * weight
        }
        val result = atan2(sumSin, sumCos)
        return if (result >= 0.0) result else result + TAU
    }

    /**
     * φᵢ ∈ [0, 2π) : l'angle annuel de la Terre sur son orbite, plus son angle
     * de rotation à l'heure solaire vraie du lieu, plus l'offset des pentagones
     * — le tout étiré par f_Φ/f_2.
     *
     * [utcOffsetH] corrige l'heure locale saisie en heure UTC. Nous ne la
     * demandons pas : la correction solaire par la longitude tient l'erreur
     * sous ±30 min pour tout fuseau légal, ce que Fred documente et applique
     * de la même façon.
     */
    fun personalPhase(
        birthUnix: Long,
        lat: Double,
        lon: Double,
        utcOffsetH: Double = 0.0,
    ): Double {
        val birthUtc = birthUnix - utcOffsetH * 3600.0
        val solarCorrection = lon / 360.0 * ORBITAL_DAY_S
        val annual = floorMod(birthUtc, ORBITAL_YEAR_S) / ORBITAL_YEAR_S * TAU
        val daily = floorMod(birthUtc + solarCorrection, ORBITAL_DAY_S) / ORBITAL_DAY_S * TAU
        val penta = pentagonOffset(lat, lon, birthUtc)
        // Le modulo final est celui de C (`fmod`), comme la station : la somme
        // étant positive, il coïncide ici avec le modulo de Python.
        return (annual + daily + penta) * WAVE_STRETCH % TAU
    }

    /**
     * La phase d'une fiche d'incarnation, ou null tant qu'il y manque de quoi
     * la calculer.
     *
     * ⚠ L'heure pèse **un tour complet par jour** dans l'angle journalier, là
     * où elle ne déplaçait la clé que d'un cran. Une fiche sans heure prend
     * midi ([BirthData.saltHour]) : c'est la convention de la station, et c'est
     * ce qui fait dire au sélecteur que l'heure est « recommandée ».
     */
    fun personalPhase(b: BirthData): Double? {
        if (!b.dateComplete || b.lat == null || b.lon == null) return null
        return personalPhase(birthUnixUtc(b), b.lat, b.lon)
    }

    /**
     * Le seuil du « match quantique » de Fred (`Atom4Peace.SUPER_COHERENCE_K`) :
     * au-delà, les deux ondes se croisent sans presque rien perdre.
     */
    const val SUPER_COHERENCE_K = 0.95

    /**
     * k = 1 / (1 + |sin Δφ|) — le taux de résonance entre deux phases.
     *
     * Il ne descend jamais sous 0,5 : deux ondes en quadrature échangent encore.
     * Le maximum, 1, est atteint aux deux extrémités — en phase **et** en
     * opposition de phase, ce que Fred appelle la singularité optique. C'est
     * voulu : s'opposer exactement, c'est encore se répondre.
     */
    fun resonanceK(phaseA: Double, phaseB: Double): Double =
        1.0 / (1.0 + kotlin.math.abs(kotlin.math.sin(phaseA - phaseB)))

    /**
     * En phase ou en opposition, à [tolerance] près — la singularité optique.
     * La distance se mesure sur le cercle : 0 et 2π sont le même endroit.
     */
    fun isOpticalSingularity(phaseA: Double, phaseB: Double, tolerance: Double = 0.05): Boolean {
        var delta = floorMod(kotlin.math.abs(phaseA - phaseB), TAU)
        if (delta > PI) delta = TAU - delta
        return delta < tolerance || kotlin.math.abs(delta - PI) < tolerance
    }

    /**
     * L'instant de naissance en secondes Unix, l'heure d'horloge du lieu étant
     * lue comme si elle était UTC — c'est ce qu'attend `compute_personal_phase`,
     * qui rattrape ensuite le décalage par la longitude.
     */
    fun birthUnixUtc(b: BirthData): Long = LocalDateTime.of(
        b.year!!, b.month!!, b.day!!, b.saltHour, b.saltMinute,
    ).toEpochSecond(ZoneOffset.UTC)
}
