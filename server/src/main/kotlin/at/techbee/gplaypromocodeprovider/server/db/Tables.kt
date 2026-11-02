package at.techbee.gplaypromocodeprovider.server.db

import org.jetbrains.exposed.v1.core.Table

// Exposed mappings of the tables created in [Migrations]. Keep both in sync.

object Apps : Table("app") {
    val id = long("id").autoIncrement()
    val name = text("name")
    val packageName = text("package_name").nullable()
    val description = text("description").nullable()
    val inviteToken = text("invite_token")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object CodeBatches : Table("code_batch") {
    val id = long("id").autoIncrement()
    val appId = long("app_id").references(Apps.id)
    val description = text("description").nullable()
    val expiresAt = long("expires_at")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object PromoCodes : Table("promo_code") {
    val id = long("id").autoIncrement()
    val appId = long("app_id").references(Apps.id)
    val batchId = long("batch_id").references(CodeBatches.id)
    val code = text("code")
    val consumedAt = long("consumed_at").nullable()
    override val primaryKey = PrimaryKey(id)
}
