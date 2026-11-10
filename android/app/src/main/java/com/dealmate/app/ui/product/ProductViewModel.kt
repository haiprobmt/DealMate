package com.dealmate.app.ui.product

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dealmate.app.R
import com.dealmate.app.domain.model.AppError
import com.dealmate.app.domain.model.Offer
import com.dealmate.app.domain.model.Outcome
import com.dealmate.app.domain.model.ProductOffers
import com.dealmate.app.domain.model.VariantSummary
import com.dealmate.app.domain.repository.ProductRepository
import com.dealmate.app.domain.repository.WishlistRepository
import com.dealmate.app.ui.common.UiMessage
import com.dealmate.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProductUiState(
    val productId: Long,
    val variantId: Long?,
    val loading: Boolean = true,
    val error: AppError? = null,
    val product: ProductOffers? = null,
    val variants: List<VariantSummary> = emptyList(),
    val wishlistBusy: Boolean = false,
    val message: UiMessage? = null,
) {
    /** The backend's best offer; never one without a final price. */
    val bestOffer: Offer?
        get() = product?.let { p -> p.offers.firstOrNull { it.id == p.bestOfferId && it.price.hasFinalPrice } }
}

@HiltViewModel
class ProductViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val productRepository: ProductRepository,
    private val wishlistRepository: WishlistRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ProductUiState(
            productId = savedStateHandle.get<Long>(Routes.ARG_PRODUCT_ID) ?: 0L,
            variantId = savedStateHandle.get<Long>(Routes.ARG_VARIANT_ID)?.takeIf { it > 0 },
        ),
    )
    val state: StateFlow<ProductUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        load()
        loadVariants()
    }

    fun load() {
        loadJob?.cancel()
        _state.update { it.copy(loading = it.product == null, error = null) }
        loadJob = viewModelScope.launch {
            val current = _state.value
            when (val outcome = productRepository.offers(current.productId, current.variantId)) {
                is Outcome.Success -> _state.update {
                    it.copy(loading = false, error = null, product = outcome.data, variantId = outcome.data.variantId)
                }
                is Outcome.Failure -> _state.update { it.copy(loading = false, error = outcome.error, product = null) }
            }
        }
    }

    private fun loadVariants() {
        viewModelScope.launch {
            // Optional extra: the variant switcher simply stays hidden when this fails.
            val outcome = productRepository.detail(_state.value.productId)
            if (outcome is Outcome.Success) _state.update { it.copy(variants = outcome.data.variants) }
        }
    }

    fun selectVariant(variantId: Long) {
        if (variantId == _state.value.variantId) return
        _state.update { it.copy(variantId = variantId, product = null) }
        load()
    }

    fun addToWishlist(targetPrice: Long?) {
        val product = _state.value.product ?: return
        _state.update { it.copy(wishlistBusy = true) }
        viewModelScope.launch {
            when (val outcome = wishlistRepository.add(product.variantId, targetPrice)) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        wishlistBusy = false,
                        product = it.product?.copy(
                            inWishlist = true, wishlistId = outcome.data.id, targetPrice = outcome.data.targetPrice,
                        ),
                        message = UiMessage.Text(R.string.wishlist_added),
                    )
                }
                is Outcome.Failure -> _state.update { it.copy(wishlistBusy = false, message = UiMessage.Failure(outcome.error)) }
            }
        }
    }

    fun removeFromWishlist() {
        val wishlistId = _state.value.product?.wishlistId ?: return
        _state.update { it.copy(wishlistBusy = true) }
        viewModelScope.launch {
            when (val outcome = wishlistRepository.remove(wishlistId)) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        wishlistBusy = false,
                        product = it.product?.copy(inWishlist = false, wishlistId = null, targetPrice = null),
                        message = UiMessage.Text(R.string.wishlist_removed),
                    )
                }
                is Outcome.Failure -> _state.update { it.copy(wishlistBusy = false, message = UiMessage.Failure(outcome.error)) }
            }
        }
    }

    /** Changes the alert price of an already saved variant, then reloads the server's state. */
    fun setTargetPrice(targetPrice: Long) {
        val product = _state.value.product ?: return
        _state.update { it.copy(wishlistBusy = true) }
        viewModelScope.launch {
            when (val outcome = wishlistRepository.setTargetPrice(product.variantId, alertId = null, targetPrice = targetPrice)) {
                is Outcome.Success -> {
                    _state.update {
                        it.copy(
                            wishlistBusy = false,
                            product = it.product?.copy(targetPrice = targetPrice),
                            message = UiMessage.Text(R.string.target_saved),
                        )
                    }
                }
                is Outcome.Failure -> _state.update { it.copy(wishlistBusy = false, message = UiMessage.Failure(outcome.error)) }
            }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}
