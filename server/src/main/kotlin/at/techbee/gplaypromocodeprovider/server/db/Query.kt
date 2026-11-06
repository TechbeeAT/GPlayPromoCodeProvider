package at.techbee.gplaypromocodeprovider.server.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Runs [statement] in a transaction on [Dispatchers.IO]. JDBC blocks, possibly for seconds (a large
 * upload, or waiting for SQLite's write lock), and must not hold up the server's request threads:
 * a small machine has only one or two, which also answer the health check and serve the web app.
 * Use this for every database access from request handling.
 */
suspend fun <T> Database.query(statement: JdbcTransaction.() -> T): T =
    withContext(Dispatchers.IO) { transaction(db = this@query, statement = statement) }
