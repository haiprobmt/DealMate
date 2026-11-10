package com.dealmate.app.domain.model

import java.time.Instant

data class ProductAttributes(
    val brand: String? = null,
    val category: String? = null,
    val productLine: String? = null,
    val model: String? = null,
    val volumeMl: Double? = null,
    val weightG: Double? = null,
    val count: Int? = null,
    val packQuantity: Int? = null,
    val size: String? = null,
    val storageGb: Int? = null,
    val color: String? = null,
    val condition: String? = null,
    val origin: String? = null,
)

data class MatchInfo(
    val type: MatchType,
    val confidence: Double = 0.0,
    val matchedAttributes: List<String> = emptyList(),
    val differentAttributes: List<String> = emptyList(),
    val unknownAttributes: List<String> = emptyList(),
    val reason: String = "",
    val decidedBy: String = "rules",
)

data class Voucher(
    val code: String? = null,
    val source: String = "",
    val kind: String = "",
    val value: Long = 0,
    val maxDiscount: Long? = null,
    val minSpend: Long = 0,
    val verified: Boolean = false,
    val requires: String? = null,
    val description: String? = null,
)

data class AppliedVoucher(val voucher: Voucher, val discount: Long, val estimated: Boolean)

data class SkippedVoucher(val voucher: Voucher, val reason: String)

data class PriceBreakdown(
    val quantity: Int = 1,
    val listedPrice: Long,
    val subtotal: Long,
    val voucherDiscount: Long,
    val appliedVouchers: List<AppliedVoucher> = emptyList(),
    val skippedVouchers: List<SkippedVoucher> = emptyList(),
    /** null == unknown, never zero. */
    val shippingFee: Long?,
    val shippingStatus: ShippingStatus,
    val mandatoryFees: Long,
    /** null when [confidence] is not VERIFIED/ESTIMATED. */
    val finalPrice: Long?,
    val knownSubtotal: Long,
    val confidence: PriceConfidence,
    val notes: List<String> = emptyList(),
) {
    /** A final price exists and may be compared with other offers. */
    val hasFinalPrice: Boolean
        get() = finalPrice != null &&
            (confidence == PriceConfidence.VERIFIED || confidence == PriceConfidence.ESTIMATED)
}

data class Seller(
    val name: String,
    val rating: Double? = null,
    val isOfficialStore: Boolean? = null,
    val location: String? = null,
)

data class Offer(
    val id: Long,
    val marketplace: Marketplace,
    val marketplaceName: String,
    val isMock: Boolean,
    val title: String,
    val url: String,
    val imageUrl: String? = null,
    val seller: Seller,
    val price: PriceBreakdown,
    val originalPrice: Long? = null,
    val match: MatchInfo,
    val rating: Double? = null,
    val ratingCount: Int? = null,
    val soldCount: Int? = null,
    val inStock: Boolean? = null,
    val retrievedAt: Instant? = null,
    val isStale: Boolean = false,
    val overBudget: Boolean = false,
)

data class ProductGroup(
    val productId: Long,
    val variantId: Long,
    val title: String,
    val imageUrl: String? = null,
    val attributes: ProductAttributes = ProductAttributes(),
    val match: MatchInfo,
    val offers: List<Offer>,
    val bestOfferId: Long? = null,
    val lowestFinalPrice: Long? = null,
    val lowestPriceConfidence: PriceConfidence? = null,
    val offerCount: Int = 0,
    val inWishlist: Boolean = false,
) {
    val marketplaces: List<Marketplace> get() = offers.map { it.marketplace }.distinct()
    val hasMock: Boolean get() = offers.any { it.isMock }

    /** Smallest known subtotal among offers without a final price (shown as "not incl. shipping"). */
    val lowestKnownSubtotal: Long? get() = offers.minOfOrNull { it.price.knownSubtotal }
}

data class BestOffer(
    val variantId: Long,
    val offerId: Long,
    val finalPrice: Long,
    val confidence: PriceConfidence,
    val marketplace: Marketplace,
    val marketplaceName: String,
    val explanation: String,
    val caveat: String? = null,
)

data class QuerySpec(
    val brand: String? = null,
    val productName: String? = null,
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
    val maxPrice: Long? = null,
    val minPrice: Long? = null,
    val constraints: List<String> = emptyList(),
    val uncertainFields: List<String> = emptyList(),
)

data class InterpretedQuery(
    val raw: String,
    val interpreter: String,
    val spec: QuerySpec = QuerySpec(),
    val notes: List<String> = emptyList(),
)

data class SourceResult(
    val marketplace: Marketplace,
    val marketplaceName: String,
    /** ok | timeout | error | not_configured | not_implemented | skipped (kept as text: forward compatible). */
    val status: String,
    val isMock: Boolean,
    val offersFound: Int = 0,
    val elapsedMs: Long = 0,
    val message: String? = null,
) {
    val isOk: Boolean get() = status == "ok"
}

data class Clarification(
    val needed: Boolean = false,
    val message: String? = null,
    val suggestions: List<String> = emptyList(),
)

data class SearchResult(
    val searchId: Long,
    val searchType: SearchType,
    val query: InterpretedQuery,
    val demoMode: Boolean,
    val partial: Boolean,
    val sources: List<SourceResult>,
    val best: BestOffer?,
    val groups: List<ProductGroup>,
    val similarGroups: List<ProductGroup>,
    val excludedCount: Int = 0,
    val overBudgetCount: Int = 0,
    val clarification: Clarification = Clarification(),
    val generatedAt: Instant? = null,
)

data class PricePoint(
    val recordedAt: Instant?,
    val lowestFinalPrice: Long?,
    val confidence: PriceConfidence?,
    val marketplace: Marketplace? = null,
    val isMock: Boolean = false,
)

data class VariantSummary(
    val variantId: Long,
    val title: String,
    val attributes: ProductAttributes = ProductAttributes(),
    val lowestFinalPrice: Long? = null,
    val lowestPriceConfidence: PriceConfidence? = null,
    val offerCount: Int = 0,
)

data class ProductDetail(
    val productId: Long,
    val title: String,
    val brand: String?,
    val category: String?,
    val imageUrl: String? = null,
    val variants: List<VariantSummary> = emptyList(),
)

data class ProductOffers(
    val productId: Long,
    val variantId: Long,
    val title: String,
    val imageUrl: String? = null,
    val attributes: ProductAttributes = ProductAttributes(),
    val demoMode: Boolean,
    val offers: List<Offer>,
    val bestOfferId: Long? = null,
    val priceHistory: List<PricePoint> = emptyList(),
    val inWishlist: Boolean = false,
    val wishlistId: Long? = null,
    val targetPrice: Long? = null,
)

data class WishlistItem(
    val id: Long,
    val productId: Long,
    val variantId: Long,
    val title: String,
    val imageUrl: String? = null,
    val currentLowestPrice: Long? = null,
    val currentPriceConfidence: PriceConfidence? = null,
    val currentMarketplace: Marketplace? = null,
    val currentOfferUrl: String? = null,
    val previousLowestPrice: Long? = null,
    val lastCheckedAt: Instant? = null,
    val targetPrice: Long? = null,
    val alertId: Long? = null,
    val isMock: Boolean = false,
    val createdAt: Instant? = null,
)

data class PriceAlert(
    val id: Long,
    val productId: Long,
    val variantId: Long,
    val title: String,
    val targetPrice: Long,
    val isActive: Boolean,
    val lastNotifiedPrice: Long? = null,
    val lastNotifiedAt: Instant? = null,
    val createdAt: Instant? = null,
)

data class AppNotification(
    val id: Long,
    val title: String,
    val body: String,
    val url: String? = null,
    val price: Long? = null,
    val variantId: Long? = null,
    val status: String = "",
    val isMock: Boolean = false,
    val createdAt: Instant? = null,
    val readAt: Instant? = null,
)

data class SearchHistoryItem(
    val id: Long,
    val searchType: SearchType,
    val query: String,
    val resultCount: Int,
    val lowestPrice: Long? = null,
    val createdAt: Instant? = null,
)

data class ViewedProduct(
    val productId: Long,
    val variantId: Long,
    val title: String,
    val imageUrl: String? = null,
    val lowestFinalPrice: Long? = null,
    val viewedAt: Instant? = null,
)

data class History(
    val searches: List<SearchHistoryItem> = emptyList(),
    val viewedProducts: List<ViewedProduct> = emptyList(),
)

data class SourceStatus(
    val marketplace: Marketplace,
    val marketplaceName: String,
    val connector: String,
    val isMock: Boolean,
    /** available | not_configured | not_implemented | error */
    val status: String,
    val verifiedLive: Boolean,
    val message: String,
    val checkedAt: Instant? = null,
)

data class SourcesStatus(
    val demoMode: Boolean,
    val aiAvailable: Boolean,
    val aiModel: String?,
    val pushConfigured: Boolean,
    val priceCheckIntervalMinutes: Int,
    val sources: List<SourceStatus>,
)

data class PriceDrop(
    val variantId: Long,
    val productId: Long,
    val title: String,
    val imageUrl: String? = null,
    val category: String? = null,
    val previousLowestPrice: Long,
    val currentLowestPrice: Long,
    val marketplace: Marketplace? = null,
    val isMock: Boolean = false,
    val recordedAt: Instant? = null,
)

data class Explore(
    val demoMode: Boolean,
    val available: Boolean,
    val reason: String? = null,
    val priceDrops: List<PriceDrop> = emptyList(),
    val recentlySeen: List<VariantSummary> = emptyList(),
)

data class AssistantAnswer(
    val answer: String,
    /** ai | rules */
    val generatedBy: String,
    val facts: List<String> = emptyList(),
    val disclaimer: String = "",
) {
    val isAi: Boolean get() = generatedBy == "ai"
}

data class Me(val userId: Long, val displayName: String, val demoMode: Boolean)

data class Health(val status: String, val demoMode: Boolean, val version: String)

data class RecentSearch(val type: SearchType, val query: String, val searchedAt: Instant)

/** What the user asked for; the Results screen is driven by one of these. */
data class SearchRequest(
    val type: SearchType,
    /** Text query, pasted URL, or (for images) the absolute path of the prepared JPEG. */
    val value: String,
    /** Optional text hint sent with an image search. */
    val hint: String? = null,
)

data class AppSettings(
    val baseUrl: String = "",
    val token: String = "",
    val preferredMarketplaces: Set<Marketplace> = Marketplace.selectable.toSet(),
    val defaultSort: SortOption = SortOption.PRICE,
    val notificationsEnabled: Boolean = false,
    val deliveryLocation: String = "",
    /** BCP-47 language tag of the UI: "vi" (default) or "en". */
    val language: String = "vi",
) {
    val isConfigured: Boolean get() = baseUrl.isNotBlank() && token.isNotBlank()
}
