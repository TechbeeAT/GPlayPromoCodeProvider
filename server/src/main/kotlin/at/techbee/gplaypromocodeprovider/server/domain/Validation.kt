package at.techbee.gplaypromocodeprovider.server.domain

const val MAX_NAME_LENGTH = 100
const val MAX_PACKAGE_NAME_LENGTH = 255
const val MAX_DESCRIPTION_LENGTH = 1000
const val MAX_CODES_PER_UPLOAD = 10_000

/** Validated app fields: trimmed, blank optional fields become null. */
data class AppInput(val name: String, val packageName: String?, val description: String?) {
    companion object {
        fun of(name: String, packageName: String?, description: String?): AppInput {
            val trimmedName = name.trim()
            if (trimmedName.isEmpty()) throw ValidationException("Name must not be empty")
            return AppInput(
                name = trimmedName.checkLength("Name", MAX_NAME_LENGTH),
                packageName = packageName.trimToNull()?.checkLength("Package name", MAX_PACKAGE_NAME_LENGTH),
                description = description.trimToNull()?.checkLength("Description", MAX_DESCRIPTION_LENGTH),
            )
        }
    }
}

fun String?.trimToNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

fun String.checkLength(field: String, max: Int): String {
    if (length > max) throw ValidationException("$field must be at most $max characters")
    return this
}
