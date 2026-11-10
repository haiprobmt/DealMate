package com.dealmate.app.domain.repository

import com.dealmate.app.domain.model.AppNotification
import com.dealmate.app.domain.model.AppSettings
import com.dealmate.app.domain.model.AssistantAnswer
import com.dealmate.app.domain.model.Explore
import com.dealmate.app.domain.model.Health
import com.dealmate.app.domain.model.History
import com.dealmate.app.domain.model.Marketplace
import com.dealmate.app.domain.model.Me
import com.dealmate.app.domain.model.Outcome
import com.dealmate.app.domain.model.PriceAlert
import com.dealmate.app.domain.model.ProductDetail
import com.dealmate.app.domain.model.ProductOffers
import com.dealmate.app.domain.model.RecentSearch
import com.dealmate.app.domain.model.SearchRequest
import com.dealmate.app.domain.model.SearchResult
import com.dealmate.app.domain.model.SortOption
import com.dealmate.app.domain.model.SourcesStatus
import com.dealmate.app.domain.model.ViewedProduct
import com.dealmate.app.domain.model.WishlistItem
import kotlinx.coroutines.flow.Flow

interface SearchRepository {
    /** Runs the search; on connectivity failure falls back to the last cached result of the same request. */
    suspend fun search(request: SearchRequest): Outcome<SearchResult>
    fun recentSearches(limit: Int = 8): Flow<List<RecentSearch>>
}

interface ProductRepository {
    suspend fun offers(productId: Long, variantId: Long?): Outcome<ProductOffers>
    suspend fun detail(productId: Long): Outcome<ProductDetail>
    fun recentlyViewed(limit: Int = 10): Flow<List<ViewedProduct>>
}

interface WishlistRepository {
    /** Locally cached wishlist (works offline). */
    fun observe(): Flow<List<WishlistItem>>
    suspend fun refresh(): Outcome<Unit>
    suspend fun add(variantId: Long, targetPrice: Long?): Outcome<WishlistItem>
    suspend fun remove(wishlistId: Long): Outcome<Unit>
    /**
     * Sets the price-alert target of a saved variant, or with `targetPrice == null` removes the
     * existing alert [alertId]. The local cache is reloaded from the server afterwards.
     */
    suspend fun setTargetPrice(variantId: Long, alertId: Long?, targetPrice: Long?): Outcome<Unit>
    suspend fun alerts(): Outcome<List<PriceAlert>>
    /** product id of a saved variant, from the local cache (used to open notifications). */
    suspend fun productIdForVariant(variantId: Long): Long?
}

interface HistoryRepository {
    suspend fun history(): Outcome<History>
    /** Clears server history and the local recent-search / recently-viewed caches. */
    suspend fun clear(): Outcome<Unit>
}

interface MetaRepository {
    suspend fun health(): Outcome<Health>
    suspend fun me(): Outcome<Me>
    suspend fun sourcesStatus(): Outcome<SourcesStatus>
    suspend fun explore(category: String?, marketplace: Marketplace?): Outcome<Explore>
}

interface AssistantRepository {
    suspend fun ask(question: String, variantIds: List<Long>): Outcome<AssistantAnswer>
}

interface NotificationRepository {
    suspend fun unread(): Outcome<List<AppNotification>>
    suspend fun markRead(id: Long): Outcome<Unit>
    suspend fun registerDevice(token: String): Outcome<Unit>
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun current(): AppSettings
    suspend fun setServer(baseUrl: String, token: String)
    suspend fun setPreferredMarketplaces(marketplaces: Set<Marketplace>)
    suspend fun setDefaultSort(sort: SortOption)
    suspend fun setNotificationsEnabled(enabled: Boolean)
    suspend fun setDeliveryLocation(location: String)
    suspend fun setLanguage(language: String)
}

/** Turns a picked/captured image into an upload-ready JPEG (< 5 MB). Returns the file path. */
interface ImagePreparer {
    suspend fun prepare(sourceUri: String): Outcome<String>
}
