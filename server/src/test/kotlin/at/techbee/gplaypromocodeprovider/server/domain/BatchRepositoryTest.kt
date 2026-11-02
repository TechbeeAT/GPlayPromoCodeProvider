package at.techbee.gplaypromocodeprovider.server.domain

import at.techbee.gplaypromocodeprovider.server.TestClock
import at.techbee.gplaypromocodeprovider.server.createTestDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.days

class BatchRepositoryTest {
    private val clock = TestClock()
    private val db = createTestDatabase()
    private val apps = AppRepository(db, clock)
    private val batches = BatchRepository(db, clock)
    private val allocator = CodeAllocator(db, clock)

    private val app = apps.create(AppInput.of("Test app", null, null))
    private val inAMonth = clock.now() + 30.days

    @Test
    fun uploadStoresTrimmedCodes() {
        val result = batches.upload(app.id, "  Beta wave 1 ", inAMonth, " A \n\nB\n  \n C")

        val created = assertIs<UploadResult.Created>(result)
        assertEquals(3, created.addedCount)
        assertEquals(emptyList(), created.duplicates)
        assertEquals("Beta wave 1", created.batch.description)
        assertEquals(inAMonth, created.batch.expiresAt)
        val handedOut = List(3) { assertIs<HandOutResult.Success>(allocator.handOut(app.id, created.batch.id)).code }
        assertEquals(setOf("A", "B", "C"), handedOut.toSet())
    }

    @Test
    fun uploadSkipsAndReportsDuplicates() {
        batches.upload(app.id, null, inAMonth, "A\nB")

        val result = assertIs<UploadResult.Created>(batches.upload(app.id, null, inAMonth, "B\nC\nC\nD"))

        assertEquals(2, result.addedCount)
        assertEquals(listOf("B", "C"), result.duplicates)
        assertEquals(Stats(available = 2, consumed = 0, expired = 0), batches.listWithStats(app.id).first().stats)
    }

    @Test
    fun consumedCodesAlsoCountAsDuplicates() {
        val first = assertIs<UploadResult.Created>(batches.upload(app.id, null, inAMonth, "A"))
        allocator.handOut(app.id, first.batch.id)

        val e = assertFailsWith<ValidationException> { batches.upload(app.id, null, inAMonth, "A") }
        assertEquals("All 1 codes already exist for this app", e.message)
    }

    @Test
    fun sameCodeIsAllowedForDifferentApps() {
        val other = apps.create(AppInput.of("Other app", null, null))
        batches.upload(app.id, null, inAMonth, "A")

        val result = assertIs<UploadResult.Created>(batches.upload(other.id, null, inAMonth, "A"))
        assertEquals(1, result.addedCount)
    }

    @Test
    fun uploadRejectsInvalidInput() {
        assertFailsWith<ValidationException> { batches.upload(app.id, null, inAMonth, " \n ") }
        assertFailsWith<ValidationException> { batches.upload(app.id, null, clock.now(), "A") }
        assertFailsWith<ValidationException> { batches.upload(app.id, "x".repeat(MAX_DESCRIPTION_LENGTH + 1), inAMonth, "A") }
        assertEquals(emptyList(), batches.listWithStats(app.id))
    }

    @Test
    fun uploadForUnknownAppIsRejected() {
        assertEquals(UploadResult.AppNotFound, batches.upload(4711, null, inAMonth, "A"))
    }

    @Test
    fun listAvailableHidesEmptyAndExpiredBatches() {
        val soon = assertIs<UploadResult.Created>(batches.upload(app.id, "soon", clock.now() + 1.days, "S")).batch
        val later = assertIs<UploadResult.Created>(batches.upload(app.id, "later", inAMonth, "L")).batch
        val empty = assertIs<UploadResult.Created>(batches.upload(app.id, "empty", inAMonth, "E")).batch
        allocator.handOut(app.id, empty.id)

        assertEquals(listOf(soon.id, later.id), batches.listAvailable(app.id).map { it.id })
        clock.advance(1.days)
        assertEquals(listOf(later.id), batches.listAvailable(app.id).map { it.id })
    }

    @Test
    fun statsSplitCodesIntoAvailableConsumedAndExpired() {
        val expiring = assertIs<UploadResult.Created>(batches.upload(app.id, null, clock.now() + 1.days, "A\nB\nC")).batch
        val running = assertIs<UploadResult.Created>(batches.upload(app.id, null, inAMonth, "D\nE")).batch
        allocator.handOut(app.id, expiring.id)
        allocator.handOut(app.id, running.id)
        clock.advance(1.days)

        val stats = batches.listWithStats(app.id).associate { it.batch.id to it.stats }
        assertEquals(Stats(available = 0, consumed = 1, expired = 2), stats[expiring.id])
        assertEquals(Stats(available = 1, consumed = 1, expired = 0), stats[running.id])
    }
}
