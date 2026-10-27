package at.techbee.gplaypromocodeprovider.server

import at.techbee.gplaypromocodeprovider.server.db.DatabaseFactory
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    val config = ServerConfig.fromEnvironment()
    val db = DatabaseFactory.connect(config.databasePath)
    embeddedServer(Netty, port = config.port) {
        module(config, db)
    }.start(wait = true)
}
