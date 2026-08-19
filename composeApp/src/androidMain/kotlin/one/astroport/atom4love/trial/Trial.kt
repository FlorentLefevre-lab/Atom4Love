package one.astroport.atom4love.trial

import one.astroport.atom4love.domain.Phi2X

/**
 * **Quand proposer le MULTIPASS** — et comment le savoir sans rien demander.
 *
 * ## Le problème qu'on résout
 *
 * La proposition tombait à la seconde où le noyau était forgé. Quelqu'un venait
 * de saisir sa date de naissance, son lieu et sa polarité ; l'écran suivant lui
 * demandait une adresse e-mail et un code pour ouvrir un compte sur une station
 * dont il n'avait jamais entendu parler, **avant** d'avoir vu une carte, une
 * conversation, une balise s'allumer. On lui demandait de régulariser une
 * situation qu'il n'avait pas encore eue.
 *
 * Le bon moment est **après la première expérience**. Restait à savoir quand
 * elle est finie — et personne ne peut le déclarer à notre place.
 *
 * ## Ce qui le dit : le déplacement
 *
 * Une première expérience d'Atom4Love, c'est un lieu : un bar, une salle, une
 * fête. Elle est finie quand on en est **parti**, et elle a compté quand on
 * **revient** ensuite à l'application. Ces deux faits se lisent dans une chose
 * que l'appareil sait déjà, sans rien demander à personne : la position, comparée
 * à celle du jour de la forge.
 *
 * D'où les deux conditions, qui doivent être remplies **toutes les deux** :
 *  - s'être éloigné d'au moins [MIN_KM] du lieu où le noyau a été forgé ;
 *  - qu'il se soit écoulé au moins [MIN_HOURS] heures depuis.
 *
 * ⚠ **La distance seule ne suffit pas, et l'heure seule non plus.** Traverser
 * une ville en vingt minutes n'est pas une soirée finie ; passer la nuit sur
 * place non plus. C'est la conjonction — être ailleurs, plus tard — qui dit
 * qu'un cycle s'est bouclé.
 *
 * ## Ce que ça ne fait pas
 *
 * ⚠ **Rien de tout ceci ne quitte l'appareil.** Aucune position n'est publiée,
 * aucune n'est datée ailleurs qu'ici, et la comparaison se fait entre deux points
 * dont un seul est retenu — celui de la forge, qui est déjà, au kilomètre près,
 * ce que le certificat publierait un jour. Ce n'est pas un suivi : c'est un
 * unique « suis-je loin de là où j'ai commencé ? », posé au démarrage.
 *
 * ⚠ **Sans position, la proposition ne vient jamais** — et c'est la bonne
 * défaillance. Quelqu'un qui refuse la localisation garde l'application entière
 * en essai, indéfiniment. Faire l'inverse — proposer par défaut faute de savoir —
 * transformerait un refus de permission en réclamation de compte, ce qui est
 * exactement le marché qu'on ne veut pas passer.
 */
object Trial {

    /**
     * Le seuil de déplacement. **Deux kilomètres**, et le chiffre se défend :
     * un kilomètre est une promenade, on peut le faire sans avoir quitté le
     * quartier de la soirée ; cinq excluraient quelqu'un qui rentre chez lui à
     * pied dans la même ville. À deux, on est chez soi et plus dans le bar.
     *
     * ⚠ À comparer à la maille du certificat, qui fait 1 km : en dessous, on
     * mesurerait du bruit de GPS plutôt qu'un trajet.
     */
    const val MIN_KM = 2.0

    /**
     * Le délai. **Trois heures** — « quelques heures » dans les mots de Florent.
     * Assez pour qu'une soirée soit derrière soi, assez peu pour que la
     * proposition arrive le lendemain matin et non trois jours plus tard, quand
     * ce qui s'est passé ne se raconte plus.
     */
    const val MIN_HOURS = 3L

    private const val HOUR_MS = 3_600_000L

    /**
     * Le point de départ : où et quand le noyau a été forgé.
     *
     * [lat] et [lon] sont nuls quand la position n'était pas connue à ce
     * moment-là — permission refusée, service coupé, fix qui n'est pas encore
     * arrivé. La proposition attend alors, indéfiniment : voir le KDoc de
     * l'objet.
     */
    data class Origin(val lat: Double?, val lon: Double?, val atMs: Long)

    /**
     * La proposition est-elle due ?
     *
     * [now] et la position courante sont explicites plutôt que lus ici : une
     * règle qui va chercher l'heure et le GPS toute seule ne se vérifie pas.
     */
    fun isDue(
        origin: Origin?,
        lat: Double?,
        lon: Double?,
        nowMs: Long,
    ): Boolean {
        val from = origin ?: return false
        val fromLat = from.lat ?: return false
        val fromLon = from.lon ?: return false
        if (lat == null || lon == null) return false
        // L'ordre compte pour la lecture, pas pour le résultat : le temps est
        // une soustraction, la distance une trigonométrie. On écarte d'abord ce
        // qui coûte le moins.
        if (nowMs - from.atMs < MIN_HOURS * HOUR_MS) return false
        return Phi2X.haversineKm(fromLat, fromLon, lat, lon) >= MIN_KM
    }
}
