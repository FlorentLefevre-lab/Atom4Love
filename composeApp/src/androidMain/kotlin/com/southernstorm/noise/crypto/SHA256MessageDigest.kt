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
 * Fallback implementation of SHA256.
 */
class SHA256MessageDigest : MessageDigest("SHA-256"), Destroyable {

    private val h = IntArray(8)
    private val block = ByteArray(64)
    private val w = IntArray(64)
    private var length: Long = 0
    private var posn: Int = 0

    /**
     * Constructs a new SHA256 message digest object.
     */
    init {
        engineReset()
    }

    override fun destroy() {
        h.fill(0)
        block.fill(0)
        w.fill(0)
    }

    override fun engineDigest(): ByteArray {
        val digest = ByteArray(32)
        try {
            engineDigest(digest, 0, 32)
        } catch (e: DigestException) {
            // Shouldn't happen, but just in case.
            digest.fill(0)
        }
        return digest
    }

    override fun engineDigest(buf: ByteArray, offset: Int, len: Int): Int {
        if (len < 32) throw DigestException("Invalid digest length for SHA256")
        if (posn <= 64 - 9) {
            block[posn] = 0x80.toByte()
            block.fill(0, posn + 1, 64 - 8)
        } else {
            block[posn] = 0x80.toByte()
            block.fill(0, posn + 1, 64)
            transform(block, 0)
            block.fill(0, 0, 64 - 8)
        }
        writeBE32(block, 64 - 8, (length ushr 32).toInt())
        writeBE32(block, 64 - 4, length.toInt())
        transform(block, 0)
        posn = 0
        for (index in 0 until 8) writeBE32(buf, offset + index * 4, h[index])
        return 32
    }

    override fun engineGetDigestLength(): Int = 32

    override fun engineReset() {
        // ⚠ `.toInt()` sur les valeurs qui débordent l'Int signé : Kotlin
        // infère un Long pour un littéral > 0x7FFFFFFF, et `.toInt()` le
        // tronque exactement comme le fait un `(int)` en Java. L'hexadécimal
        // reste donc celui de l'amont, à recopier sans rien recalculer — une
        // constante convertie de tête est une faute qu'aucun test ne rattrape.
        h[0] = 0x6A09E667
        h[1] = 0xBB67AE85.toInt()
        h[2] = 0x3C6EF372
        h[3] = 0xA54FF53A.toInt()
        h[4] = 0x510E527F
        h[5] = 0x9B05688C.toInt()
        h[6] = 0x1F83D9AB
        h[7] = 0x5BE0CD19
        length = 0
        posn = 0
    }

    override fun engineUpdate(input: Byte) {
        block[posn++] = input
        length += 8
        if (posn >= 64) {
            transform(block, 0)
            posn = 0
        }
    }

    override fun engineUpdate(input: ByteArray, offset: Int, len: Int) {
        var offset = offset
        var len = len
        while (len > 0) {
            if (posn == 0 && len >= 64) {
                transform(input, offset)
                offset += 64
                len -= 64
                length += (64 * 8).toLong()
            } else {
                var temp = 64 - posn
                if (temp > len) temp = len
                input.copyInto(block, posn, offset, offset + temp)
                posn += temp
                length += (temp * 8).toLong()
                if (posn >= 64) {
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
        var a: Int
        var b: Int
        var c: Int
        var d: Int
        var e: Int
        var f: Int
        var g: Int
        var h: Int
        var temp1: Int
        var temp2: Int

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
            w[index] = ((m[offset].toInt() and 0xFF) shl 24) or
                ((m[offset + 1].toInt() and 0xFF) shl 16) or
                ((m[offset + 2].toInt() and 0xFF) shl 8) or
                (m[offset + 3].toInt() and 0xFF)
            offset += 4
        }

        // Extend the first 16 words to 64.
        for (index in 16 until 64) {
            w[index] = w[index - 16] + w[index - 7] +
                (rightRotate(w[index - 15], 7) xor
                    rightRotate(w[index - 15], 18) xor
                    (w[index - 15] ushr 3)) +
                (rightRotate(w[index - 2], 17) xor
                    rightRotate(w[index - 2], 19) xor
                    (w[index - 2] ushr 10))
        }

        // Compression function main loop.
        for (index in 0 until 64) {
            temp1 = h + k[index] + w[index] +
                (rightRotate(e, 6) xor rightRotate(e, 11) xor rightRotate(e, 25)) +
                ((e and f) xor (e.inv() and g))
            temp2 = (rightRotate(a, 2) xor rightRotate(a, 13) xor rightRotate(a, 22)) +
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
        private fun writeBE32(buf: ByteArray, offset: Int, value: Int) {
            buf[offset] = (value shr 24).toByte()
            buf[offset + 1] = (value shr 16).toByte()
            buf[offset + 2] = (value shr 8).toByte()
            buf[offset + 3] = value.toByte()
        }

        // Les 64 constantes de SHA-256, recopiées telles quelles de l'amont ;
        // `.toInt()` sur celles dont le bit de poids fort est à 1.
        private val k = intArrayOf(
            0x428a2f98, 0x71374491, 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(),
            0x3956c25b, 0x59f111f1, 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
            0xd807aa98.toInt(), 0x12835b01, 0x243185be, 0x550c7dc3,
            0x72be5d74, 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
            0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6, 0x240ca1cc,
            0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
            0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(),
            0xc6e00bf3.toInt(), 0xd5a79147.toInt(), 0x06ca6351, 0x14292967,
            0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
            0x650a7354, 0x766a0abb, 0x81c2c92e.toInt(), 0x92722c85.toInt(),
            0xa2bfe8a1.toInt(), 0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(),
            0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070,
            0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
            0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
            0x748f82ee, 0x78a5636f, 0x84c87814.toInt(), 0x8cc70208.toInt(),
            0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt(),
        )

        private fun rightRotate(value: Int, n: Int): Int =
            (value ushr n) or (value shl (32 - n))
    }
}
