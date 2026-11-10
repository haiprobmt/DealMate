package com.dealmate.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dealmate.app.domain.model.AppError
import com.dealmate.app.domain.model.Outcome
import com.dealmate.app.domain.model.RecentSearch
import com.dealmate.app.domain.model.SearchRequest
import com.dealmate.app.domain.model.SearchType
import com.dealmate.app.domain.model.ViewedProduct
import com.dealmate.app.domain.repository.ImagePreparer
import com.dealmate.app.domain.repository.ProductRepository
import com.dealmate.app.domain.repository.SearchRepository
import com.dealmate.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val method: SearchType = SearchType.TEXT,
    val query: String = "",
    val url: String = "",
    val imagePath: String? = null,
    val imageHint: String = "",
    val preparingImage: Boolean = false,
    val imageError: AppError? = null,
    val configured: Boolean = true,
    val recentSearches: List<RecentSearch> = emptyList(),
    val recentlyViewed: List<ViewedProduct> = emptyList(),
) {
    /** The request the search button would run right now, or null when the input is not usable. */
    val request: SearchRequest?
        get() = when (method) {
            SearchType.TEXT -> query.trim().takeIf { it.length >= 2 }?.let { SearchRequest(SearchType.TEXT, it) }
            SearchType.URL -> url.trim().takeIf { it.length >= 10 }?.let { SearchRequest(SearchType.URL, it) }
            SearchType.IMAGE -> imagePath?.let { SearchRequest(SearchType.IMAGE, it, imageHint.trim().ifEmpty { null }) }
        }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    searchRepository: SearchRepository,
    productRepository: ProductRepository,
    settingsRepository: SettingsRepository,
    private val imagePreparer: ImagePreparer,
) : ViewModel() {

    private val input = MutableStateFlow(HomeUiState())

    val state: StateFlow<HomeUiState> = combine(
        input,
        searchRepository.recentSearches(),
        productRepository.recentlyViewed(),
        settingsRepository.settings.map { it.isConfigured },
    ) { current, recent, viewed, configured ->
        current.copy(recentSearches = recent, recentlyViewed = viewed, configured = configured)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setMethod(method: SearchType) = input.update { it.copy(method = method) }
    fun setQuery(query: String) = input.update { it.copy(query = query.take(300)) }
    fun setUrl(url: String) = input.update { it.copy(url = url.take(2000)) }
    fun setImageHint(hint: String) = input.update { it.copy(imageHint = hint.take(200)) }
    fun clearImage() = input.update { it.copy(imagePath = null, imageError = null) }

    fun onImagePicked(uri: String) {
        input.update { it.copy(preparingImage = true, imageError = null) }
        viewModelScope.launch {
            when (val outcome = imagePreparer.prepare(uri)) {
                is Outcome.Success -> input.update { it.copy(preparingImage = false, imagePath = outcome.data) }
                is Outcome.Failure -> input.update { it.copy(preparingImage = false, imageError = outcome.error) }
            }
        }
    }
}
