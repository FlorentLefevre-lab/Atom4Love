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

import com.southernstorm.noise.NoSuchAlgorithmException
import com.southernstorm.noise.crypto.GHASH
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import com.southernstorm.noise.ShortBufferException as NoiseShortBufferException

/**
 * Emulates the "AESGCM" cipher for Noise using the "AES/CTR/NoPadding"
 * transformation from JCA/JCE.
 *
 * This class is used on platforms that don't have "AES/GCM/NoPadding",
 * but which do have the older "AES/CTR/NoPadding".
 *
 * ## ⚠ La seule classe du paquet qui ne montera jamais dans `commonMain`
 *
 * Tout le portage a consisté à décrocher la bibliothèque de `java.security` et
 * `javax.crypto`. Celle-ci fait exception, et **c'est sa raison d'être** :
 * elle n'existe que pour déléguer AES à la plateforme, quand celle-ci sait le
 * faire plus vite que du Kotlin. La décrocher du JCA reviendrait à la
 * supprimer — c'est déjà ce qu'est [AESGCMFallbackCipherState].
 *
 * Le jour où le paquet devient multiplateforme, ce fichier reste dans
 * `androidMain`. Côté iOS il n'y a rien à écrire : `Noise.createCipher` retombe
 * seul sur l'implémentation pure. Rien chez nous n'appelle AES-GCM de toute
 * façon — la cabine parle ChaChaPoly.
 */
internal class AESGCMOnCtrCipherState : CipherState {

    private val cipher: Cipher
    private var keySpec: SecretKeySpec? = null
    private var n: Long = 0
    private val iv = ByteArray(16)
    private val hashKey = ByteArray(16)
    private val ghash = GHASH()

    /**
     * Constructs a new cipher state for the "AESGCM" algorithm.
     *
     * @throws NoSuchAlgorithmException The system does not have a
     * provider for this algorithm.
     */
    init {
        cipher = try {
            Cipher.getInstance("AES/CTR/NoPadding")
        } catch (e: java.security.NoSuchAlgorithmException) {
            throw NoSuchAlgorithmException("AES/CTR/NoPadding not available")
        } catch (e: javax.crypto.NoSuchPaddingException) {
            // AES/CTR is available, but not the unpadded version?  Huh?
            throw NoSuchAlgorithmException("AES/CTR/NoPadding not available")
        }

        // Try to set a 256-bit key on the cipher.  Some JCE's are
        // configured to disallow 256-bit AES if an extra policy
        // file has not been installed.
        try {
            val spec = SecretKeySpec(ByteArray(32), "AES")
            val params = IvParameterSpec(iv)
            cipher.init(Cipher.ENCRYPT_MODE, spec, params)
        } catch (e: InvalidKeyException) {
            throw NoSuchAlgorithmException("AES/CTR/NoPadding does not support 256-bit keys")
        } catch (e: InvalidAlgorithmParameterException) {
            throw NoSuchAlgorithmException("AES/CTR/NoPadding does not support 256-bit keys")
        }
    }

    override fun destroy() {
        // There doesn't seem to be a standard API to clean out a Cipher.
        // So we instead set the key and IV to all-zeroes to hopefully
        // destroy the sensitive data in the cipher instance.
        ghash.destroy()
        Noise.destroy(hashKey)
        Noise.destroy(iv)
        val spec = SecretKeySpec(ByteArray(32), "AES")
        keySpec = spec
        val params = IvParameterSpec(iv)
        try {
            cipher.init(Cipher.ENCRYPT_MODE, spec, params)
        } catch (e: InvalidKeyException) {
            // Shouldn't happen.
        } catch (e: InvalidAlgorithmParameterException) {
            // Shouldn't happen.
        }
    }

    override val cipherName: String
        get() = "AESGCM"

    override val keyLength: Int
        get() = 32

    override val macLength: Int
        get() = if (keySpec != null) 16 else 0

    override fun initializeKey(key: ByteArray, offset: Int) {
        // Set the encryption key.
        val spec = SecretKeySpec(key, offset, 32, "AES")
        keySpec = spec

        // Generate the hashing key by encrypting a block of zeroes.
        iv.fill(0)
        hashKey.fill(0)
        try {
            cipher.init(Cipher.ENCRYPT_MODE, spec, IvParameterSpec(iv))
        } catch (e: InvalidKeyException) {
            // Shouldn't happen.
            throw IllegalStateException(e)
        } catch (e: InvalidAlgorithmParameterException) {
            // Shouldn't happen.
            throw IllegalStateException(e)
        }
        try {
            val result = cipher.update(hashKey, 0, 16, hashKey, 0)
            cipher.doFinal(hashKey, result)
        } catch (e: GeneralJcaFailure) {
            // Shouldn't happen.
            throw IllegalStateException(e)
        }
        ghash.reset(hashKey, 0)

        // Reset the nonce.
        n = 0
    }

    override fun hasKey(): Boolean = keySpec != null

    /**
     * Set up to encrypt or decrypt the next packet.
     *
     * @param ad The associated data for the packet.
     */
    private fun setup(ad: ByteArray?) {
        // Check for nonce wrap-around.
        if (n == -1L) throw IllegalStateException("Nonce has wrapped around")

        // Format the counter/IV block for AES/CTR/NoPadding.
        iv[0] = 0
        iv[1] = 0
        iv[2] = 0
        iv[3] = 0
        iv[4] = (n shr 56).toByte()
        iv[5] = (n shr 48).toByte()
        iv[6] = (n shr 40).toByte()
        iv[7] = (n shr 32).toByte()
        iv[8] = (n shr 24).toByte()
        iv[9] = (n shr 16).toByte()
        iv[10] = (n shr 8).toByte()
        iv[11] = n.toByte()
        iv[12] = 0
        iv[13] = 0
        iv[14] = 0
        iv[15] = 1
        ++n

        // Initialize the CTR mode cipher with the key and IV.
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, IvParameterSpec(iv))

        // Encrypt a block of zeroes to generate the hash key to XOR
        // the GHASH tag with at the end of the encrypt/decrypt operation.
        hashKey.fill(0)
        try {
            cipher.update(hashKey, 0, 16, hashKey, 0)
        } catch (e: javax.crypto.ShortBufferException) {
            // Shouldn't happen.
            throw IllegalStateException(e)
        }

        // Initialize the GHASH with the associated data value.
        ghash.reset()
        if (ad != null) {
            ghash.update(ad, 0, ad.size)
            ghash.pad()
        }
    }

    override fun encryptWithAd(
        ad: ByteArray?,
        plaintext: ByteArray,
        plaintextOffset: Int,
        ciphertext: ByteArray,
        ciphertextOffset: Int,
        length: Int,
    ): Int {
        var space: Int
        if (ciphertextOffset < 0 || ciphertextOffset > ciphertext.size) {
            throw IllegalArgumentException()
        }
        if (length < 0 || plaintextOffset < 0 || plaintextOffset > plaintext.size ||
            length > plaintext.size || (plaintext.size - plaintextOffset) < length
        ) {
            throw IllegalArgumentException()
        }
        space = ciphertext.size - ciphertextOffset
        if (keySpec == null) {
            // The key is not set yet - return the plaintext as-is.
            if (length > space) throw NoiseShortBufferException()
            if (plaintext !== ciphertext || plaintextOffset != ciphertextOffset) {
                plaintext.copyInto(ciphertext, ciphertextOffset, plaintextOffset, plaintextOffset + length)
            }
            return length
        }
        if (space < 16 || length > (space - 16)) throw NoiseShortBufferException()
        try {
            setup(ad)
            val result = cipher.update(plaintext, plaintextOffset, length, ciphertext, ciphertextOffset)
            cipher.doFinal(ciphertext, ciphertextOffset + result)
        } catch (e: GeneralJcaFailure) {
            // Shouldn't happen.
            throw IllegalStateException(e)
        }
        ghash.update(ciphertext, ciphertextOffset, length)
        ghash.pad((if (ad != null) ad.size else 0).toLong(), length.toLong())
        ghash.finish(ciphertext, ciphertextOffset + length, 16)
        for (index in 0 until 16) {
            ciphertext[ciphertextOffset + length + index] =
                (ciphertext[ciphertextOffset + length + index].toInt() xor hashKey[index].toInt()).toByte()
        }
        return length + 16
    }

    override fun decryptWithAd(
        ad: ByteArray?,
        ciphertext: ByteArray,
        ciphertextOffset: Int,
        plaintext: ByteArray,
        plaintextOffset: Int,
        length: Int,
    ): Int {
        var space: Int
        if (ciphertextOffset < 0 || ciphertextOffset > ciphertext.size) {
            throw IllegalArgumentException()
        } else {
            space = ciphertext.size - ciphertextOffset
        }
        if (length > space) throw NoiseShortBufferException()
        if (length < 0 || plaintextOffset < 0 || plaintextOffset > plaintext.size ||
            length > ciphertext.size || (ciphertext.size - ciphertextOffset) < length
        ) {
            throw IllegalArgumentException()
        }
        space = plaintext.size - plaintextOffset
        if (keySpec == null) {
            // The key is not set yet - return the ciphertext as-is.
            if (length > space) throw NoiseShortBufferException()
            if (plaintext !== ciphertext || plaintextOffset != ciphertextOffset) {
                ciphertext.copyInto(plaintext, plaintextOffset, ciphertextOffset, ciphertextOffset + length)
            }
            return length
        }
        if (length < 16) Noise.throwBadTagException()
        val dataLen = length - 16
        if (dataLen > space) throw NoiseShortBufferException()
        try {
            setup(ad)
        } catch (e: InvalidKeyException) {
            // Shouldn't happen.
            throw IllegalStateException(e)
        } catch (e: InvalidAlgorithmParameterException) {
            // Shouldn't happen.
            throw IllegalStateException(e)
        }
        ghash.update(ciphertext, ciphertextOffset, dataLen)
        ghash.pad((if (ad != null) ad.size else 0).toLong(), dataLen.toLong())
        ghash.finish(iv, 0, 16)
        var temp = 0
        for (index in 0 until 16) {
            temp = temp or (
                hashKey[index].toInt() xor iv[index].toInt() xor
                    ciphertext[ciphertextOffset + dataLen + index].toInt()
                )
        }
        if ((temp and 0xFF) != 0) Noise.throwBadTagException()
        try {
            val result = cipher.update(ciphertext, ciphertextOffset, dataLen, plaintext, plaintextOffset)
            cipher.doFinal(plaintext, plaintextOffset + result)
        } catch (e: GeneralJcaFailure) {
            // Shouldn't happen.
            throw IllegalStateException(e)
        }
        return dataLen
    }

    /**
     * ⚠ L'amont rend `null` quand le constructeur échoue (« ne devrait pas
     * arriver »), alors que l'interface promet un objet. Ici on lève : un
     * `null` rendu à cet endroit produirait de toute façon un plantage plus
     * loin, mais sans dire d'où il vient.
     */
    override fun fork(key: ByteArray, offset: Int): CipherState {
        val cipher: CipherState = AESGCMOnCtrCipherState()
        cipher.initializeKey(key, offset)
        return cipher
    }

    override fun setNonce(nonce: Long) {
        n = nonce
    }
}

/**
 * Les échecs du JCA que l'amont attrape un par un — `ShortBufferException`,
 * `IllegalBlockSizeException`, `BadPaddingException`, `InvalidKeyException`,
 * `InvalidAlgorithmParameterException` — et traite tous de la même façon :
 * « ne devrait pas arriver », donc `IllegalStateException`.
 *
 * Regroupés sous leur ancêtre commun pour ne pas répéter cinq `catch`
 * identiques. Aucun n'est rattrapable.
 */
private typealias GeneralJcaFailure = java.security.GeneralSecurityException
