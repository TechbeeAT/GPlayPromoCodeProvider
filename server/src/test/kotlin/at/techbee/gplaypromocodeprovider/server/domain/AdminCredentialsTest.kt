package at.techbee.gplaypromocodeprovider.server.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdminCredentialsTest {
    private val credentials = AdminCredentials("admin", "s3cret-password")

    @Test
    fun acceptsOnlyTheConfiguredCredentials() {
        assertTrue(credentials.matches("admin", "s3cret-password"))
        assertFalse(credentials.matches("admin", "wrong"))
        assertFalse(credentials.matches("other", "s3cret-password"))
        assertFalse(credentials.matches("", ""))
        assertFalse(credentials.matches("admin", "s3cret-password "))
    }

    @Test
    fun toStringHidesThePassword() {
        assertFalse("s3cret-password" in credentials.toString())
    }
}
