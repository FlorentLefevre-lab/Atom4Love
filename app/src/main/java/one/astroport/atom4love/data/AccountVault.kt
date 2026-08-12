package one.astroport.atom4love.data

import one.astroport.atom4love.multipass.Atom4LoveActivation
import one.astroport.atom4love.multipass.MultipassResponse

/**
 * Le rangement d'un compte Astroport.ONE, vu par ce qui l'utilise.
 *
 * L'implémentation réelle est [MultipassStore], adossée au coffre matériel de
 * l'appareil. L'interface existe pour que le parcours d'inscription — la seule
 * logique vraiment délicate, l'enchaînement création puis activation — se
 * vérifie sur JVM, là où le Keystore d'Android n'existe pas.
 */
interface AccountVault {

    /** null tant qu'aucun MULTIPASS n'a été créé depuis cet appareil. */
    suspend fun load(): MultipassAccount?

    /** Le compte tel que la station vient de le rendre, sans clé LOVE encore. */
    suspend fun save(station: String, response: MultipassResponse)

    /** Pose la clé LOVE sur le compte existant, et rend le compte complété. */
    suspend fun saveLove(activation: Atom4LoveActivation): MultipassAccount?
}
