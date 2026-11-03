package at.techbee.gplaypromocodeprovider.admin

import at.techbee.gplaypromocodeprovider.Route
import at.techbee.gplaypromocodeprovider.admin.app.components.inviteLink
import kotlin.test.Test
import kotlin.test.assertEquals

class InviteLinkTest {

    @Test
    fun linkPointsToTheInvitePageOfTheOrigin() {
        assertEquals("https://promo.example.com/invite/a-B_9", inviteLink("https://promo.example.com", "a-B_9"))
        assertEquals("https://promo.example.com/invite/a-B_9", inviteLink("https://promo.example.com/", "a-B_9"))
    }

    @Test
    fun linkOpensTheInvitePageForTheToken() {
        val token = "q8Vh1bT2kLx9_Za-3mN7pR4sW6yE0cJ5uI8oA2dF1gH"
        val path = inviteLink("https://promo.example.com", token).removePrefix("https://promo.example.com")

        assertEquals(Route.Invite(token), Route.parse(path))
    }
}
