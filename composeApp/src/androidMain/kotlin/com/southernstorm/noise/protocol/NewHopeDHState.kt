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

import com.southernstorm.noise.crypto.NewHope
import com.southernstorm.noise.crypto.NewHopeTor

/**
 * Implementation of the New Hope post-quantum algorithm for the Noise protocol.
 *
 * ⚠ **Rien n'appelle New Hope dans Atom4Love** : `HandshakeState` refuse même
 * de l'utiliser pour les clés statiques ou éphémères — seuls les jetons `f` et
 * `ff` des motifs « hfs » y mènent, et nous n'en employons aucun.
 *
 * ⚠ Cette classe est **asymétrique** : New Hope n'est pas un Diffie-Hellman.
 * Alice et Bob ne font pas le même calcul, leurs clés n'ont pas la même
 * taille, et [keyType] est ce qui dit lequel des deux rôles on tient. C'est
 * la raison d'être de [DHStateHybrid].
 */
internal class NewHopeDHState : DHStateHybrid {

    enum class KeyType {
        None,
        AlicePrivate,
        AlicePublic,
        BobPrivate,
        BobPublic,
        BobCalculated,
    }

    private var nh: NewHope? = null
    private var publicKey: ByteArray? = null
    private var privateKey: ByteArray? = null
    private var keyType: KeyType = KeyType.None

    /**
     * Special version of NewHopeTor that allows explicit random data
     * to be specified for test vectors.
     */
    private class NewHopeWithPrivateKey(private val randomData: ByteArray) : NewHopeTor() {
        override fun randombytes(buffer: ByteArray) {
            randomData.copyInto(buffer, 0, 0, buffer.size)
        }
    }

    private fun isAlice(): Boolean =
        keyType == KeyType.AlicePrivate || keyType == KeyType.AlicePublic

    override fun destroy() {
        clearKey()
    }

    override val dhName: String
        get() = "NewHope"

    override val publicKeyLength: Int
        get() = if (isAlice()) NewHope.SENDABYTES else NewHope.SENDBBYTES

    override val privateKeyLength: Int
        // New Hope doesn't have private keys in the same sense as
        // Curve25519 and Curve448.  Instead return the number of
        // random bytes that we need to generate each key type.
        get() = if (isAlice()) 64 else 32

    override val sharedKeyLength: Int
        get() = NewHope.SHAREDBYTES

    override fun generateKeyPair() {
        clearKey()
        keyType = KeyType.AlicePrivate
        val n = NewHopeTor()
        nh = n
        val pk = ByteArray(NewHope.SENDABYTES)
        publicKey = pk
        n.keygen(pk, 0)
    }

    override fun generateKeyPair(remote: DHState?) {
        if (remote == null) {
            // No remote public key, so always generate in Alice mode.
            generateKeyPair()
            return
        } else if (remote !is NewHopeDHState) {
            throw IllegalStateException("Mismatched DH objects")
        }
        val remotePublic = remote.publicKey
        if (remote.isAlice() && remotePublic != null) {
            // We have a remote public key for Alice, so generate in Bob mode.
            clearKey()
            keyType = KeyType.BobCalculated
            val n = NewHopeTor()
            nh = n
            val pk = ByteArray(NewHope.SENDBBYTES)
            val sk = ByteArray(NewHope.SHAREDBYTES)
            publicKey = pk
            privateKey = sk
            n.sharedb(sk, 0, pk, 0, remotePublic, 0)
        } else {
            generateKeyPair()
        }
    }

    override fun getPublicKey(key: ByteArray, offset: Int) {
        val pk = publicKey
        if (pk != null) {
            pk.copyInto(key, offset, 0, publicKeyLength)
        } else {
            key.fill(0, 0, publicKeyLength)
        }
    }

    override fun setPublicKey(key: ByteArray, offset: Int) {
        publicKey?.let { Noise.destroy(it) }
        // ⚠ `offset` est ignoré : l'amont copie toujours depuis le début de
        // `key`. Reproduit tel quel — tous les appelants passent 0.
        val pk = ByteArray(publicKeyLength)
        publicKey = pk
        key.copyInto(pk, 0, 0, pk.size)
    }

    override fun getPrivateKey(key: ByteArray, offset: Int) {
        val sk = privateKey
        if (sk != null) {
            sk.copyInto(key, offset, 0, privateKeyLength)
        } else {
            key.fill(0, 0, privateKeyLength)
        }
    }

    override fun setPrivateKey(key: ByteArray, offset: Int) {
        clearKey()
        // Guess the key type from the length of the test data.
        keyType = if (offset == 0 && key.size == 64) KeyType.AlicePrivate else KeyType.BobPrivate
        val sk = ByteArray(privateKeyLength)
        privateKey = sk
        key.copyInto(sk, 0, 0, sk.size)
    }

    override fun setToNullPublicKey() {
        // Null public keys are not supported by New Hope.
        // Destroy the current values but otherwise ignore.
        clearKey()
    }

    override fun clearKey() {
        nh?.let {
            it.destroy()
            nh = null
        }
        publicKey?.let {
            Noise.destroy(it)
            publicKey = null
        }
        privateKey?.let {
            Noise.destroy(it)
            privateKey = null
        }
        keyType = KeyType.None
    }

    override fun hasPublicKey(): Boolean = publicKey != null

    override fun hasPrivateKey(): Boolean = privateKey != null

    override fun isNullPublicKey(): Boolean = false

    override fun calculate(sharedKey: ByteArray, offset: Int, publicDH: DHState) {
        if (publicDH !is NewHopeDHState) {
            throw IllegalArgumentException("Incompatible DH algorithms")
        }
        if (keyType == KeyType.AlicePrivate) {
            // Compute the shared key for Alice.
            nh!!.shareda(sharedKey, 0, publicDH.publicKey!!, 0)
        } else if (keyType == KeyType.BobCalculated) {
            // The shared key for Bob was already computed when the key was generated.
            privateKey!!.copyInto(sharedKey, 0, 0, NewHope.SHAREDBYTES)
        } else {
            throw IllegalStateException("Cannot calculate with this DH object")
        }
    }

    override fun copyFrom(other: DHState) {
        if (other !is NewHopeDHState) {
            throw IllegalStateException("Mismatched DH key objects")
        }
        if (other === this) return
        clearKey()
        when (other.keyType) {
            KeyType.None -> {}

            KeyType.AlicePrivate -> {
                val otherPrivate = other.privateKey
                    ?: throw IllegalStateException("Cannot copy generated key for Alice")
                keyType = KeyType.AlicePrivate
                val sk = ByteArray(otherPrivate.size)
                privateKey = sk
                otherPrivate.copyInto(sk, 0, 0, sk.size)
            }

            KeyType.BobPrivate, KeyType.BobCalculated ->
                throw IllegalStateException("Cannot copy private key for Bob without public key for Alice")

            KeyType.AlicePublic, KeyType.BobPublic -> {
                keyType = other.keyType
                val otherPublic = other.publicKey!!
                val pk = ByteArray(otherPublic.size)
                publicKey = pk
                otherPublic.copyInto(pk, 0, 0, pk.size)
            }
        }
    }

    override fun copyFrom(other: DHState, remote: DHState?) {
        if (remote == null) {
            copyFrom(other)
            return
        }
        if (other !is NewHopeDHState || remote !is NewHopeDHState) {
            throw IllegalStateException("Mismatched DH key objects")
        }
        if (other === this) return
        clearKey()
        when (other.keyType) {
            KeyType.None -> {}

            KeyType.AlicePrivate -> {
                val otherPrivate = other.privateKey
                    ?: throw IllegalStateException("Cannot copy generated key for Alice")
                // Generate Alice's public and private key now.
                keyType = KeyType.AlicePrivate
                val n = NewHopeWithPrivateKey(otherPrivate)
                nh = n
                val pk = ByteArray(NewHope.SENDABYTES)
                publicKey = pk
                n.keygen(pk, 0)
            }

            KeyType.BobPrivate -> {
                val otherPrivate = other.privateKey
                if (otherPrivate != null && remote.keyType == KeyType.AlicePublic) {
                    // Now we know the public key for Alice, we can calculate
                    // Bob's public and shared keys.
                    keyType = KeyType.BobCalculated
                    val n = NewHopeWithPrivateKey(otherPrivate)
                    nh = n
                    val pk = ByteArray(NewHope.SENDBBYTES)
                    val sk = ByteArray(NewHope.SHAREDBYTES)
                    publicKey = pk
                    privateKey = sk
                    n.sharedb(sk, 0, pk, 0, remote.publicKey!!, 0)
                } else {
                    throw IllegalStateException(
                        "Cannot copy private key for Bob without public key for Alice",
                    )
                }
            }

            KeyType.BobCalculated ->
                throw IllegalStateException("Cannot copy generated key for Bob")

            KeyType.AlicePublic, KeyType.BobPublic -> {
                keyType = other.keyType
                val otherPublic = other.publicKey!!
                val pk = ByteArray(otherPublic.size)
                publicKey = pk
                otherPublic.copyInto(pk, 0, 0, pk.size)
            }
        }
    }

    override fun specifyPeer(local: DHState?) {
        if (local !is NewHopeDHState) return
        clearKey()
        keyType = if (local.keyType == KeyType.AlicePrivate) KeyType.BobPublic else KeyType.AlicePublic
    }
}
