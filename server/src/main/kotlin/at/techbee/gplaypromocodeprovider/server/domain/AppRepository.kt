package at.techbee.gplaypromocodeprovider.server.domain

import at.techbee.gplaypromocodeprovider.server.db.Apps
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock

class AppRepository(private val db: Database, private val clock: Clock = Clock.System) {

    fun create(input: AppInput): App = transaction(db) {
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
    fun update(id: Long, input: AppInput): App? = transaction(db) {
        val updated = Apps.update({ Apps.id eq id }) {
            it[name] = input.name
            it[packageName] = input.packageName
            it[description] = input.description
        }
        if (updated == 0) null else find(id)
    }

    /** Replaces the invite token, which revokes the old invite link. Null if there is no such app. */
    fun regenerateInviteToken(id: Long): App? = transaction(db) {
        val updated = Apps.update({ Apps.id eq id }) { it[inviteToken] = InviteTokens.generate() }
        if (updated == 0) null else find(id)
    }

    fun find(id: Long): App? = transaction(db) {
        Apps.selectAll().where { Apps.id eq id }.singleOrNull()?.toApp()
    }

    fun findByInviteToken(token: String): App? = transaction(db) {
        Apps.selectAll().where { Apps.inviteToken eq token }.singleOrNull()?.toApp()
    }

    fun list(): List<App> = transaction(db) {
        Apps.selectAll().orderBy(Apps.name).map { it.toApp() }
    }

    private fun ResultRow.toApp() = App(
        id = this[Apps.id],
        name = this[Apps.name],
        packageName = this[Apps.packageName],
        description = this[Apps.description],
        inviteToken = this[Apps.inviteToken],
    )
}
