package one.astroport.atom4love.proximity

import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Le dernier mètre — un rythme que deux téléphones trouvent sans se parler.
 *
 * La chaleur du RSSI mène au bon mètre carré et s'arrête là : elle dit à quel
 * point un signal est fort, jamais **lequel des six corps** attablés est la
 * carte qu'on suit. Aucun réglage ne le lui apprendra, c'est la physique de la
 * mesure. Il fallait donc autre chose pour le dernier pas, et la cabine ne
 * pouvait pas être ce quelque chose : elle porte le npub attesté, et s'en
 * servir pour se trouver revient à savoir qui est quelqu'un **avant** de
 * l'avoir trouvé — l'ordre inverse d'une rencontre.
 *
 * D'où ceci. Les deux appareils connaissent déjà les deux φ : le sien, et celui
 * qui arrive dans l'annonce de proximité. Deux nombres suffisent à en dériver
 * un motif clignotant, et la dérivation étant **symétrique**, les deux en
 * tirent le même sans avoir rien à échanger. On lève son écran, on cherche dans
 * la salle celui qui bat pareil, et le dernier mètre est franchi par les yeux.
 *
 * ## Ce que ça ne diffuse pas
 *
 * Rien. Pas un octet de plus dans l'annonce — le motif ne circule jamais, il se
 * recalcule de chaque côté. Un observateur qui écoute la salle n'apprend donc
 * strictement rien de neuf : il voit ce que voit n'importe qui debout dans la
 * pièce, deux personnes qui se rejoignent.
 *
 * ## Le consentement est dans le silence
 *
 * Le motif ne se montre que si **les deux** ont choisi l'autre : chaque
 * téléphone ne bat que pour la carte que son porteur a retournée. Un seul qui
 * cherche, et il clignote seul dans son coin, sans que personne le sache. Il
 * n'y a donc rien à accepter, rien à refuser, rien à notifier — l'accord se
 * manifeste en se produisant, et son absence ne se dénonce pas.
 *
 * ## Ce dont ça dépend
 *
 * D'une horloge commune : le motif est calé sur le temps Unix absolu, pas sur
 * un départ négocié. Deux Android à l'heure du réseau tiennent la dizaine de
 * millisecondes, très en dessous du pas de [SLOT_MS] ; un appareil dont
 * l'horloge automatique est coupée verra le même rythme décalé, ce qui reste
 * lisible à l'œil — même figure, un temps de retard — mais moins net.
 */
object Rendezvous {

    /** Longueur du motif. 16 pas de [SLOT_MS] font un cycle de 2,4 s. */
    const val SLOTS = 16

    /**
     * Durée d'un pas. 150 ms est au-dessus de la dérive de deux horloges NTP et
     * au-dessus de la persistance rétinienne : chaque éclair se compte.
     */
    const val SLOT_MS = 150L

    /**
     * Éclairs par cycle, le premier pas compris. Cinq sur seize laissent du noir
     * entre les groupes — c'est le noir qui rend une figure comparable de loin,
     * un strobe dense ressemble à tous les autres.
     *
     * Le premier pas est toujours allumé : il donne au cycle un début visible,
     * sans quoi deux motifs identiques décalés d'un cran sembleraient différents.
     * Restent donc quatre éclairs à placer parmi quinze, soit 1365 figures
     * distinctes — largement de quoi séparer les quelques rendez-vous simultanés
     * d'une salle.
     */
    const val LIT = 5

    /** Le cycle complet, en millisecondes. */
    const val CYCLE_MS = SLOTS * SLOT_MS

    /**
     * ## Chercher plusieurs cartes à la fois
     *
     * Un écran ne peut battre qu'une figure. Chercher trois personnes demande
     * donc de les jouer à tour de rôle — et le piège est là : si chacun défile
     * dans **son** ordre, deux appareils qui se cherchent l'un l'autre peuvent
     * ne jamais afficher leur figure commune au même moment. Ils se manqueraient
     * indéfiniment tout en étant tous les deux en train de se chercher.
     *
     * D'où des **fenêtres attribuées à la paire**, pas au rang dans la liste.
     * Chaque couple de phases tombe toujours dans la même fenêtre
     * ([windowOf]), et la fenêtre courante se lit sur l'horloge commune
     * ([windowAt]) : les deux appareils jouent donc cette paire exactement
     * pendant les mêmes secondes, quel que soit le nombre de cartes que chacun
     * cherche et quel que soit son ordre.
     *
     * Une fenêtre dure un cycle entier : la figure se voit en entier ou pas du
     * tout, jamais tronquée. Trois fenêtres font revenir une paire donnée
     * toutes les 7,2 s — assez rare pour qu'on lève l'écran deux fois, assez
     * fréquent pour ne pas renoncer.
     */
    const val WINDOWS = 3

    /**
     * La fenêtre d'une paire — **symétrique**, comme le motif lui-même, et
     * dérivée du même condensat : les deux côtés la calculent sans rien
     * échanger. `null` si l'une des phases manque.
     */
    fun windowOf(mine: Double?, theirs: Double?): Int? {
        if (mine == null || theirs == null) return null
        val a = ProximityPayload.encodePhase(mine)
        val b = ProximityPayload.encodePhase(theirs)
        return Math.floorMod(ofOnAir(minOf(a, b), maxOf(a, b)).mask, WINDOWS)
    }

    /** La fenêtre en cours, sur l'horloge absolue — le même repère que [Beat.slotAt]. */
    fun windowAt(millis: Long): Int =
        Math.floorMod(millis / CYCLE_MS, WINDOWS.toLong()).toInt()

    /**
     * Le rythme de la paire, ou null tant qu'un des deux φ manque — le sceau
     * vient de la date, la phase demande le lieu, et sans lieu il n'y a pas de
     * rendez-vous possible.
     *
     * Les deux phases sont d'abord **ramenées à ce qui passe dans l'air** :
     * l'autre ne connaît la nôtre qu'arrondie aux 65535ᵉ de tour par
     * [ProximityPayload.encodePhase], et si nous hachions notre φ en pleine
     * précision nous ne tirerions pas le même motif que lui. C'est le genre
     * d'écart qui ne se voit qu'une fois dans un bar, à deux mètres, en se
     * demandant pourquoi rien ne bat ensemble.
     */
    fun of(mine: Double?, theirs: Double?): Beat? {
        if (mine == null || theirs == null) return null
        val a = ProximityPayload.encodePhase(mine)
        val b = ProximityPayload.encodePhase(theirs)
        return ofOnAir(minOf(a, b), maxOf(a, b))
    }

    /**
     * Le motif à partir des deux phases telles qu'elles voyagent, **déjà
     * ordonnées**. C'est cet ordre qui rend la dérivation symétrique : ni l'un
     * ni l'autre n'est « l'appelant », les deux hachent la même paire.
     */
    internal fun ofOnAir(low: Int, high: Int): Beat {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(ByteBuffer.allocate(8).putInt(low).putInt(high).array())

        // Un tirage sans remise parmi les pas 1..15 : chaque octet du condensat
        // désigne un rang dans ce qui reste, si bien que les quatre éclairs sont
        // toujours distincts et que les deux appareils tirent dans le même ordre.
        val pool = (1 until SLOTS).toMutableList()
        var mask = 1
        repeat(LIT - 1) { draw ->
            val pick = pool.removeAt((digest[draw].toInt() and 0xFF) % pool.size)
            mask = mask or (1 shl pick)
        }
        return Beat(mask)
    }

    /**
     * Un motif clignotant, seize pas dans un entier — un bit par pas, le bit 0
     * pour le premier. Le tenir en masque plutôt qu'en tableau donne l'égalité
     * gratuitement, ce dont les tests se servent pour vérifier la symétrie.
     */
    @JvmInline
    value class Beat(val mask: Int) {

        /** Le pas en cours à cet instant du temps Unix. */
        fun slotAt(millis: Long): Int = Math.floorMod(millis / SLOT_MS, SLOTS.toLong()).toInt()

        /** Ce pas porte-t-il un éclair ? */
        fun isLit(slot: Int): Boolean = (mask shr Math.floorMod(slot, SLOTS)) and 1 == 1

        /**
         * L'éclat à cet instant : 1 au moment de l'éclair, éteint à la fin du
         * pas. La décroissance est quadratique — un flash sec plutôt qu'une
         * respiration, parce que ce qu'on compare de l'autre bout d'une salle
         * est un **instant**, pas une intensité.
         */
        fun glowAt(millis: Long): Float {
            if (!isLit(slotAt(millis))) return 0f
            val progress = Math.floorMod(millis, SLOT_MS) / SLOT_MS.toFloat()
            val fading = 1f - progress
            return fading * fading
        }

        /** La figure en clair, pour les tests et le journal : `▮▯▯▮…`. */
        override fun toString(): String =
            (0 until SLOTS).joinToString("") { if (isLit(it)) "▮" else "▯" }
    }
}
