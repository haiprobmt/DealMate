package com.dealmate.app.ui.results

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dealmate.app.domain.model.AppError
import com.dealmate.app.domain.model.Marketplace
import com.dealmate.app.domain.model.Outcome
import com.dealmate.app.domain.model.ProductGroup
import com.dealmate.app.domain.model.SearchRequest
import com.dealmate.app.domain.model.SearchResult
import com.dealmate.app.domain.model.SearchType
import com.dealmate.app.domain.repository.SearchRepository
import com.dealmate.app.domain.repository.SettingsRepository
import com.dealmate.app.domain.usecase.ResultFilter
import com.dealmate.app.domain.usecase.ResultFilters
import com.dealmate.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class ResultsUiState(
    val request: SearchRequest,
    val loading: Boolean = true,
    val error: AppError? = null,
    val result: SearchResult? = null,
    /** The network failed and this is the last saved copy of the same search. */
    val fromCache: Boolean = false,
    val cachedAt: Instant? = null,
    val filter: ResultFilter = ResultFilter(),
) {
    val groups: List<ProductGroup> get() = result?.let { ResultFilters.apply(it.groups, filter) }.orEmpty()

    /** Similar products are a separate section; marketplace / budget filters apply, match filter does not. */
    val similarGroups: List<ProductGroup>
        get() = result?.let { ResultFilters.apply(it.similarGroups, filter.copy(matchTypes = emptySet())) }.orEmpty()

    /** The backend's best offer is shown only while the active filter has not hidden it. */
    val bestVisible: Boolean
        get() {
            val best = result?.best ?: return false
            return groups.any { g -> g.variantId == best.variantId && g.offers.any { it.id == best.offerId } }
        }

    val bestGroup: ProductGroup? get() = result?.best?.let { best -> result.groups.firstOrNull { it.variantId == best.variantId } }

    val availableMarketplaces: List<Marketplace>
        get() = result?.let { r ->
            (r.sources.map { it.marketplace } + (r.groups + r.similarGroups).flatMap { it.marketplaces })
                .distinct().filter { it != Marketplace.UNKNOWN }
        }.orEmpty()

    /** Variants the assistant may be asked about (best matches first, max 5 per the API). */
    val assistantVariantIds: List<Long> get() = result?.groups.orEmpty().map { it.variantId }.distinct().take(5)

    val title: String
        get() = result?.query?.raw?.takeIf { it.isNotBlank() }
            ?: if (request.type == SearchType.IMAGE) request.hint.orEmpty() else request.value
}

@HiltViewModel
class ResultsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val searchRepository: SearchRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ResultsUiState(request = Routes.searchRequestFrom(savedStateHandle)))
    val state: StateFlow<ResultsUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch {
            val sort = settingsRepository.current().defaultSort
            _state.update { it.copy(filter = it.filter.copy(sort = sort)) }
        }
        run()
    }

    fun retry() = run()

    /** Runs one of the backend's clarification suggestions as a new text search. */
    fun searchSuggestion(text: String) {
        _state.update { it.copy(request = SearchRequest(SearchType.TEXT, text), filter = ResultFilter(sort = it.filter.sort)) }
        run()
    }

    fun setFilter(filter: ResultFilter) = _state.update { it.copy(filter = filter) }

    private fun run() {
        job?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            when (val outcome = searchRepository.search(_state.value.request)) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        loading = false, error = null, result = outcome.data,
                        fromCache = outcome.fromCache, cachedAt = outcome.cachedAt,
                    )
                }
                is Outcome.Failure -> _state.update { it.copy(loading = false, error = outcome.error, result = null) }
            }
        }
    }
}
