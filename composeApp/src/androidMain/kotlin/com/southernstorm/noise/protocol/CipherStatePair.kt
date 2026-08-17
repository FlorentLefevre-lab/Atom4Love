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
 * Class that contains a pair of CipherState objects.
 *
 * CipherState pairs typically arise when HandshakeState.split() is called.
 *
 * ⚠ `sender` et `receiver` sont déclarés nullables, et ce n'est pas un excès
 * de prudence Kotlin : [senderOnly] et [receiverOnly] mettent délibérément
 * l'un des deux à `null` pour les motifs de handshake à sens unique. En Java
 * le champ devenait `null` sans que le type le dise.
 */
class CipherStatePair(sender: CipherState?, receiver: CipherState?) : Destroyable {

    private var send: CipherState? = sender
    private var recv: CipherState? = receiver

    /**
     * Gets the CipherState to use to send packets to the remote party.
     */
    val sender: CipherState?
        get() = send

    /**
     * Gets the CipherState to use to receive packets from the remote party.
     */
    val receiver: CipherState?
        get() = recv

    /**
     * Destroys the receiving CipherState and retains only the sending CipherState.
     *
     * This function is intended for use with one-way handshake patterns.
     */
    fun senderOnly() {
        recv?.let {
            it.destroy()
            recv = null
        }
    }

    /**
     * Destroys the sending CipherState and retains only the receiving CipherState.
     *
     * This function is intended for use with one-way handshake patterns.
     */
    fun receiverOnly() {
        send?.let {
            it.destroy()
            send = null
        }
    }

    /**
     * Swaps the sender and receiver.
     */
    fun swap() {
        val temp = send
        send = recv
        recv = temp
    }

    override fun destroy() {
        senderOnly()
        receiverOnly()
    }
}
