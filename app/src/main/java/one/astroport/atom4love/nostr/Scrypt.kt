package one.astroport.atom4love.nostr

/**
 * scrypt (RFC 7914), et le PBKDF2-HMAC-SHA256 dont il se sert.
 *
 * Écrit ici parce qu'aucune plateforme ne le donne : ni `javax.crypto` ni le
 * fournisseur Android n'exposent scrypt, et tirer BouncyCastle en entier
 * (~7 Mo) pour une fonction de 130 lignes serait payer cher un dérivé de
 * Salsa20. Les vecteurs de la RFC le vérifient (`ScryptTest`), et c'est la
 * seule garantie qui vaille pour un code de ce genre.
 *
 * Il sert à une chose : refaire ce que la station fait avec `keygen`, donc
 * retrouver la clé LOVE sans elle. Voir [LoveKeyForge].
 *
 * ⚠ Coûteux **par construction** — c'est le but d'une fonction de dérivation.
 * Aux paramètres de Duniter (N=4096, r=16) elle réclame 8 Mo et quelques
 * centaines de millisecondes : jamais sur le fil principal.
 */
object Scrypt {

    /**
     * @param n coût CPU/mémoire, une puissance de deux
     * @param r taille de bloc
     * @param p parallélisme (exécuté en série ici : `p` vaut 1 chez Duniter)
     */
    fun generate(
        password: ByteArray,
        salt: ByteArray,
        n: Int,
        r: Int,
        p: Int,
        dkLen: Int,
    ): ByteArray {
        require(n > 1 && (n and (n - 1)) == 0) { "N doit être une puissance de deux" }
        require(r > 0 && p > 0 && dkLen > 0) { "paramètres scrypt invalides" }

        val blockLen = 128 * r
        val b = pbkdf2(password, salt, 1, p * blockLen)
        val v = IntArray(n * 32 * r)
        val xy = IntArray(64 * r)

        for (i in 0 until p) {
            romix(b, i * blockLen, r, n, v, xy)
        }
        return pbkdf2(password, b, 1, dkLen)
    }

    private const val HASH_LEN = 32
    private const val HMAC_BLOCK = 64

    /**
     * PBKDF2-HMAC-SHA256. Écrit à la main plutôt que par
     * `SecretKeyFactory("PBKDF2WithHmacSHA256")` : celui-là ne prend qu'un
     * `char[]` et le ré-encode, là où scrypt doit passer des octets quelconques.
     */
    fun pbkdf2(password: ByteArray, salt: ByteArray, iterations: Int, dkLen: Int): ByteArray {
        val hmac = HmacSha256(password)
        val out = ByteArray(dkLen)
        val block = ByteArray(salt.size + 4)
        salt.copyInto(block)
        var offset = 0
        var counter = 1
        while (offset < dkLen) {
            block[salt.size] = (counter ushr 24).toByte()
            block[salt.size + 1] = (counter ushr 16).toByte()
            block[salt.size + 2] = (counter ushr 8).toByte()
            block[salt.size + 3] = counter.toByte()
            var u = hmac.of(block)
            val t = u.copyOf()
            for (round in 1 until iterations) {
                u = hmac.of(u)
                for (k in t.indices) t[k] = (t[k].toInt() xor u[k].toInt()).toByte()
            }
            val take = minOf(HASH_LEN, dkLen - offset)
            t.copyInto(out, offset, 0, take)
            offset += take
            counter++
        }
        return out
    }

    /**
     * HMAC-SHA256, monté sur `MessageDigest` plutôt que sur `javax.crypto.Mac`.
     *
     * `Mac` passe par `SecretKeySpec`, qui refuse une clé vide — le premier
     * vecteur de la RFC 7914 en a une, et un algorithme qu'on ne peut pas
     * vérifier sur son vecteur le plus simple ne se vérifie pas du tout. Les
     * deux moitiés de clé sont préparées une fois pour toutes : PBKDF2 rappelle
     * cette fonction 600 000 fois de suite.
     */
    private class HmacSha256(key: ByteArray) {
        private val digest = java.security.MessageDigest.getInstance("SHA-256")
        private val innerKey = ByteArray(HMAC_BLOCK)
        private val outerKey = ByteArray(HMAC_BLOCK)

        init {
            val normalized = if (key.size > HMAC_BLOCK) digest.digest(key) else key
            normalized.copyInto(innerKey)
            normalized.copyInto(outerKey)
            for (i in 0 until HMAC_BLOCK) {
                innerKey[i] = (innerKey[i].toInt() xor 0x36).toByte()
                outerKey[i] = (outerKey[i].toInt() xor 0x5c).toByte()
            }
        }

        fun of(message: ByteArray): ByteArray {
            digest.reset()
            digest.update(innerKey)
            val inner = digest.digest(message)
            digest.reset()
            digest.update(outerKey)
            return digest.digest(inner)
        }
    }

    /** ROMix : N passages aller, puis N passages de va-et-vient pseudo-aléatoire. */
    private fun romix(b: ByteArray, bOff: Int, r: Int, n: Int, v: IntArray, xy: IntArray) {
        val words = 32 * r
        for (i in 0 until words) {
            xy[i] = littleEndianInt(b, bOff + i * 4)
        }
        for (i in 0 until n) {
            xy.copyInto(v, i * words, 0, words)
            blockMix(xy, r)
        }
        for (i in 0 until n) {
            // Integerify : le dernier bloc de 64 octets donne l'indice visité.
            val j = xy[words - 16] and (n - 1)
            for (k in 0 until words) xy[k] = xy[k] xor v[j * words + k]
            blockMix(xy, r)
        }
        for (i in 0 until words) {
            putLittleEndianInt(b, bOff + i * 4, xy[i])
        }
    }

    /**
     * BlockMix : Salsa20/8 en chaîne sur les 2r sous-blocs, puis les pairs
     * d'abord et les impairs ensuite — c'est ce ré-entrelacement qui donne à
     * scrypt sa dépendance mémoire.
     */
    private fun blockMix(xy: IntArray, r: Int) {
        val x = IntArray(16)
        val out = IntArray(32 * r)
        xy.copyInto(x, 0, (2 * r - 1) * 16, 2 * r * 16)
        for (i in 0 until 2 * r) {
            for (k in 0 until 16) x[k] = x[k] xor xy[i * 16 + k]
            salsa20Core8(x)
            val destination = if (i % 2 == 0) (i / 2) * 16 else (r + i / 2) * 16
            x.copyInto(out, destination, 0, 16)
        }
        out.copyInto(xy, 0, 0, 32 * r)
    }

    /** Le cœur Salsa20, huit tours — quatre doubles-tours. */
    private fun salsa20Core8(block: IntArray) {
        val x = block.copyOf()
        repeat(4) {
            // colonnes
            x[4] = x[4] xor rotl(x[0] + x[12], 7);  x[8] = x[8] xor rotl(x[4] + x[0], 9)
            x[12] = x[12] xor rotl(x[8] + x[4], 13); x[0] = x[0] xor rotl(x[12] + x[8], 18)
            x[9] = x[9] xor rotl(x[5] + x[1], 7);   x[13] = x[13] xor rotl(x[9] + x[5], 9)
            x[1] = x[1] xor rotl(x[13] + x[9], 13); x[5] = x[5] xor rotl(x[1] + x[13], 18)
            x[14] = x[14] xor rotl(x[10] + x[6], 7); x[2] = x[2] xor rotl(x[14] + x[10], 9)
            x[6] = x[6] xor rotl(x[2] + x[14], 13); x[10] = x[10] xor rotl(x[6] + x[2], 18)
            x[3] = x[3] xor rotl(x[15] + x[11], 7); x[7] = x[7] xor rotl(x[3] + x[15], 9)
            x[11] = x[11] xor rotl(x[7] + x[3], 13); x[15] = x[15] xor rotl(x[11] + x[7], 18)
            // lignes
            x[1] = x[1] xor rotl(x[0] + x[3], 7);   x[2] = x[2] xor rotl(x[1] + x[0], 9)
            x[3] = x[3] xor rotl(x[2] + x[1], 13);  x[0] = x[0] xor rotl(x[3] + x[2], 18)
            x[6] = x[6] xor rotl(x[5] + x[4], 7);   x[7] = x[7] xor rotl(x[6] + x[5], 9)
            x[4] = x[4] xor rotl(x[7] + x[6], 13);  x[5] = x[5] xor rotl(x[4] + x[7], 18)
            x[11] = x[11] xor rotl(x[10] + x[9], 7); x[8] = x[8] xor rotl(x[11] + x[10], 9)
            x[9] = x[9] xor rotl(x[8] + x[11], 13); x[10] = x[10] xor rotl(x[9] + x[8], 18)
            x[12] = x[12] xor rotl(x[15] + x[14], 7); x[13] = x[13] xor rotl(x[12] + x[15], 9)
            x[14] = x[14] xor rotl(x[13] + x[12], 13); x[15] = x[15] xor rotl(x[14] + x[13], 18)
        }
        for (i in 0 until 16) block[i] += x[i]
    }

    private fun rotl(value: Int, bits: Int): Int = (value shl bits) or (value ushr (32 - bits))

    private fun littleEndianInt(b: ByteArray, offset: Int): Int =
        (b[offset].toInt() and 0xff) or
            ((b[offset + 1].toInt() and 0xff) shl 8) or
            ((b[offset + 2].toInt() and 0xff) shl 16) or
            ((b[offset + 3].toInt() and 0xff) shl 24)

    private fun putLittleEndianInt(b: ByteArray, offset: Int, value: Int) {
        b[offset] = value.toByte()
        b[offset + 1] = (value ushr 8).toByte()
        b[offset + 2] = (value ushr 16).toByte()
        b[offset + 3] = (value ushr 24).toByte()
    }
}
