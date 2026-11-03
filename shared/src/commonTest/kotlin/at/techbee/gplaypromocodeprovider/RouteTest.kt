package at.techbee.gplaypromocodeprovider

import kotlin.test.Test
import kotlin.test.assertEquals

class RouteTest {

    @Test
    fun rootIsHome() {
        assertEquals(Route.Home, Route.parse("/"))
        assertEquals(Route.Home, Route.parse(""))
    }

    @Test
    fun inviteLinkCarriesTheToken() {
        assertEquals(Route.Invite("abc-DEF_123"), Route.parse("/invite/abc-DEF_123"))
    }

    @Test
    fun trailingSlashIsIgnored() {
        assertEquals(Route.Invite("abc"), Route.parse("/invite/abc/"))
    }

    @Test
    fun tokenIsPercentDecoded() {
        assertEquals(Route.Invite("a b"), Route.parse("/invite/a%20b"))
    }

    @Test
    fun malformedPercentEncodingIsKeptAsIs() {
        assertEquals(Route.Invite("abc%zz"), Route.parse("/invite/abc%zz"))
    }

    @Test
    fun inviteWithoutTokenOrWithExtraSegmentsIsHome() {
        assertEquals(Route.Home, Route.parse("/invite"))
        assertEquals(Route.Home, Route.parse("/invite/"))
        assertEquals(Route.Home, Route.parse("/invite/abc/more"))
    }

    @Test
    fun unknownPathIsHome() {
        assertEquals(Route.Home, Route.parse("/something/else"))
    }
}
