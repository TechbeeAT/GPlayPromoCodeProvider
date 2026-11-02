package at.techbee.gplaypromocodeprovider.server.domain

import java.security.SecureRandom
import java.util.Base64

object InviteTokens {
    private val random = SecureRandom()

    /** A new random invite token: 32 random bytes, URL-safe Base64 (43 characters). */
    fun generate(): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
