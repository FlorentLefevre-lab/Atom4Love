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
     * Le nom du médium, pour l'indicateur du haut. Reste en dur : ce sont les
     * noms des technologies, d'aucune langue.
     *
     * « BT » plutôt que « BLE », « Wi-Fi P2P » plutôt que « Wi-Fi Direct » :
     * trois noms courts, du même moule, qui se comparent d'un coup d'œil dans
     * une ligne où l'on cherche lequel est actif. Le rune du glyphe est le
     * Bluetooth générique, la paire AP / P2P se répond.
     */
    val short: String,
) {
    BLE(R.string.medium_ble, "BT"),

    /**
     * Le **Bluetooth classique** (RFCOMM), entre la radio basse consommation et
     * le Wi-Fi — et c'est bien sa place, mesurée le 16/08 sur le banc
     * `diag/RfcommProbe` : **78 ko/s la balise allumée, 102 sans**, contre
     * 14 ko/s en BLE et 15,5 Mo/s en Wi-Fi Direct. Cinq à sept fois le BLE,
     * deux cents fois moins que le Wi-Fi.
     *
     * Il ne sert donc qu'à **une** situation, mais elle est réelle : deux
     * téléphones sans réseau du lieu et sans groupe Wi-Fi possible. Là où l'on
     * n'avait que 14 ko/s, on en a cinq fois plus.
     *
     * ⚠ **Il ne remplace pas le BLE et ne le remplacera pas.** Le BLE porte
     * l'annonce anonyme — adresses qui tournent toutes les trente secondes,
     * diffusion sans connexion. Le classique, lui, exige de connaître l'adresse
     * du pair, et cette adresse est une **MAC publique stable à vie**. Elle
     * voyage donc sur le lien BLE déjà scellé, comme le nom et la passe du
     * groupe Wi-Fi, et n'est donnée qu'à un pair attesté — jamais à la salle.
     *
     * ⚠ Ni appairage ni découvrabilité (vérifié : `bondState` reste
     * `BOND_NONE`), mais **la première connexion échoue souvent** : il faut
     * réessayer.
     */
    BT_CLASSIC(R.string.medium_bt_classic, "BT classique"),

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
    WIFI_DIRECT(R.string.medium_wifi_direct, "Wi-Fi P2P"),
    ;

    /** Rang de routage — l'ordre de déclaration est l'échelle. */
    val rank: Int get() = ordinal

    /**
     * Ce que la bêta laisse voir, et donc emprunter.
     *
     * **Le code des quatre médiums reste entier** : rien n'est supprimé, ni les
     * sockets, ni les trames `ADDRESS`/`GROUP`, ni le routage par rang. Ce
     * drapeau ne ferme qu'une porte — celle de l'écran. Or la cabine ne monte
     * JAMAIS d'elle-même : elle s'établit en BLE et toute montée se décide
     * (`enable`, `select`). Une voie que l'interface ne propose plus est donc
     * une voie que personne n'emprunte, sans qu'aucune ligne du moteur ait à
     * changer. Le jour où la bêta s'ouvre, il n'y a qu'ici à revenir.
     *
     * Pourquoi les trois autres, alors qu'elles marchent et qu'elles sont
     * mesurées (78 ko/s en RFCOMM, 15,5 Mo/s en Wi-Fi Direct) : parce qu'elles
     * coûtent toutes une **explication**. Le Wi-Fi Direct demande une
     * permission de plus, fabrique un groupe dont le départ de l'hôte dissout
     * la voie pour tout le monde, et impose deux bandeaux pour dire qui le
     * tient ; le Wi-Fi AP ne marche qu'entre deux personnes sur la même box.
     * Ça fait quatre entrées dans une liste, quatre conséquences à lire et deux
     * bandeaux, pour un débit que l'application ne promet nulle part. La bêta
     * porte deux voies et deux seulement — **la radio pour ce qui est ici, le
     * relais pour ce qui est loin** — et c'est déjà toute la portée du produit.
     */
    val inBeta: Boolean get() = this == BLE

    companion object {
        /** Les voies que l'interface a le droit de nommer. Voir [inBeta]. */
        val betaEntries: List<Medium> = entries.filter { it.inBeta }
    }

    /** Une lettre pour la clé de lien : `b:c:AA:BB` se lit d'un coup d'œil. */
    val tag: Char get() = when (this) {
        BLE -> 'b'
        BT_CLASSIC -> 'r'
        WIFI_STATION -> 'w'
        WIFI_DIRECT -> 'p'
    }
}
