package at.techbee.gplaypromocodeprovider.server.domain

import java.security.MessageDigest

/** The single admin account, configured through environment variables. */
class AdminCredentials(private val username: String, private val password: String) {

    /** Compares in constant time, so response times don't reveal how much of the input was right. */
    fun matches(username: String, password: String): Boolean {
        val usernameMatches = MessageDigest.isEqual(sha256(username), sha256(this.username))
        val passwordMatches = MessageDigest.isEqual(sha256(password), sha256(this.password))
        return usernameMatches and passwordMatches
    }

    override fun toString() = "AdminCredentials(username=$username)"

    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
}
