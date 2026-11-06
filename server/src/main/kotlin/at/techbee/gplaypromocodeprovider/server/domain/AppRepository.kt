package at.techbee.gplaypromocodeprovider.server.domain

import at.techbee.gplaypromocodeprovider.server.db.Apps
import at.techbee.gplaypromocodeprovider.server.db.query
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock

class AppRepository(private val db: Database, private val clock: Clock = Clock.System) {

    suspend fun create(input: AppInput): App = db.query {
        val token = InviteTokens.generate()
        val id = Apps.insert {
            it[name] = input.name
            it[packageName] = input.packageName
            it[description] = input.description
            it[inviteToken] = token
            it[createdAt] = clock.now().toEpochMilliseconds()
        }[Apps.id]
        App(id, input.name, input.packageName, input.description, token)
    }

    /** Returns the updated app, or null if there is no app with this id. */
    suspend fun update(id: Long, input: AppInput): App? = db.query {
        val updated = Apps.update({ Apps.id eq id }) {
            it[name] = input.name
            it[packageName] = input.packageName
            it[description] = input.description
        }
        if (updated == 0) null else findById(id)
    }

    /** Replaces the invite token, which revokes the old invite link. Null if there is no such app. */
    suspend fun regenerateInviteToken(id: Long): App? = db.query {
        val updated = Apps.update({ Apps.id eq id }) { it[inviteToken] = InviteTokens.generate() }
        if (updated == 0) null else findById(id)
    }

    suspend fun find(id: Long): App? = db.query { findById(id) }

    suspend fun findByInviteToken(token: String): App? = db.query {
        Apps.selectAll().where { Apps.inviteToken eq token }.singleOrNull()?.toApp()
    }

    suspend fun list(): List<App> = db.query {
        Apps.selectAll().orderBy(Apps.name).map { it.toApp() }
    }

    /** Within a transaction. */
    private fun findById(id: Long): App? = Apps.selectAll().where { Apps.id eq id }.singleOrNull()?.toApp()

    private fun ResultRow.toApp() = App(
        id = this[Apps.id],
        name = this[Apps.name],
        packageName = this[Apps.packageName],
        description = this[Apps.description],
        inviteToken = this[Apps.inviteToken],
    )
}
