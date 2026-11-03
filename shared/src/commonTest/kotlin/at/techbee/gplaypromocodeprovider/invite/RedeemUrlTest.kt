package at.techbee.gplaypromocodeprovider.invite

import kotlin.test.Test
import kotlin.test.assertEquals

class RedeemUrlTest {

    @Test
    fun codeIsAppendedToGooglePlaysRedeemPage() {
        assertEquals("https://play.google.com/redeem?code=ABC123", redeemUrl("ABC123"))
    }

    @Test
    fun codeIsUrlEncoded() {
        assertEquals("https://play.google.com/redeem?code=A%26B%3DC%20D", redeemUrl("A&B=C D"))
    }
}
