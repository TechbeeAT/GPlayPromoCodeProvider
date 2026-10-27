package at.techbee.gplaypromocodeprovider.server.domain

/** Codes parsed from an upload: [unique] keeps the first occurrence, [duplicates] lists each repeat. */
data class ParsedCodes(val unique: List<String>, val duplicates: List<String>)

object CodeParser {
    const val MAX_CODE_LENGTH = 128

    /**
     * Parses pasted codes, one per line. Surrounding whitespace is trimmed and blank lines are
     * ignored. Throws [ValidationException] for a code longer than [MAX_CODE_LENGTH]
     * (the message names the line, never the code).
     */
    fun parse(text: String): ParsedCodes {
        val seen = LinkedHashSet<String>()
        val duplicates = mutableListOf<String>()
        text.lineSequence().forEachIndexed { index, line ->
            val code = line.trim()
            if (code.isEmpty()) return@forEachIndexed
            if (code.length > MAX_CODE_LENGTH) {
                throw ValidationException("Line ${index + 1}: code is longer than $MAX_CODE_LENGTH characters")
            }
            if (!seen.add(code)) duplicates += code
        }
        return ParsedCodes(seen.toList(), duplicates)
    }
}

/** Invalid admin input. The message is shown to the admin and must not contain secrets. */
class ValidationException(message: String) : Exception(message)
