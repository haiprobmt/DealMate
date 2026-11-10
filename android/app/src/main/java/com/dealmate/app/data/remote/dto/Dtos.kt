@file:UseSerializers(
    MarketplaceSerializer::class,
    MatchTypeSerializer::class,
    PriceConfidenceSerializer::class,
    ShippingStatusSerializer::class,
)

package com.dealmate.app.data.remote.dto

import com.dealmate.app.domain.model.AppNotification
import com.dealmate.app.domain.model.AppliedVoucher
import com.dealmate.app.domain.model.AssistantAnswer
import com.dealmate.app.domain.model.BestOffer
import com.dealmate.app.domain.model.Clarification
import com.dealmate.app.domain.model.Explore
import com.dealmate.app.domain.model.Health
import com.dealmate.app.domain.model.History
import com.dealmate.app.domain.model.InterpretedQuery
import com.dealmate.app.domain.model.Marketplace
import com.dealmate.app.domain.model.MatchInfo
import com.dealmate.app.domain.model.MatchType
import com.dealmate.app.domain.model.Me
import com.dealmate.app.domain.model.Offer
import com.dealmate.app.domain.model.PriceAlert
import com.dealmate.app.domain.model.PriceBreakdown
import com.dealmate.app.domain.model.PriceConfidence
import com.dealmate.app.domain.model.PriceDrop
import com.dealmate.app.domain.model.PricePoint
import com.dealmate.app.domain.model.ProductAttributes
import com.dealmate.app.domain.model.ProductDetail
import com.dealmate.app.domain.model.ProductGroup
import com.dealmate.app.domain.model.ProductOffers
import com.dealmate.app.domain.model.QuerySpec
import com.dealmate.app.domain.model.SearchHistoryItem
import com.dealmate.app.domain.model.SearchResult
import com.dealmate.app.domain.model.SearchType
import com.dealmate.app.domain.model.Seller
import com.dealmate.app.domain.model.ShippingStatus
import com.dealmate.app.domain.model.SkippedVoucher
import com.dealmate.app.domain.model.SourceResult
import com.dealmate.app.domain.model.SourceStatus
import com.dealmate.app.domain.model.SourcesStatus
import com.dealmate.app.domain.model.VariantSummary
import com.dealmate.app.domain.model.ViewedProduct
import com.dealmate.app.domain.model.Voucher
import com.dealmate.app.domain.model.WishlistItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Instant

// Field names mirror backend/app/schemas/{api,domain}.py exactly (snake_case, integer VND).
// Every field has a default so that a missing/null value never crashes parsing.

@Serializable
data class ProductAttributesDto(
    val brand: String? = null,
    val category: String? = null,
    @SerialName("product_line") val productLine: String? = null,
    val model: String? = null,
    @SerialName("volume_ml") val volumeMl: Double? = null,
    @SerialName("weight_g") val weightG: Double? = null,
    val count: Int? = null,
    @SerialName("pack_quantity") val packQuantity: Int? = null,
    val size: String? = null,
    @SerialName("storage_gb") val storageGb: Int? = null,
    val color: String? = null,
    val condition: String? = null,
    val origin: String? = null,
) {
    fun toDomain() = ProductAttributes(
        brand, category, productLine, model, volumeMl, weightG, count, packQuantity, size, storageGb,
        color, condition, origin,
    )
}

@Serializable
data class MatchResultDto(
    @SerialName("match_type") val matchType: MatchType = MatchType.UNKNOWN,
    val confidence: Double = 0.0,
    @SerialName("matched_attributes") val matchedAttributes: List<String> = emptyList(),
    @SerialName("different_attributes") val differentAttributes: List<String> = emptyList(),
    @SerialName("unknown_attributes") val unknownAttributes: List<String> = emptyList(),
    val reason: String = "",
    @SerialName("decided_by") val decidedBy: String = "rules",
) {
    fun toDomain() = MatchInfo(
        matchType, confidence, matchedAttributes, differentAttributes, unknownAttributes, reason, decidedBy,
    )
}

@Serializable
data class VoucherDto(
    val code: String? = null,
    val source: String = "",
    val kind: String = "",
    val value: Long = 0,
    @SerialName("max_discount") val maxDiscount: Long? = null,
    @SerialName("min_spend") val minSpend: Long = 0,
    val verified: Boolean = false,
    val requires: String? = null,
    val description: String? = null,
) {
    fun toDomain() = Voucher(code, source, kind, value, maxDiscount, minSpend, verified, requires, description)
}

@Serializable
data class AppliedVoucherDto(val voucher: VoucherDto = VoucherDto(), val discount: Long = 0, val estimated: Boolean = false)

@Serializable
data class SkippedVoucherDto(val voucher: VoucherDto = VoucherDto(), val reason: String = "")

@Serializable
data class PriceBreakdownDto(
    val currency: String = "VND",
    val quantity: Int = 1,
    @SerialName("listed_price") val listedPrice: Long = 0,
    val subtotal: Long = 0,
    @SerialName("voucher_discount") val voucherDiscount: Long = 0,
    @SerialName("applied_vouchers") val appliedVouchers: List<AppliedVoucherDto> = emptyList(),
    @SerialName("skipped_vouchers") val skippedVouchers: List<SkippedVoucherDto> = emptyList(),
    @SerialName("shipping_fee") val shippingFee: Long? = null,
    @SerialName("shipping_status") val shippingStatus: ShippingStatus = ShippingStatus.UNKNOWN,
    @SerialName("mandatory_fees") val mandatoryFees: Long = 0,
    @SerialName("final_price") val finalPrice: Long? = null,
    @SerialName("known_subtotal") val knownSubtotal: Long = 0,
    val confidence: PriceConfidence = PriceConfidence.UNKNOWN,
    val notes: List<String> = emptyList(),
) {
    fun toDomain() = PriceBreakdown(
        quantity = quantity,
        listedPrice = listedPrice,
        subtotal = subtotal,
        voucherDiscount = voucherDiscount,
        appliedVouchers = appliedVouchers.map { AppliedVoucher(it.voucher.toDomain(), it.discount, it.estimated) },
        skippedVouchers = skippedVouchers.map { SkippedVoucher(it.voucher.toDomain(), it.reason) },
        shippingFee = shippingFee,
        shippingStatus = shippingStatus,
        mandatoryFees = mandatoryFees,
        // Defensive: a final price is only trusted together with a confidence that allows one.
        finalPrice = finalPrice.takeIf { confidence == PriceConfidence.VERIFIED || confidence == PriceConfidence.ESTIMATED },
        knownSubtotal = knownSubtotal,
        confidence = confidence,
        notes = notes,
    )
}

@Serializable
data class SellerDto(
    val name: String = "",
    val rating: Double? = null,
    @SerialName("is_official_store") val isOfficialStore: Boolean? = null,
    val location: String? = null,
) {
    fun toDomain() = Seller(name, rating, isOfficialStore, location)
}

@Serializable
data class OfferDto(
    val id: Long = 0,
    val marketplace: Marketplace = Marketplace.UNKNOWN,
    @SerialName("marketplace_name") val marketplaceName: String = "",
    @SerialName("is_mock") val isMock: Boolean = false,
    val title: String = "",
    val url: String = "",
    @SerialName("image_url") val imageUrl: String? = null,
    val seller: SellerDto = SellerDto(),
    val price: PriceBreakdownDto = PriceBreakdownDto(),
    @SerialName("original_price") val originalPrice: Long? = null,
    val match: MatchResultDto = MatchResultDto(),
    val rating: Double? = null,
    @SerialName("rating_count") val ratingCount: Int? = null,
    @SerialName("sold_count") val soldCount: Int? = null,
    @SerialName("in_stock") val inStock: Boolean? = null,
    @Serializable(with = LenientInstantSerializer::class)
    @SerialName("retrieved_at") val retrievedAt: Instant? = null,
    @SerialName("is_stale") val isStale: Boolean = false,
    @SerialName("over_budget") val overBudget: Boolean = false,
) {
    fun toDomain() = Offer(
        id = id,
        marketplace = marketplace,
        marketplaceName = marketplaceName.ifBlank { marketplace.displayName },
        isMock = isMock,
        title = title,
        url = url,
        imageUrl = imageUrl,
        seller = seller.toDomain(),
        price = price.toDomain(),
        originalPrice = originalPrice,
        match = match.toDomain(),
        rating = rating,
        ratingCount = ratingCount,
        soldCount = soldCount,
        inStock = inStock,
        retrievedAt = retrievedAt,
        isStale = isStale,
        overBudget = overBudget,
    )
}

@Serializable
data class ProductGroupDto(
    @SerialName("product_id") val productId: Long = 0,
    @SerialName("variant_id") val variantId: Long = 0,
    val title: String = "",
    @SerialName("image_url") val imageUrl: String? = null,
    val attributes: ProductAttributesDto = ProductAttributesDto(),
    val match: MatchResultDto = MatchResultDto(),
    val offers: List<OfferDto> = emptyList(),
    @SerialName("best_offer_id") val bestOfferId: Long? = null,
    @SerialName("lowest_final_price") val lowestFinalPrice: Long? = null,
    @SerialName("lowest_price_confidence") val lowestPriceConfidence: PriceConfidence? = null,
    @SerialName("offer_count") val offerCount: Int = 0,
    @SerialName("in_wishlist") val inWishlist: Boolean = false,
) {
    fun toDomain() = ProductGroup(
        productId = productId,
        variantId = variantId,
        title = title,
        imageUrl = imageUrl,
        attributes = attributes.toDomain(),
        match = match.toDomain(),
        offers = offers.map { it.toDomain() },
        bestOfferId = bestOfferId,
        lowestFinalPrice = lowestFinalPrice,
        lowestPriceConfidence = lowestPriceConfidence,
        offerCount = if (offerCount > 0) offerCount else offers.size,
        inWishlist = inWishlist,
    )
}

@Serializable
data class BestOfferDto(
    @SerialName("variant_id") val variantId: Long = 0,
    @SerialName("offer_id") val offerId: Long = 0,
    @SerialName("final_price") val finalPrice: Long = 0,
    val confidence: PriceConfidence = PriceConfidence.UNKNOWN,
    val marketplace: Marketplace = Marketplace.UNKNOWN,
    @SerialName("marketplace_name") val marketplaceName: String = "",
    val explanation: String = "",
    val caveat: String? = null,
) {
    fun toDomain() = BestOffer(
        variantId, offerId, finalPrice, confidence, marketplace,
        marketplaceName.ifBlank { marketplace.displayName }, explanation, caveat,
    )
}

@Serializable
data class ProductQueryDto(
    val brand: String? = null,
    @SerialName("product_name") val productName: String? = null,
    val model: String? = null,
    val category: String? = null,
    val variant: String? = null,
    val volume: String? = null,
    val weight: String? = null,
    val size: String? = null,
    val quantity: Int? = null,
    val count: Int? = null,
    val storage: String? = null,
    val color: String? = null,
    @SerialName("max_price") val maxPrice: Long? = null,
    @SerialName("min_price") val minPrice: Long? = null,
    val constraints: List<String> = emptyList(),
    @SerialName("uncertain_fields") val uncertainFields: List<String> = emptyList(),
) {
    fun toDomain() = QuerySpec(
        brand, productName, model, category, variant, volume, weight, size, quantity, count, storage,
        color, maxPrice, minPrice, constraints, uncertainFields,
    )
}

@Serializable
data class InterpretedQueryDto(
    val raw: String = "",
    val interpreter: String = "rules",
    val spec: ProductQueryDto = ProductQueryDto(),
    val notes: List<String> = emptyList(),
) {
    fun toDomain() = InterpretedQuery(raw, interpreter, spec.toDomain(), notes)
}

@Serializable
data class SourceResultDto(
    val marketplace: Marketplace = Marketplace.UNKNOWN,
    @SerialName("marketplace_name") val marketplaceName: String = "",
    val status: String = "",
    @SerialName("is_mock") val isMock: Boolean = false,
    @SerialName("offers_found") val offersFound: Int = 0,
    @SerialName("elapsed_ms") val elapsedMs: Long = 0,
    val message: String? = null,
) {
    fun toDomain() = SourceResult(
        marketplace, marketplaceName.ifBlank { marketplace.displayName }, status, isMock, offersFound, elapsedMs, message,
    )
}

@Serializable
data class ClarificationDto(
    val needed: Boolean = false,
    val message: String? = null,
    val suggestions: List<String> = emptyList(),
) {
    fun toDomain() = Clarification(needed, message, suggestions)
}

@Serializable
data class SearchResponseDto(
    @SerialName("search_id") val searchId: Long = 0,
    @SerialName("search_type") val searchType: String = "text",
    val query: InterpretedQueryDto = InterpretedQueryDto(),
    @SerialName("demo_mode") val demoMode: Boolean = false,
    val partial: Boolean = false,
    val sources: List<SourceResultDto> = emptyList(),
    val best: BestOfferDto? = null,
    val groups: List<ProductGroupDto> = emptyList(),
    @SerialName("similar_groups") val similarGroups: List<ProductGroupDto> = emptyList(),
    @SerialName("excluded_count") val excludedCount: Int = 0,
    @SerialName("over_budget_count") val overBudgetCount: Int = 0,
    val clarification: ClarificationDto = ClarificationDto(),
    @Serializable(with = LenientInstantSerializer::class)
    @SerialName("generated_at") val generatedAt: Instant? = null,
) {
    fun toDomain() = SearchResult(
        searchId = searchId,
        searchType = SearchType.fromWire(searchType),
        query = query.toDomain(),
        demoMode = demoMode,
        partial = partial,
        sources = sources.map { it.toDomain() },
        best = best?.toDomain(),
        groups = groups.map { it.toDomain() },
        similarGroups = similarGroups.map { it.toDomain() },
        excludedCount = excludedCount,
        overBudgetCount = overBudgetCount,
        clarification = clarification.toDomain(),
        generatedAt = generatedAt,
    )
}

@Serializable
data class PricePointDto(
    @Serializable(with = LenientInstantSerializer::class)
    @SerialName("recorded_at") val recordedAt: Instant? = null,
    @SerialName("lowest_final_price") val lowestFinalPrice: Long? = null,
    val confidence: PriceConfidence? = null,
    val marketplace: Marketplace? = null,
    @SerialName("is_mock") val isMock: Boolean = false,
) {
    fun toDomain() = PricePoint(recordedAt, lowestFinalPrice, confidence, marketplace, isMock)
}

@Serializable
data class VariantSummaryDto(
    @SerialName("variant_id") val variantId: Long = 0,
    val title: String = "",
    val attributes: ProductAttributesDto = ProductAttributesDto(),
    @SerialName("lowest_final_price") val lowestFinalPrice: Long? = null,
    @SerialName("lowest_price_confidence") val lowestPriceConfidence: PriceConfidence? = null,
    @SerialName("offer_count") val offerCount: Int = 0,
) {
    fun toDomain() = VariantSummary(
        variantId, title, attributes.toDomain(), lowestFinalPrice, lowestPriceConfidence, offerCount,
    )
}

@Serializable
data class ProductDetailDto(
    @SerialName("product_id") val productId: Long = 0,
    val title: String = "",
    val brand: String? = null,
    val category: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    val variants: List<VariantSummaryDto> = emptyList(),
) {
    fun toDomain() = ProductDetail(productId, title, brand, category, imageUrl, variants.map { it.toDomain() })
}

@Serializable
data class ProductOffersResponseDto(
    @SerialName("product_id") val productId: Long = 0,
    @SerialName("variant_id") val variantId: Long = 0,
    val title: String = "",
    @SerialName("image_url") val imageUrl: String? = null,
    val attributes: ProductAttributesDto = ProductAttributesDto(),
    @SerialName("demo_mode") val demoMode: Boolean = false,
    val offers: List<OfferDto> = emptyList(),
    @SerialName("best_offer_id") val bestOfferId: Long? = null,
    @SerialName("price_history") val priceHistory: List<PricePointDto> = emptyList(),
    @SerialName("in_wishlist") val inWishlist: Boolean = false,
    @SerialName("wishlist_id") val wishlistId: Long? = null,
    @SerialName("target_price") val targetPrice: Long? = null,
) {
    fun toDomain() = ProductOffers(
        productId, variantId, title, imageUrl, attributes.toDomain(), demoMode, offers.map { it.toDomain() },
        bestOfferId, priceHistory.map { it.toDomain() }, inWishlist, wishlistId, targetPrice,
    )
}

@Serializable
data class WishlistItemDto(
    val id: Long = 0,
    @SerialName("product_id") val productId: Long = 0,
    @SerialName("variant_id") val variantId: Long = 0,
    val title: String = "",
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("current_lowest_price") val currentLowestPrice: Long? = null,
    @SerialName("current_price_confidence") val currentPriceConfidence: PriceConfidence? = null,
    @SerialName("current_marketplace") val currentMarketplace: Marketplace? = null,
    @SerialName("current_offer_url") val currentOfferUrl: String? = null,
    @SerialName("previous_lowest_price") val previousLowestPrice: Long? = null,
    @Serializable(with = LenientInstantSerializer::class)
    @SerialName("last_checked_at") val lastCheckedAt: Instant? = null,
    @SerialName("target_price") val targetPrice: Long? = null,
    @SerialName("alert_id") val alertId: Long? = null,
    @SerialName("is_mock") val isMock: Boolean = false,
    @Serializable(with = LenientInstantSerializer::class)
    @SerialName("created_at") val createdAt: Instant? = null,
) {
    fun toDomain() = WishlistItem(
        id, productId, variantId, title, imageUrl, currentLowestPrice, currentPriceConfidence,
        currentMarketplace, currentOfferUrl, previousLowestPrice, lastCheckedAt, targetPrice, alertId,
        isMock, createdAt,
    )
}

@Serializable
data class WishlistListDto(val items: List<WishlistItemDto> = emptyList())

@Serializable
data class PriceAlertDto(
    val id: Long = 0,
    @SerialName("product_id") val productId: Long = 0,
    @SerialName("variant_id") val variantId: Long = 0,
    val title: String = "",
    @SerialName("target_price") val targetPrice: Long = 0,
    @SerialName("is_active") val isActive: Boolean = false,
    @SerialName("last_notified_price") val lastNotifiedPrice: Long? = null,
    @Serializable(with = LenientInstantSerializer::class)
    @SerialName("last_notified_at") val lastNotifiedAt: Instant? = null,
    @Serializable(with = LenientInstantSerializer::class)
    @SerialName("created_at") val createdAt: Instant? = null,
) {
    fun toDomain() = PriceAlert(
        id, productId, variantId, title, targetPrice, isActive, lastNotifiedPrice, lastNotifiedAt, createdAt,
    )
}

@Serializable
data class PriceAlertListDto(val items: List<PriceAlertDto> = emptyList())

@Serializable
data class NotificationDto(
    val id: Long = 0,
    val title: String = "",
    val body: String = "",
    val url: String? = null,
    val price: Long? = null,
    @SerialName("variant_id") val variantId: Long? = null,
    val status: String = "",
    @SerialName("is_mock") val isMock: Boolean = false,
    @Serializable(with = LenientInstantSerializer::class)
    @SerialName("created_at") val createdAt: Instant? = null,
    @Serializable(with = LenientInstantSerializer::class)
    @SerialName("read_at") val readAt: Instant? = null,
) {
    fun toDomain() = AppNotification(id, title, body, url, price, variantId, status, isMock, createdAt, readAt)
}

@Serializable
data class NotificationListDto(val items: List<NotificationDto> = emptyList())

@Serializable
data class SearchHistoryItemDto(
    val id: Long = 0,
    @SerialName("search_type") val searchType: String = "text",
    val query: String = "",
    @SerialName("result_count") val resultCount: Int = 0,
    @SerialName("lowest_price") val lowestPrice: Long? = null,
    @Serializable(with = LenientInstantSerializer::class)
    @SerialName("created_at") val createdAt: Instant? = null,
) {
    fun toDomain() = SearchHistoryItem(id, SearchType.fromWire(searchType), query, resultCount, lowestPrice, createdAt)
}

@Serializable
data class ViewedProductItemDto(
    @SerialName("product_id") val productId: Long = 0,
    @SerialName("variant_id") val variantId: Long = 0,
    val title: String = "",
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("lowest_final_price") val lowestFinalPrice: Long? = null,
    @Serializable(with = LenientInstantSerializer::class)
    @SerialName("viewed_at") val viewedAt: Instant? = null,
) {
    fun toDomain() = ViewedProduct(productId, variantId, title, imageUrl, lowestFinalPrice, viewedAt)
}

@Serializable
data class HistoryResponseDto(
    val searches: List<SearchHistoryItemDto> = emptyList(),
    @SerialName("viewed_products") val viewedProducts: List<ViewedProductItemDto> = emptyList(),
) {
    fun toDomain() = History(searches.map { it.toDomain() }, viewedProducts.map { it.toDomain() })
}

@Serializable
data class SourceStatusDto(
    val marketplace: Marketplace = Marketplace.UNKNOWN,
    @SerialName("marketplace_name") val marketplaceName: String = "",
    val connector: String = "",
    @SerialName("is_mock") val isMock: Boolean = false,
    val status: String = "",
    @SerialName("verified_live") val verifiedLive: Boolean = false,
    val message: String = "",
    @Serializable(with = LenientInstantSerializer::class)
    @SerialName("checked_at") val checkedAt: Instant? = null,
) {
    fun toDomain() = SourceStatus(
        marketplace, marketplaceName.ifBlank { marketplace.displayName }, connector, isMock, status,
        verifiedLive, message, checkedAt,
    )
}

@Serializable
data class SourcesStatusResponseDto(
    @SerialName("demo_mode") val demoMode: Boolean = false,
    @SerialName("ai_available") val aiAvailable: Boolean = false,
    @SerialName("ai_model") val aiModel: String? = null,
    @SerialName("push_configured") val pushConfigured: Boolean = false,
    @SerialName("price_check_interval_minutes") val priceCheckIntervalMinutes: Int = 0,
    val sources: List<SourceStatusDto> = emptyList(),
) {
    fun toDomain() = SourcesStatus(
        demoMode, aiAvailable, aiModel, pushConfigured, priceCheckIntervalMinutes, sources.map { it.toDomain() },
    )
}

@Serializable
data class PriceDropItemDto(
    @SerialName("variant_id") val variantId: Long = 0,
    @SerialName("product_id") val productId: Long = 0,
    val title: String = "",
    @SerialName("image_url") val imageUrl: String? = null,
    val category: String? = null,
    @SerialName("previous_lowest_price") val previousLowestPrice: Long = 0,
    @SerialName("current_lowest_price") val currentLowestPrice: Long = 0,
    val marketplace: Marketplace? = null,
    @SerialName("is_mock") val isMock: Boolean = false,
    @Serializable(with = LenientInstantSerializer::class)
    @SerialName("recorded_at") val recordedAt: Instant? = null,
) {
    fun toDomain() = PriceDrop(
        variantId, productId, title, imageUrl, category, previousLowestPrice, currentLowestPrice,
        marketplace, isMock, recordedAt,
    )
}

@Serializable
data class TrendingResponseDto(
    @SerialName("demo_mode") val demoMode: Boolean = false,
    val available: Boolean = false,
    val reason: String? = null,
    @SerialName("price_drops") val priceDrops: List<PriceDropItemDto> = emptyList(),
    @SerialName("recently_seen") val recentlySeen: List<VariantSummaryDto> = emptyList(),
) {
    fun toDomain() = Explore(demoMode, available, reason, priceDrops.map { it.toDomain() }, recentlySeen.map { it.toDomain() })
}

@Serializable
data class AssistantResponseDto(
    val answer: String = "",
    @SerialName("generated_by") val generatedBy: String = "rules",
    val facts: List<String> = emptyList(),
    val disclaimer: String = "",
) {
    fun toDomain() = AssistantAnswer(answer, generatedBy, facts, disclaimer)
}

@Serializable
data class MeResponseDto(
    @SerialName("user_id") val userId: Long = 0,
    @SerialName("display_name") val displayName: String = "",
    @SerialName("demo_mode") val demoMode: Boolean = false,
) {
    fun toDomain() = Me(userId, displayName, demoMode)
}

@Serializable
data class HealthDto(
    val status: String = "",
    @SerialName("demo_mode") val demoMode: Boolean = false,
    val version: String = "",
) {
    fun toDomain() = Health(status, demoMode, version)
}

// ---- Requests ------------------------------------------------------------------------

@Serializable
data class SearchRequestDto(
    val query: String,
    val marketplaces: List<Marketplace>? = null,
    val quantity: Int = 1,
    @SerialName("include_similar") val includeSimilar: Boolean = true,
)

@Serializable
data class UrlSearchRequestDto(val url: String, val marketplaces: List<Marketplace>? = null)

@Serializable
data class WishlistCreateDto(
    @SerialName("variant_id") val variantId: Long,
    @SerialName("target_price") val targetPrice: Long? = null,
)

@Serializable
data class PriceAlertCreateDto(
    @SerialName("variant_id") val variantId: Long,
    @SerialName("target_price") val targetPrice: Long,
)

@Serializable
data class DeviceRegisterDto(val token: String, val platform: String = "android")

@Serializable
data class AssistantRequestDto(
    val question: String,
    @SerialName("variant_ids") val variantIds: List<Long>,
)

// ---- Error envelope ------------------------------------------------------------------

@Serializable
data class ErrorEnvelopeDto(val error: ErrorBodyDto? = null)

@Serializable
data class ErrorBodyDto(
    val code: String? = null,
    val message: String? = null,
    @SerialName("request_id") val requestId: String? = null,
)
