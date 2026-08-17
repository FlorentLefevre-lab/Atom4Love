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
 * Fallback implementation of SHA512.
 *
 * Note: This implementation is limited to a maximum 2^56 - 1 bytes of input.
 * That is, we don't bother trying to implement 128-bit length values.
 */
class SHA512MessageDigest : MessageDigest("SHA-512"), Destroyable {

    private val h = LongArray(8)
    private val block = ByteArray(128)
    private val w = LongArray(80)
    private var length: Long = 0
    private var posn: Int = 0

    /**
     * Constructs a new SHA512 message digest object.
     */
    init {
        engineReset()
    }

    override fun destroy() {
        h.fill(0L)
        block.fill(0)
        w.fill(0L)
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
        if (len < 64) throw DigestException("Invalid digest length for SHA512")
        if (posn <= 128 - 17) {
            block[posn] = 0x80.toByte()
            block.fill(0, posn + 1, 128 - 8)
        } else {
            block[posn] = 0x80.toByte()
            block.fill(0, posn + 1, 128)
            transform(block, 0)
            block.fill(0, 0, 128 - 8)
        }
        writeBE64(block, 128 - 8, length)
        transform(block, 0)
        posn = 0
        for (index in 0 until 8) writeBE64(buf, offset + index * 8, h[index])
        return 64
    }

    override fun engineGetDigestLength(): Int = 64

    override fun engineReset() {
        h[0] = 0x6a09e667f3bcc908uL.toLong()
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
        block[posn++] = input
        length += 8
        if (posn >= 128) {
            transform(block, 0)
            posn = 0
        }
    }

    override fun engineUpdate(input: ByteArray, offset: Int, len: Int) {
        var offset = offset
        var len = len
        while (len > 0) {
            if (posn == 0 && len >= 128) {
                transform(input, offset)
                offset += 128
                len -= 128
                length += (128 * 8).toLong()
            } else {
                var temp = 128 - posn
                if (temp > len) temp = len
                input.copyInto(block, posn, offset, offset + temp)
                posn += temp
                length += (temp * 8).toLong()
                if (posn >= 128) {
                    transform(block, 0)
                    posn = 0
                }
                offset += temp
                len -= temp
            }
        }
    }

    private fun transform(m: ByteArray, offset: Int) {
        var offset = offset
        var a: Long
        var b: Long
        var c: Long
        var d: Long
        var e: Long
        var f: Long
        var g: Long
        var h: Long
        var temp1: Long
        var temp2: Long

        // Initialize working variables to the current hash value.
        a = this.h[0]
        b = this.h[1]
        c = this.h[2]
        d = this.h[3]
        e = this.h[4]
        f = this.h[5]
        g = this.h[6]
        h = this.h[7]

        // Convert the 16 input message words from big endian to host byte order.
        for (index in 0 until 16) {
            w[index] = ((m[offset].toLong() and 0xFFL) shl 56) or
                ((m[offset + 1].toLong() and 0xFFL) shl 48) or
                ((m[offset + 2].toLong() and 0xFFL) shl 40) or
                ((m[offset + 3].toLong() and 0xFFL) shl 32) or
                ((m[offset + 4].toLong() and 0xFFL) shl 24) or
                ((m[offset + 5].toLong() and 0xFFL) shl 16) or
                ((m[offset + 6].toLong() and 0xFFL) shl 8) or
                (m[offset + 7].toLong() and 0xFFL)
            offset += 8
        }

        // Extend the first 16 words to 80.
        for (index in 16 until 80) {
            w[index] = w[index - 16] + w[index - 7] +
                (rightRotate(w[index - 15], 1) xor
                    rightRotate(w[index - 15], 8) xor
                    (w[index - 15] ushr 7)) +
                (rightRotate(w[index - 2], 19) xor
                    rightRotate(w[index - 2], 61) xor
                    (w[index - 2] ushr 6))
        }

        // Compression function main loop.
        for (index in 0 until 80) {
            temp1 = h + k[index] + w[index] +
                (rightRotate(e, 14) xor rightRotate(e, 18) xor rightRotate(e, 41)) +
                ((e and f) xor (e.inv() and g))
            temp2 = (rightRotate(a, 28) xor rightRotate(a, 34) xor rightRotate(a, 39)) +
                ((a and b) xor (a and c) xor (b and c))
            h = g
            g = f
            f = e
            e = d + temp1
            d = c
            c = b
            b = a
            a = temp1 + temp2
        }

        // Add the compressed chunk to the current hash value.
        this.h[0] += a
        this.h[1] += b
        this.h[2] += c
        this.h[3] += d
        this.h[4] += e
        this.h[5] += f
        this.h[6] += g
        this.h[7] += h
    }

    companion object {
        private fun writeBE64(buf: ByteArray, offset: Int, value: Long) {
            buf[offset] = (value shr 56).toByte()
            buf[offset + 1] = (value shr 48).toByte()
            buf[offset + 2] = (value shr 40).toByte()
            buf[offset + 3] = (value shr 32).toByte()
            buf[offset + 4] = (value shr 24).toByte()
            buf[offset + 5] = (value shr 16).toByte()
            buf[offset + 6] = (value shr 8).toByte()
            buf[offset + 7] = value.toByte()
        }

        // Les 80 constantes de SHA-512. Toutes écrites en littéral NON SIGNÉ
        // suivi de `.toLong()`, sans exception : Java accepte `0xCA27…L` avec
        // le bit de signe posé, Kotlin le refuse. Traiter uniformément évite
        // d'avoir à juger, ligne par ligne, laquelle déborde.
        private val k = longArrayOf(
            0x428A2F98D728AE22uL.toLong(), 0x7137449123EF65CDuL.toLong(), 0xB5C0FBCFEC4D3B2FuL.toLong(),
            0xE9B5DBA58189DBBCuL.toLong(), 0x3956C25BF348B538uL.toLong(), 0x59F111F1B605D019uL.toLong(),
            0x923F82A4AF194F9BuL.toLong(), 0xAB1C5ED5DA6D8118uL.toLong(), 0xD807AA98A3030242uL.toLong(),
            0x12835B0145706FBEuL.toLong(), 0x243185BE4EE4B28CuL.toLong(), 0x550C7DC3D5FFB4E2uL.toLong(),
            0x72BE5D74F27B896FuL.toLong(), 0x80DEB1FE3B1696B1uL.toLong(), 0x9BDC06A725C71235uL.toLong(),
            0xC19BF174CF692694uL.toLong(), 0xE49B69C19EF14AD2uL.toLong(), 0xEFBE4786384F25E3uL.toLong(),
            0x0FC19DC68B8CD5B5uL.toLong(), 0x240CA1CC77AC9C65uL.toLong(), 0x2DE92C6F592B0275uL.toLong(),
            0x4A7484AA6EA6E483uL.toLong(), 0x5CB0A9DCBD41FBD4uL.toLong(), 0x76F988DA831153B5uL.toLong(),
            0x983E5152EE66DFABuL.toLong(), 0xA831C66D2DB43210uL.toLong(), 0xB00327C898FB213FuL.toLong(),
            0xBF597FC7BEEF0EE4uL.toLong(), 0xC6E00BF33DA88FC2uL.toLong(), 0xD5A79147930AA725uL.toLong(),
            0x06CA6351E003826FuL.toLong(), 0x142929670A0E6E70uL.toLong(), 0x27B70A8546D22FFCuL.toLong(),
            0x2E1B21385C26C926uL.toLong(), 0x4D2C6DFC5AC42AEDuL.toLong(), 0x53380D139D95B3DFuL.toLong(),
            0x650A73548BAF63DEuL.toLong(), 0x766A0ABB3C77B2A8uL.toLong(), 0x81C2C92E47EDAEE6uL.toLong(),
            0x92722C851482353BuL.toLong(), 0xA2BFE8A14CF10364uL.toLong(), 0xA81A664BBC423001uL.toLong(),
            0xC24B8B70D0F89791uL.toLong(), 0xC76C51A30654BE30uL.toLong(), 0xD192E819D6EF5218uL.toLong(),
            0xD69906245565A910uL.toLong(), 0xF40E35855771202AuL.toLong(), 0x106AA07032BBD1B8uL.toLong(),
            0x19A4C116B8D2D0C8uL.toLong(), 0x1E376C085141AB53uL.toLong(), 0x2748774CDF8EEB99uL.toLong(),
            0x34B0BCB5E19B48A8uL.toLong(), 0x391C0CB3C5C95A63uL.toLong(), 0x4ED8AA4AE3418ACBuL.toLong(),
            0x5B9CCA4F7763E373uL.toLong(), 0x682E6FF3D6B2B8A3uL.toLong(), 0x748F82EE5DEFB2FCuL.toLong(),
            0x78A5636F43172F60uL.toLong(), 0x84C87814A1F0AB72uL.toLong(), 0x8CC702081A6439ECuL.toLong(),
            0x90BEFFFA23631E28uL.toLong(), 0xA4506CEBDE82BDE9uL.toLong(), 0xBEF9A3F7B2C67915uL.toLong(),
            0xC67178F2E372532BuL.toLong(), 0xCA273ECEEA26619CuL.toLong(), 0xD186B8C721C0C207uL.toLong(),
            0xEADA7DD6CDE0EB1EuL.toLong(), 0xF57D4F7FEE6ED178uL.toLong(), 0x06F067AA72176FBAuL.toLong(),
            0x0A637DC5A2C898A6uL.toLong(), 0x113F9804BEF90DAEuL.toLong(), 0x1B710B35131C471BuL.toLong(),
            0x28DB77F523047D84uL.toLong(), 0x32CAAB7B40C72493uL.toLong(), 0x3C9EBE0A15C9BEBCuL.toLong(),
            0x431D67C49C100D4CuL.toLong(), 0x4CC5D4BECB3E42B6uL.toLong(), 0x597F299CFC657E2AuL.toLong(),
            0x5FCB6FAB3AD6FAECuL.toLong(), 0x6C44198C4A475817uL.toLong(),
        )

        private fun rightRotate(value: Long, n: Int): Long =
            (value ushr n) or (value shl (64 - n))
    }
}
