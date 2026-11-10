package com.dealmate.app.ui.navigation

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.dealmate.app.domain.model.SearchRequest
import com.dealmate.app.domain.model.SearchType

object Routes {
    const val HOME = "home"
    const val EXPLORE = "explore"
    const val WISHLIST = "wishlist"
    const val HISTORY = "history"
    const val SETTINGS = "settings"

    const val ARG_TYPE = "type"
    const val ARG_VALUE = "value"
    const val ARG_HINT = "hint"
    const val ARG_PRODUCT_ID = "productId"
    const val ARG_VARIANT_ID = "variantId"

    const val RESULTS = "results?$ARG_TYPE={$ARG_TYPE}&$ARG_VALUE={$ARG_VALUE}&$ARG_HINT={$ARG_HINT}"
    const val PRODUCT = "product/{$ARG_PRODUCT_ID}?$ARG_VARIANT_ID={$ARG_VARIANT_ID}"

    val topLevel = listOf(HOME, EXPLORE, WISHLIST, HISTORY, SETTINGS)

    fun results(request: SearchRequest): String =
        "results?$ARG_TYPE=${request.type.wire}&$ARG_VALUE=${Uri.encode(request.value)}&$ARG_HINT=${Uri.encode(request.hint.orEmpty())}"

    fun product(productId: Long, variantId: Long?): String = "product/$productId?$ARG_VARIANT_ID=${variantId ?: 0L}"

    fun searchRequestFrom(handle: SavedStateHandle): SearchRequest = SearchRequest(
        type = SearchType.fromWire(handle.get<String>(ARG_TYPE)),
        value = handle.get<String>(ARG_VALUE).orEmpty(),
        hint = handle.get<String>(ARG_HINT)?.takeIf { it.isNotBlank() },
    )
}
