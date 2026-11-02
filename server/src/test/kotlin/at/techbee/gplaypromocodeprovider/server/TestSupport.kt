package at.techbee.gplaypromocodeprovider.server

import at.techbee.gplaypromocodeprovider.server.db.DatabaseFactory
import org.jetbrains.exposed.v1.jdbc.Database
import java.nio.file.Files
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/** A clock the test controls. */
class TestClock(var now: Instant = Instant.parse("2026-01-01T12:00:00Z")) : Clock {
    override fun now() = now
    fun advance(duration: Duration) {
        now += duration
    }
}

/** A fresh SQLite database in a temporary file. A file (not `:memory:`) so that parallel connections share it. */
fun createTestDatabase(): Database {
    val dir = Files.createTempDirectory("gplay-test").toFile().apply { deleteOnExit() }
    return DatabaseFactory.connect(dir.resolve("test.db").path)
}
