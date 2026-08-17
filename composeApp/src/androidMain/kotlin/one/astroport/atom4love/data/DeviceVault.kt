package one.astroport.atom4love.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Le coffre de l'appareil : AES-256-GCM sous une clé de l'Android Keystore.
 *
 * La clé ne quitte jamais le magasin — elle n'est pas exportable, et sur les
 * appareils qui en ont un, elle vit dans l'élément sécurisé matériel. Ce qui
 * est scellé ici ne se relit donc que sur cet appareil-là : ni la sauvegarde
 * cloud, ni un `adb backup`, ni la copie du DataStore ne rendent le contenu
 * lisible ailleurs.
 *
 * Remplace `EncryptedSharedPreferences` (androidx.security-crypto), déprécié
 * depuis sa 1.1.0. Même garantie, deux appels de la plateforme, et le compte
 * reste rangé dans le DataStore comme le reste des données de la station.
 *
 * GCM authentifie autant qu'il chiffre : un octet modifié fait échouer
 * l'ouverture au lieu de rendre du n'importe quoi. Le vecteur d'initialisation
 * est tiré au sort par la plateforme à chaque scellement et voyage en tête du
 * message — jamais réutilisé, ce qui serait la faute fatale en GCM.
 */
object DeviceVault {

    private const val TAG = "Vault"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "atom4love.vault.v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    private const val IV_BYTES = 12

    /** Scelle [plain] ; rend `base64(iv ‖ chiffré)`. */
    fun seal(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val packed = cipher.iv + body
        return Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    /**
     * Ouvre ce que [seal] a scellé.
     *
     * null si le contenu a été altéré, ou si la clé n'est plus celle qui l'a
     * scellé — ce qui arrive pour de bonnes raisons : restauration sur un autre
     * appareil, réinitialisation du verrou d'écran sur certains modèles. Le
     * compte est alors simplement inconnu de cette installation, et se récupère
     * auprès de la station avec le code PASS.
     */
    fun open(sealed: String): String? = runCatching {
        val packed = Base64.decode(sealed, Base64.NO_WRAP)
        require(packed.size > IV_BYTES) { "message tronqué" }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(
                Cipher.DECRYPT_MODE,
                key(),
                GCMParameterSpec(TAG_BITS, packed, 0, IV_BYTES),
            )
        }
        String(
            cipher.doFinal(packed, IV_BYTES, packed.size - IV_BYTES),
            Charsets.UTF_8,
        )
    }.onFailure { Log.w(TAG, "contenu illisible — clé changée ou message altéré", it) }.getOrNull()

    /** La clé du magasin, créée au premier usage puis retrouvée par son alias. */
    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_BITS)
                    // Volontairement sans authentification de l'utilisateur : la
                    // station lit son compte au démarrage, y compris quand elle
                    // se réveille seule pour tenir la balise. Exiger un
                    // déverrouillage ici couperait la proximité en tâche de fond.
                    .build(),
            )
        }.generateKey()
    }
}
