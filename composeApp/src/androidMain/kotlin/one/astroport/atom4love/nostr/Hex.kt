package one.astroport.atom4love.nostr

/** Hex minuscule, la représentation native des identifiants NOSTR. */
internal object Hex {
    fun encode(bytes: ByteArray): String = buildString(bytes.size * 2) {
        bytes.forEach { append("%02x".format(it)) }
    }

    fun decode(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "longueur hex impaire" }
        return ByteArray(hex.length / 2) {
            hex.substring(it * 2, it * 2 + 2).toInt(16).toByte()
        }
    }
}
