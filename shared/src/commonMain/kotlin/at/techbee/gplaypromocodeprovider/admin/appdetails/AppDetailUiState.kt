package at.techbee.gplaypromocodeprovider.admin.appdetails

import at.techbee.gplaypromocodeprovider.admin.ActionError
import at.techbee.gplaypromocodeprovider.admin.components.AppDialogState
import at.techbee.gplaypromocodeprovider.api.AdminApp
import at.techbee.gplaypromocodeprovider.api.AdminBatch
import at.techbee.gplaypromocodeprovider.api.BatchUploadResponse

/** Everything the page of one app shows. */
data class AppDetailUiState(
    val content: AppDetailContent = AppDetailContent.Loading,
    /** The app and batches are being reloaded in the background, e.g. after an upload. */
    val refreshing: Boolean = false,
    /** The "Edit app" dialog; `null` while it is closed. */
    val editDialog: AppDialogState? = null,
    /** The confirmation for a new invite link; `null` while it is closed. */
    val regenerateDialog: RegenerateDialogState? = null,
    /** The confirmation for invalidating a batch; `null` while it is closed. */
    val invalidateDialog: InvalidateDialogState? = null,
    val upload: BatchUploadFormState = BatchUploadFormState(),
)

sealed interface AppDetailContent {
    data object Loading : AppDetailContent

    /** No app with this id (e.g. a mistyped URL). */
    data object NotFound : AppDetailContent

    data class LoadFailed(val error: ActionError) : AppDetailContent

    /** [batches] newest first. */
    data class Loaded(val app: AdminApp, val batches: List<AdminBatch>) : AppDetailContent
}

data class RegenerateDialogState(val running: Boolean = false, val error: ActionError? = null)

/** Asks before [batch] is invalidated, since that can't be undone. */
data class InvalidateDialogState(val batch: AdminBatch, val running: Boolean = false, val error: ActionError? = null)

/** The batch upload form. Kept in the view model, so it survives a login in between. */
data class BatchUploadFormState(
    val description: String = "",
    /** The picked expiration day as the date picker returns it (midnight UTC of that day), or `null`. */
    val expiryDateMillis: Long? = null,
    val codes: String = "",
    /**
     * Number of non-blank lines in [codes], i.e. codes before duplicates are removed (the server's rules).
     * Counted once per change of [codes] and kept by `copy`, so other form changes don't rescan
     * thousands of lines. Change the codes only through [withCodes].
     */
    val codeCount: Int = countCodes(codes),
    val uploading: Boolean = false,
    val error: ActionError? = null,
    /** The outcome of the last upload, shown until dismissed or until the next upload. */
    val result: BatchUploadResponse? = null,
) {
    fun withCodes(codes: String) = copy(codes = codes, codeCount = countCodes(codes))

    val canUpload: Boolean get() = !uploading && expiryDateMillis != null && codeCount > 0
}

/** Counts the codes in pasted text the way the server parses them: one per line, blank lines ignored. */
fun countCodes(text: String): Int = text.lineSequence().count { it.isNotBlank() }
