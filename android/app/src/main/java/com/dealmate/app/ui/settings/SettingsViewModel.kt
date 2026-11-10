package com.dealmate.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dealmate.app.domain.model.AppError
import com.dealmate.app.domain.model.AppSettings
import com.dealmate.app.domain.model.Health
import com.dealmate.app.domain.model.Marketplace
import com.dealmate.app.domain.model.Me
import com.dealmate.app.domain.model.Outcome
import com.dealmate.app.domain.model.SortOption
import com.dealmate.app.domain.model.SourcesStatus
import com.dealmate.app.domain.repository.MetaRepository
import com.dealmate.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Checking : ConnectionState

    /** Server reachable (/health) and the token accepted (/me). */
    data class Connected(val health: Health, val me: Me) : ConnectionState

    /** [serverReachable] tells whether /health worked, i.e. whether the problem is the token. */
    data class Failed(val error: AppError, val serverReachable: Boolean) : ConnectionState
}

data class SettingsUiState(
    val loaded: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val baseUrlInput: String = "",
    val tokenInput: String = "",
    val locationInput: String = "",
    val connection: ConnectionState = ConnectionState.Idle,
    val sourcesLoading: Boolean = false,
    val sources: SourcesStatus? = null,
    val sourcesError: AppError? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val metaRepository: MetaRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val initial = settingsRepository.settings.first()
            _state.update {
                it.copy(
                    loaded = true, settings = initial, baseUrlInput = initial.baseUrl,
                    tokenInput = initial.token, locationInput = initial.deliveryLocation,
                )
            }
            if (initial.isConfigured) loadSources()
            settingsRepository.settings.collect { s -> _state.update { it.copy(settings = s) } }
        }
    }

    fun setBaseUrlInput(value: String) = _state.update { it.copy(baseUrlInput = value, connection = ConnectionState.Idle) }
    fun setTokenInput(value: String) = _state.update { it.copy(tokenInput = value, connection = ConnectionState.Idle) }

    /** Saves the server address + token, then verifies them with /health and /me. */
    fun saveAndCheckConnection() {
        val current = _state.value
        _state.update { it.copy(connection = ConnectionState.Checking) }
        viewModelScope.launch {
            settingsRepository.setServer(current.baseUrlInput, current.tokenInput)
            val health = when (val outcome = metaRepository.health()) {
                is Outcome.Success -> outcome.data
                is Outcome.Failure -> {
                    _state.update { it.copy(connection = ConnectionState.Failed(outcome.error, serverReachable = false)) }
                    return@launch
                }
            }
            when (val outcome = metaRepository.me()) {
                is Outcome.Success -> {
                    _state.update { it.copy(connection = ConnectionState.Connected(health, outcome.data)) }
                    loadSources()
                }
                is Outcome.Failure ->
                    _state.update { it.copy(connection = ConnectionState.Failed(outcome.error, serverReachable = true)) }
            }
        }
    }

    fun loadSources() {
        _state.update { it.copy(sourcesLoading = true, sourcesError = null) }
        viewModelScope.launch {
            when (val outcome = metaRepository.sourcesStatus()) {
                is Outcome.Success -> _state.update { it.copy(sourcesLoading = false, sources = outcome.data) }
                is Outcome.Failure -> _state.update { it.copy(sourcesLoading = false, sourcesError = outcome.error, sources = null) }
            }
        }
    }

    fun toggleMarketplace(marketplace: Marketplace) {
        val current = _state.value.settings.preferredMarketplaces
        val next = if (marketplace in current) current - marketplace else current + marketplace
        // At least one marketplace must stay selected, otherwise there is nothing to search.
        if (next.isEmpty()) return
        viewModelScope.launch { settingsRepository.setPreferredMarketplaces(next) }
    }

    fun setDefaultSort(sort: SortOption) {
        viewModelScope.launch { settingsRepository.setDefaultSort(sort) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setNotificationsEnabled(enabled) }
    }

    fun setLocation(value: String) {
        val text = value.take(120)
        _state.update { it.copy(locationInput = text) }
        viewModelScope.launch { settingsRepository.setDeliveryLocation(text) }
    }

    fun setLanguage(language: String) {
        viewModelScope.launch { settingsRepository.setLanguage(language) }
    }
}
