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

/**
 * Ce que `java.security.MessageDigest` était pour l'original.
 *
 * Les quatre implémentations de hachage en héritaient. La classe du JDK
 * n'existe pas en Kotlin/Native, donc elle est reprise ici — réduite aux quatre
 * opérations que la bibliothèque appelle réellement : `update`, `digest`,
 * `reset`, `digestLength`.
 *
 * ⚠ **L'indirection `engineX` est gardée exprès.** Elle n'apporte rien en
 * Kotlin, mais elle fait que chaque implémentation portée se relit ligne à
 * ligne contre le fichier Java d'origine. C'est le peu d'auditabilité qu'on
 * peut rendre après avoir renoncé au `diff` contre l'amont.
 *
 * ⚠ **Et le comportement du JDK est reproduit tel quel, y compris ce qui
 * surprend** : `digest()` ne rappelle PAS `engineReset()`. Les implémentations
 * remettent `posn` à zéro mais laissent l'état de hachage en place, si bien que
 * hacher deux fois de suite sans `reset()` explicite donne un résultat faux.
 * La bibliothèque appelle toujours `reset()` avant d'accumuler, donc cela ne
 * l'atteint jamais. « Corriger » ce point ici ferait diverger le port de son
 * oracle sans que rien ne le signale.
 */
abstract class MessageDigest(
    /** Le nom de l'algorithme, tel que l'original le passait à `super(...)`. */
    val algorithm: String,
) {
    /** La taille de l'empreinte, en octets. */
    val digestLength: Int
        get() = engineGetDigestLength()

    /** Absorbe un octet. */
    fun update(input: Byte) = engineUpdate(input)

    /** Absorbe [len] octets de [input] à partir de [offset]. */
    fun update(input: ByteArray, offset: Int, len: Int) = engineUpdate(input, offset, len)

    /** Rend l'empreinte des données absorbées depuis le dernier [reset]. */
    fun digest(): ByteArray = engineDigest()

    /**
     * Écrit l'empreinte dans [buf] à [offset], et rend le nombre d'octets
     * écrits.
     *
     * @throws DigestException si [len] est trop petit pour l'empreinte.
     */
    fun digest(buf: ByteArray, offset: Int, len: Int): Int {
        require(buf.size - offset >= len) { "Output buffer too small for specified offset and length" }
        return engineDigest(buf, offset, len)
    }

    /** Remet l'objet dans l'état d'un hachage qui n'a rien absorbé. */
    fun reset() = engineReset()

    protected abstract fun engineGetDigestLength(): Int
    protected abstract fun engineUpdate(input: Byte)
    protected abstract fun engineUpdate(input: ByteArray, offset: Int, len: Int)
    protected abstract fun engineDigest(): ByteArray
    protected abstract fun engineDigest(buf: ByteArray, offset: Int, len: Int): Int
    protected abstract fun engineReset()
}
