package at.techbee.gplaypromocodeprovider.admin.apps

import androidx.lifecycle.viewModelScope
import at.techbee.gplaypromocodeprovider.admin.ActionError
import at.techbee.gplaypromocodeprovider.admin.AdminViewModel
import at.techbee.gplaypromocodeprovider.admin.components.AppDialogState
import at.techbee.gplaypromocodeprovider.api.AdminApp
import at.techbee.gplaypromocodeprovider.api.AppRequest
import at.techbee.gplaypromocodeprovider.client.ApiClient
import at.techbee.gplaypromocodeprovider.client.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppListUiState(
    val content: AppListContent = AppListContent.Loading,
    /** The "New app" dialog; `null` while it is closed. */
    val createDialog: AppDialogState? = null,
)

sealed interface AppListContent {
    data object Loading : AppListContent
    data class LoadFailed(val error: ActionError) : AppListContent

    /** All apps with their statistics, sorted by name. */
    data class Loaded(val apps: List<AdminApp>) : AppListContent
}

/** The admin's list of apps, and creating an app. */
class AppListViewModel(private val api: ApiClient, onSessionExpired: () -> Unit) : AdminViewModel(onSessionExpired) {

    private val _state = MutableStateFlow(AppListUiState())
    val state: StateFlow<AppListUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    override fun onLoggedInAgain() {
        val current = _state.value
        if ((current.content as? AppListContent.LoadFailed)?.error == ActionError.SessionExpired) load()
        if (current.createDialog?.error == ActionError.SessionExpired) {
            _state.update { it.copy(createDialog = it.createDialog?.copy(error = null)) }
        }
    }

    fun openCreateDialog() = _state.update { it.copy(createDialog = it.createDialog ?: AppDialogState()) }

    /** Ignored while saving: the app may be created anyway. */
    fun closeCreateDialog() = _state.update { if (it.createDialog?.saving == true) it else it.copy(createDialog = null) }

    fun createApp(request: AppRequest) {
        val dialog = _state.value.createDialog ?: return
        if (dialog.saving) return
        _state.update { it.copy(createDialog = AppDialogState(saving = true)) }
        viewModelScope.launch {
            when (val result = api.createApp(request)) {
                is ApiResult.Success -> _state.update {
                    val content = (it.content as? AppListContent.Loaded)
                        ?.let { loaded -> AppListContent.Loaded((loaded.apps + result.value).sortedBy(AdminApp::name)) }
                        ?: it.content
                    it.copy(content = content, createDialog = null)
                }
                else -> {
                    val error = errorOf(result)
                    _state.update { it.copy(createDialog = AppDialogState(error = error)) }
                }
            }
        }
    }

    private fun load() {
        _state.update { it.copy(content = AppListContent.Loading) }
        viewModelScope.launch {
            val content = when (val result = api.listApps()) {
                is ApiResult.Success -> AppListContent.Loaded(result.value)
                else -> AppListContent.LoadFailed(errorOf(result))
            }
            _state.update { it.copy(content = content) }
        }
    }
}
