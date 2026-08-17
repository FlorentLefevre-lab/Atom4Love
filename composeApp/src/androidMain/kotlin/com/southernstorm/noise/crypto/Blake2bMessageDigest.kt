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

package com.southernstorm.noise.crypto

import com.southernstorm.noise.DigestException
import com.southernstorm.noise.protocol.Destroyable

/**
 * Fallback implementation of BLAKE2b for the Noise library.
 *
 * This implementation only supports message digesting with an output
 * length of 64 bytes and a limit of 2^64 - 1 bytes of input.
 * Keyed hashing and variable-length digests are not supported.
 */
class Blake2bMessageDigest : MessageDigest("BLAKE2B-512"), Destroyable {

    private val h = LongArray(8)
    private val block = ByteArray(128)
    private val m = LongArray(16)
    private val v = LongArray(16)
    private var length: Long = 0
    private var posn: Int = 0

    /**
     * Constructs a new BLAKE2b message digest object.
     */
    init {
        engineReset()
    }

    override fun engineDigest(): ByteArray {
        val digest = ByteArray(64)
        try {
            engineDigest(digest, 0, 64)
        } catch (e: DigestException) {
            // Shouldn't happen, but just in case.
            digest.fill(0)
        }
        return digest
    }

    override fun engineDigest(buf: ByteArray, offset: Int, len: Int): Int {
        var offset = offset
        if (len < 64) throw DigestException("Invalid digest length for BLAKE2b")
        block.fill(0, posn, 128)
        transform(-1L)
        for (index in 0 until 8) {
            val value = h[index]
            buf[offset++] = value.toByte()
            buf[offset++] = (value shr 8).toByte()
            buf[offset++] = (value shr 16).toByte()
            buf[offset++] = (value shr 24).toByte()
            buf[offset++] = (value shr 32).toByte()
            buf[offset++] = (value shr 40).toByte()
            buf[offset++] = (value shr 48).toByte()
            buf[offset++] = (value shr 56).toByte()
        }
        // ⚠ 32, pas 64 — et ce n'est PAS une coquille de transcription.
        // L'amont écrit `return 32` alors qu'il vient d'écrire 64 octets.
        // Le port doit rendre la même valeur que son oracle, sinon le test
        // différentiel devient inutilisable ici. Aucun appelant de la
        // bibliothèque ne lit ce retour, donc rien n'en dépend.
        return 32
    }

    override fun engineGetDigestLength(): Int = 64

    override fun engineReset() {
        h[0] = 0x6a09e667f3bcc908uL.toLong() xor 0x01010040L
        h[1] = 0xbb67ae8584caa73buL.toLong()
        h[2] = 0x3c6ef372fe94f82buL.toLong()
        h[3] = 0xa54ff53a5f1d36f1uL.toLong()
        h[4] = 0x510e527fade682d1uL.toLong()
        h[5] = 0x9b05688c2b3e6c1fuL.toLong()
        h[6] = 0x1f83d9abfb41bd6buL.toLong()
        h[7] = 0x5be0cd19137e2179uL.toLong()
        length = 0
        posn = 0
    }

    override fun engineUpdate(input: Byte) {
        if (posn >= 128) {
            transform(0L)
            posn = 0
        }
        block[posn++] = input
        ++length
    }

    override fun engineUpdate(input: ByteArray, offset: Int, len: Int) {
        var offset = offset
        var len = len
        while (len > 0) {
            if (posn >= 128) {
                transform(0L)
                posn = 0
            }
            var temp = 128 - posn
            if (temp > len) temp = len
            input.copyInto(block, posn, offset, offset + temp)
            posn += temp
            length += temp.toLong()
            offset += temp
            len -= temp
        }
    }

    private fun transform(f0: Long) {
        // Unpack the input block from little-endian into host-endian.
        var offset = 0
        for (index in 0 until 16) {
            m[index] = (block[offset].toLong() and 0xFFL) or
                ((block[offset + 1].toLong() and 0xFFL) shl 8) or
                ((block[offset + 2].toLong() and 0xFFL) shl 16) or
                ((block[offset + 3].toLong() and 0xFFL) shl 24) or
                ((block[offset + 4].toLong() and 0xFFL) shl 32) or
                ((block[offset + 5].toLong() and 0xFFL) shl 40) or
                ((block[offset + 6].toLong() and 0xFFL) shl 48) or
                ((block[offset + 7].toLong() and 0xFFL) shl 56)
            offset += 8
        }

        // Format the block to be hashed.
        for (index in 0 until 8) v[index] = h[index]
        v[8] = 0x6a09e667f3bcc908uL.toLong()
        v[9] = 0xbb67ae8584caa73buL.toLong()
        v[10] = 0x3c6ef372fe94f82buL.toLong()
        v[11] = 0xa54ff53a5f1d36f1uL.toLong()
        v[12] = 0x510e527fade682d1uL.toLong() xor length
        v[13] = 0x9b05688c2b3e6c1fuL.toLong()
        v[14] = 0x1f83d9abfb41bd6buL.toLong() xor f0
        v[15] = 0x5be0cd19137e2179uL.toLong()

        // Perform the 12 BLAKE2b rounds.
        for (index in 0 until 12) {
            // Column round.
            quarterRound(0, 4, 8, 12, 0, index)
            quarterRound(1, 5, 9, 13, 1, index)
            quarterRound(2, 6, 10, 14, 2, index)
            quarterRound(3, 7, 11, 15, 3, index)

            // Diagonal round.
            quarterRound(0, 5, 10, 15, 4, index)
            quarterRound(1, 6, 11, 12, 5, index)
            quarterRound(2, 7, 8, 13, 6, index)
            quarterRound(3, 4, 9, 14, 7, index)
        }

        // Combine the new and old hash values.
        for (index in 0 until 8) h[index] = h[index] xor (v[index] xor v[index + 8])
    }

    private fun quarterRound(a: Int, b: Int, c: Int, d: Int, i: Int, row: Int) {
        v[a] += v[b] + m[sigma[row][2 * i].toInt()]
        v[d] = rightRotate32(v[d] xor v[a])
        v[c] += v[d]
        v[b] = rightRotate24(v[b] xor v[c])
        v[a] += v[b] + m[sigma[row][2 * i + 1].toInt()]
        v[d] = rightRotate16(v[d] xor v[a])
        v[c] += v[d]
        v[b] = rightRotate63(v[b] xor v[c])
    }

    override fun destroy() {
        h.fill(0L)
        block.fill(0)
        m.fill(0L)
        v.fill(0L)
    }

    companion object {
        // Permutation on the message input state for BLAKE2b.
        @JvmStatic
        val sigma = arrayOf(
            byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15),
            byteArrayOf(14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3),
            byteArrayOf(11, 8, 12, 0, 5, 2, 15, 13, 10, 14, 3, 6, 7, 1, 9, 4),
            byteArrayOf(7, 9, 3, 1, 13, 12, 11, 14, 2, 6, 5, 10, 4, 0, 15, 8),
            byteArrayOf(9, 0, 5, 7, 2, 4, 10, 15, 14, 1, 11, 12, 6, 8, 3, 13),
            byteArrayOf(2, 12, 6, 10, 0, 11, 8, 3, 4, 13, 7, 5, 15, 14, 1, 9),
            byteArrayOf(12, 5, 1, 15, 14, 13, 4, 10, 0, 7, 6, 3, 9, 2, 8, 11),
            byteArrayOf(13, 11, 7, 14, 12, 1, 3, 9, 5, 0, 15, 4, 8, 6, 2, 10),
            byteArrayOf(6, 15, 14, 9, 11, 3, 0, 8, 12, 2, 13, 7, 1, 4, 10, 5),
            byteArrayOf(10, 2, 8, 4, 7, 6, 1, 5, 15, 11, 9, 14, 3, 12, 13, 0),
            byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15),
            byteArrayOf(14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3),
        )

        // ⚠ Comme dans BLAKE2s : les noms disent « right » et les corps
        // décalent à gauche. Une rotation à droite de n sur 64 bits est une
        // rotation à gauche de 64-n. Le nom trompe, le calcul est juste.
        private fun rightRotate32(v: Long): Long = (v shl 32) or (v ushr 32)

        private fun rightRotate24(v: Long): Long = (v shl 40) or (v ushr 24)

        private fun rightRotate16(v: Long): Long = (v shl 48) or (v ushr 16)

        private fun rightRotate63(v: Long): Long = (v shl 1) or (v ushr 63)
    }
}
