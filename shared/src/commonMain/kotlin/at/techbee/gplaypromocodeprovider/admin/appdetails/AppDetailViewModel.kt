package at.techbee.gplaypromocodeprovider.admin.appdetails

import androidx.lifecycle.viewModelScope
import at.techbee.gplaypromocodeprovider.admin.ActionError
import at.techbee.gplaypromocodeprovider.admin.AdminViewModel
import at.techbee.gplaypromocodeprovider.admin.components.AppDialogState
import at.techbee.gplaypromocodeprovider.api.AdminApp
import at.techbee.gplaypromocodeprovider.api.AdminBatch
import at.techbee.gplaypromocodeprovider.api.ApiError
import at.techbee.gplaypromocodeprovider.api.AppRequest
import at.techbee.gplaypromocodeprovider.api.BatchUploadRequest
import at.techbee.gplaypromocodeprovider.client.ApiClient
import at.techbee.gplaypromocodeprovider.client.ApiResult
import at.techbee.gplaypromocodeprovider.platform.endOfLocalDay
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The page of one app: details, invite link, batches with statistics, and the upload form.
 *
 * @param endOfDay turns the picked expiry day into the batch's expiration instant; tests replace it,
 *   because the real one depends on the time zone
 * @param clock decides which expiry days can still be picked
 */
class AppDetailViewModel(
    private val appId: Long,
    private val api: ApiClient,
    onSessionExpired: () -> Unit,
    private val endOfDay: (Long) -> Instant = ::endOfLocalDay,
    private val clock: Clock = Clock.System,
) : AdminViewModel(onSessionExpired) {

    private val _state = MutableStateFlow(AppDetailUiState())
    val state: StateFlow<AppDetailUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        load(showLoading = true)
    }

    fun retry() = load(showLoading = true)

    override fun onLoggedInAgain() {
        // Also after a failed background refresh, whose old content is still shown.
        load(showLoading = _state.value.content !is AppDetailContent.Loaded)
        // The admin can now simply try again; the form keeps its input.
        _state.update {
            it.copy(
                editDialog = it.editDialog?.clearSessionError(),
                regenerateDialog = it.regenerateDialog?.let { d ->
                    if (d.error == ActionError.SessionExpired) d.copy(error = null) else d
                },
                invalidateDialog = it.invalidateDialog?.let { d ->
                    if (d.error == ActionError.SessionExpired) d.copy(error = null) else d
                },
                upload = if (it.upload.error == ActionError.SessionExpired) it.upload.copy(error = null) else it.upload,
            )
        }
    }

    // Editing the app

    fun openEditDialog() = _state.update { it.copy(editDialog = it.editDialog ?: AppDialogState()) }

    fun closeEditDialog() = _state.update { if (it.editDialog?.saving == true) it else it.copy(editDialog = null) }

    fun saveApp(request: AppRequest) {
        if (_state.value.editDialog?.saving != false) return
        _state.update { it.copy(editDialog = AppDialogState(saving = true)) }
        viewModelScope.launch {
            when (val result = api.updateApp(appId, request)) {
                is ApiResult.Success -> _state.update { it.withApp(result.value).copy(editDialog = null) }
                else -> {
                    val error = errorOf(result)
                    _state.update { it.copy(editDialog = AppDialogState(error = error)) }
                }
            }
        }
    }

    // Invite link

    fun askToRegenerateInviteLink() =
        _state.update { it.copy(regenerateDialog = it.regenerateDialog ?: RegenerateDialogState()) }

    fun cancelRegenerateInviteLink() =
        _state.update { if (it.regenerateDialog?.running == true) it else it.copy(regenerateDialog = null) }

    /** Gives the app a new invite token after the admin confirmed. The old link stops working. */
    fun regenerateInviteLink() {
        if (_state.value.regenerateDialog?.running != false) return
        _state.update { it.copy(regenerateDialog = RegenerateDialogState(running = true)) }
        viewModelScope.launch {
            when (val result = api.regenerateInviteToken(appId)) {
                is ApiResult.Success -> _state.update { it.withApp(result.value).copy(regenerateDialog = null) }
                else -> {
                    val error = errorOf(result)
                    _state.update { it.copy(regenerateDialog = RegenerateDialogState(error = error)) }
                }
            }
        }
    }

    // Invalidating a batch

    fun askToInvalidateBatch(batch: AdminBatch) =
        _state.update { it.copy(invalidateDialog = it.invalidateDialog ?: InvalidateDialogState(batch)) }

    fun cancelInvalidateBatch() =
        _state.update { if (it.invalidateDialog?.running == true) it else it.copy(invalidateDialog = null) }

    /** Invalidates the batch of the open dialog after the admin confirmed. Its remaining codes are never handed out. */
    fun invalidateBatch() {
        val dialog = _state.value.invalidateDialog ?: return
        if (dialog.running) return
        _state.update { it.copy(invalidateDialog = dialog.copy(running = true, error = null)) }
        viewModelScope.launch {
            when (val result = api.invalidateBatch(appId, dialog.batch.id)) {
                is ApiResult.Success -> {
                    _state.update { it.withBatch(result.value).copy(invalidateDialog = null) }
                    load(showLoading = false) // New statistics for the app.
                }
                else -> {
                    val error = errorOf(result)
                    _state.update { it.copy(invalidateDialog = it.invalidateDialog?.copy(running = false, error = error)) }
                }
            }
        }
    }

    // Upload form

    fun setDescription(description: String) = updateForm { it.copy(description = description) }

    /** Whether the date picker offers this day (midnight UTC, as the picker passes it): only days that haven't ended. */
    fun isSelectableExpiryDay(utcDateMillis: Long): Boolean = endOfDay(utcDateMillis) > clock.now()

    fun setExpiryDate(utcDateMillis: Long?) = updateForm { it.copy(expiryDateMillis = utcDateMillis) }

    fun setCodes(codes: String) = updateForm { it.withCodes(codes) }

    fun dismissUploadResult() = _state.update { it.copy(upload = it.upload.copy(result = null)) }

    /** Uploads the form as a new batch. On success the form is cleared and the result is shown. */
    fun upload() {
        val form = _state.value.upload
        if (!form.canUpload) return
        val request = BatchUploadRequest(
            description = form.description.trim().ifEmpty { null },
            expiresAt = endOfDay(form.expiryDateMillis!!),
            codes = form.codes,
        )
        _state.update { it.copy(upload = form.copy(uploading = true, error = null, result = null)) }
        viewModelScope.launch {
            when (val result = api.uploadBatch(appId, request)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(upload = UploadFormState(result = result.value)) }
                    load(showLoading = false) // New statistics for the app and the new batch in the list.
                }
                else -> {
                    val error = errorOf(result)
                    _state.update { it.copy(upload = it.upload.copy(uploading = false, error = error)) }
                }
            }
        }
    }

    /** Changes the form while no upload is running (the fields are disabled meanwhile anyway). */
    private fun updateForm(change: (UploadFormState) -> UploadFormState) = _state.update {
        if (it.upload.uploading) it else it.copy(upload = change(it.upload))
    }

    private fun AppDetailUiState.withApp(app: AdminApp) =
        (content as? AppDetailContent.Loaded)?.let { copy(content = it.copy(app = app)) } ?: this

    private fun AppDetailUiState.withBatch(batch: AdminBatch) =
        (content as? AppDetailContent.Loaded)?.let { loaded ->
            copy(content = loaded.copy(batches = loaded.batches.map { if (it.id == batch.id) batch else it }))
        } ?: this

    private fun AppDialogState.clearSessionError() = if (error == ActionError.SessionExpired) copy(error = null) else this

    /**
     * Loads the app and its batches. With [showLoading] `false` the current content stays visible
     * until the new one arrives, and a failure keeps it.
     */
    private fun load(showLoading: Boolean) {
        loadJob?.cancel()
        _state.update { if (showLoading) it.copy(content = AppDetailContent.Loading) else it.copy(refreshing = true) }
        loadJob = viewModelScope.launch {
            val app = async { api.getApp(appId) }
            val batches = async { api.listBatches(appId) }
            val appResult = app.await()
            val batchesResult = batches.await()
            val content = when {
                appResult is ApiResult.Success && batchesResult is ApiResult.Success ->
                    AppDetailContent.Loaded(appResult.value, batchesResult.value)
                appResult is ApiResult.Failure && appResult.error == ApiError.NOT_FOUND -> AppDetailContent.NotFound
                appResult !is ApiResult.Success -> AppDetailContent.LoadFailed(errorOf(appResult))
                else -> AppDetailContent.LoadFailed(errorOf(batchesResult))
            }
            _state.update {
                val keepCurrent = !showLoading && content !is AppDetailContent.Loaded && it.content is AppDetailContent.Loaded
                if (keepCurrent) it.copy(refreshing = false) else it.copy(content = content, refreshing = false)
            }
        }
    }
}
