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

package com.southernstorm.noise

/**
 * Ce que le portage Kotlin remplace du JDK — et c'est tout le fichier.
 *
 * L'original s'appuyait sur `java.security` et `javax.crypto` : quatre classes
 * de hachage héritaient de `java.security.MessageDigest`, et les exceptions
 * `ShortBufferException` / `BadPaddingException` traversaient toute l'API.
 * Rien de cela n'existe en Kotlin/Native — une transcription qui les aurait
 * gardées aurait donné du Kotlin toujours impossible à compiler pour iOS,
 * c'est-à-dire un port pour rien.
 *
 * Les noms sont conservés à l'identique. Une méthode portée qui écrit
 * `throw ShortBufferException()` dit exactement ce que disait la ligne Java
 * d'origine, et se relit contre elle sans traduction.
 *
 * ⚠ La seule chose ici qui touche encore la plateforme est [SecureRandomSource].
 */

/** Le tampon fourni n'a pas la place d'accueillir le résultat. */
class ShortBufferException(message: String? = null) : Exception(message)

/**
 * Un déchiffrement a échoué. En Noise, cela veut toujours dire qu'un MAC n'a
 * pas été vérifié — donc que le message a été altéré, ou n'était pas pour nous.
 */
open class BadPaddingException(message: String? = null) : Exception(message)

/**
 * Le MAC d'un message AEAD est faux.
 *
 * L'original attrapait `javax.crypto.AEADBadTagException` par réflexion, en
 * retombant sur `BadPaddingException` si le JDK ne la connaissait pas. La
 * réflexion n'est pas portable ; le sous-typage dit la même chose et se vérifie
 * à la compilation.
 */
class AEADBadTagException(message: String? = null) : BadPaddingException(message)

/** Le nom d'algorithme n'est pas reconnu comme un nom de protocole Noise. */
class NoSuchAlgorithmException(message: String? = null) : Exception(message)

/** Une empreinte n'a pas pu être produite dans le tampon demandé. */
class DigestException(message: String? = null) : Exception(message)

/**
 * La source d'aléa, unique couture de plateforme du portage.
 *
 * L'original tenait un `java.security.SecureRandom` statique. Le jour où le
 * paquet monte dans `commonMain`, cette interface devient un `expect` et
 * [JvmSecureRandom] son `actual` Android ; il n'y a rien d'autre à déplacer.
 *
 * ⚠ Toute implémentation doit être cryptographiquement sûre. Les clés
 * éphémères de chaque handshake sortent d'ici : un générateur prévisible
 * dévoilerait toute conversation, sans qu'aucun test ne s'en aperçoive.
 */
interface SecureRandomSource {
    /** Remplit [data] d'octets aléatoires imprévisibles. */
    fun nextBytes(data: ByteArray)
}

/** L'implémentation Android/JVM, adossée à `java.security.SecureRandom`. */
class JvmSecureRandom : SecureRandomSource {
    private val random = java.security.SecureRandom()
    override fun nextBytes(data: ByteArray) = random.nextBytes(data)
}
