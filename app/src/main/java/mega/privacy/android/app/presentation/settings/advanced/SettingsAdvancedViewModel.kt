package mega.privacy.android.app.presentation.settings.advanced

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import mega.privacy.android.app.di.IoDispatcher
import mega.privacy.android.app.domain.usecase.IsUseHttpsEnabled
import mega.privacy.android.app.domain.usecase.MonitorConnectivity
import mega.privacy.android.app.domain.usecase.RootNodeExists
import mega.privacy.android.app.domain.usecase.SetUseHttps
import mega.privacy.android.app.presentation.settings.advanced.model.SettingsAdvancedState
import javax.inject.Inject

/**
 * Settings advanced view model
 *
 *
 * @param isUseHttpsEnabled use case to check current status of the preference
 * @param rootNodeExists use case to determine if the root node exists
 * @param monitorConnectivity use case to monitor connectivity
 * @param ioDispatcher coroutine dispatcher for io code
 *
 * @property setUseHttps use case that is called when the preference is updated
 *
 * @property state The current UI state
 */
@HiltViewModel
class SettingsAdvancedViewModel @Inject constructor(
    private val setUseHttps: SetUseHttps,
    isUseHttpsEnabled: IsUseHttpsEnabled,
    rootNodeExists: RootNodeExists,
    monitorConnectivity: MonitorConnectivity,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsAdvancedState())
    val state: StateFlow<SettingsAdvancedState> = _state

    init {
        viewModelScope.launch(ioDispatcher) {
            merge(
                checkPreferenceCheckedState(isUseHttpsEnabled),
                checkPreferenceEnabledState(monitorConnectivity, rootNodeExists),
            ).collect{
                _state.update(it)
            }
        }
    }

    private fun checkPreferenceEnabledState(
        monitorConnectivity: MonitorConnectivity,
        rootNodeExists: RootNodeExists
    ) = combine(
        monitorConnectivity(),
        flowOf(rootNodeExists())
    ) { online, exists ->
        online && exists
    }.map { enabled ->
        { state: SettingsAdvancedState -> state.copy(useHttpsEnabled = enabled) }
    }

    private suspend fun checkPreferenceCheckedState(isUseHttpsEnabled: IsUseHttpsEnabled) =
        flowOf(isUseHttpsEnabled())
            .map { enabled ->
                { state: SettingsAdvancedState -> state.copy(useHttpsChecked = enabled) }
            }

    /**
     * Use https preference changed
     *
     * @param enabled new status
     */
    fun useHttpsPreferenceChanged(enabled: Boolean) {
        viewModelScope.launch {
            setUseHttps(enabled)
        }
    }
}
