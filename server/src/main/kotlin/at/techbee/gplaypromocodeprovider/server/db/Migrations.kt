package at.techbee.gplaypromocodeprovider.server.db

import java.sql.Connection

/**
 * Schema migrations. Each entry is applied once, in order; SQLite's `PRAGMA user_version`
 * stores how many have been applied. Never edit a migration that has been released,
 * append a new one instead.
 *
 * Statements are split at `;`, so don't use `;` inside comments or string literals.
 *
 * Timestamps are stored as INTEGER milliseconds since the epoch (UTC).
 */
object Migrations {
    val all: List<String> = listOf(
        // 1: initial schema
        """
        CREATE TABLE app (
            id           INTEGER PRIMARY KEY AUTOINCREMENT,
            name         TEXT    NOT NULL,
            package_name TEXT,
            description  TEXT,
            invite_token TEXT    NOT NULL UNIQUE,
            created_at   INTEGER NOT NULL
        );
        CREATE TABLE code_batch (
            id          INTEGER PRIMARY KEY AUTOINCREMENT,
            app_id      INTEGER NOT NULL REFERENCES app (id),
            description TEXT,
            expires_at  INTEGER NOT NULL,
            created_at  INTEGER NOT NULL
        );
        CREATE INDEX code_batch_app_id ON code_batch (app_id);
        -- app_id is duplicated from the batch so that codes can be unique per app.
        -- consumed_at is NULL while the code is available.
        CREATE TABLE promo_code (
            id          INTEGER PRIMARY KEY AUTOINCREMENT,
            app_id      INTEGER NOT NULL REFERENCES app (id),
            batch_id    INTEGER NOT NULL REFERENCES code_batch (id),
            code        TEXT    NOT NULL,
            consumed_at INTEGER,
            UNIQUE (app_id, code)
        );
        CREATE INDEX promo_code_batch_id_consumed_at ON promo_code (batch_id, consumed_at);
        """,
    )

    /** Applies all pending migrations, each in its own transaction. */
    fun migrate(connection: Connection) {
        val current = connection.createStatement().use { stmt ->
            stmt.executeQuery("PRAGMA user_version").use { it.next(); it.getInt(1) }
        }
        check(current <= all.size) { "Database schema version $current is newer than this server (${all.size})" }
        val autoCommit = connection.autoCommit
        connection.autoCommit = false
        try {
            for (version in current + 1..all.size) {
                connection.createStatement().use { stmt ->
                    all[version - 1].split(';').map { it.trim() }.filter { it.isNotEmpty() }.forEach(stmt::execute)
                    stmt.execute("PRAGMA user_version = $version")
                }
                connection.commit()
            }
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = autoCommit
        }
    }
}
