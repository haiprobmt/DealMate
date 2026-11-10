package com.dealmate.app.data.repository

import com.dealmate.app.data.local.RecentlyViewedEntity
import com.dealmate.app.data.local.SearchDao
import com.dealmate.app.data.local.ViewedDao
import com.dealmate.app.data.local.WishlistDao
import com.dealmate.app.data.local.WishlistEntity
import com.dealmate.app.data.remote.DealMateApi
import com.dealmate.app.data.remote.apiCall
import com.dealmate.app.data.remote.apiCallUnit
import com.dealmate.app.data.remote.dto.AssistantRequestDto
import com.dealmate.app.data.remote.dto.DealMateJson
import com.dealmate.app.data.remote.dto.DeviceRegisterDto
import com.dealmate.app.data.remote.dto.PriceAlertCreateDto
import com.dealmate.app.data.remote.dto.WishlistCreateDto
import com.dealmate.app.data.remote.dto.WishlistItemDto
import com.dealmate.app.domain.model.AppNotification
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
import com.dealmate.app.domain.model.SourcesStatus
import com.dealmate.app.domain.model.ViewedProduct
import com.dealmate.app.domain.model.WishlistItem
import com.dealmate.app.domain.model.map
import com.dealmate.app.domain.repository.AssistantRepository
import com.dealmate.app.domain.repository.HistoryRepository
import com.dealmate.app.domain.repository.MetaRepository
import com.dealmate.app.domain.repository.NotificationRepository
import com.dealmate.app.domain.repository.ProductRepository
import com.dealmate.app.domain.repository.WishlistRepository
import com.dealmate.app.domain.usecase.ResultFilters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

class ProductRepositoryImpl @Inject constructor(
    private val api: DealMateApi,
    private val viewedDao: ViewedDao,
    private val clock: Clock,
) : ProductRepository {

    override suspend fun offers(productId: Long, variantId: Long?): Outcome<ProductOffers> =
        apiCall { api.productOffers(productId, variantId).toDomain() }.also { outcome ->
            if (outcome is Outcome.Success) rememberViewed(outcome.data)
        }

    private suspend fun rememberViewed(p: ProductOffers) {
        runCatching {
            viewedDao.upsert(
                RecentlyViewedEntity(
                    variantId = p.variantId,
                    productId = p.productId,
                    title = p.title,
                    imageUrl = p.imageUrl,
                    lowestFinalPrice = ResultFilters.bestOffer(p.offers)?.price?.finalPrice,
                    viewedAt = clock.millis(),
                ),
            )
            viewedDao.trim()
        }
    }

    override suspend fun detail(productId: Long): Outcome<ProductDetail> = apiCall { api.product(productId).toDomain() }

    override fun recentlyViewed(limit: Int): Flow<List<ViewedProduct>> = viewedDao.recent(limit).map { rows ->
        rows.map {
            ViewedProduct(it.productId, it.variantId, it.title, it.imageUrl, it.lowestFinalPrice, Instant.ofEpochMilli(it.viewedAt))
        }
    }
}

class WishlistRepositoryImpl @Inject constructor(
    private val api: DealMateApi,
    private val dao: WishlistDao,
) : WishlistRepository {

    override fun observe(): Flow<List<WishlistItem>> = dao.observe().map { rows ->
        rows.mapNotNull { row ->
            runCatching { DealMateJson.decodeFromString(WishlistItemDto.serializer(), row.json).toDomain() }.getOrNull()
        }
    }

    private fun entity(dto: WishlistItemDto, position: Int) = WishlistEntity(
        id = dto.id,
        variantId = dto.variantId,
        productId = dto.productId,
        json = DealMateJson.encodeToString(WishlistItemDto.serializer(), dto),
        position = position,
    )

    override suspend fun refresh(): Outcome<Unit> = apiCall { api.wishlist() }.map { list ->
        dao.replaceAll(list.items.mapIndexed { index, dto -> entity(dto, index) })
    }

    override suspend fun add(variantId: Long, targetPrice: Long?): Outcome<WishlistItem> =
        apiCall { api.addWishlist(WishlistCreateDto(variantId, targetPrice)) }.map { dto ->
            // Newest first, like the server list; a later refresh() restores the exact server order.
            dao.insertAll(listOf(entity(dto, dao.minPosition() - 1)))
            dto.toDomain()
        }

    override suspend fun remove(wishlistId: Long): Outcome<Unit> =
        apiCallUnit { api.deleteWishlist(wishlistId) }.map { dao.delete(wishlistId) }

    override suspend fun setTargetPrice(variantId: Long, alertId: Long?, targetPrice: Long?): Outcome<Unit> {
        val change: Outcome<Unit> = if (targetPrice != null) {
            apiCall { api.createAlert(PriceAlertCreateDto(variantId, targetPrice)) }.map { }
        } else {
            if (alertId == null) return Outcome.Success(Unit)
            apiCallUnit { api.deleteAlert(alertId) }
        }
        if (change is Outcome.Failure) return change
        // The server owns the resulting state (target price, alert id): reload instead of guessing.
        return refresh()
    }

    override suspend fun alerts(): Outcome<List<PriceAlert>> = apiCall { api.alerts().items.map { it.toDomain() } }

    override suspend fun productIdForVariant(variantId: Long): Long? =
        runCatching { dao.productIdForVariant(variantId) }.getOrNull()
}

class HistoryRepositoryImpl @Inject constructor(
    private val api: DealMateApi,
    private val searchDao: SearchDao,
    private val viewedDao: ViewedDao,
) : HistoryRepository {
    override suspend fun history(): Outcome<History> = apiCall { api.history().toDomain() }

    override suspend fun clear(): Outcome<Unit> = apiCallUnit { api.clearHistory() }.map {
        searchDao.clearRecent()
        searchDao.clearCached()
        viewedDao.clear()
    }
}

class MetaRepositoryImpl @Inject constructor(private val api: DealMateApi) : MetaRepository {
    override suspend fun health(): Outcome<Health> = apiCall { api.health().toDomain() }
    override suspend fun me(): Outcome<Me> = apiCall { api.me().toDomain() }
    override suspend fun sourcesStatus(): Outcome<SourcesStatus> = apiCall { api.sourcesStatus().toDomain() }
    override suspend fun explore(category: String?, marketplace: Marketplace?): Outcome<Explore> =
        apiCall { api.explore(category, marketplace?.wire).toDomain() }
}

class AssistantRepositoryImpl @Inject constructor(private val api: DealMateApi) : AssistantRepository {
    override suspend fun ask(question: String, variantIds: List<Long>): Outcome<AssistantAnswer> =
        apiCall { api.ask(AssistantRequestDto(question.trim(), variantIds.distinct().take(5))).toDomain() }
}

class NotificationRepositoryImpl @Inject constructor(private val api: DealMateApi) : NotificationRepository {
    override suspend fun unread(): Outcome<List<AppNotification>> =
        apiCall { api.notifications(unreadOnly = true).items.map { it.toDomain() } }

    override suspend fun markRead(id: Long): Outcome<Unit> = apiCallUnit { api.markNotificationRead(id) }

    override suspend fun registerDevice(token: String): Outcome<Unit> =
        apiCallUnit { api.registerDevice(DeviceRegisterDto(token)) }
}
