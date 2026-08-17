package one.astroport.atom4love.nostr

/**
 * Bech32 (BIP-173), le strict nécessaire pour NIP-19 : encoder/décoder les
 * identifiants `npub` (clé publique) et `nsec` (clé privée). NOSTR utilise le
 * bech32 d'origine, pas la variante bech32m.
 */
object Bech32 {

    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private val GENERATOR = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)

    /** Encode [data] (octets bruts, 8 bits) sous `hrp1…` avec checksum bech32. */
    fun encode(hrp: String, data: ByteArray): String {
        val five = convertBits(data, from = 8, to = 5, pad = true)
        val checksum = createChecksum(hrp, five)
        val sb = StringBuilder(hrp).append('1')
        (five + checksum).forEach { sb.append(CHARSET[it]) }
        return sb.toString()
    }

    /**
     * Décode `hrp1…` et rend (hrp, octets bruts).
     * @throws IllegalArgumentException si le format ou le checksum est invalide.
     */
    fun decode(bech: String): Pair<String, ByteArray> {
        val lower = bech.lowercase()
        require(bech == lower || bech == bech.uppercase()) { "casse mélangée" }
        val sep = lower.lastIndexOf('1')
        require(sep >= 1 && sep + 7 <= lower.length) { "séparateur invalide" }
        val hrp = lower.substring(0, sep)
        val data = lower.substring(sep + 1).map {
            val v = CHARSET.indexOf(it)
            require(v >= 0) { "caractère invalide : $it" }
            v
        }.toIntArray()
        require(verifyChecksum(hrp, data)) { "checksum invalide" }
        val payload = data.copyOfRange(0, data.size - 6)
        return hrp to convertBits5to8(payload)
    }

    private fun polymod(values: IntArray): Int {
        var chk = 1
        for (v in values) {
            val top = chk shr 25
            chk = ((chk and 0x1ffffff) shl 5) xor v
            for (i in 0..4) {
                if ((top shr i) and 1 == 1) chk = chk xor GENERATOR[i]
            }
        }
        return chk
    }

    private fun hrpExpand(hrp: String): IntArray =
        (hrp.map { it.code shr 5 } + 0 + hrp.map { it.code and 31 }).toIntArray()

    private fun createChecksum(hrp: String, data: IntArray): IntArray {
        val values = hrpExpand(hrp) + data + IntArray(6)
        val mod = polymod(values) xor 1
        return IntArray(6) { (mod shr (5 * (5 - it))) and 31 }
    }

    private fun verifyChecksum(hrp: String, data: IntArray): Boolean =
        polymod(hrpExpand(hrp) + data) == 1

    private fun convertBits(data: ByteArray, from: Int, to: Int, pad: Boolean): IntArray {
        var acc = 0
        var bits = 0
        val out = mutableListOf<Int>()
        val maxV = (1 shl to) - 1
        for (b in data) {
            acc = (acc shl from) or (b.toInt() and 0xff)
            bits += from
            while (bits >= to) {
                bits -= to
                out += (acc shr bits) and maxV
            }
        }
        if (pad && bits > 0) out += (acc shl (to - bits)) and maxV
        return out.toIntArray()
    }

    private fun convertBits5to8(data: IntArray): ByteArray {
        var acc = 0
        var bits = 0
        val out = mutableListOf<Byte>()
        for (v in data) {
            acc = (acc shl 5) or v
            bits += 5
            while (bits >= 8) {
                bits -= 8
                out += ((acc shr bits) and 0xff).toByte()
            }
        }
        require(bits < 5 && (acc shl (8 - bits)) and 0xff == 0 || bits < 5) { "padding invalide" }
        return out.toByteArray()
    }
}
