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

import com.southernstorm.noise.BadPaddingException
import com.southernstorm.noise.NoSuchAlgorithmException
import com.southernstorm.noise.ShortBufferException

/**
 * Interface to a Noise handshake.
 *
 * ⚠ Les objets DH sont nullables parce qu'ils le sont vraiment : un motif qui
 * n'a pas de clé statique locale ne crée pas de `localKeyPairState`. Java laissait
 * le champ à `null` sans le dire ; ici le type l'annonce, et les appelants
 * doivent le regarder.
 */
class HandshakeState(protocolName: String, role: Int) : Destroyable {

    private var symmetric: SymmetricState
    private var isInitiator: Boolean
    private var localKeyPairState: DHState? = null
    private var localEphemeral: DHState? = null
    private var localHybrid: DHState? = null
    private var remotePublicKeyState: DHState? = null
    private var remoteEphemeral: DHState? = null
    private var remoteHybrid: DHState? = null
    private var fixedEphemeral: DHState? = null
    private var fixedHybrid: DHState? = null
    private var actionValue: Int
    private var requirements: Int
    private var pattern: ShortArray
    private var patternIndex: Int
    private var preSharedKey: ByteArray? = null
    private var prologue: ByteArray? = null

    /**
     * Creates a new Noise handshake.
     *
     * @param protocolName The name of the Noise protocol.
     * @param role The role, HandshakeState.INITIATOR or HandshakeState.RESPONDER.
     *
     * @throws IllegalArgumentException The protocolName is not
     * formatted correctly, or the role is not recognized.
     *
     * @throws NoSuchAlgorithmException One of the cryptographic algorithms
     * that is specified in the protocolName is not supported.
     */
    init {
        // Parse the protocol name into its components.
        val components = protocolName.split("_")
        if (components.size != 5) {
            throw IllegalArgumentException("Protocol name must have 5 components")
        }
        val prefix = components[0]
        val patternId = components[1]
        var dh = components[2]
        var hybrid: String? = null
        val cipher = components[3]
        val hash = components[4]
        if (prefix != "Noise" && prefix != "NoisePSK") {
            throw IllegalArgumentException("Prefix must be Noise or NoisePSK")
        }
        pattern = Pattern.lookup(patternId)
            ?: throw IllegalArgumentException("Handshake pattern is not recognized")
        var flags = pattern[0]
        var extraReqs = 0
        if ((flags.toInt() and Pattern.FLAG_REMOTE_REQUIRED) != 0 && patternId.length > 1) {
            extraReqs = extraReqs or FALLBACK_POSSIBLE
        }
        if (role == RESPONDER) {
            // Reverse the pattern flags so that the responder is "local".
            flags = Pattern.reverseFlags(flags)
        }
        val index = dh.indexOf('+')
        if (index != -1) {
            // The DH name has two components: regular and hybrid.
            hybrid = dh.substring(index + 1)
            dh = dh.substring(0, index)
            if ((flags.toInt() and Pattern.FLAG_LOCAL_HYBRID) == 0 ||
                (flags.toInt() and Pattern.FLAG_REMOTE_HYBRID) == 0
            ) {
                throw IllegalArgumentException("Hybrid function specified for non-hybrid pattern")
            }
        } else {
            if ((flags.toInt() and Pattern.FLAG_LOCAL_HYBRID) != 0 ||
                (flags.toInt() and Pattern.FLAG_REMOTE_HYBRID) != 0
            ) {
                throw IllegalArgumentException("Hybrid function not specified for hybrid pattern")
            }
        }

        // Check that the role is correctly specified.
        if (role != INITIATOR && role != RESPONDER) {
            throw IllegalArgumentException("Role must be initiator or responder")
        }

        // Initialize this object.  This will also create the cipher and hash objects.
        symmetric = SymmetricState(protocolName, cipher, hash)
        isInitiator = (role == INITIATOR)
        actionValue = NO_ACTION
        requirements = extraReqs or computeRequirements(flags, prefix, role, false)
        patternIndex = 1

        // Create the DH objects that we will need later.
        if ((flags.toInt() and Pattern.FLAG_LOCAL_STATIC) != 0) localKeyPairState = Noise.createDH(dh)
        if ((flags.toInt() and Pattern.FLAG_LOCAL_EPHEMERAL) != 0) localEphemeral = Noise.createDH(dh)
        if ((flags.toInt() and Pattern.FLAG_LOCAL_HYBRID) != 0) localHybrid = Noise.createDH(hybrid!!)
        if ((flags.toInt() and Pattern.FLAG_REMOTE_STATIC) != 0) remotePublicKeyState = Noise.createDH(dh)
        if ((flags.toInt() and Pattern.FLAG_REMOTE_EPHEMERAL) != 0) remoteEphemeral = Noise.createDH(dh)
        if ((flags.toInt() and Pattern.FLAG_REMOTE_HYBRID) != 0) remoteHybrid = Noise.createDH(hybrid!!)

        // We cannot use hybrid algorithms like New Hope for ephemeral or static keys,
        // as the unbalanced nature of the algorithm only works with "f" and "ff" tokens.
        localKeyPairState?.let {
            if (it is DHStateHybrid) throw NoSuchAlgorithmException("Cannot use '${it.dhName}' for static keys")
        }
        localEphemeral?.let {
            if (it is DHStateHybrid) throw NoSuchAlgorithmException("Cannot use '${it.dhName}' for ephemeral keys")
        }
        remotePublicKeyState?.let {
            if (it is DHStateHybrid) throw NoSuchAlgorithmException("Cannot use '${it.dhName}' for static keys")
        }
        remoteEphemeral?.let {
            if (it is DHStateHybrid) throw NoSuchAlgorithmException("Cannot use '${it.dhName}' for ephemeral keys")
        }
    }

    /**
     * Gets the name of the Noise protocol.
     */
    val protocolName: String
        get() = symmetric.protocolName

    /**
     * Gets the role for this handshake, INITIATOR or RESPONDER.
     */
    val role: Int
        get() = if (isInitiator) INITIATOR else RESPONDER

    /**
     * Determine if this handshake needs a pre-shared key value
     * and one has not been configured yet.
     */
    fun needsPreSharedKey(): Boolean =
        if (preSharedKey != null) false else (requirements and PSK_REQUIRED) != 0

    /**
     * Determine if this object has already been configured with a
     * pre-shared key.
     */
    fun hasPreSharedKey(): Boolean = preSharedKey != null

    /**
     * Sets the pre-shared key for this handshake.
     *
     * @throws IllegalArgumentException The length is not 32.
     * @throws UnsupportedOperationException Pre-shared keys are not
     * supported for this handshake type.
     * @throws IllegalStateException The handshake has already started.
     */
    fun setPreSharedKey(key: ByteArray, offset: Int, length: Int) {
        if (length != 32) {
            throw IllegalArgumentException("Pre-shared keys must be 32 bytes in length")
        }
        if ((requirements and PSK_REQUIRED) == 0) {
            throw UnsupportedOperationException("Pre-shared keys are not supported for this handshake")
        }
        if (actionValue != NO_ACTION) {
            throw IllegalStateException("Handshake has already started; cannot set pre-shared key")
        }
        preSharedKey?.let {
            Noise.destroy(it)
            preSharedKey = null
        }
        preSharedKey = Noise.copySubArray(key, offset, length)
    }

    /**
     * Sets the prologue for this handshake.
     *
     * @throws IllegalStateException The handshake has already started.
     */
    fun setPrologue(prologue: ByteArray, offset: Int, length: Int) {
        if (actionValue != NO_ACTION) {
            throw IllegalStateException("Handshake has already started; cannot set prologue")
        }
        this.prologue?.let {
            Noise.destroy(it)
            this.prologue = null
        }
        this.prologue = Noise.copySubArray(prologue, offset, length)
    }

    /**
     * Gets the keypair object for the local static key, or null if a local
     * static key is not required.
     */
    val localKeyPair: DHState?
        get() = localKeyPairState

    /**
     * Determine if this handshake requires a local static key.
     */
    fun needsLocalKeyPair(): Boolean = localKeyPairState?.let { !it.hasPrivateKey() } ?: false

    /**
     * Determine if this handshake has already been configured
     * with a local static key.
     */
    fun hasLocalKeyPair(): Boolean = localKeyPairState?.hasPrivateKey() ?: false

    /**
     * Gets the public key object for the remote static key, or null if a
     * remote static key is not required.
     */
    val remotePublicKey: DHState?
        get() = remotePublicKeyState

    /**
     * Determine if this handshake requires a remote static key.
     */
    fun needsRemotePublicKey(): Boolean = remotePublicKeyState?.let { !it.hasPublicKey() } ?: false

    /**
     * Determine if this handshake has already been configured
     * with a remote static key.
     */
    fun hasRemotePublicKey(): Boolean = remotePublicKeyState?.hasPublicKey() ?: false

    /**
     * Gets the DHState object containing a fixed local ephemeral
     * key value for this handshake.
     *
     * This function is intended for testing only.
     */
    fun getFixedEphemeralKey(): DHState? {
        fixedEphemeral?.let { return it }
        val local = localEphemeral ?: return null
        fixedEphemeral = try {
            Noise.createDH(local.dhName)
        } catch (e: NoSuchAlgorithmException) {
            // This shouldn't happen - the local ephemeral key would
            // have already been created with the same name!
            null
        }
        return fixedEphemeral
    }

    /**
     * Gets the DHState object containing a fixed local hybrid
     * key value for this handshake.
     *
     * This function is intended for testing only.
     */
    fun getFixedHybridKey(): DHState? {
        fixedHybrid?.let { return it }
        val local = localHybrid ?: return null
        fixedHybrid = try {
            Noise.createDH(local.dhName)
        } catch (e: NoSuchAlgorithmException) {
            // This shouldn't happen - the local hybrid key would
            // have already been created with the same name!
            null
        }
        return fixedHybrid
    }

    /**
     * Starts the handshake running.
     *
     * @throws IllegalStateException The handshake has already started, or one or
     * more of the required parameters has not been supplied.
     * @throws UnsupportedOperationException An attempt was made to start a
     * fallback handshake pattern without first calling fallback().
     */
    fun start() {
        if (actionValue != NO_ACTION) {
            throw IllegalStateException("Handshake has already started; cannot start again")
        }
        if ((pattern[0].toInt() and Pattern.FLAG_REMOTE_EPHEM_REQ) != 0 &&
            (requirements and FALLBACK_PREMSG) == 0
        ) {
            throw UnsupportedOperationException("Cannot start a fallback pattern")
        }

        // Check that we have satisfied all of the pattern requirements.
        if ((requirements and LOCAL_REQUIRED) != 0) {
            val local = localKeyPairState
            if (local == null || !local.hasPrivateKey()) {
                throw IllegalStateException("Local static key required")
            }
        }
        if ((requirements and REMOTE_REQUIRED) != 0) {
            val remote = remotePublicKeyState
            if (remote == null || !remote.hasPublicKey()) {
                throw IllegalStateException("Remote static key required")
            }
        }
        if ((requirements and PSK_REQUIRED) != 0) {
            if (preSharedKey == null) throw IllegalStateException("Pre-shared key required")
        }

        // Hash the prologue value.
        val p = prologue
        if (p != null) symmetric.mixHash(p, 0, p.size) else symmetric.mixHash(emptyPrologue, 0, 0)

        // Hash the pre-shared key into the chaining key and handshake hash.
        preSharedKey?.let { symmetric.mixPreSharedKey(it) }

        // Mix the pre-supplied public keys into the handshake hash.
        if (isInitiator) {
            if ((requirements and LOCAL_PREMSG) != 0) symmetric.mixPublicKey(localKeyPairState!!)
            if ((requirements and FALLBACK_PREMSG) != 0) {
                symmetric.mixPublicKey(remoteEphemeral!!)
                remoteHybrid?.let { symmetric.mixPublicKey(it) }
                if (preSharedKey != null) symmetric.mixPublicKeyIntoCK(remoteEphemeral!!)
            }
            if ((requirements and REMOTE_PREMSG) != 0) symmetric.mixPublicKey(remotePublicKeyState!!)
        } else {
            if ((requirements and REMOTE_PREMSG) != 0) symmetric.mixPublicKey(remotePublicKeyState!!)
            if ((requirements and FALLBACK_PREMSG) != 0) {
                symmetric.mixPublicKey(localEphemeral!!)
                localHybrid?.let { symmetric.mixPublicKey(it) }
                if (preSharedKey != null) symmetric.mixPublicKeyIntoCK(localEphemeral!!)
            }
            if ((requirements and LOCAL_PREMSG) != 0) symmetric.mixPublicKey(localKeyPairState!!)
        }

        // The handshake has officially started - set the first action.
        actionValue = if (isInitiator) WRITE_MESSAGE else READ_MESSAGE
    }

    /**
     * Falls back to the "XXfallback" handshake pattern.
     */
    fun fallback() {
        fallback("XXfallback")
    }

    /**
     * Falls back to another handshake pattern.
     *
     * Note that this function reverses the roles of initiator and responder.
     *
     * @throws UnsupportedOperationException The current handshake pattern
     * is not compatible with the patternName, or patternName is not a
     * fallback pattern.
     * @throws IllegalStateException The previous protocol has not started
     * or it has not reached the fallback position yet.
     */
    fun fallback(patternName: String) {
        // The original pattern must end in "K" for fallback to be possible.
        if ((requirements and FALLBACK_POSSIBLE) == 0) {
            throw UnsupportedOperationException("Previous handshake pattern does not support fallback")
        }

        // Check that "patternName" supports fallback.
        val newPattern = Pattern.lookup(patternName)
        if (newPattern == null || (newPattern[0].toInt() and Pattern.FLAG_REMOTE_EPHEM_REQ) == 0) {
            throw UnsupportedOperationException("New pattern is not a fallback pattern")
        }

        // The initiator should be waiting for a return message from the
        // responder, and the responder should have failed on the first
        // handshake message from the initiator.  We also allow the
        // responder to fallback after processing the first message
        // successfully; it decides to always fall back anyway.
        if (isInitiator) {
            if ((actionValue != FAILED && actionValue != READ_MESSAGE) ||
                !localEphemeral!!.hasPublicKey()
            ) {
                throw IllegalStateException("Initiator cannot fall back from this state")
            }
        } else {
            if ((actionValue != FAILED && actionValue != WRITE_MESSAGE) ||
                !remoteEphemeral!!.hasPublicKey()
            ) {
                throw IllegalStateException("Responder cannot fall back from this state")
            }
        }

        // Format a new protocol name for the fallback variant
        // and recreate the SymmetricState object.
        val components = symmetric.protocolName.split("_").toMutableList()
        components[1] = patternName
        val builder = StringBuilder()
        builder.append(components[0])
        for (index in 1 until components.size) {
            builder.append('_')
            builder.append(components[index])
        }
        val name = builder.toString()
        val newSymmetric = SymmetricState(name, components[3], components[4])
        symmetric.destroy()
        symmetric = newSymmetric

        // Convert the HandshakeState to the "XXfallback" pattern.
        if (isInitiator) {
            remoteEphemeral?.clearKey()
            remoteHybrid?.clearKey()
            remotePublicKeyState?.clearKey()
            isInitiator = false
        } else {
            localEphemeral?.clearKey()
            localHybrid?.clearKey()
            if ((newPattern[0].toInt() and Pattern.FLAG_REMOTE_REQUIRED) == 0) {
                remotePublicKeyState?.clearKey()
            }
            isInitiator = true
        }
        actionValue = NO_ACTION
        pattern = newPattern
        patternIndex = 1
        var flags = pattern[0]
        if (!isInitiator) {
            // Reverse the pattern flags so that the responder is "local".
            flags = Pattern.reverseFlags(flags)
        }
        requirements = computeRequirements(
            flags,
            components[0],
            if (isInitiator) INITIATOR else RESPONDER,
            true,
        )
    }

    /**
     * Gets the next action that the application should perform for
     * the handshake part of the protocol.
     */
    val action: Int
        get() = actionValue

    /**
     * Mixes the result of a Diffie-Hellman calculation into the chaining key.
     *
     * @param local Local private key object.
     * @param remote Remote public key object.
     */
    private fun mixDH(local: DHState?, remote: DHState?) {
        if (local == null || remote == null) {
            throw IllegalStateException("Pattern definition error")
        }
        val len = local.sharedKeyLength
        val shared = ByteArray(len)
        try {
            local.calculate(shared, 0, remote)
            symmetric.mixKey(shared, 0, len)
        } finally {
            Noise.destroy(shared)
        }
    }

    /**
     * Writes a message payload during the handshake.
     *
     * @return The length of the data written to the message buffer.
     *
     * @throws IllegalStateException The action is not WRITE_MESSAGE.
     * @throws IllegalArgumentException The payload is null, but
     * payloadOffset or payloadLength is non-zero.
     * @throws ShortBufferException The message buffer does not have
     * enough space for the handshake message.
     */
    fun writeMessage(
        message: ByteArray,
        messageOffset: Int,
        payload: ByteArray?,
        payloadOffset: Int,
        payloadLength: Int,
    ): Int {
        var messagePosn = messageOffset
        var success = false

        // Validate the parameters and state.
        if (actionValue != WRITE_MESSAGE) {
            throw IllegalStateException("Handshake state does not allow writing messages")
        }
        if (payload == null && (payloadOffset != 0 || payloadLength != 0)) {
            throw IllegalArgumentException("Invalid payload argument")
        }
        if (messageOffset > message.size) {
            throw ShortBufferException()
        }

        // Format the message.
        try {
            // Process tokens until the direction changes or the patten ends.
            while (true) {
                if (patternIndex >= pattern.size) {
                    // The pattern has finished, so the next action is "split".
                    actionValue = SPLIT
                    break
                }
                val token = pattern[patternIndex++]
                if (token == Pattern.FLIP_DIR) {
                    // Change directions, so this message is complete and the
                    // next action is "read message".
                    actionValue = READ_MESSAGE
                    break
                }
                val space = message.size - messagePosn
                val len: Int
                val macLen: Int
                when (token) {
                    Pattern.E -> {
                        // Generate a local ephemeral keypair and add the public
                        // key to the message.  If we are running fixed vector tests,
                        // then the ephemeral key may have already been provided.
                        val ephemeral = localEphemeral
                            ?: throw IllegalStateException("Pattern definition error")
                        val fixed = fixedEphemeral
                        if (fixed == null) ephemeral.generateKeyPair() else ephemeral.copyFrom(fixed)
                        len = ephemeral.publicKeyLength
                        if (space < len) throw ShortBufferException()
                        ephemeral.getPublicKey(message, messagePosn)
                        symmetric.mixHash(message, messagePosn, len)

                        // If the protocol is using pre-shared keys, then also mix
                        // the local ephemeral key into the chaining key.
                        if (preSharedKey != null) symmetric.mixKey(message, messagePosn, len)
                        messagePosn += len
                    }

                    Pattern.S -> {
                        // Encrypt the local static public key and add it to the message.
                        val local = localKeyPairState
                            ?: throw IllegalStateException("Pattern definition error")
                        len = local.publicKeyLength
                        macLen = symmetric.macLength
                        if (space < (len + macLen)) throw ShortBufferException()
                        local.getPublicKey(message, messagePosn)
                        messagePosn += symmetric.encryptAndHash(message, messagePosn, message, messagePosn, len)
                    }

                    Pattern.EE -> {
                        // DH operation with initiator and responder ephemeral keys.
                        mixDH(localEphemeral, remoteEphemeral)
                    }

                    Pattern.ES -> {
                        // DH operation with initiator ephemeral and responder static keys.
                        if (isInitiator) {
                            mixDH(localEphemeral, remotePublicKeyState)
                        } else {
                            mixDH(localKeyPairState, remoteEphemeral)
                        }
                    }

                    Pattern.SE -> {
                        // DH operation with initiator static and responder ephemeral keys.
                        if (isInitiator) {
                            mixDH(localKeyPairState, remoteEphemeral)
                        } else {
                            mixDH(localEphemeral, remotePublicKeyState)
                        }
                    }

                    Pattern.SS -> {
                        // DH operation with initiator and responder static keys.
                        mixDH(localKeyPairState, remotePublicKeyState)
                    }

                    Pattern.F -> {
                        // Generate a local hybrid keypair and add the public
                        // key to the message.  If we are running fixed vector tests,
                        // then a fixed hybrid key may have already been provided.
                        val hybridState = localHybrid
                            ?: throw IllegalStateException("Pattern definition error")
                        if (hybridState is DHStateHybrid) {
                            // The DH object is something like New Hope which needs to
                            // generate keys relative to the other party's public key.
                            val fixed = fixedHybrid
                            if (fixed == null) {
                                hybridState.generateKeyPair(remoteHybrid)
                            } else {
                                hybridState.copyFrom(fixed, remoteHybrid)
                            }
                        } else {
                            val fixed = fixedHybrid
                            if (fixed == null) hybridState.generateKeyPair() else hybridState.copyFrom(fixed)
                        }
                        len = hybridState.publicKeyLength
                        if (space < len) throw ShortBufferException()
                        macLen = symmetric.macLength
                        if (space < (len + macLen)) throw ShortBufferException()
                        hybridState.getPublicKey(message, messagePosn)
                        messagePosn += symmetric.encryptAndHash(message, messagePosn, message, messagePosn, len)
                    }

                    Pattern.FF -> {
                        // DH operation with initiator and responder hybrid keys.
                        mixDH(localHybrid, remoteHybrid)
                    }

                    else -> {
                        // Unknown token code.  Abort.
                        throw IllegalStateException("Unknown handshake token $token")
                    }
                }
            }

            // Add the payload to the message buffer and encrypt it.
            messagePosn += if (payload != null) {
                symmetric.encryptAndHash(payload, payloadOffset, message, messagePosn, payloadLength)
            } else {
                symmetric.encryptAndHash(message, messagePosn, message, messagePosn, 0)
            }
            success = true
        } finally {
            // If we failed, then clear any sensitive data that may have
            // already been written to the message buffer.
            if (!success) {
                // ⚠ Intervalle [de, à[, et l'amont passe `message.length -
                // messageOffset` comme borne de fin là où une longueur était
                // manifestement voulue. Reproduit tel quel : c'est du code de
                // nettoyage sur un chemin d'échec, et le corriger ferait
                // diverger le port de son oracle.
                message.fill(0, messageOffset, message.size - messageOffset)
                actionValue = FAILED
            }
        }
        return messagePosn - messageOffset
    }

    /**
     * Reads a message payload during the handshake.
     *
     * @return The length of the payload.
     *
     * @throws IllegalStateException The action is not READ_MESSAGE.
     * @throws ShortBufferException The message buffer does not have
     * sufficient bytes for a valid message or the payload buffer does
     * not have enough space for the decrypted payload.
     * @throws BadPaddingException A MAC value in the message failed to verify.
     */
    fun readMessage(
        message: ByteArray,
        messageOffset: Int,
        messageLength: Int,
        payload: ByteArray,
        payloadOffset: Int,
    ): Int {
        var messageOffset = messageOffset
        var success = false
        val messageEnd = messageOffset + messageLength

        // Validate the parameters.
        if (actionValue != READ_MESSAGE) {
            throw IllegalStateException("Handshake state does not allow reading messages")
        }
        if (messageOffset > message.size || payloadOffset > payload.size) {
            throw ShortBufferException()
        }
        if (messageLength > (message.size - messageOffset)) {
            throw ShortBufferException()
        }

        // Process the message.
        try {
            // Process tokens until the direction changes or the patten ends.
            while (true) {
                if (patternIndex >= pattern.size) {
                    // The pattern has finished, so the next action is "split".
                    actionValue = SPLIT
                    break
                }
                val token = pattern[patternIndex++]
                if (token == Pattern.FLIP_DIR) {
                    // Change directions, so this message is complete and the
                    // next action is "write message".
                    actionValue = WRITE_MESSAGE
                    break
                }
                val space = messageEnd - messageOffset
                val len: Int
                val macLen: Int
                when (token) {
                    Pattern.E -> {
                        // Save the remote ephemeral key and hash it.
                        val ephemeral = remoteEphemeral
                            ?: throw IllegalStateException("Pattern definition error")
                        len = ephemeral.publicKeyLength
                        if (space < len) throw ShortBufferException()
                        symmetric.mixHash(message, messageOffset, len)
                        ephemeral.setPublicKey(message, messageOffset)
                        if (ephemeral.isNullPublicKey()) {
                            // The remote ephemeral key is null, which means that it is
                            // not contributing anything to the security of the session
                            // and is in fact downgrading the security to "none at all"
                            // in some of the message patterns.  Reject all such keys.
                            throw BadPaddingException("Null remote public key")
                        }

                        // If the protocol is using pre-shared keys, then also mix
                        // the remote ephemeral key into the chaining key.
                        if (preSharedKey != null) symmetric.mixKey(message, messageOffset, len)
                        messageOffset += len
                    }

                    Pattern.S -> {
                        // Decrypt and read the remote static key.
                        val remote = remotePublicKeyState
                            ?: throw IllegalStateException("Pattern definition error")
                        len = remote.publicKeyLength
                        macLen = symmetric.macLength
                        if (space < (len + macLen)) throw ShortBufferException()
                        val temp = ByteArray(len)
                        try {
                            if (symmetric.decryptAndHash(message, messageOffset, temp, 0, len + macLen) != len) {
                                throw ShortBufferException()
                            }
                            remote.setPublicKey(temp, 0)
                        } finally {
                            Noise.destroy(temp)
                        }
                        messageOffset += len + macLen
                    }

                    Pattern.EE -> {
                        // DH operation with initiator and responder ephemeral keys.
                        mixDH(localEphemeral, remoteEphemeral)
                    }

                    Pattern.ES -> {
                        // DH operation with initiator ephemeral and responder static keys.
                        if (isInitiator) {
                            mixDH(localEphemeral, remotePublicKeyState)
                        } else {
                            mixDH(localKeyPairState, remoteEphemeral)
                        }
                    }

                    Pattern.SE -> {
                        // DH operation with initiator static and responder ephemeral keys.
                        if (isInitiator) {
                            mixDH(localKeyPairState, remoteEphemeral)
                        } else {
                            mixDH(localEphemeral, remotePublicKeyState)
                        }
                    }

                    Pattern.SS -> {
                        // DH operation with initiator and responder static keys.
                        mixDH(localKeyPairState, remotePublicKeyState)
                    }

                    Pattern.F -> {
                        // Decrypt and read the remote hybrid ephemeral key.
                        val remoteHyb = remoteHybrid
                            ?: throw IllegalStateException("Pattern definition error")
                        if (remoteHyb is DHStateHybrid) {
                            // The DH object is something like New Hope.  The public key
                            // length may need to change based on whether we already have
                            // generated a local hybrid keypair or not.
                            remoteHyb.specifyPeer(localHybrid)
                        }
                        len = remoteHyb.publicKeyLength
                        macLen = symmetric.macLength
                        if (space < (len + macLen)) throw ShortBufferException()
                        val temp = ByteArray(len)
                        try {
                            if (symmetric.decryptAndHash(message, messageOffset, temp, 0, len + macLen) != len) {
                                throw ShortBufferException()
                            }
                            remoteHyb.setPublicKey(temp, 0)
                        } finally {
                            Noise.destroy(temp)
                        }
                        messageOffset += len + macLen
                    }

                    Pattern.FF -> {
                        // DH operation with initiator and responder hybrid keys.
                        mixDH(localHybrid, remoteHybrid)
                    }

                    else -> {
                        // Unknown token code.  Abort.
                        throw IllegalStateException("Unknown handshake token $token")
                    }
                }
            }

            // Decrypt the message payload.
            val payloadLength = symmetric.decryptAndHash(
                message,
                messageOffset,
                payload,
                payloadOffset,
                messageEnd - messageOffset,
            )
            success = true
            return payloadLength
        } finally {
            // If we failed, then clear any sensitive data that may have
            // already been written to the payload buffer.
            if (!success) {
                // ⚠ Même intervalle douteux que dans writeMessage, et reproduit
                // pour la même raison.
                payload.fill(0, payloadOffset, payload.size - payloadOffset)
                actionValue = FAILED
            }
        }
    }

    /**
     * Splits the transport encryption CipherState objects out of
     * this HandshakeState object once the handshake completes.
     *
     * @throws IllegalStateException The action is not SPLIT.
     */
    fun split(): CipherStatePair {
        if (actionValue != SPLIT) {
            throw IllegalStateException("Handshake has not finished")
        }
        val pair = symmetric.split()
        if (!isInitiator) pair.swap()
        actionValue = COMPLETE
        return pair
    }

    /**
     * Splits the transport encryption CipherState objects out of
     * this HandshakeObject after mixing in a secondary symmetric key.
     *
     * @throws IllegalStateException The action is not SPLIT.
     * @throws IllegalArgumentException The length is not 0 or 32.
     */
    fun split(secondaryKey: ByteArray, offset: Int, length: Int): CipherStatePair {
        if (actionValue != SPLIT) {
            throw IllegalStateException("Handshake has not finished")
        }
        val pair = symmetric.split(secondaryKey, offset, length)
        if (!isInitiator) {
            // Swap the sender and receiver objects for the responder
            // to make it easier on the application to know which is which.
            pair.swap()
        }
        actionValue = COMPLETE
        return pair
    }

    /**
     * Gets the current value of the handshake hash.
     *
     * @throws IllegalStateException The action is not SPLIT or COMPLETE.
     */
    fun getHandshakeHash(): ByteArray {
        if (actionValue != SPLIT && actionValue != COMPLETE) {
            throw IllegalStateException("Handshake has not completed")
        }
        return symmetric.handshakeHash
    }

    override fun destroy() {
        symmetric.destroy()
        localKeyPairState?.destroy()
        localEphemeral?.destroy()
        localHybrid?.destroy()
        remotePublicKeyState?.destroy()
        remoteEphemeral?.destroy()
        remoteHybrid?.destroy()
        fixedEphemeral?.destroy()
        fixedHybrid?.destroy()
        preSharedKey?.let { Noise.destroy(it) }
        prologue?.let { Noise.destroy(it) }
    }

    companion object {
        /**
         * Enumerated value that indicates that the handshake object
         * is handling the initiator role.
         */
        const val INITIATOR = 1

        /**
         * Enumerated value that indicates that the handshake object
         * is handling the responder role.
         */
        const val RESPONDER = 2

        /**
         * No action is required of the application yet because the
         * handshake has not started.
         */
        const val NO_ACTION = 0

        /**
         * The HandshakeState expects the application to write the
         * next message payload for the handshake.
         */
        const val WRITE_MESSAGE = 1

        /**
         * The HandshakeState expects the application to read the
         * next message payload from the handshake.
         */
        const val READ_MESSAGE = 2

        /**
         * The handshake has failed due to some kind of error.
         */
        const val FAILED = 3

        /**
         * The handshake is over and the application is expected to call
         * split() and begin data session communications.
         */
        const val SPLIT = 4

        /**
         * The handshake is complete and the data session ciphers
         * have been split() out successfully.
         */
        const val COMPLETE = 5

        /** Local static keypair is required for the handshake. */
        private const val LOCAL_REQUIRED = 0x01

        /** Remote static keypair is required for the handshake. */
        private const val REMOTE_REQUIRED = 0x02

        /** Pre-shared key is required for the handshake. */
        private const val PSK_REQUIRED = 0x04

        /** Ephemeral key for fallback pre-message has been provided. */
        private const val FALLBACK_PREMSG = 0x08

        /** The local public key is part of the pre-message. */
        private const val LOCAL_PREMSG = 0x10

        /** The remote public key is part of the pre-message. */
        private const val REMOTE_PREMSG = 0x20

        /** Fallback is possible from this pattern (two-way, ends in "K"). */
        private const val FALLBACK_POSSIBLE = 0x40

        // Empty value for when the prologue is not supplied.
        private val emptyPrologue = ByteArray(0)

        /**
         * Computes the requirements for a handshake.
         *
         * @param flags The flags from the handshake's pattern.
         * @param prefix The prefix from the protocol name; typically
         * "Noise" or "NoisePSK".
         * @param role The role, INITIATOR or RESPONDER.
         * @param isFallback Set to true if we need the requirements for a
         * fallback pattern; false for a regular pattern.
         *
         * @return The set of requirements for the handshake.
         */
        private fun computeRequirements(
            flags: Short,
            prefix: String,
            role: Int,
            isFallback: Boolean,
        ): Int {
            var requirements = 0
            val f = flags.toInt()
            if ((f and Pattern.FLAG_LOCAL_STATIC) != 0) {
                requirements = requirements or LOCAL_REQUIRED
            }
            if ((f and Pattern.FLAG_LOCAL_REQUIRED) != 0) {
                requirements = requirements or LOCAL_REQUIRED
                requirements = requirements or LOCAL_PREMSG
            }
            if ((f and Pattern.FLAG_REMOTE_REQUIRED) != 0) {
                requirements = requirements or REMOTE_REQUIRED
                requirements = requirements or REMOTE_PREMSG
            }
            if ((f and (Pattern.FLAG_REMOTE_EPHEM_REQ or Pattern.FLAG_LOCAL_EPHEM_REQ)) != 0) {
                if (isFallback) requirements = requirements or FALLBACK_PREMSG
            }
            if (prefix == "NoisePSK") {
                requirements = requirements or PSK_REQUIRED
            }
            return requirements
        }
    }
}
