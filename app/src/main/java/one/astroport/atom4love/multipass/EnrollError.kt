package one.astroport.atom4love.multipass

import androidx.annotation.StringRes
import one.astroport.atom4love.R

/**
 * Pourquoi une demande de MULTIPASS n'a pas abouti.
 *
 * Comme [one.astroport.atom4love.chat.CabinError], c'est une **valeur** :
 * l'inscription se joue dans une coroutine sans `Context`, et la phrase se
 * choisit à l'affichage, dans la langue du moment.
 *
 * [FromStation] est le cas particulier qui compte : le message vient
 * d'Astroport.ONE, dans la langue que la station a bien voulu employer. On ne
 * le traduit pas — on ne le réécrit pas non plus. Le taire pour cause de
 * langue serait pire : c'est souvent la seule explication précise disponible.
 */
sealed interface EnrollError {

    data object NoAccount : EnrollError
    data object IncompleteForm : EnrollError
    data object AccountMissing : EnrollError
    data object AlreadyBound : EnrollError
    data object NoPass : EnrollError
    data object NoMultipass : EnrollError
    data object Refused : EnrollError
    data object Unreachable : EnrollError

    /** Le mot de la station elle-même, tel quel. */
    data class FromStation(val message: String) : EnrollError

    companion object {
        /** La ressource qui porte la phrase, ou null pour [FromStation]. */
        @StringRes
        fun messageRes(error: EnrollError): Int? = when (error) {
            NoAccount -> R.string.enroll_err_no_account
            IncompleteForm -> R.string.enroll_err_incomplete
            AccountMissing -> R.string.enroll_err_account_missing
            AlreadyBound -> R.string.enroll_err_already_bound
            NoPass -> R.string.enroll_err_no_pass
            NoMultipass -> R.string.enroll_err_no_multipass
            Refused -> R.string.enroll_err_station_refused
            Unreachable -> R.string.enroll_err_station_unreachable
            is FromStation -> null
        }
    }
}
