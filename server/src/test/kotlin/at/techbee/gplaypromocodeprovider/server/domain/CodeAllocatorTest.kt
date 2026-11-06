package at.techbee.gplaypromocodeprovider.server.domain

import at.techbee.gplaypromocodeprovider.server.TestClock
import at.techbee.gplaypromocodeprovider.server.createTestDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

class CodeAllocatorTest {
    private val clock = TestClock()
    private val db = createTestDatabase()
    private val apps = AppRepository(db, clock)
    private val batches = BatchRepository(db, clock)
    private val allocator = CodeAllocator(db, clock)

    private val app = runBlocking { apps.create(AppInput.of("Test app", null, null)) }

    private suspend fun upload(codes: List<String>, appId: Long = app.id, expiresIn: kotlin.time.Duration = 30.days): Batch {
        val result = batches.upload(appId, null, clock.now() + expiresIn, codes.joinToString("\n"))
        return assertIs<UploadResult.Created>(result).batch
    }

    private suspend fun statsOf(batch: Batch) = batches.listWithStats(batch.appId).single { it.batch.id == batch.id }.stats

    @Test
    fun handOutReturnsACodeAndMarksItConsumed(): Unit = runBlocking {
        val batch = upload(listOf("CODE-1", "CODE-2"))

        val result = allocator.handOut(app.id, batch.id)

        val code = assertIs<HandOutResult.Success>(result).code
        assert(code in listOf("CODE-1", "CODE-2"))
        assertEquals(Stats(available = 1, consumed = 1, expired = 0), statsOf(batch))
    }

    @Test
    fun handsOutEveryCodeOnceThenNoCodeAvailable(): Unit = runBlocking {
        val batch = upload(listOf("A", "B", "C"))

        val codes = List(3) { assertIs<HandOutResult.Success>(allocator.handOut(app.id, batch.id)).code }

        assertEquals(setOf("A", "B", "C"), codes.toSet())
        assertEquals(HandOutResult.NoCodeAvailable, allocator.handOut(app.id, batch.id))
        assertEquals(Stats(available = 0, consumed = 3, expired = 0), statsOf(batch))
    }

    @Test
    fun expiredBatchHandsOutNothing(): Unit = runBlocking {
        val batch = upload(listOf("A", "B"), expiresIn = 1.days)
        clock.advance(1.days)

        assertEquals(HandOutResult.NoCodeAvailable, allocator.handOut(app.id, batch.id))
        assertEquals(Stats(available = 0, consumed = 0, expired = 2), statsOf(batch))
    }

    @Test
    fun batchIsUsableUntilJustBeforeItsExpiration(): Unit = runBlocking {
        val batch = upload(listOf("A", "B"), expiresIn = 1.days)
        clock.advance(1.days - 1.milliseconds)

        assertIs<HandOutResult.Success>(allocator.handOut(app.id, batch.id))
        clock.advance(1.milliseconds)
        assertEquals(HandOutResult.NoCodeAvailable, allocator.handOut(app.id, batch.id))
        assertEquals(Stats(available = 0, consumed = 1, expired = 1), statsOf(batch))
    }

    @Test
    fun unknownBatchIsNotFound(): Unit = runBlocking {
        assertEquals(HandOutResult.BatchNotFound, allocator.handOut(app.id, 4711))
    }

    @Test
    fun batchOfAnotherAppIsNotFound(): Unit = runBlocking {
        val otherApp = apps.create(AppInput.of("Other app", null, null))
        val otherBatch = upload(listOf("OTHER"), appId = otherApp.id)

        assertEquals(HandOutResult.BatchNotFound, allocator.handOut(app.id, otherBatch.id))
        assertEquals(Stats(available = 1, consumed = 0, expired = 0), statsOf(otherBatch))
    }

    @Test
    fun invalidatedBatchHandsOutNothing(): Unit = runBlocking {
        val batch = upload(listOf("A", "B", "C"))
        allocator.handOut(app.id, batch.id)

        batches.invalidate(app.id, batch.id)

        assertEquals(HandOutResult.NoCodeAvailable, allocator.handOut(app.id, batch.id))
        assertEquals(Stats(available = 0, consumed = 1, expired = 0, invalidated = 2), statsOf(batch))
    }

    @Test
    fun invalidatingDuringParallelRequestsNeverHandsOutAfterwards(): Unit = runBlocking {
        val batch = upload(List(200) { "CODE-$it" })

        val requests = List(100) { async(Dispatchers.IO) { allocator.handOut(app.id, batch.id) } }
        val invalidated = async(Dispatchers.IO) { batches.invalidate(app.id, batch.id)!! }
        val results = requests.awaitAll()

        // Every code is counted exactly once: handed out before the invalidation, or invalidated.
        val handedOut = results.count { it is HandOutResult.Success }
        assertEquals(handedOut, invalidated.await().stats.consumed)
        assertEquals(Stats(available = 0, consumed = handedOut, expired = 0, invalidated = 200 - handedOut), statsOf(batch))
        assertEquals(HandOutResult.NoCodeAvailable, allocator.handOut(app.id, batch.id))
    }

    @Test
    fun parallelRequestsNeverGetTheSameCode(): Unit = runBlocking {
        val batch = upload(List(50) { "CODE-$it" })

        val results = List(100) {
            async(Dispatchers.IO) { allocator.handOut(app.id, batch.id) }
        }.awaitAll()

        val codes = results.filterIsInstance<HandOutResult.Success>().map { it.code }
        assertEquals(50, codes.size)
        assertEquals(50, codes.toSet().size)
        assertEquals(50, results.count { it == HandOutResult.NoCodeAvailable })
        assertEquals(Stats(available = 0, consumed = 50, expired = 0), statsOf(batch))
    }
}
