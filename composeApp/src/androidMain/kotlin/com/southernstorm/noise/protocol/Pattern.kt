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

/**
 * Information about all supported handshake patterns.
 *
 * Chaque motif est un tableau dont le **premier élément est un mot de
 * drapeaux** et dont les suivants sont des jetons. `FLIP_DIR` sépare deux
 * messages : ici les jetons sont écrits une ligne par message, ce que
 * l'amont ne fait pas — c'est la seule façon de voir à l'œil qu'un motif est
 * bien celui qu'on croit, et une table recopiée de travers ne se signale
 * jamais autrement que par un handshake qui échoue.
 *
 * ⚠ Les drapeaux sont des `Int` ici alors que l'amont les déclare `short` :
 * Kotlin n'a pas d'opérateur `or` sur `Short`, et Java ne s'en tirait que
 * parce que ses expressions constantes se rétrécissent implicitement. Les
 * valeurs sont les mêmes ; seul le premier élément de chaque tableau repasse
 * en `Short` au moment de la construction.
 */
internal object Pattern {

    // Token codes.
    const val S: Short = 1
    const val E: Short = 2
    const val EE: Short = 3
    const val ES: Short = 4
    const val SE: Short = 5
    const val SS: Short = 6
    const val F: Short = 7
    const val FF: Short = 8
    const val FLIP_DIR: Short = 255

    // Pattern flag bits.
    const val FLAG_LOCAL_STATIC = 0x0001
    const val FLAG_LOCAL_EPHEMERAL = 0x0002
    const val FLAG_LOCAL_REQUIRED = 0x0004
    const val FLAG_LOCAL_EPHEM_REQ = 0x0008
    const val FLAG_LOCAL_HYBRID = 0x0010
    const val FLAG_LOCAL_HYBRID_REQ = 0x0020
    const val FLAG_REMOTE_STATIC = 0x0100
    const val FLAG_REMOTE_EPHEMERAL = 0x0200
    const val FLAG_REMOTE_REQUIRED = 0x0400
    const val FLAG_REMOTE_EPHEM_REQ = 0x0800
    const val FLAG_REMOTE_HYBRID = 0x1000
    const val FLAG_REMOTE_HYBRID_REQ = 0x2000

    private val noise_pattern_N = shortArrayOf(
        (FLAG_LOCAL_EPHEMERAL or FLAG_REMOTE_STATIC or FLAG_REMOTE_REQUIRED).toShort(),
        E, ES,
    )

    private val noise_pattern_K = shortArrayOf(
        (
            FLAG_LOCAL_STATIC or FLAG_LOCAL_EPHEMERAL or FLAG_LOCAL_REQUIRED or
                FLAG_REMOTE_STATIC or FLAG_REMOTE_REQUIRED
            ).toShort(),
        E, ES, SS,
    )

    private val noise_pattern_X = shortArrayOf(
        (
            FLAG_LOCAL_STATIC or FLAG_LOCAL_EPHEMERAL or
                FLAG_REMOTE_STATIC or FLAG_REMOTE_REQUIRED
            ).toShort(),
        E, ES, S, SS,
    )

    private val noise_pattern_NN = shortArrayOf(
        (FLAG_LOCAL_EPHEMERAL or FLAG_REMOTE_EPHEMERAL).toShort(),
        E,
        FLIP_DIR, E, EE,
    )

    private val noise_pattern_NK = shortArrayOf(
        (
            FLAG_LOCAL_EPHEMERAL or FLAG_REMOTE_STATIC or
                FLAG_REMOTE_EPHEMERAL or FLAG_REMOTE_REQUIRED
            ).toShort(),
        E, ES,
        FLIP_DIR, E, EE,
    )

    private val noise_pattern_NX = shortArrayOf(
        (FLAG_LOCAL_EPHEMERAL or FLAG_REMOTE_STATIC or FLAG_REMOTE_EPHEMERAL).toShort(),
        E,
        FLIP_DIR, E, EE, S, ES,
    )

    private val noise_pattern_XN = shortArrayOf(
        (FLAG_LOCAL_STATIC or FLAG_LOCAL_EPHEMERAL or FLAG_REMOTE_EPHEMERAL).toShort(),
        E,
        FLIP_DIR, E, EE,
        FLIP_DIR, S, SE,
    )

    private val noise_pattern_XK = shortArrayOf(
        (
            FLAG_LOCAL_STATIC or FLAG_LOCAL_EPHEMERAL or FLAG_REMOTE_STATIC or
                FLAG_REMOTE_EPHEMERAL or FLAG_REMOTE_REQUIRED
            ).toShort(),
        E, ES,
        FLIP_DIR, E, EE,
        FLIP_DIR, S, SE,
    )

    private val noise_pattern_XX = shortArrayOf(
        (
            FLAG_LOCAL_STATIC or FLAG_LOCAL_EPHEMERAL or
                FLAG_REMOTE_STATIC or FLAG_REMOTE_EPHEMERAL
            ).toShort(),
        E,
        FLIP_DIR, E, EE, S, ES,
        FLIP_DIR, S, SE,
    )

    private val noise_pattern_KN = shortArrayOf(
        (
            FLAG_LOCAL_STATIC or FLAG_LOCAL_EPHEMERAL or
                FLAG_LOCAL_REQUIRED or FLAG_REMOTE_EPHEMERAL
            ).toShort(),
        E,
        FLIP_DIR, E, EE, SE,
    )

    private val noise_pattern_KK = shortArrayOf(
        (
            FLAG_LOCAL_STATIC or FLAG_LOCAL_EPHEMERAL or FLAG_LOCAL_REQUIRED or
                FLAG_REMOTE_STATIC or FLAG_REMOTE_EPHEMERAL or FLAG_REMOTE_REQUIRED
            ).toShort(),
        E, ES, SS,
        FLIP_DIR, E, EE, SE,
    )

    private val noise_pattern_KX = shortArrayOf(
        (
            FLAG_LOCAL_STATIC or FLAG_LOCAL_EPHEMERAL or FLAG_LOCAL_REQUIRED or
                FLAG_REMOTE_STATIC or FLAG_REMOTE_EPHEMERAL
            ).toShort(),
        E,
        FLIP_DIR, E, EE, SE, S, ES,
    )

    private val noise_pattern_IN = shortArrayOf(
        (FLAG_LOCAL_STATIC or FLAG_LOCAL_EPHEMERAL or FLAG_REMOTE_EPHEMERAL).toShort(),
        E, S,
        FLIP_DIR, E, EE, SE,
    )

    private val noise_pattern_IK = shortArrayOf(
        (
            FLAG_LOCAL_STATIC or FLAG_LOCAL_EPHEMERAL or FLAG_REMOTE_STATIC or
                FLAG_REMOTE_EPHEMERAL or FLAG_REMOTE_REQUIRED
            ).toShort(),
        E, ES, S, SS,
        FLIP_DIR, E, EE, SE,
    )

    private val noise_pattern_IX = shortArrayOf(
        (
            FLAG_LOCAL_STATIC or FLAG_LOCAL_EPHEMERAL or
                FLAG_REMOTE_STATIC or FLAG_REMOTE_EPHEMERAL
            ).toShort(),
        E, S,
        FLIP_DIR, E, EE, SE, S, ES,
    )

    private val noise_pattern_XXfallback = shortArrayOf(
        (
            FLAG_LOCAL_STATIC or FLAG_LOCAL_EPHEMERAL or FLAG_REMOTE_STATIC or
                FLAG_REMOTE_EPHEMERAL or FLAG_REMOTE_EPHEM_REQ
            ).toShort(),
        E, EE, S, SE,
        FLIP_DIR, S, ES,
    )

    private val noise_pattern_Xnoidh = shortArrayOf(
        (
            FLAG_LOCAL_STATIC or FLAG_LOCAL_EPHEMERAL or
                FLAG_REMOTE_STATIC or FLAG_REMOTE_REQUIRED
            ).toShort(),
        E, S, ES, SS,
    )

    private val noise_pattern_NXnoidh = shortArrayOf(
        (FLAG_LOCAL_EPHEMERAL or FLAG_REMOTE_STATIC or FLAG_REMOTE_EPHEMERAL).toShort(),
        E,
        FLIP_DIR, E, S, EE, ES,
    )

    private val noise_pattern_XXnoidh = shortArrayOf(
        (
            FLAG_LOCAL_STATIC or FLAG_LOCAL_EPHEMERAL or
                FLAG_REMOTE_STATIC or FLAG_REMOTE_EPHEMERAL
            ).toShort(),
        E,
        FLIP_DIR, E, S, EE, ES,
        FLIP_DIR, S, SE,
    )

    private val noise_pattern_KXnoidh = shortArrayOf(
        (
            FLAG_LOCAL_STATIC or FLAG_LOCAL_EPHEMERAL or FLAG_LOCAL_REQUIRED or
                FLAG_REMOTE_STATIC or FLAG_REMOTE_EPHEMERAL
            ).toShort(),
        E,
        FLIP_DIR, E, S, EE, SE, ES,
    )

    private val noise_pattern_IKnoidh = shortArrayOf(
        (
            FLAG_LOCAL_STATIC or FLAG_LOCAL_EPHEMERAL or FLAG_REMOTE_STATIC or
                FLAG_REMOTE_EPHEMERAL or FLAG_REMOTE_REQUIRED
            ).toShort(),
        E, S, ES, SS,
        FLIP_DIR, E, EE, SE,
    )

    private val noise_pattern_IXnoidh = shortArrayOf(
        (
            FLAG_LOCAL_STATIC or FLAG_LOCAL_EPHEMERAL or
                FLAG_REMOTE_STATIC or FLAG_REMOTE_EPHEMERAL
            ).toShort(),
        E, S,
        FLIP_DIR, E, S, EE, SE, ES,
    )

    private val noise_pattern_NNhfs = shortArrayOf(
        (
            FLAG_LOCAL_EPHEMERAL or FLAG_LOCAL_HYBRID or
                FLAG_REMOTE_EPHEMERAL or FLAG_REMOTE_HYBRID
            ).toShort(),
        E, F,
        FLIP_DIR, E, F, EE, FF,
    )

    private val noise_pattern_NKhfs = shortArrayOf(
        (
            FLAG_LOCAL_EPHEMERAL or FLAG_LOCAL_HYBRID or FLAG_REMOTE_STATIC or
                FLAG_REMOTE_EPHEMERAL or FLAG_REMOTE_HYBRID or FLAG_REMOTE_REQUIRED
            ).toShort(),
        E, F, ES,
        FLIP_DIR, E, F, EE, FF,
    )

    private val noise_pattern_NXhfs = shortArrayOf(
        (
            FLAG_LOCAL_EPHEMERAL or FLAG_LOCAL_HYBRID or FLAG_REMOTE_STATIC or
                FLAG_REMOTE_EPHEMERAL or FLAG_REMOTE_HYBRID
            ).toShort(),
        E, F,
        FLIP_DIR, E, F, EE, FF, S, ES,
    )

    private val noise_pattern_XNhfs = shortArrayOf(
        (
            FLAG_LOCAL_STATIC or FLAG_LOCAL_EPHEMERAL or FLAG_LOCAL_HYBRID or
                FLAG_REMOTE_EPHEMERAL or FLAG_REMOTE_HYBRID
            ).toShort(),
        E, F,
        FLIP_DIR, E, F, EE, FF,
        FLIP_DIR, S, SE,
    )

    private val noise_pattern_XKhfs = shortArrayOf(
        (
            FLAG_LOCAL_STATIC or FLAG_LOCAL_EPHEMERAL or FLAG_LOCAL_HYBRID or
                FLAG_REMOTE_STATIC or FLAG_REMOTE_EPHEMERAL or FLAG_REMOTE_HYBRID or
                FLAG_REMOTE_REQUIRED
            ).toShort(),
        E, F, ES,
        FLIP_DIR, E, F, EE, FF,
        FLIP_DIR, S, SE,
    )

    private val noise_pattern_XXhfs = shortArrayOf(
        (
            FLAG_LOCAL_STATIC or FLAG_LOCAL_EPHEMERAL or FLAG_LOCAL_HYBRID or
                FLAG_REMOTE_STATIC or FLAG_REMOTE_EPHEMERAL or FLAG_REMOTE_HYBRID
            ).toShort(),
        E, F,
        FLIP_DIR, E, F, EE, FF, S, ES,
        FLIP_DIR, S, SE,
    )

    private val noise_pattern_KNhfs = shortArrayOf(
        (
            FLAG_LOCAL_STATIC or FLAG_LOCAL_EPHEMERAL or FLAG_LOCAL_REQUIRED or
                FLAG_LOCAL_HYBRID or FLAG_REMOTE_EPHEMERAL or FLAG_REMOTE_HYBRID
            ).toShort(),
        E, F,
        FLIP_DIR, E, F, EE, FF, SE,
    )

    private val noise_pattern_KKhfs = shortArrayOf(
        (
            FLAG_LOCAL_STATIC or FLAG_LOCAL_EPHEMERAL or FLAG_LOCAL_REQUIRED or
                FLAG_LOCAL_HYBRID or FLAG_REMOTE_STATIC or FLAG_REMOTE_EPHEMERAL or
                FLAG_REMOTE_HYBRID or FLAG_REMOTE_REQUIRED
            ).toShort(),
        E, F, ES, SS,
        FLIP_DIR, E, F, EE, FF, SE,
    )

    private val noise_pattern_KXhfs = shortArrayOf(
        (
            FLAG_LOCAL_STATIC or FLAG_LOCAL_EPHEMERAL or FLAG_LOCAL_REQUIRED or
                FLAG_LOCAL_HYBRID or FLAG_REMOTE_STATIC or FLAG_REMOTE_EPHEMERAL or
                FLAG_REMOTE_HYBRID
            ).toShort(),
        E, F,
        FLIP_DIR, E, F, EE, FF, SE, S, ES,
    )

    private val noise_pattern_INhfs = shortArrayOf(
        (
            FLAG_LOCAL_STATIC or FLAG_LOCAL_EPHEMERAL or FLAG_LOCAL_HYBRID or
                FLAG_REMOTE_EPHEMERAL or FLAG_REMOTE_HYBRID
            ).toShort(),
        E, F, S,
        FLIP_DIR, E, F, EE, FF, SE,
    )

    private val noise_pattern_IKhfs = shortArrayOf(
        (
            FLAG_LOCAL_STATIC or FLAG_LOCAL_EPHEMERAL or FLAG_LOCAL_HYBRID or
                FLAG_REMOTE_STATIC or FLAG_REMOTE_EPHEMERAL or FLAG_REMOTE_HYBRID or
                FLAG_REMOTE_REQUIRED
            ).toShort(),
        E, F, ES, S, SS,
        FLIP_DIR, E, F, EE, FF, SE,
    )

    private val noise_pattern_IXhfs = shortArrayOf(
        (
            FLAG_LOCAL_STATIC or FLAG_LOCAL_EPHEMERAL or FLAG_LOCAL_HYBRID or
                FLAG_REMOTE_STATIC or FLAG_REMOTE_EPHEMERAL or FLAG_REMOTE_HYBRID
            ).toShort(),
        E, F, S,
        FLIP_DIR, E, F, EE, FF, SE, S, ES,
    )

    private val noise_pattern_XXfallback_hfs = shortArrayOf(
        (
            FLAG_LOCAL_STATIC or FLAG_LOCAL_EPHEMERAL or FLAG_LOCAL_HYBRID or
                FLAG_REMOTE_STATIC or FLAG_REMOTE_EPHEMERAL or FLAG_REMOTE_EPHEM_REQ or
                FLAG_REMOTE_HYBRID or FLAG_REMOTE_HYBRID_REQ
            ).toShort(),
        E, F, EE, FF, S, SE,
        FLIP_DIR, S, ES,
    )

    private val noise_pattern_NXnoidh_hfs = shortArrayOf(
        (
            FLAG_LOCAL_EPHEMERAL or FLAG_LOCAL_HYBRID or FLAG_REMOTE_STATIC or
                FLAG_REMOTE_EPHEMERAL or FLAG_REMOTE_HYBRID
            ).toShort(),
        E, F,
        FLIP_DIR, E, F, S, EE, FF, ES,
    )

    private val noise_pattern_XXnoidh_hfs = shortArrayOf(
        (
            FLAG_LOCAL_STATIC or FLAG_LOCAL_EPHEMERAL or FLAG_LOCAL_HYBRID or
                FLAG_REMOTE_STATIC or FLAG_REMOTE_EPHEMERAL or FLAG_REMOTE_HYBRID
            ).toShort(),
        E, F,
        FLIP_DIR, E, F, S, EE, FF, ES,
        FLIP_DIR, S, SE,
    )

    private val noise_pattern_KXnoidh_hfs = shortArrayOf(
        (
            FLAG_LOCAL_STATIC or FLAG_LOCAL_EPHEMERAL or FLAG_LOCAL_REQUIRED or
                FLAG_LOCAL_HYBRID or FLAG_REMOTE_STATIC or FLAG_REMOTE_EPHEMERAL or
                FLAG_REMOTE_HYBRID
            ).toShort(),
        E, F,
        FLIP_DIR, E, F, S, EE, FF, SE, ES,
    )

    // ⚠ `FLAG_REMOTE_EPHEMERAL` apparaît DEUX FOIS dans ce mot de drapeaux
    // chez l'amont. Un « ou » logique est idempotent, la valeur est donc la
    // bonne ; c'est très probablement `FLAG_REMOTE_EPHEM_REQ` qui était voulu.
    // Reproduit tel quel : corriger changerait le comportement d'un motif que
    // personne n'utilise, sans oracle pour dire lequel est juste.
    private val noise_pattern_IKnoidh_hfs = shortArrayOf(
        (
            FLAG_LOCAL_STATIC or FLAG_LOCAL_EPHEMERAL or FLAG_LOCAL_HYBRID or
                FLAG_REMOTE_STATIC or FLAG_REMOTE_EPHEMERAL or FLAG_REMOTE_EPHEMERAL or
                FLAG_REMOTE_HYBRID
            ).toShort(),
        E, F, S, ES, SS,
        FLIP_DIR, E, F, EE, FF, SE,
    )

    private val noise_pattern_IXnoidh_hfs = shortArrayOf(
        (
            FLAG_LOCAL_STATIC or FLAG_LOCAL_EPHEMERAL or FLAG_LOCAL_HYBRID or
                FLAG_REMOTE_STATIC or FLAG_REMOTE_EPHEMERAL or FLAG_REMOTE_HYBRID
            ).toShort(),
        E, F, S,
        FLIP_DIR, E, F, S, EE, FF, SE, ES,
    )

    /**
     * Look up the description information for a pattern.
     *
     * @param name The name of the pattern.
     * @return The pattern description or null.
     */
    @JvmStatic
    fun lookup(name: String): ShortArray? = when (name) {
        "N" -> noise_pattern_N
        "K" -> noise_pattern_K
        "X" -> noise_pattern_X
        "NN" -> noise_pattern_NN
        "NK" -> noise_pattern_NK
        "NX" -> noise_pattern_NX
        "XN" -> noise_pattern_XN
        "XK" -> noise_pattern_XK
        "XX" -> noise_pattern_XX
        "KN" -> noise_pattern_KN
        "KK" -> noise_pattern_KK
        "KX" -> noise_pattern_KX
        "IN" -> noise_pattern_IN
        "IK" -> noise_pattern_IK
        "IX" -> noise_pattern_IX
        "XXfallback" -> noise_pattern_XXfallback
        "Xnoidh" -> noise_pattern_Xnoidh
        "NXnoidh" -> noise_pattern_NXnoidh
        "XXnoidh" -> noise_pattern_XXnoidh
        "KXnoidh" -> noise_pattern_KXnoidh
        "IKnoidh" -> noise_pattern_IKnoidh
        "IXnoidh" -> noise_pattern_IXnoidh
        "NNhfs" -> noise_pattern_NNhfs
        "NKhfs" -> noise_pattern_NKhfs
        "NXhfs" -> noise_pattern_NXhfs
        "XNhfs" -> noise_pattern_XNhfs
        "XKhfs" -> noise_pattern_XKhfs
        "XXhfs" -> noise_pattern_XXhfs
        "KNhfs" -> noise_pattern_KNhfs
        "KKhfs" -> noise_pattern_KKhfs
        "KXhfs" -> noise_pattern_KXhfs
        "INhfs" -> noise_pattern_INhfs
        "IKhfs" -> noise_pattern_IKhfs
        "IXhfs" -> noise_pattern_IXhfs
        "XXfallback+hfs" -> noise_pattern_XXfallback_hfs
        "NXnoidh+hfs" -> noise_pattern_NXnoidh_hfs
        "XXnoidh+hfs" -> noise_pattern_XXnoidh_hfs
        "KXnoidh+hfs" -> noise_pattern_KXnoidh_hfs
        "IKnoidh+hfs" -> noise_pattern_IKnoidh_hfs
        "IXnoidh+hfs" -> noise_pattern_IXnoidh_hfs
        else -> null
    }

    /**
     * Reverses the local and remote flags for a pattern.
     *
     * @param flags The flags, assuming that the initiator is "local".
     * @return The reversed flags, with the responder now being "local".
     */
    @JvmStatic
    fun reverseFlags(flags: Short): Short =
        (((flags.toInt() shr 8) and 0x00FF) or ((flags.toInt() shl 8) and 0xFF00)).toShort()
}
