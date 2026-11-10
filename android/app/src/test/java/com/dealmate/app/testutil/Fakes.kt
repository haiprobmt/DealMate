package com.dealmate.app.testutil

import com.dealmate.app.domain.model.AppError
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
import com.dealmate.app.domain.repository.AssistantRepository
import com.dealmate.app.domain.repository.HistoryRepository
import com.dealmate.app.domain.repository.ImagePreparer
import com.dealmate.app.domain.repository.MetaRepository
import com.dealmate.app.domain.repository.NotificationRepository
import com.dealmate.app.domain.repository.ProductRepository
import com.dealmate.app.domain.repository.SearchRepository
import com.dealmate.app.domain.repository.SettingsRepository
import com.dealmate.app.domain.repository.WishlistRepository
import com.dealmate.app.notifications.AlertPresenter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

// In-memory stand-ins for the domain repositories, shared by ViewModel tests and Robolectric UI tests.
// Defaults describe a configured app talking to a healthy demo backend (fixture data).

fun failure(kind: AppError.Kind, message: String? = null, code: String? = null): Outcome.Failure =
    Outcome.Failure(AppError(kind, message, code))

class FakeSearchRepository : SearchRepository {
    var outcome: Outcome<SearchResult> = Outcome.Success(Fixtures.search())
    val requests = mutableListOf<SearchRequest>()
    val recent = MutableStateFlow<List<RecentSearch>>(emptyList())

    override suspend fun search(request: SearchRequest): Outcome<SearchResult> {
        requests += request
        return outcome
    }

    override fun recentSearches(limit: Int): Flow<List<RecentSearch>> = recent
}

class FakeProductRepository : ProductRepository {
    var offersOutcome: Outcome<ProductOffers> = Outcome.Success(Fixtures.offers())
    var detailOutcome: Outcome<ProductDetail> = Outcome.Success(Fixtures.product())
    val offerRequests = mutableListOf<Pair<Long, Long?>>()
    val viewed = MutableStateFlow<List<ViewedProduct>>(emptyList())

    override suspend fun offers(productId: Long, variantId: Long?): Outcome<ProductOffers> {
        offerRequests += productId to variantId
        return offersOutcome
    }

    override suspend fun detail(productId: Long): Outcome<ProductDetail> = detailOutcome
    override fun recentlyViewed(limit: Int): Flow<List<ViewedProduct>> = viewed
}

class FakeWishlistRepository : WishlistRepository {
    val items = MutableStateFlow<List<WishlistItem>>(emptyList())

    /** What the "server" holds; [refresh] copies it into [items]. */
    var serverItems: List<WishlistItem>? = null
    var refreshOutcome: Outcome<Unit> = Outcome.Success(Unit)
    var mutationError: AppError? = null
    val added = mutableListOf<Pair<Long, Long?>>()
    val removed = mutableListOf<Long>()
    val targets = mutableListOf<Triple<Long, Long?, Long?>>()
    private var nextId = 900L

    override fun observe(): Flow<List<WishlistItem>> = items

    override suspend fun refresh(): Outcome<Unit> {
        if (refreshOutcome is Outcome.Success) serverItems?.let { items.value = it }
        return refreshOutcome
    }

    override suspend fun add(variantId: Long, targetPrice: Long?): Outcome<WishlistItem> {
        mutationError?.let { return Outcome.Failure(it) }
        added += variantId to targetPrice
        val item = WishlistItem(
            id = nextId++, productId = 1, variantId = variantId, title = "Variant $variantId", targetPrice = targetPrice,
        )
        items.update { listOf(item) + it }
        return Outcome.Success(item)
    }

    override suspend fun remove(wishlistId: Long): Outcome<Unit> {
        mutationError?.let { return Outcome.Failure(it) }
        removed += wishlistId
        items.update { list -> list.filter { it.id != wishlistId } }
        return Outcome.Success(Unit)
    }

    override suspend fun setTargetPrice(variantId: Long, alertId: Long?, targetPrice: Long?): Outcome<Unit> {
        mutationError?.let { return Outcome.Failure(it) }
        targets += Triple(variantId, alertId, targetPrice)
        items.update { list -> list.map { if (it.variantId == variantId) it.copy(targetPrice = targetPrice) else it } }
        return Outcome.Success(Unit)
    }

    override suspend fun alerts(): Outcome<List<PriceAlert>> = Outcome.Success(emptyList())
    override suspend fun productIdForVariant(variantId: Long): Long? = items.value.firstOrNull { it.variantId == variantId }?.productId
}

class FakeHistoryRepository : HistoryRepository {
    var outcome: Outcome<History> = Outcome.Success(Fixtures.history())
    var clearOutcome: Outcome<Unit> = Outcome.Success(Unit)
    var cleared = 0

    override suspend fun history(): Outcome<History> = outcome
    override suspend fun clear(): Outcome<Unit> {
        if (clearOutcome is Outcome.Success) {
            cleared++
            outcome = Outcome.Success(History())
        }
        return clearOutcome
    }
}

class FakeMetaRepository : MetaRepository {
    var healthOutcome: Outcome<Health> = Outcome.Success(Health("ok", demoMode = true, version = "0.1.0"))
    var meOutcome: Outcome<Me> = Outcome.Success(Me(1, "Gia đình", demoMode = true))
    var sourcesOutcome: Outcome<SourcesStatus> = Outcome.Success(Fixtures.sources())
    var exploreOutcome: Outcome<Explore> = Outcome.Success(Fixtures.explore())
    val exploreRequests = mutableListOf<Pair<String?, Marketplace?>>()

    override suspend fun health(): Outcome<Health> = healthOutcome
    override suspend fun me(): Outcome<Me> = meOutcome
    override suspend fun sourcesStatus(): Outcome<SourcesStatus> = sourcesOutcome
    override suspend fun explore(category: String?, marketplace: Marketplace?): Outcome<Explore> {
        exploreRequests += category to marketplace
        return exploreOutcome
    }
}

class FakeAssistantRepository : AssistantRepository {
    var outcome: Outcome<AssistantAnswer> = Outcome.Success(Fixtures.assistant())
    val questions = mutableListOf<Pair<String, List<Long>>>()

    override suspend fun ask(question: String, variantIds: List<Long>): Outcome<AssistantAnswer> {
        questions += question to variantIds
        return outcome
    }
}

class FakeNotificationRepository : NotificationRepository {
    var unreadOutcome: Outcome<List<AppNotification>> = Outcome.Success(emptyList())
    val read = mutableListOf<Long>()
    val devices = mutableListOf<String>()

    override suspend fun unread(): Outcome<List<AppNotification>> = unreadOutcome
    override suspend fun markRead(id: Long): Outcome<Unit> {
        read += id
        return Outcome.Success(Unit)
    }

    override suspend fun registerDevice(token: String): Outcome<Unit> {
        devices += token
        return Outcome.Success(Unit)
    }
}

class FakeSettingsRepository(
    initial: AppSettings = AppSettings(baseUrl = "https://dealmate.test/", token = "test-token"),
) : SettingsRepository {
    val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state

    override suspend fun current(): AppSettings = state.value
    override suspend fun setServer(baseUrl: String, token: String) = state.update { it.copy(baseUrl = baseUrl.trim(), token = token.trim()) }
    override suspend fun setPreferredMarketplaces(marketplaces: Set<Marketplace>) = state.update { it.copy(preferredMarketplaces = marketplaces) }
    override suspend fun setDefaultSort(sort: SortOption) = state.update { it.copy(defaultSort = sort) }
    override suspend fun setNotificationsEnabled(enabled: Boolean) = state.update { it.copy(notificationsEnabled = enabled) }
    override suspend fun setDeliveryLocation(location: String) = state.update { it.copy(deliveryLocation = location) }
    override suspend fun setLanguage(language: String) = state.update { it.copy(language = language) }
}

class FakeImagePreparer : ImagePreparer {
    var outcome: Outcome<String> = Outcome.Success("/tmp/upload.jpg")
    override suspend fun prepare(sourceUri: String): Outcome<String> = outcome
}

class FakeAlertPresenter : AlertPresenter {
    var allowed = true
    val shown = mutableListOf<Pair<AppNotification, Long?>>()

    override fun canPost(): Boolean = allowed
    override fun show(notification: AppNotification, productId: Long?) {
        shown += notification to productId
    }
}
