package at.techbee.gplaypromocodeprovider.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class ServerConfigTest {

    @Test
    fun readsEnvironmentWithDefaults() {
        val config = ServerConfig.fromEnvironment(mapOf("ADMIN_USERNAME" to "admin", "ADMIN_PASSWORD" to "pw"))

        assertEquals(ServerConfig(8080, "data/gplaypromocodeprovider.db", "admin", "pw", cookieSecure = true), config)
    }

    @Test
    fun readsAllVariables() {
        val config = ServerConfig.fromEnvironment(
            mapOf(
                "PORT" to "9000",
                "DATABASE_PATH" to "/data/codes.db",
                "ADMIN_USERNAME" to "admin",
                "ADMIN_PASSWORD" to "pw",
                "COOKIE_SECURE" to "false",
            ),
        )

        assertEquals(ServerConfig(9000, "/data/codes.db", "admin", "pw", cookieSecure = false), config)
    }

    @Test
    fun requiresAdminCredentials() {
        assertFailsWith<IllegalStateException> { ServerConfig.fromEnvironment(mapOf("ADMIN_USERNAME" to "admin")) }
        assertFailsWith<IllegalStateException> {
            ServerConfig.fromEnvironment(mapOf("ADMIN_USERNAME" to " ", "ADMIN_PASSWORD" to "pw"))
        }
    }

    @Test
    fun toStringHidesThePassword() {
        val config = ServerConfig.fromEnvironment(mapOf("ADMIN_USERNAME" to "admin", "ADMIN_PASSWORD" to "s3cret"))
        assertFalse("s3cret" in config.toString())
    }
}
