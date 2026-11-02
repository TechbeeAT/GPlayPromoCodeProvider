package at.techbee.gplaypromocodeprovider.server.domain

import at.techbee.gplaypromocodeprovider.server.db.Apps
import at.techbee.gplaypromocodeprovider.server.db.CodeBatches
import at.techbee.gplaypromocodeprovider.server.db.PromoCodes
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Clock
import kotlin.time.Instant

sealed interface UploadResult {
    data class Created(val batch: Batch, val addedCount: Int, val duplicates: List<String>) : UploadResult
    data object AppNotFound : UploadResult
}

class BatchRepository(private val db: Database, private val clock: Clock = Clock.System) {

    /**
     * Creates a batch from pasted [codesText] (one code per line). Codes that already exist for
     * the app, or repeat within the upload, are skipped and reported in [UploadResult.Created.duplicates].
     * Throws [ValidationException] for invalid input, including an upload without any new code.
     */
    fun upload(appId: Long, description: String?, expiresAt: Instant, codesText: String): UploadResult {
        val now = clock.now()
        if (expiresAt <= now) throw ValidationException("The expiration date must be in the future")
        val batchDescription = description.trimToNull()?.checkLength("Description", MAX_DESCRIPTION_LENGTH)
        val parsed = CodeParser.parse(codesText)
        if (parsed.unique.isEmpty()) throw ValidationException("No codes found. Paste one code per line.")
        if (parsed.unique.size > MAX_CODES_PER_UPLOAD) {
            throw ValidationException("At most $MAX_CODES_PER_UPLOAD codes per upload")
        }

        // The transaction holds SQLite's write lock from the start (BEGIN IMMEDIATE), so no
        // other upload can insert the same code between the check and the insert. The
        // UNIQUE (app_id, code) constraint is the last line of defence.
        return transaction(db) {
            if (Apps.select(Apps.id).where { Apps.id eq appId }.empty()) return@transaction UploadResult.AppNotFound

            val existing = parsed.unique.chunked(500).flatMapTo(HashSet()) { chunk ->
                PromoCodes.select(PromoCodes.code)
                    .where { (PromoCodes.appId eq appId) and (PromoCodes.code inList chunk) }
                    .map { it[PromoCodes.code] }
            }
            val newCodes = parsed.unique.filterNot { it in existing }
            val duplicates = parsed.unique.filter { it in existing } + parsed.duplicates
            if (newCodes.isEmpty()) {
                throw ValidationException("All ${parsed.unique.size} codes already exist for this app")
            }

            val batchId = CodeBatches.insert {
                it[CodeBatches.appId] = appId
                it[CodeBatches.description] = batchDescription
                it[CodeBatches.expiresAt] = expiresAt.toEpochMilliseconds()
                it[createdAt] = now.toEpochMilliseconds()
            }[CodeBatches.id]
            PromoCodes.batchInsert(newCodes, shouldReturnGeneratedValues = false) { code ->
                this[PromoCodes.appId] = appId
                this[PromoCodes.batchId] = batchId
                this[PromoCodes.code] = code
            }
            val batch = Batch(batchId, appId, batchDescription, expiresAt.truncatedToMillis(), now.truncatedToMillis())
            UploadResult.Created(batch, newCodes.size, duplicates)
        }
    }

    /** Batches of the app that are not expired and still have available codes, soonest expiry first. */
    fun listAvailable(appId: Long): List<Batch> = transaction(db) {
        val now = clock.now().toEpochMilliseconds()
        val availableCode = PromoCodes.select(PromoCodes.id).where {
            (PromoCodes.batchId eq CodeBatches.id) and PromoCodes.consumedAt.isNull()
        }
        CodeBatches.selectAll()
            .where { (CodeBatches.appId eq appId) and (CodeBatches.expiresAt greater now) and exists(availableCode) }
            .orderBy(CodeBatches.expiresAt to SortOrder.ASC, CodeBatches.id to SortOrder.ASC)
            .map { it.toBatch() }
    }

    /** All batches of the app (or of all apps if [appId] is null) with their code statistics, newest first. */
    fun listWithStats(appId: Long? = null): List<BatchWithStats> = transaction(db) {
        val now = clock.now()
        val batches = CodeBatches.selectAll()
            .apply { if (appId != null) where { CodeBatches.appId eq appId } }
            .orderBy(CodeBatches.createdAt to SortOrder.DESC, CodeBatches.id to SortOrder.DESC)
            .map { it.toBatch() }

        // Count consumed and not consumed codes per batch. Whether a not consumed code is
        // available or expired depends only on its batch, which is decided below.
        val count = PromoCodes.id.count()
        val notConsumed = PromoCodes.consumedAt.isNull()
        val counts = PromoCodes.select(PromoCodes.batchId, notConsumed, count)
            .apply { if (appId != null) where { PromoCodes.appId eq appId } }
            .groupBy(PromoCodes.batchId, notConsumed)
            .associate { (it[PromoCodes.batchId] to !it[notConsumed]) to it[count].toInt() }

        batches.map { batch ->
            val consumed = counts[batch.id to true] ?: 0 // key: (batch id, consumed?)
            val remaining = counts[batch.id to false] ?: 0
            val stats = if (batch.isExpired(now)) Stats(0, consumed, remaining) else Stats(remaining, consumed, 0)
            BatchWithStats(batch, stats)
        }
    }

    private fun ResultRow.toBatch() = Batch(
        id = this[CodeBatches.id],
        appId = this[CodeBatches.appId],
        description = this[CodeBatches.description],
        expiresAt = Instant.fromEpochMilliseconds(this[CodeBatches.expiresAt]),
        createdAt = Instant.fromEpochMilliseconds(this[CodeBatches.createdAt]),
    )
}

/** Timestamps are stored with millisecond precision. */
private fun Instant.truncatedToMillis() = Instant.fromEpochMilliseconds(toEpochMilliseconds())
