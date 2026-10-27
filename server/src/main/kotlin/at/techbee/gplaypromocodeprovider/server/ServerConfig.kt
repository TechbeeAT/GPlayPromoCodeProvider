package at.techbee.gplaypromocodeprovider.server

/**
 * Server configuration, read from environment variables.
 *
 * | Variable         | Default                         | Meaning                                       |
 * |------------------|---------------------------------|-----------------------------------------------|
 * | `PORT`           | `8080`                          | HTTP port                                     |
 * | `DATABASE_PATH`  | `data/gplaypromocodeprovider.db`| Path of the SQLite file (created if missing)  |
 * | `ADMIN_USERNAME` | *(required)*                    | Admin login name                              |
 * | `ADMIN_PASSWORD` | *(required)*                    | Admin password                                |
 * | `COOKIE_SECURE`  | `true`                          | Send the session cookie over HTTPS only       |
 */
data class ServerConfig(
    val port: Int,
    val databasePath: String,
    val adminUsername: String,
    val adminPassword: String,
    val cookieSecure: Boolean,
) {
    // Keep the password out of logs and exception messages.
    override fun toString() =
        "ServerConfig(port=$port, databasePath=$databasePath, adminUsername=$adminUsername, cookieSecure=$cookieSecure)"

    companion object {
        fun fromEnvironment(env: Map<String, String> = System.getenv()): ServerConfig {
            fun required(name: String) = env[name]?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Environment variable $name must be set")

            return ServerConfig(
                port = env["PORT"]?.toIntOrNull() ?: 8080,
                databasePath = env["DATABASE_PATH"]?.takeIf { it.isNotBlank() } ?: "data/gplaypromocodeprovider.db",
                adminUsername = required("ADMIN_USERNAME"),
                adminPassword = required("ADMIN_PASSWORD"),
                cookieSecure = env["COOKIE_SECURE"]?.toBooleanStrictOrNull() ?: true,
            )
        }
    }
}
