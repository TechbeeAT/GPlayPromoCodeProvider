package at.techbee.gplaypromocodeprovider.server.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CodeParserTest {

    @Test
    fun trimsWhitespaceAndIgnoresBlankLines() {
        val parsed = CodeParser.parse("  AAA \n\n\tBBB\t\r\n   \nCCC")

        assertEquals(listOf("AAA", "BBB", "CCC"), parsed.unique)
        assertEquals(emptyList(), parsed.duplicates)
    }

    @Test
    fun reportsRepeatedCodesOnce() {
        val parsed = CodeParser.parse("AAA\nBBB\nAAA\n AAA ")

        assertEquals(listOf("AAA", "BBB"), parsed.unique)
        assertEquals(listOf("AAA", "AAA"), parsed.duplicates)
    }

    @Test
    fun emptyInputGivesNoCodes() {
        assertEquals(ParsedCodes(emptyList(), emptyList()), CodeParser.parse(" \n\n "))
    }

    @Test
    fun rejectsTooLongCodeWithoutEchoingIt() {
        val tooLong = "X".repeat(CodeParser.MAX_CODE_LENGTH + 1)

        val e = assertFailsWith<ValidationException> { CodeParser.parse("OK\n$tooLong") }

        assertEquals("Line 2: code is longer than ${CodeParser.MAX_CODE_LENGTH} characters", e.message)
    }
}
