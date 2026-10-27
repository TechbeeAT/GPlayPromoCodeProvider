package at.techbee.gplaypromocodeprovider.server.domain

import at.techbee.gplaypromocodeprovider.server.db.CodeBatches
import org.jetbrains.exposed.v1.core.LongColumnType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Clock

sealed interface HandOutResult {
    data class Success(val code: String) : HandOutResult
    /** The batch belongs to the app but has no available, non-expired code. */
    data object NoCodeAvailable : HandOutResult
    /** There is no such batch for this app. */
    data object BatchNotFound : HandOutResult
}

/** Hands out promo codes. Each code is handed out at most once (CLAUDE.md, domain rule 1). */
class CodeAllocator(private val db: Database, private val clock: Clock = Clock.System) {

    fun handOut(appId: Long, batchId: Long): HandOutResult = transaction(db) {
        val now = clock.now().toEpochMilliseconds()
        val long = LongColumnType()
        // Picks an available code of a non-expired batch and marks it consumed in a single
        // statement, so two requests can never get the same code. The outer
        // `consumed_at IS NULL` makes the UPDATE conditional on the code still being available.
        val code = exec(
            """
            UPDATE promo_code SET consumed_at = ?
            WHERE consumed_at IS NULL AND id = (
                SELECT c.id FROM promo_code c JOIN code_batch b ON b.id = c.batch_id
                WHERE c.batch_id = ? AND b.app_id = ? AND b.expires_at > ? AND c.consumed_at IS NULL
                LIMIT 1
            )
            RETURNING code
            """.trimIndent(),
            args = listOf(long to now, long to batchId, long to appId, long to now),
            explicitStatementType = StatementType.SELECT,
        ) { rs -> if (rs.next()) rs.getString(1) else null }

        when {
            code != null -> HandOutResult.Success(code)
            CodeBatches.select(CodeBatches.id)
                .where { (CodeBatches.id eq batchId) and (CodeBatches.appId eq appId) }
                .empty() -> HandOutResult.BatchNotFound
            else -> HandOutResult.NoCodeAvailable
        }
    }
}
