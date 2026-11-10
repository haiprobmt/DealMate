package com.dealmate.app.domain.usecase

import com.dealmate.app.domain.model.Marketplace
import com.dealmate.app.domain.model.MatchType
import com.dealmate.app.domain.model.Offer
import com.dealmate.app.domain.model.PriceConfidence
import com.dealmate.app.domain.model.ProductGroup
import com.dealmate.app.domain.model.SortOption

data class ResultFilter(
    /** Empty == all marketplaces. */
    val marketplaces: Set<Marketplace> = emptySet(),
    /** Empty == all match types. */
    val matchTypes: Set<MatchType> = emptySet(),
    val hideOverBudget: Boolean = false,
    val sort: SortOption = SortOption.PRICE,
) {
    val activeCount: Int
        get() = (if (marketplaces.isNotEmpty()) 1 else 0) + (if (matchTypes.isNotEmpty()) 1 else 0) +
            (if (hideOverBudget) 1 else 0)
}

/**
 * Client-side filtering/sorting of search results.
 *
 * Price order always keeps the backend's honesty tiers: offers with a VERIFIED final price first
 * (cheapest first), then ESTIMATED (cheapest first), then offers without a final price in the order
 * the backend returned them. An incomplete price can therefore never be ranked as "the cheapest".
 */
object ResultFilters {

    fun tier(confidence: PriceConfidence?): Int = when (confidence) {
        PriceConfidence.VERIFIED -> 0
        PriceConfidence.ESTIMATED -> 1
        else -> 2
    }

    private fun offerTier(offer: Offer): Int = if (offer.price.hasFinalPrice) tier(offer.price.confidence) else 2

    private fun matchRank(type: MatchType): Int = when (type) {
        MatchType.EXACT -> 0
        MatchType.LIKELY -> 1
        MatchType.SIMILAR -> 2
        MatchType.DIFFERENT -> 3
        MatchType.UNKNOWN -> 4
    }

    val priceOrder: Comparator<Offer> = compareBy<Offer> { offerTier(it) }
        .thenBy { if (offerTier(it) < 2) it.price.finalPrice ?: Long.MAX_VALUE else 0L }

    private val ratingOrder: Comparator<Offer> = compareByDescending { it.rating ?: -1.0 }

    fun sortOffers(offers: List<Offer>, sort: SortOption): List<Offer> = when (sort) {
        SortOption.PRICE -> offers.sortedWith(priceOrder)
        SortOption.RATING -> offers.sortedWith(ratingOrder)
    }

    /** Cheapest VERIFIED offer, else cheapest ESTIMATED; null when no offer has a final price. */
    fun bestOffer(offers: List<Offer>): Offer? =
        offers.filter { offerTier(it) < 2 }.minWithOrNull(priceOrder)

    fun apply(groups: List<ProductGroup>, filter: ResultFilter): List<ProductGroup> {
        val filtered = groups.asSequence()
            .filter { filter.matchTypes.isEmpty() || it.match.type in filter.matchTypes }
            .mapNotNull { group ->
                val offers = group.offers.filter { offer ->
                    (filter.marketplaces.isEmpty() || offer.marketplace in filter.marketplaces) &&
                        !(filter.hideOverBudget && offer.overBudget)
                }
                if (offers.isEmpty()) return@mapNotNull null
                val best = bestOffer(offers)
                group.copy(
                    offers = sortOffers(offers, filter.sort),
                    bestOfferId = best?.id,
                    lowestFinalPrice = best?.price?.finalPrice,
                    lowestPriceConfidence = best?.price?.confidence,
                    offerCount = offers.size,
                )
            }
            .toList()
        return when (filter.sort) {
            // Stable: groups the backend ranked equally keep the backend's order.
            SortOption.PRICE -> filtered.sortedWith(
                compareBy<ProductGroup> { matchRank(it.match.type) }
                    .thenBy { if (it.lowestFinalPrice == null) 2 else tier(it.lowestPriceConfidence) }
                    .thenBy { it.lowestFinalPrice ?: Long.MAX_VALUE },
            )
            SortOption.RATING -> filtered.sortedWith(
                compareBy<ProductGroup> { matchRank(it.match.type) }
                    .thenByDescending { g -> g.offers.maxOfOrNull { it.rating ?: -1.0 } ?: -1.0 },
            )
        }
    }
}
