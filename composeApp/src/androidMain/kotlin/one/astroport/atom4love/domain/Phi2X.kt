package one.astroport.atom4love.domain

import java.time.ZoneOffset
import java.util.Locale
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
        var sumSin = 0.0
        var sumCos = 0.0
        dynamicPentagons(unixTs).forEachIndexed { i, (plat, plon) ->
            val weight = exp(-haversineKm(lat, lon, plat, plon) / PENTAGON_FALLOFF_KM)
            val angle = i / 12.0 * TAU
            sumSin += sin(angle) * weight
            sumCos += cos(angle) * weight
        }
        val result = atan2(sumSin, sumCos)
        return if (result >= 0.0) result else result + TAU
    }

    /**
     * La grille pentagonale à l'instant donné — `get_dynamic_pentagons()`.
     *
     * Les dix pentagones non polaires tournent autour de l'axe des pôles, un
     * tour complet par [GRID_ROT_S]. Les deux pôles restent en place : les faire
     * tourner autour de cet axe-là ne veut rien dire. [unixTs] null rend la
     * grille figée de J2000.
     */
    fun dynamicPentagons(unixTs: Double?): List<Pair<Double, Double>> {
        val rotationDeg = unixTs?.let { Math.toDegrees(floorMod(it, GRID_ROT_S) / GRID_ROT_S * TAU) }
        return PENTAGONS.mapIndexed { i, (plat, plon0) ->
            if (rotationDeg == null || i <= 1) {
                plat to plon0
            } else {
                val turned = floorMod(plon0 + rotationDeg, 360.0)
                plat to (if (turned > 180.0) turned - 360.0 else turned)
            }
        }
    }

    /**
     * Le pentagone le plus proche à cet instant, **compté à partir de zéro** —
     * `get_nearest_pentagon_id()`. C'est le `P<pp>` d'une adresse `a4l:`.
     *
     * ⚠ Rien à voir avec les douze portails de [GoldbergPortal], numérotés de 1
     * à 12 et dérivés de la grille H3 : ceux-là ne tournent pas. Deux grilles,
     * deux numérotations — ne jamais confondre l'une pour l'autre.
     */
    fun nearestPentagonId(lat: Double, lon: Double, unixTs: Double): Int {
        val pentagons = dynamicPentagons(unixTs)
        var best = 0
        var bestKm = Double.MAX_VALUE
        pentagons.forEachIndexed { i, (plat, plon) ->
            val d = haversineKm(lat, lon, plat, plon)
            if (d < bestKm) {
                bestKm = d
                best = i
            }
        }
        return best
    }

    /**
     * Ψ ∈ [0, 1] — l'amplitude du champ de résonance cymatique planétaire en ce
     * lieu, à cet instant. Portage de `compute_resonance_field()`.
     *
     * Chaque pentagone envoie une onde dont la phase est sa distance rapportée
     * au rayon terrestre et étirée par [WAVE_STRETCH], amortie en `exp(−d/1500)`.
     * La somme des douze vit dans [−12, 12] ; on la ramène sur [0, 1].
     *
     * ⚠ Sur des coordonnées réelles elle ne bouge presque pas — Fred a mesuré
     * qu'elle reste dans [0,50 ; 0,545] et a retiré de son projecteur la teinte
     * qui en dépendait. Elle vaut comme donnée du certificat, pas comme signal
     * visuel.
     */
    fun resonanceField(lat: Double, lon: Double, unixTs: Double): Double {
        var amplitude = 0.0
        dynamicPentagons(unixTs).forEach { (plat, plon) ->
            val d = haversineKm(lat, lon, plat, plon)
            amplitude += cos(d / EARTH_RADIUS_KM * WAVE_STRETCH) * exp(-d / PENTAGON_FALLOFF_KM)
        }
        return (amplitude + 12.0) / 24.0
    }

    /** `a5l:<XXXX>` — Ψ quantifié sur seize bits. Portage de `encode_a5l_tag()`. */
    fun encodeA5lTag(amplitude: Double): String {
        val v = amplitude.coerceIn(0.0, 1.0)
        return "a5l:%04X".format(Locale.US, kotlin.math.round(v * 65535.0).toInt())
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
        if (b.lat == null || b.lon == null) return null
        val unix = birthUnixUtc(b) ?: return null
        return personalPhase(unix, b.lat, b.lon)
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

    /** Ce que deux phases se disent : combien, et de quelle sorte. */
    data class Classification(
        val k: Double,
        /** k ramené sur 0..100 — `(k − 0,5) × 200`, arrondi comme chez Fred. */
        val percent: Int,
        /** L'écart replié dans [0, π]. */
        val deltaPhi: Double,
        val union: Boolean,
    )

    /**
     * Portage de `classifyResonance()` : [resonanceK] seul ne distingue pas
     * l'union (Δφ ≈ 0) du défi (Δφ ≈ π) — il vaut 1 aux deux bouts. L'écart,
     * lui, tranche : en deçà du quart de tour c'est une union, au-delà un
     * défi. C'est le 🤝 et le ⚡ de ses écrans.
     *
     * ⚠ Le ⚡ dit **de quelle sorte**, jamais **combien** — et surtout pas
     * « moins ». L'opposable du Tzolkin est le complémentaire, pas le
     * contraire : sa planche `assets/miz/tzolkin_oracle.svg` nomme l'antipode
     * « DÉFI », l'un des cinq **pouvoirs** de l'Oracle, et [resonanceK] lui
     * donne la même valeur qu'à l'union. Ne jamais s'en servir pour dégrader un
     * rang : ce qui doit tomber, c'est le quart de tour, où k vaut 0,5.
     */
    fun classifyResonance(phaseA: Double, phaseB: Double): Classification {
        val k = resonanceK(phaseA, phaseB)
        var delta = floorMod(kotlin.math.abs(phaseA - phaseB), TAU)
        if (delta > PI) delta = TAU - delta
        return Classification(
            k = k,
            percent = kotlin.math.round((k - 0.5) * 200).toInt(),
            deltaPhi = delta,
            union = delta < PI / 2,
        )
    }

    /**
     * En phase ou en opposition, à [tolerance] près — la singularité optique.
     * La distance se mesure sur le cercle : 0 et 2π sont le même endroit.
     */
    fun isOpticalSingularity(phaseA: Double, phaseB: Double, tolerance: Double = 0.05): Boolean {
        var delta = floorMod(kotlin.math.abs(phaseA - phaseB), TAU)
        if (delta > PI) delta = TAU - delta
        return delta < tolerance || kotlin.math.abs(delta - PI) < tolerance
    }

    // ⚠ `F_WATER` (429,62 Hz) et `F_WATER_D` (= F_WATER + F_PHI, « 462,79 Hz →
    // D » chez Fred) vivaient ici : le couple du binaural de cabine-33. Elles
    // sont parties le 15/08 avec ω_bio puis avec le binaural lui-même. F_PHI,
    // lui, reste — il entre dans le calcul de la phase, qui n'a rien à voir.


    /**
     * L'instant de naissance en secondes Unix — celui que scelle le SALT.
     *
     * ⚠ Ce commentaire disait le contraire jusqu'au 2026-08-14 : que
     * `compute_personal_phase` attendait l'heure d'horloge brute et rattrapait
     * elle-même la longitude. Elle rattrape bien quelque chose — le
     * `solar_corr_s` de son angle journalier — mais `atom4love_publish.py` lui
     * passe l'instant **déjà converti** (`birth_dt_utc.timestamp()`). Lire
     * l'heure d'horloge ici nous donnait donc une phase que la station ne
     * confirmerait pas : dix minutes d'écart sous nos longitudes, et le Radar
     * compare des phases.
     *
     * Null quand la longitude manque : sans elle, il n'y a pas d'instant.
     */
    fun birthUnixUtc(b: BirthData): Long? =
        b.birthInstantUtc?.toEpochSecond(ZoneOffset.UTC)
}
