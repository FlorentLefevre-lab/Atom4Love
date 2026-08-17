/*
 * Copyright (C) 2016 Southern Storm Software, Pty Ltd.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.southernstorm.noise.protocol

import com.southernstorm.noise.AEADBadTagException
import com.southernstorm.noise.JvmSecureRandom
import com.southernstorm.noise.NoSuchAlgorithmException
import com.southernstorm.noise.SecureRandomSource
import com.southernstorm.noise.crypto.Blake2bMessageDigest
import com.southernstorm.noise.crypto.Blake2sMessageDigest
import com.southernstorm.noise.crypto.MessageDigest
import com.southernstorm.noise.crypto.SHA256MessageDigest
import com.southernstorm.noise.crypto.SHA512MessageDigest

/**
 * Utility functions for the Noise protocol library.
 *
 * ## Ce que le portage change ici, et pourquoi
 *
 * L'original interrogeait d'abord le JCA (`MessageDigest.getInstance("SHA-256")`,
 * `Cipher.getInstance("AES/CTR/NoPadding")`) et ne retombait sur les
 * implémentations de la bibliothèque qu'en cas d'échec. Il n'y a pas de JCA en
 * Kotlin/Native : [createHash] rend donc **toujours** l'implémentation maison.
 *
 * C'est exactement ce que faisait déjà `setForceFallbacks(true)`, et la sortie
 * est identique au bit près — SHA-256 est SHA-256. Seule la vitesse change :
 * l'implémentation du système est écrite pour la machine, la nôtre pour la
 * portabilité. La cabine hache des messages de quelques kilo-octets, la
 * différence ne se voit pas.
 *
 * [createCipher] garde en revanche la préférence de l'original pour le chemin
 * accéléré d'AES-GCM, parce que ce chemin existe sur Android — voir
 * [AESGCMOnCtrCipherState], seule classe du paquet qui ne montera jamais dans
 * `commonMain`. Rien chez nous n'appelle AESGCM.
 */
object Noise {

    /**
     * Maximum length for Noise packets.
     */
    const val MAX_PACKET_LEN = 65535

    /**
     * La source d'aléa. Remplaçable — c'est la couture de plateforme décrite
     * dans `com.southernstorm.noise.Platform`.
     *
     * ⚠ N'y mettre qu'un générateur cryptographiquement sûr : les clés
     * éphémères de chaque handshake en sortent.
     */
    @JvmStatic
    var randomSource: SecureRandomSource = JvmSecureRandom()

    /**
     * Generates random data using the system random number generator.
     *
     * @param data The data buffer to fill with random data.
     */
    @JvmStatic
    fun random(data: ByteArray) {
        randomSource.nextBytes(data)
    }

    private var forceFallbacks = false

    /**
     * Force the use of plain Java fallback crypto implementations.
     *
     * @param force Set to true for force fallbacks, false to
     * try to use the system implementation before falling back.
     *
     * This function is intended for testing purposes to toggle between
     * the system JCA/JCE implementations and the plain Java fallback
     * reference implementations.
     *
     * ⚠ N'a plus d'effet que sur AESGCM : les hachages sont désormais
     * toujours ceux de la bibliothèque.
     */
    @JvmStatic
    fun setForceFallbacks(force: Boolean) {
        forceFallbacks = force
    }

    /**
     * Creates a Diffie-Hellman object from its Noise protocol name.
     *
     * @param name The name of the DH algorithm; e.g. "25519", "448", etc.
     *
     * @return The Diffie-Hellman object if the name is recognized.
     *
     * @throws NoSuchAlgorithmException The name is not recognized as a
     * valid Noise protocol name, or there is no cryptography provider
     * in the system that implements the algorithm.
     */
    @JvmStatic
    fun createDH(name: String): DHState {
        if (name == "25519") return Curve25519DHState()
        if (name == "448") return Curve448DHState()
        if (name == "NewHope") return NewHopeDHState()
        throw NoSuchAlgorithmException("Unknown Noise DH algorithm name: $name")
    }

    /**
     * Creates a cipher object from its Noise protocol name.
     *
     * @param name The name of the cipher algorithm; e.g. "AESGCM", "ChaChaPoly", etc.
     *
     * @return The cipher object if the name is recognized.
     *
     * @throws NoSuchAlgorithmException The name is not recognized as a
     * valid Noise protocol name, or there is no cryptography provider
     * in the system that implements the algorithm.
     */
    @JvmStatic
    fun createCipher(name: String): CipherState {
        if (name == "AESGCM") {
            if (forceFallbacks) return AESGCMFallbackCipherState()
            // "AES/GCM/NoPadding" exists in some recent JDK's but it is flaky
            // to use and not easily back-portable to older Android versions.
            // We instead emulate AESGCM on top of "AES/CTR/NoPadding".
            return try {
                AESGCMOnCtrCipherState()
            } catch (e1: NoSuchAlgorithmException) {
                // Could not find anything useful in the JCA/JCE so
                // use the pure Java fallback implementation instead.
                AESGCMFallbackCipherState()
            }
        } else if (name == "ChaChaPoly") {
            return ChaChaPolyCipherState()
        }
        throw NoSuchAlgorithmException("Unknown Noise cipher algorithm name: $name")
    }

    /**
     * Creates a hash object from its Noise protocol name.
     *
     * @param name The name of the hash algorithm; e.g. "SHA256", "BLAKE2s", etc.
     *
     * @return The hash object if the name is recognized.
     *
     * @throws NoSuchAlgorithmException The name is not recognized as a
     * valid Noise protocol name.
     */
    @JvmStatic
    fun createHash(name: String): MessageDigest {
        // ⚠ Plus d'interrogation du JCA : la bibliothèque répond toujours
        // elle-même. Voir la note de classe — même sortie, autre vitesse.
        return when (name) {
            "SHA256" -> SHA256MessageDigest()
            "SHA512" -> SHA512MessageDigest()
            "BLAKE2b" -> Blake2bMessageDigest()
            "BLAKE2s" -> Blake2sMessageDigest()
            else -> throw NoSuchAlgorithmException("Unknown Noise hash algorithm name: $name")
        }
    }

    // The rest of this class consists of internal utility functions
    // that are not part of the public API.

    /**
     * Destroys the contents of a byte array.
     *
     * @param array The array whose contents should be destroyed.
     */
    @JvmStatic
    internal fun destroy(array: ByteArray) {
        array.fill(0)
    }

    /**
     * Makes a copy of part of an array.
     *
     * @param data The buffer containing the data to copy.
     * @param offset Offset of the first byte to copy.
     * @param length The number of bytes to copy.
     *
     * @return A new array with a copy of the sub-array.
     */
    @JvmStatic
    internal fun copySubArray(data: ByteArray, offset: Int, length: Int): ByteArray =
        data.copyOfRange(offset, offset + length)

    /**
     * Throws an instance of AEADBadTagException.
     *
     * L'original allait chercher `javax.crypto.AEADBadTagException` par
     * réflexion, en retombant sur `BadPaddingException` quand le JDK ne la
     * connaissait pas. La réflexion n'est pas portable : le type est ici un
     * sous-type de `BadPaddingException`, ce qui rend la même hiérarchie sans
     * chargement dynamique.
     */
    @JvmStatic
    internal fun throwBadTagException(): Nothing {
        throw AEADBadTagException()
    }
}
