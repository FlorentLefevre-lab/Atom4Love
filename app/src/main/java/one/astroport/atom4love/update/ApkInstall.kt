package one.astroport.atom4love.update

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File

/**
 * Le passage de main : de l'APK vérifié à l'installeur du système.
 *
 * Android ne laisse pas une application en installer une autre. Il pose deux
 * verrous, et les deux se tiennent devant la personne :
 *
 *  1. **l'autorisation d'installer** (`REQUEST_INSTALL_PACKAGES`), qui ne se
 *     donne pas à l'installation de l'app mais dans les réglages du système,
 *     application par application ;
 *  2. **l'écran d'installation** lui-même, hors de nous, avec la liste des
 *     permissions demandées et un bouton qu'il faut appuyer.
 *
 * On ne contourne ni l'un ni l'autre. Le dernier mot n'est pas à l'app.
 *
 * ⚠ L'URI passe par [FileProvider] : depuis Nougat, un `file://` tendu à une
 * autre application fait crasher l'émetteur (`FileUriExposedException`). C'est
 * le même fournisseur que les pièces jointes de la causerie, avec un chemin en
 * plus (`res/xml/file_paths.xml`).
 */
object ApkInstall {

    /**
     * L'autorisation d'installer est-elle donnée à Atom4Love ?
     *
     * Elle se retire à tout moment depuis les réglages, et elle vaut pour
     * cette application seulement — l'accorder ici n'ouvre rien ailleurs.
     */
    fun allowed(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /**
     * L'écran des réglages où cette autorisation se donne, ouvert directement
     * sur notre ligne — chercher Atom4Love dans une liste de cent applications
     * décourage plus sûrement qu'un refus.
     */
    fun permissionIntent(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        "package:${context.packageName}".toUri(),
    )

    /** L'écran d'installation du système, sur l'APK vérifié. */
    fun installIntent(context: Context, apk: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
