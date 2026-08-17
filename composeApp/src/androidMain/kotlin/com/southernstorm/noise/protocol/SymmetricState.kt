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

import com.southernstorm.noise.DigestException
import com.southernstorm.noise.crypto.MessageDigest

/**
 * Symmetric state for helping manage a Noise handshake.
 *
 * ⚠ **Les champs sont nullables, et il faut qu'ils le restent.** [destroy] les
 * met à `null` : réutiliser un état détruit doit lever, pas travailler
 * silencieusement sur des tampons remis à zéro. Les `!!` ci-dessous
 * reproduisent le `NullPointerException` que Java levait — et le test
 * « une session détruite n'est plus utilisable » en dépend.
 */
internal class SymmetricState(
    /**
     * Gets the name of the Noise protocol.
     */
    val protocolName: String,
    cipherName: String,
    hashName: String,
) : Destroyable {

    private var cipher: CipherState? = Noise.createCipher(cipherName)
    private var hash: MessageDigest? = Noise.createHash(hashName)
    private var ck: ByteArray?
    private var h: ByteArray?
    private var prev_h: ByteArray?

    /**
     * Constructs a new symmetric state object.
     *
     * @param protocolName The name of the Noise protocol, which is assumed to be valid.
     * @param cipherName The name of the cipher within protocolName.
     * @param hashName The name of the hash within protocolName.
     *
     * @throws com.southernstorm.noise.NoSuchAlgorithmException The cipher or
     * hash algorithm in the protocol name is not supported.
     */
    init {
        val hashLength = hash!!.digestLength
        ck = ByteArray(hashLength)
        h = ByteArray(hashLength)
        prev_h = ByteArray(hashLength)

        // `encodeToByteArray()` est l'encodage UTF-8 du stdlib Kotlin, présent
        // sur toutes les cibles. L'original passait par `getBytes("UTF-8")` et
        // devait donc attraper `UnsupportedEncodingException` — une branche qui
        // n'existe plus, faute de pouvoir échouer.
        val protocolNameBytes = protocolName.encodeToByteArray()

        if (protocolNameBytes.size <= hashLength) {
            protocolNameBytes.copyInto(h!!, 0, 0, protocolNameBytes.size)
            h!!.fill(0, protocolNameBytes.size, h!!.size)
        } else {
            hashOne(protocolNameBytes, 0, protocolNameBytes.size, h!!, 0, h!!.size)
        }

        h!!.copyInto(ck!!, 0, 0, hashLength)
    }

    /**
     * Gets the length of MAC values in the current state.
     *
     * @return The length of the MAC value for the underlying cipher
     * or zero if the cipher has not yet been initialized with a key.
     */
    val macLength: Int
        get() = cipher!!.macLength

    /**
     * Mixes data into the chaining key.
     *
     * @param data The buffer containing the data to mix in.
     * @param offset The offset of the first data byte to mix in.
     * @param length The number of bytes to mix in.
     */
    fun mixKey(data: ByteArray, offset: Int, length: Int) {
        val keyLength = cipher!!.keyLength
        val tempKey = ByteArray(keyLength)
        try {
            hkdf(ck!!, 0, ck!!.size, data, offset, length, ck!!, 0, ck!!.size, tempKey, 0, keyLength)
            cipher!!.initializeKey(tempKey, 0)
        } finally {
            Noise.destroy(tempKey)
        }
    }

    /**
     * Mixes data into the handshake hash.
     *
     * @param data The buffer containing the data to mix in.
     * @param offset The offset of the first data byte to mix in.
     * @param length The number of bytes to mix in.
     */
    fun mixHash(data: ByteArray, offset: Int, length: Int) {
        hashTwo(h!!, 0, h!!.size, data, offset, length, h!!, 0, h!!.size)
    }

    /**
     * Mixes a pre-shared key into the chaining key and handshake hash.
     *
     * @param key The pre-shared key value.
     */
    fun mixPreSharedKey(key: ByteArray) {
        val temp = ByteArray(hash!!.digestLength)
        try {
            hkdf(ck!!, 0, ck!!.size, key, 0, key.size, ck!!, 0, ck!!.size, temp, 0, temp.size)
            mixHash(temp, 0, temp.size)
        } finally {
            Noise.destroy(temp)
        }
    }

    /**
     * Mixes a pre-supplied public key into the handshake hash.
     *
     * @param dh The object containing the public key.
     */
    fun mixPublicKey(dh: DHState) {
        val temp = ByteArray(dh.publicKeyLength)
        try {
            dh.getPublicKey(temp, 0)
            mixHash(temp, 0, temp.size)
        } finally {
            Noise.destroy(temp)
        }
    }

    /**
     * Mixes a pre-supplied public key into the chaining key.
     *
     * @param dh The object containing the public key.
     */
    fun mixPublicKeyIntoCK(dh: DHState) {
        val temp = ByteArray(dh.publicKeyLength)
        try {
            dh.getPublicKey(temp, 0)
            mixKey(temp, 0, temp.size)
        } finally {
            Noise.destroy(temp)
        }
    }

    /**
     * Encrypts a block of plaintext and mixes the ciphertext into the handshake hash.
     *
     * @param plaintext The buffer containing the plaintext to encrypt.
     * @param plaintextOffset The offset within the plaintext buffer of the
     * first byte or plaintext data.
     * @param ciphertext The buffer to place the ciphertext in.  This can
     * be the same as the plaintext buffer.
     * @param ciphertextOffset The first offset within the ciphertext buffer
     * to place the ciphertext and the MAC tag.
     * @param length The length of the plaintext.
     * @return The length of the ciphertext plus the MAC tag.
     *
     * @throws com.southernstorm.noise.ShortBufferException There is not enough
     * space in the ciphertext buffer for the encrypted data plus MAC value.
     */
    fun encryptAndHash(
        plaintext: ByteArray,
        plaintextOffset: Int,
        ciphertext: ByteArray,
        ciphertextOffset: Int,
        length: Int,
    ): Int {
        val ciphertextLength =
            cipher!!.encryptWithAd(h, plaintext, plaintextOffset, ciphertext, ciphertextOffset, length)
        mixHash(ciphertext, ciphertextOffset, ciphertextLength)
        return ciphertextLength
    }

    /**
     * Decrypts a block of ciphertext and mixes it into the handshake hash.
     *
     * @param ciphertext The buffer containing the ciphertext to decrypt.
     * @param ciphertextOffset The offset within the ciphertext buffer of
     * the first byte of ciphertext data.
     * @param plaintext The buffer to place the plaintext in.  This can be
     * the same as the ciphertext buffer.
     * @param plaintextOffset The first offset within the plaintext buffer
     * to place the plaintext.
     * @param length The length of the incoming ciphertext plus the MAC tag.
     * @return The length of the plaintext with the MAC tag stripped off.
     *
     * @throws com.southernstorm.noise.ShortBufferException There is not enough
     * space in the plaintext buffer for the decrypted data.
     *
     * @throws com.southernstorm.noise.BadPaddingException The MAC value failed
     * to verify.
     */
    fun decryptAndHash(
        ciphertext: ByteArray,
        ciphertextOffset: Int,
        plaintext: ByteArray,
        plaintextOffset: Int,
        length: Int,
    ): Int {
        h!!.copyInto(prev_h!!, 0, 0, h!!.size)
        mixHash(ciphertext, ciphertextOffset, length)
        return cipher!!.decryptWithAd(prev_h, ciphertext, ciphertextOffset, plaintext, plaintextOffset, length)
    }

    /**
     * Splits the symmetric state into two ciphers for session encryption.
     *
     * @return The pair of ciphers for sending and receiving.
     */
    fun split(): CipherStatePair = split(ByteArray(0), 0, 0)

    /**
     * Splits the symmetric state into two ciphers for session encryption,
     * and optionally mixes in a secondary symmetric key.
     *
     * @param secondaryKey The buffer containing the secondary key.
     * @param offset The offset of the first secondary key byte.
     * @param length The length of the secondary key in bytes, which
     * must be either 0 or 32.
     * @return The pair of ciphers for sending and receiving.
     *
     * @throws IllegalArgumentException The length is not 0 or 32.
     */
    fun split(secondaryKey: ByteArray, offset: Int, length: Int): CipherStatePair {
        if (length != 0 && length != 32) {
            throw IllegalArgumentException("Secondary keys must be 0 or 32 bytes in length")
        }
        val keyLength = cipher!!.keyLength
        val k1 = ByteArray(keyLength)
        val k2 = ByteArray(keyLength)
        try {
            hkdf(ck!!, 0, ck!!.size, secondaryKey, offset, length, k1, 0, k1.size, k2, 0, k2.size)
            var c1: CipherState? = null
            var c2: CipherState? = null
            var pair: CipherStatePair? = null
            try {
                c1 = cipher!!.fork(k1, 0)
                c2 = cipher!!.fork(k2, 0)
                pair = CipherStatePair(c1, c2)
            } finally {
                if (c1 == null || c2 == null || pair == null) {
                    // Could not create some of the objects.  Clean up the others
                    // to avoid accidental leakage of k1 or k2.
                    c1?.destroy()
                    c2?.destroy()
                    pair = null
                }
            }
            return pair!!
        } finally {
            Noise.destroy(k1)
            Noise.destroy(k2)
        }
    }

    /**
     * Gets the current value of the handshake hash.
     *
     * @return The handshake hash.  This must not be modified by the caller.
     *
     * The handshake hash value is only of use to the application after
     * split() has been called.
     */
    val handshakeHash: ByteArray
        get() = h!!

    override fun destroy() {
        cipher?.let {
            it.destroy()
            cipher = null
        }
        hash?.let {
            // The built-in fallback implementations are destroyable.
            // JCA/JCE implementations aren't, so try reset() instead.
            if (it is Destroyable) it.destroy() else it.reset()
            hash = null
        }
        ck?.let {
            Noise.destroy(it)
            ck = null
        }
        h?.let {
            Noise.destroy(it)
            h = null
        }
        prev_h?.let {
            Noise.destroy(it)
            prev_h = null
        }
    }

    /**
     * Hashes a single data buffer.
     *
     * The output buffer can be the same as the input data buffer.
     */
    private fun hashOne(
        data: ByteArray,
        offset: Int,
        length: Int,
        output: ByteArray,
        outputOffset: Int,
        outputLength: Int,
    ) {
        hash!!.reset()
        hash!!.update(data, offset, length)
        try {
            hash!!.digest(output, outputOffset, outputLength)
        } catch (e: DigestException) {
            // ⚠ `fill` prend un intervalle [de, à[, pas une longueur — comme
            // `Arrays.fill` de l'amont, qui écrit ici `outputOffset` puis
            // `outputLength`. C'est douteux dès que l'offset n'est pas nul,
            // mais c'est du code de rattrapage qui ne se déclenche jamais :
            // reproduit tel quel plutôt que corrigé en silence.
            output.fill(0, outputOffset, outputLength)
        }
    }

    /**
     * Hashes two data buffers.
     *
     * The output buffer can be same as either of the input buffers.
     */
    private fun hashTwo(
        data1: ByteArray,
        offset1: Int,
        length1: Int,
        data2: ByteArray,
        offset2: Int,
        length2: Int,
        output: ByteArray,
        outputOffset: Int,
        outputLength: Int,
    ) {
        hash!!.reset()
        hash!!.update(data1, offset1, length1)
        hash!!.update(data2, offset2, length2)
        try {
            hash!!.digest(output, outputOffset, outputLength)
        } catch (e: DigestException) {
            output.fill(0, outputOffset, outputLength)
        }
    }

    /**
     * Computes a HMAC value using key and data values.
     */
    private fun hmac(
        key: ByteArray,
        keyOffset: Int,
        keyLength: Int,
        data: ByteArray,
        dataOffset: Int,
        dataLength: Int,
        output: ByteArray,
        outputOffset: Int,
        outputLength: Int,
    ) {
        // In all of the algorithms of interest to us, the block length
        // is twice the size of the hash length.
        val hashLength = hash!!.digestLength
        val blockLength = hashLength * 2
        val block = ByteArray(blockLength)
        try {
            if (keyLength <= blockLength) {
                key.copyInto(block, 0, keyOffset, keyOffset + keyLength)
                block.fill(0, keyLength, blockLength)
            } else {
                hash!!.reset()
                hash!!.update(key, keyOffset, keyLength)
                hash!!.digest(block, 0, hashLength)
                block.fill(0, hashLength, blockLength)
            }
            for (index in 0 until blockLength) {
                block[index] = (block[index].toInt() xor 0x36).toByte()
            }
            hash!!.reset()
            hash!!.update(block, 0, blockLength)
            hash!!.update(data, dataOffset, dataLength)
            hash!!.digest(output, outputOffset, hashLength)
            for (index in 0 until blockLength) {
                block[index] = (block[index].toInt() xor (0x36 xor 0x5C)).toByte()
            }
            hash!!.reset()
            hash!!.update(block, 0, blockLength)
            hash!!.update(output, outputOffset, hashLength)
            hash!!.digest(output, outputOffset, outputLength)
        } catch (e: DigestException) {
            output.fill(0, outputOffset, outputLength)
        } finally {
            Noise.destroy(block)
        }
    }

    /**
     * Computes a HKDF value.
     */
    private fun hkdf(
        key: ByteArray,
        keyOffset: Int,
        keyLength: Int,
        data: ByteArray,
        dataOffset: Int,
        dataLength: Int,
        output1: ByteArray,
        output1Offset: Int,
        output1Length: Int,
        output2: ByteArray,
        output2Offset: Int,
        output2Length: Int,
    ) {
        val hashLength = hash!!.digestLength
        val tempKey = ByteArray(hashLength)
        val tempHash = ByteArray(hashLength + 1)
        try {
            hmac(key, keyOffset, keyLength, data, dataOffset, dataLength, tempKey, 0, hashLength)
            tempHash[0] = 0x01
            hmac(tempKey, 0, hashLength, tempHash, 0, 1, tempHash, 0, hashLength)
            tempHash.copyInto(output1, output1Offset, 0, output1Length)
            tempHash[hashLength] = 0x02
            hmac(tempKey, 0, hashLength, tempHash, 0, hashLength + 1, tempHash, 0, hashLength)
            tempHash.copyInto(output2, output2Offset, 0, output2Length)
        } finally {
            Noise.destroy(tempKey)
            Noise.destroy(tempHash)
        }
    }
}
