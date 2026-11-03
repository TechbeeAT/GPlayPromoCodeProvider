package at.techbee.gplaypromocodeprovider.invite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.techbee.gplaypromocodeprovider.api.ApiError
import at.techbee.gplaypromocodeprovider.client.ApiClient
import at.techbee.gplaypromocodeprovider.client.ApiResult
import at.techbee.gplaypromocodeprovider.client.ServerUnreachableException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State and actions of the invite page for one invite [token]. Loads the app's batches on creation.
 */
class InviteViewModel(private val token: String, private val api: ApiClient) : ViewModel() {

    private val _state = MutableStateFlow(InviteUiState())
    val state: StateFlow<InviteUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        load(showLoading = true)
    }

    /** Loads the batch list again, e.g. after it failed to load. */
    fun retry() = load(showLoading = true)

    /**
     * Requests one code of [batchId]. Ignored while another request is running, so a double tap
     * can't hand out two codes, and while the list is being refreshed.
     */
    fun requestCode(batchId: Long) {
        val current = _state.value
        if (current.requestingBatchId != null || current.refreshing) return
        val batch = (current.content as? InviteContent.Loaded)?.batches?.find { it.id == batchId } ?: return
        _state.update { it.copy(requestingBatchId = batchId, notice = null) }

        viewModelScope.launch {
            when (val result = api.requestCode(token, batchId)) {
                is ApiResult.Success -> {
                    _state.update {
                        val code = ReceivedCode(result.value.code, batch.description, batch.expiresAt)
                        it.copy(requestingBatchId = null, receivedCodes = listOf(code) + it.receivedCodes)
                    }
                    // That may have been the batch's last code; the list then no longer shows it.
                    load(showLoading = false)
                }
                is ApiResult.Failure -> when (result.error) {
                    // The batch is dropped from the list right away, so it isn't offered again even if
                    // the refresh fails.
                    ApiError.NO_CODE_AVAILABLE -> {
                        _state.update { it.withoutBatch(batchId).copy(notice = InviteNotice.BATCH_RAN_OUT) }
                        load(showLoading = false)
                    }
                    // The batch is gone, or the link was revoked. Reloading shows which: the current
                    // batch list, or the invalid link message.
                    ApiError.NOT_FOUND -> {
                        _state.update { it.withoutBatch(batchId).copy(notice = InviteNotice.BATCH_UNAVAILABLE) }
                        load(showLoading = false)
                    }
                    else -> _state.update { it.copy(requestingBatchId = null, notice = InviteNotice.SERVER_ERROR) }
                }
                is ApiResult.NetworkError -> {
                    val notice = if (result.cause is ServerUnreachableException) InviteNotice.NO_ANSWER else InviteNotice.NETWORK_ERROR
                    _state.update { it.copy(requestingBatchId = null, notice = notice) }
                }
            }
        }
    }

    /** Ends the request for [batchId] and removes that batch from the shown list. */
    private fun InviteUiState.withoutBatch(batchId: Long): InviteUiState {
        val loaded = content as? InviteContent.Loaded ?: return copy(requestingBatchId = null)
        return copy(
            requestingBatchId = null,
            content = loaded.copy(batches = loaded.batches.filterNot { it.id == batchId }),
        )
    }

    /** Hides the current notice. */
    fun dismissNotice() = _state.update { it.copy(notice = null) }

    /**
     * Loads the batch list. With [showLoading] `false` the current list stays visible until the new
     * one arrives (used for refreshing after every code request).
     */
    private fun load(showLoading: Boolean) {
        loadJob?.cancel()
        _state.update { if (showLoading) it.copy(content = InviteContent.Loading) else it.copy(refreshing = true) }
        loadJob = viewModelScope.launch {
            val content = when (val result = api.getInvite(token)) {
                is ApiResult.Success -> InviteContent.Loaded(result.value.appName, result.value.batches)
                is ApiResult.Failure ->
                    if (result.error == ApiError.NOT_FOUND) {
                        InviteContent.InvalidLink
                    } else {
                        InviteContent.LoadFailed(FailureReason.SERVER)
                    }
                is ApiResult.NetworkError -> InviteContent.LoadFailed(FailureReason.NETWORK)
            }
            _state.update {
                // A failed refresh keeps the list on screen; the next request will tell again.
                val keepCurrent = !showLoading && content is InviteContent.LoadFailed && it.content is InviteContent.Loaded
                if (keepCurrent) it.copy(refreshing = false) else it.copy(content = content, refreshing = false)
            }
        }
    }
}
