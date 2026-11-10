package com.dealmate.app.domain

import com.dealmate.app.domain.model.Marketplace
import com.dealmate.app.domain.model.MatchInfo
import com.dealmate.app.domain.model.MatchType
import com.dealmate.app.domain.model.Offer
import com.dealmate.app.domain.model.PriceBreakdown
import com.dealmate.app.domain.model.PriceConfidence
import com.dealmate.app.domain.model.ProductGroup
import com.dealmate.app.domain.model.Seller
import com.dealmate.app.domain.model.ShippingStatus
import com.dealmate.app.domain.model.SortOption
import com.dealmate.app.domain.usecase.ResultFilter
import com.dealmate.app.domain.usecase.ResultFilters
import com.dealmate.app.testutil.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultFiltersTest {

    private fun offer(
        id: Long,
        confidence: PriceConfidence,
        final: Long?,
        known: Long = final ?: 0,
        marketplace: Marketplace = Marketplace.SHOPEE,
        rating: Double? = null,
        overBudget: Boolean = false,
    ) = Offer(
        id = id, marketplace = marketplace, marketplaceName = marketplace.displayName, isMock = false,
        title = "offer $id", url = "https://example.vn/$id", seller = Seller("s"),
        price = PriceBreakdown(
            listedPrice = known, subtotal = known, voucherDiscount = 0, shippingFee = null,
            shippingStatus = ShippingStatus.UNKNOWN, mandatoryFees = 0, finalPrice = final, knownSubtotal = known,
            confidence = confidence,
        ),
        match = MatchInfo(MatchType.EXACT), rating = rating, overBudget = overBudget,
    )

    private fun group(variantId: Long, type: MatchType, vararg offers: Offer) = ProductGroup(
        productId = variantId, variantId = variantId, title = "g$variantId", match = MatchInfo(type),
        offers = offers.toList(), offerCount = offers.size,
    )

    @Test
    fun `price sort keeps tiers - verified then estimated then incomplete`() {
        val offers = listOf(
            offer(1, PriceConfidence.INCOMPLETE, null, known = 100),
            offer(2, PriceConfidence.ESTIMATED, 200),
            offer(3, PriceConfidence.VERIFIED, 400),
            offer(4, PriceConfidence.ESTIMATED, 150),
            offer(5, PriceConfidence.VERIFIED, 300),
            offer(6, PriceConfidence.UNKNOWN, 50, known = 50),
            offer(7, PriceConfidence.INCOMPLETE, null, known = 90),
        )
        val sorted = ResultFilters.sortOffers(offers, SortOption.PRICE).map { it.id }
        // verified asc, estimated asc, then no-final-price offers in their original order
        assertEquals(listOf(5L, 3L, 4L, 2L, 1L, 6L, 7L), sorted)
    }

    @Test
    fun `an incomplete price is never the best offer even when its number is the smallest`() {
        val offers = listOf(
            offer(1, PriceConfidence.INCOMPLETE, null, known = 100),
            offer(2, PriceConfidence.ESTIMATED, 200),
            offer(3, PriceConfidence.VERIFIED, 400),
        )
        // the cheapest VERIFIED wins over a cheaper ESTIMATED, like the backend
        assertEquals(3L, ResultFilters.bestOffer(offers)?.id)
        assertEquals(2L, ResultFilters.bestOffer(offers.filter { it.id != 3L })?.id)
        assertNull(ResultFilters.bestOffer(offers.filter { it.id == 1L }))
    }

    @Test
    fun `an unknown confidence with a final price is treated as incomplete`() {
        assertNull(ResultFilters.bestOffer(listOf(offer(6, PriceConfidence.UNKNOWN, 50, known = 50))))
    }

    @Test
    fun `no filter keeps the backend result of the fixture intact`() {
        val result = Fixtures.search()
        val filtered = ResultFilters.apply(result.groups, ResultFilter())
        assertEquals(result.groups.map { it.variantId }, filtered.map { it.variantId })
        assertEquals(result.groups.map { g -> g.offers.map { it.id } }, filtered.map { g -> g.offers.map { it.id } })
        assertEquals(result.groups.map { it.bestOfferId }, filtered.map { it.bestOfferId })
        assertEquals(result.groups.map { it.lowestFinalPrice }, filtered.map { it.lowestFinalPrice })
    }

    @Test
    fun `marketplace filter drops offers and recomputes the group summary`() {
        val result = Fixtures.search()
        val filtered = ResultFilters.apply(result.groups, ResultFilter(marketplaces = setOf(Marketplace.LAZADA)))
        val first = filtered.first { it.variantId == 11L }
        assertEquals(listOf(102L), first.offers.map { it.id })
        assertEquals(102L, first.bestOfferId)
        assertEquals(322_000L, first.lowestFinalPrice)
        assertEquals(PriceConfidence.ESTIMATED, first.lowestPriceConfidence)
        assertEquals(1, first.offerCount)
    }

    @Test
    fun `group left with only incomplete offers has no lowest price and no best offer`() {
        val g = group(1, MatchType.EXACT, offer(1, PriceConfidence.INCOMPLETE, null, known = 100), offer(2, PriceConfidence.VERIFIED, 300, marketplace = Marketplace.LAZADA))
        val filtered = ResultFilters.apply(listOf(g), ResultFilter(marketplaces = setOf(Marketplace.SHOPEE))).single()
        assertNull(filtered.bestOfferId)
        assertNull(filtered.lowestFinalPrice)
        assertNull(filtered.lowestPriceConfidence)
    }

    @Test
    fun `match type filter and hide over budget`() {
        val exact = group(1, MatchType.EXACT, offer(1, PriceConfidence.VERIFIED, 300, overBudget = true), offer(2, PriceConfidence.VERIFIED, 350))
        val likely = group(2, MatchType.LIKELY, offer(3, PriceConfidence.VERIFIED, 100, overBudget = true))
        assertEquals(listOf(1L), ResultFilters.apply(listOf(exact, likely), ResultFilter(matchTypes = setOf(MatchType.EXACT))).map { it.variantId })
        val budget = ResultFilters.apply(listOf(exact, likely), ResultFilter(hideOverBudget = true))
        assertEquals(listOf(1L), budget.map { it.variantId })
        assertEquals(listOf(2L), budget.single().offers.map { it.id })
        assertEquals(2, ResultFilter(matchTypes = setOf(MatchType.EXACT), hideOverBudget = true).activeCount)
    }

    @Test
    fun `price sort of groups - exact before likely, then verified before estimated before none`() {
        val groups = listOf(
            group(1, MatchType.LIKELY, offer(1, PriceConfidence.VERIFIED, 100)),
            group(2, MatchType.EXACT, offer(2, PriceConfidence.INCOMPLETE, null, known = 50)),
            group(3, MatchType.EXACT, offer(3, PriceConfidence.ESTIMATED, 200)),
            group(4, MatchType.EXACT, offer(4, PriceConfidence.VERIFIED, 500)),
            group(5, MatchType.EXACT, offer(5, PriceConfidence.VERIFIED, 400)),
        )
        assertEquals(listOf(5L, 4L, 3L, 2L, 1L), ResultFilters.apply(groups, ResultFilter(sort = SortOption.PRICE)).map { it.variantId })
    }

    @Test
    fun `rating sort orders offers by rating but does not change which offer is cheapest`() {
        val g = group(
            1, MatchType.EXACT,
            offer(1, PriceConfidence.VERIFIED, 300, rating = 4.1),
            offer(2, PriceConfidence.INCOMPLETE, null, known = 100, rating = 5.0),
            offer(3, PriceConfidence.VERIFIED, 350, rating = null),
        )
        val sorted = ResultFilters.apply(listOf(g), ResultFilter(sort = SortOption.RATING)).single()
        assertEquals(listOf(2L, 1L, 3L), sorted.offers.map { it.id })
        assertEquals(1L, sorted.bestOfferId)
        assertTrue(sorted.lowestFinalPrice == 300L)
    }
}
