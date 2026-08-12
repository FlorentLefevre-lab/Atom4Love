package one.astroport.atom4love.chat

import androidx.annotation.StringRes
import one.astroport.atom4love.R

/**
 * Ce que la cabine signale quand ça se passe mal.
 *
 * **Une valeur, jamais une phrase.** Ces incidents naissent dans le moteur —
 * un rappel de la pile Bluetooth, un socket qui refuse — là où il n'y a ni
 * `Context` ni composition, et où la langue de l'utilisateur n'est pas une
 * question qu'on peut se poser. Le moteur dit *ce qui* s'est passé et porte
 * les quelques nombres qui vont avec ; c'est l'écran qui l'écrit, dans la
 * langue du moment, avec [CabinError.text].
 *
 * Le corollaire compte autant : rien ici ne remplace un journal. Les `Log.d`
 * de [CabinChat] restent en français, ils s'adressent à qui débogue.
 */
sealed interface CabinError {

    /** Le message tout prêt, à ceci près qu'il lui manque encore sa langue. */
    @get:StringRes
    val messageRes: Int

    /** Les arguments du format, dans l'ordre. Vide quand la phrase se suffit. */
    val args: List<Any> get() = emptyList()

    data object BluetoothOff : CabinError {
        override val messageRes = R.string.cabin_err_bluetooth_off
    }

    data object BluetoothCut : CabinError {
        override val messageRes = R.string.cabin_err_bluetooth_cut
    }

    data class TextTooLong(val maxBytes: Int) : CabinError {
        override val messageRes = R.string.cabin_err_text_too_long
        override val args get() = listOf<Any>(maxBytes)
    }

    data object UnreadableAttachment : CabinError {
        override val messageRes = R.string.cabin_err_unreadable_attachment
    }

    data object NoLink : CabinError {
        override val messageRes = R.string.cabin_err_no_link
    }

    /** [who] est une adresse déjà tronquée : on ne montre pas une MAC entière. */
    data class NoAck(val who: String) : CabinError {
        override val messageRes = R.string.cabin_err_no_ack
        override val args get() = listOf<Any>(who)
    }

    data class SendFailed(val who: String) : CabinError {
        override val messageRes = R.string.cabin_err_send_failed
        override val args get() = listOf<Any>(who)
    }

    /** [medium] porte le nom court de la technologie, qui ne se traduit pas. */
    data class MediumUnreachable(val medium: Medium) : CabinError {
        override val messageRes = R.string.cabin_err_medium_unreachable
        override val args get() = listOf<Any>(medium.short)
    }

    data object P2pUnreachable : CabinError {
        override val messageRes = R.string.cabin_err_p2p_unreachable
    }

    data object P2pImpossible : CabinError {
        override val messageRes = R.string.cabin_err_p2p_impossible
    }

    data object AdvertiseUnavailable : CabinError {
        override val messageRes = R.string.cabin_err_advertise_unavailable
    }

    data class AdvertiseRefused(val code: Int) : CabinError {
        override val messageRes = R.string.cabin_err_advertise_refused
        override val args get() = listOf<Any>(code)
    }

    data class ScanRefused(val code: Int) : CabinError {
        override val messageRes = R.string.cabin_err_scan_refused
        override val args get() = listOf<Any>(code)
    }
}
