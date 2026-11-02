package at.techbee.gplaypromocodeprovider.server.db

import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.Database
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteDataSource
import java.io.File
import java.sql.Connection

object DatabaseFactory {
    /** Opens (and creates, if needed) the SQLite database at [path] and brings its schema up to date. */
    fun connect(path: String): Database {
        File(path).absoluteFile.parentFile?.mkdirs()
        val config = SQLiteConfig().apply {
            enforceForeignKeys(true)
            setJournalMode(SQLiteConfig.JournalMode.WAL)
            // Concurrent writers wait for the lock instead of failing right away.
            setBusyTimeout(10_000)
            // BEGIN IMMEDIATE: a transaction takes the write lock when it starts. SQLite allows only
            // one writer anyway, and this avoids deadlocks when a transaction upgrades from read to write.
            setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE)
        }
        val dataSource = SQLiteDataSource(config).apply { url = "jdbc:sqlite:$path" }
        dataSource.connection.use(Migrations::migrate)
        return Database.connect(
            datasource = dataSource,
            databaseConfig = DatabaseConfig {
                // SQLite only supports SERIALIZABLE (and READ UNCOMMITTED).
                defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
            },
        )
    }
}
