package one.astroport.atom4love.proximity

/**
 * Rotation temporelle du pavage — la couche « dynamique » de l'adresse 4D (spéc. D2,
 * Dynamic Goldberg Polyhedra).
 *
 * L'adresse diffusée doit être opaque : le même identifiant ne doit pas désigner le
 * même lieu physique d'un instant à l'autre. La formule exacte est le point
 * d'architecture encore ouvert du projet (cf. README, note d'implémentation) ;
 * cette interface l'isole pour qu'elle s'applique à l'encodage et au décodage sans
 * toucher au reste du module proximité.
 */
fun interface CellRotation {

    /** Transforme l'index H3 statique en adresse diffusable à l'instant [atMillis]. */
    fun apply(h3Index: Long, atMillis: Long): Long

    companion object {
        /**
         * v0 — identité : l'adresse diffusée est l'index H3 statique, sans rotation.
         * Assumé tant que la formule D2 n'est pas arrêtée : la portée BLE (~10-100 m)
         * borne l'information révélée à des appareils déjà physiquement voisins.
         */
        val None: CellRotation = CellRotation { h3Index, _ -> h3Index }
    }
}