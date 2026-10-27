package at.techbee.gplaypromocodeprovider.server.routes

import at.techbee.gplaypromocodeprovider.api.AdminApp
import at.techbee.gplaypromocodeprovider.api.ApiErrors
import at.techbee.gplaypromocodeprovider.api.ErrorResponse
import at.techbee.gplaypromocodeprovider.api.InviteResponse
import at.techbee.gplaypromocodeprovider.api.PromoCodeResponse
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.days

class UserRoutesTest {

    @Test
    fun inviteLinkListsAvailableBatchesWithoutCodes() = apiTest {
        login()
        val app = createApp("My App")
        val batch = uploadBatch(app.id, "SECRET-1\nSECRET-2", description = "Beta wave 1").batch
        val empty = uploadBatch(app.id, "SECRET-3").batch
        client.post("/api/invite/${app.inviteToken}/batches/${empty.id}/code")

        val response = client.get("/api/invite/${app.inviteToken}")

        assertEquals(HttpStatusCode.OK, response.status)
        val invite = response.body<InviteResponse>()
        assertEquals("My App", invite.appName)
        assertEquals(listOf(batch.id), invite.batches.map { it.id })
        assertEquals("Beta wave 1", invite.batches.single().description)
        assertEquals(batch.expiresAt, invite.batches.single().expiresAt)
        assertFalse("SECRET" in response.bodyAsText())
    }

    @Test
    fun requestingACodeHandsOutOneCode() = apiTest {
        login()
        val app = createApp()
        val batch = uploadBatch(app.id, "ONLY-CODE").batch

        val first = client.post("/api/invite/${app.inviteToken}/batches/${batch.id}/code")
        assertEquals(HttpStatusCode.OK, first.status)
        assertEquals("ONLY-CODE", first.body<PromoCodeResponse>().code)

        val second = client.post("/api/invite/${app.inviteToken}/batches/${batch.id}/code")
        assertEquals(HttpStatusCode.Conflict, second.status)
        assertEquals(ApiErrors.NO_CODE_AVAILABLE, second.body<ErrorResponse>().error)
    }

    @Test
    fun expiredBatchIsHiddenAndHandsOutNothing() = apiTest {
        login()
        val app = createApp()
        val batch = uploadBatch(app.id, "A", expiresIn = 1.days).batch
        clock.advance(1.days)

        assertEquals(emptyList(), client.get("/api/invite/${app.inviteToken}").body<InviteResponse>().batches)
        val response = client.post("/api/invite/${app.inviteToken}/batches/${batch.id}/code")
        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals(ApiErrors.NO_CODE_AVAILABLE, response.body<ErrorResponse>().error)
    }

    @Test
    fun unknownTokenRevealsNothing() = apiTest {
        login()
        val app = createApp()
        val batch = uploadBatch(app.id, "A").batch
        val notFound = ErrorResponse(ApiErrors.NOT_FOUND, "Not found")

        for (response in listOf(
            client.get("/api/invite/unknown-token"),
            client.post("/api/invite/unknown-token/batches/${batch.id}/code"),
            client.post("/api/invite/unknown-token/batches/4711/code"),
        )) {
            assertEquals(HttpStatusCode.NotFound, response.status)
            assertEquals(notFound, response.body<ErrorResponse>())
        }
    }

    @Test
    fun batchOfAnotherAppCannotBeRequested() = apiTest {
        login()
        val app = createApp("App")
        val other = createApp("Other")
        val otherBatch = uploadBatch(other.id, "OTHER").batch

        for (path in listOf("/batches/${otherBatch.id}/code", "/batches/4711/code", "/batches/abc/code")) {
            val response = client.post("/api/invite/${app.inviteToken}$path")
            assertEquals(HttpStatusCode.NotFound, response.status)
            assertEquals(ErrorResponse(ApiErrors.NOT_FOUND, "Not found"), response.body<ErrorResponse>())
        }
    }

    @Test
    fun regeneratedTokenRevokesTheOldInviteLink() = apiTest {
        login()
        val app = createApp()

        val regenerated = client.post("/api/admin/apps/${app.id}/invite-token").body<AdminApp>()

        assertFalse(regenerated.inviteToken == app.inviteToken)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/invite/${app.inviteToken}").status)
        assertEquals(HttpStatusCode.OK, client.get("/api/invite/${regenerated.inviteToken}").status)
    }
}
