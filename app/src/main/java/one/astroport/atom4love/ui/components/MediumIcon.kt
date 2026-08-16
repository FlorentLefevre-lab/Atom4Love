package one.astroport.atom4love.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.ui.graphics.vector.ImageVector
import one.astroport.atom4love.chat.Medium

/**
 * Le pictogramme d'un médium — **un seul endroit, deux lecteurs.**
 *
 * La ligne du titre de la Carte et la liste déroulante de l'en-tête montrent les
 * mêmes quatre voies ; elles portaient chacune leur propre `when`, et rien
 * n'obligeait les deux à dire la même chose. Le jour où le BLE a cessé de
 * partager son rune avec le Bluetooth classique, une des deux copies serait
 * restée en arrière sans que rien ne le signale.
 *
 * Ce sont les **symboles génériques de Material**, pas les marques déposées du
 * Bluetooth SIG ni de la Wi-Fi Alliance : celles-là ont des conditions d'usage
 * réservées aux produits certifiés. Le rune et les arcs disent la même chose et
 * se reconnaissent aussi bien.
 *
 * ⚠ **La forme dit ce que le médium EST, pas seulement quelle radio il
 * emprunte.** C'est ce qui sépare les deux Bluetooth, qui partageaient un
 * pictogramme identique et ne se distinguaient donc pas du tout :
 *
 * - [Medium.BLE] — le rune **aux arcs** : l'annonce anonyme et le balayage. Il
 *   parle à personne en particulier, et c'est par lui qu'un inconnu se découvre.
 * - [Medium.BT_CLASSIC] — le rune **au trait de liaison** : il ne découvre
 *   personne, il compose vers une adresse qu'on connaît déjà, donnée sur un lien
 *   scellé. Un lien établi, jamais une recherche.
 * - [Medium.WIFI_STATION] — les arcs : on est client d'un point d'accès.
 * - [Medium.WIFI_DIRECT] — les arcs qui rayonnent d'un point : le groupe est à
 *   nous.
 *
 * ⚠ Les noms Material portent une connotation d'**état** (« searching »,
 * « connected ») alors que l'état est déjà porté par la couleur. Ce n'est pas
 * une contradiction : dans ces deux écrans **la couleur dit l'état et la forme
 * dit l'identité** — et ces connotations décrivent une propriété permanente, pas
 * un statut passager. Le BLE cherche toujours ; le classique est toujours le
 * lien vers une adresse connue.
 */
val Medium.icon: ImageVector
    get() = when (this) {
        Medium.BLE -> Icons.Filled.BluetoothSearching
        Medium.BT_CLASSIC -> Icons.Filled.BluetoothConnected
        Medium.WIFI_STATION -> Icons.Filled.Wifi
        Medium.WIFI_DIRECT -> Icons.Filled.WifiTethering
    }
