package one.astroport.atom4love.chat

import androidx.annotation.StringRes
import one.astroport.atom4love.R

/**
 * Les médiums par lesquels une cabine peut porter ses trames.
 *
 * L'ordre est celui de l'échelle : **le BLE d'abord**, toujours — c'est la
 * seule porte d'entrée, la seule qui découvre un inconnu et l'atteste. Les
 * deux médiums Wi-Fi ne sont que des montées en débit : on ne les atteint
 * jamais par une recherche sur le réseau, seulement par une adresse annoncée
 * *dans* un canal BLE déjà scellé (trame `ENDPOINT`). Rien de la cabine ne
 * s'annonce sur un LAN partagé, et un pair isolé par l'AP d'un bar ne casse
 * pas la découverte.
 *
 * [rank] sert au routage : à personne égale, la trame part par le lien de plus
 * haut rang **parmi les médiums acceptés**. Station avant Direct parce qu'une
 * station existe déjà quand les deux noyaux s'y trouvent, là où un groupe P2P
 * doit être formé exprès.
 *
 * **Débits, mesurés et non promis.** La liaison brute entre deux appareils du
 * banc porte 11,8 Mo/s par la station contre 14 Ko/s en BLE — 840×. La cabine,
 * elle, a transporté 1,4 Mo par la station en 12,5 s puis 38 s puis 43 s d'un
 * essai à l'autre (2026-08-11), soit 32 à 112 Ko/s : le gain est réel mais
 * loin du lien, et très variable. Aucun rapport n'est donc affiché à
 * l'utilisateur — la cabine nomme le médium, elle ne chiffre pas une promesse
 * qu'elle ne tient pas. Le plafond reste à trouver ; couper scan et annonce
 * pendant le transfert n'a rien changé, ce qui écarte la seule coexistence
 * Bluetooth/Wi-Fi.
 */
enum class Medium(
    /**
     * Ce que la cabine en dit à l'écran — une ressource, pas une phrase : ce
     * mot-là s'affiche, il suit donc la langue choisie. Le lire demande un
     * `Context` (`stringResource(medium.labelRes)` en Compose).
     */
    @StringRes val labelRes: Int,
    /**
     * Le nom du médium, pour l'indicateur du haut. Reste en dur : « BLE » et
     * « Wi-Fi Direct » sont les noms des technologies, d'aucune langue.
     */
    val short: String,
) {
    BLE(R.string.medium_ble, "BLE"),

    /**
     * « Par la station » disait le contraire de ce qui se passe : dans cette
     * application, *la station* c'est Astroport.ONE — et l'écran d'accueil
     * lui-même. Or ce médium ne passe par aucune des deux : les deux noyaux
     * sont clients du même point d'accès, la box du lieu, et rien de ce qu'ils
     * se disent ne sort de ce réseau. Le mot vient du vocabulaire Wi-Fi, où
     * une station est le client d'un point d'accès — juste dans le code, mais
     * lu à côté de « sans relais » il suggérait un serveur dans la boucle.
     */
    WIFI_STATION(R.string.medium_wifi_station, "Wi-Fi AP"),
    WIFI_DIRECT(R.string.medium_wifi_direct, "Wi-Fi Direct"),
    ;

    /** Rang de routage — l'ordre de déclaration est l'échelle. */
    val rank: Int get() = ordinal

    /** Une lettre pour la clé de lien : `b:c:AA:BB` se lit d'un coup d'œil. */
    val tag: Char get() = when (this) {
        BLE -> 'b'
        WIFI_STATION -> 'w'
        WIFI_DIRECT -> 'p'
    }
}
