package com.dealmate.app.data

import com.dealmate.app.data.remote.dto.AssistantResponseDto
import com.dealmate.app.data.remote.dto.DealMateJson
import com.dealmate.app.data.remote.dto.ErrorEnvelopeDto
import com.dealmate.app.data.remote.dto.HealthDto
import com.dealmate.app.data.remote.dto.LenientInstantSerializer
import com.dealmate.app.data.remote.dto.MeResponseDto
import com.dealmate.app.data.remote.dto.NotificationListDto
import com.dealmate.app.data.remote.dto.PriceAlertListDto
import com.dealmate.app.data.remote.dto.SearchRequestDto
import com.dealmate.app.data.remote.dto.SearchResponseDto
import com.dealmate.app.data.remote.dto.WishlistCreateDto
import com.dealmate.app.domain.model.Marketplace
import com.dealmate.app.domain.model.MatchType
import com.dealmate.app.domain.model.PriceConfidence
import com.dealmate.app.domain.model.SearchType
import com.dealmate.app.domain.model.ShippingStatus
import com.dealmate.app.testutil.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DtoParsingTest {

    @Test
    fun `search response - full text search`() {
        val r = Fixtures.search()
        assertEquals(501L, r.searchId)
        assertEquals(SearchType.TEXT, r.searchType)
        assertTrue(r.demoMode)
        assertTrue(r.partial)
        assertEquals("Anessa", r.query.spec.brand)
        assertEquals("60ml", r.query.spec.volume)
        assertEquals(400_000L, r.query.spec.maxPrice)
        assertEquals("rules", r.query.interpreter)
        assertEquals(Instant.parse("2026-01-15T10:00:00Z"), r.generatedAt)

        assertEquals(listOf("ok", "ok", "timeout"), r.sources.map { it.status })
        assertEquals(Marketplace.TIKTOK_SHOP, r.sources[2].marketplace)
        assertFalse(r.sources[2].isOk)

        val best = requireNotNull(r.best)
        assertEquals(315_000L, best.finalPrice)
        assertEquals(PriceConfidence.VERIFIED, best.confidence)
        assertEquals(Marketplace.SHOPEE, best.marketplace)
        assertNotNull(best.caveat)

        assertEquals(listOf(MatchType.EXACT, MatchType.LIKELY), r.groups.map { it.match.type })
        assertEquals(listOf(MatchType.SIMILAR), r.similarGroups.map { it.match.type })
        assertEquals(4, r.excludedCount)
        assertFalse(r.clarification.needed)
    }

    @Test
    fun `offer - verified price with applied and skipped vouchers`() {
        val offer = Fixtures.search().groups[0].offers[0]
        assertEquals(101L, offer.id)
        assertTrue(offer.isMock)
        assertEquals(true, offer.seller.isOfficialStore)
        assertEquals(Instant.parse("2026-01-15T09:55:00Z"), offer.retrievedAt)
        with(offer.price) {
            assertEquals(359_000L, listedPrice)
            assertEquals(50_000L, voucherDiscount)
            assertEquals(6_000L, shippingFee)
            assertEquals(ShippingStatus.VERIFIED, shippingStatus)
            assertEquals(315_000L, finalPrice)
            assertEquals(PriceConfidence.VERIFIED, confidence)
            assertTrue(hasFinalPrice)
            assertEquals(2, appliedVouchers.size)
            assertEquals("ANESSA30", appliedVouchers[0].voucher.code)
            assertEquals(1, skippedVouchers.size)
            assertEquals("new_user", skippedVouchers[0].voucher.requires)
            assertEquals(1, notes.size)
        }
    }

    @Test
    fun `offer - incomplete price has null final price and keeps known subtotal`() {
        val offer = Fixtures.search().groups[0].offers[2]
        assertEquals(PriceConfidence.INCOMPLETE, offer.price.confidence)
        assertNull(offer.price.finalPrice)
        assertNull(offer.price.shippingFee)
        assertEquals(ShippingStatus.UNKNOWN, offer.price.shippingStatus)
        assertEquals(299_000L, offer.price.knownSubtotal)
        assertFalse(offer.price.hasFinalPrice)
        assertTrue(offer.isStale)
        assertNull(offer.seller.isOfficialStore)
        // fractional seconds are accepted
        assertEquals(Instant.parse("2026-01-13T08:00:00.123456Z"), offer.retrievedAt)
    }

    @Test
    fun `offer - percent voucher estimated and offset datetime`() {
        val offer = Fixtures.search().groups[0].offers[1]
        assertEquals(PriceConfidence.ESTIMATED, offer.price.confidence)
        assertTrue(offer.price.appliedVouchers.single().estimated)
        assertNull(offer.price.appliedVouchers.single().voucher.code)
        assertEquals(Instant.parse("2026-01-15T09:40:00Z"), offer.retrievedAt)
    }

    @Test
    fun `clarification response with empty groups`() {
        val r = Fixtures.search("search_clarification.json")
        assertEquals(SearchType.IMAGE, r.searchType)
        assertTrue(r.clarification.needed)
        assertEquals(2, r.clarification.suggestions.size)
        assertTrue(r.groups.isEmpty())
        assertNull(r.best)
        assertEquals("ai", r.query.interpreter)
    }

    @Test
    fun `unknown enum values and unknown fields do not crash`() {
        val r = Fixtures.search("search_unknown_enums.json")
        // unknown search_type falls back to text; a naive datetime is read as UTC
        assertEquals(SearchType.TEXT, r.searchType)
        assertEquals(Instant.parse("2026-01-15T10:02:00Z"), r.generatedAt)
        assertEquals(Marketplace.UNKNOWN, r.sources.single().marketplace)
        assertEquals("Sendo", r.sources.single().marketplaceName)
        assertEquals("degraded", r.sources.single().status)

        val group = r.groups.single()
        assertEquals(MatchType.UNKNOWN, group.match.type)
        val unknown = group.offers[0]
        assertEquals(Marketplace.UNKNOWN, unknown.marketplace)
        assertEquals(PriceConfidence.UNKNOWN, unknown.price.confidence)
        assertEquals(ShippingStatus.UNKNOWN, unknown.price.shippingStatus)
        // a final price next to an unknown confidence is not trusted
        assertNull(unknown.price.finalPrice)
        assertFalse(unknown.price.hasFinalPrice)
        // omitted optional keys take their defaults
        val sparse = group.offers[1]
        assertNull(sparse.ratingCount)
        assertFalse(sparse.isStale)
    }

    @Test
    fun `product offers with price history`() {
        val p = Fixtures.offers()
        assertEquals(1L, p.productId)
        assertEquals(11L, p.variantId)
        assertTrue(p.demoMode)
        assertEquals(3, p.offers.size)
        assertEquals(101L, p.bestOfferId)
        assertEquals(7, p.priceHistory.size)
        assertNull(p.priceHistory[3].lowestFinalPrice)
        assertNull(p.priceHistory[3].confidence)
        assertEquals(Marketplace.LAZADA, p.priceHistory[2].marketplace)
        assertEquals(60.0, p.attributes.volumeMl!!, 0.0)
        assertFalse(p.inWishlist)
        assertNull(p.wishlistId)
    }

    @Test
    fun `product detail`() {
        val p = Fixtures.product()
        assertEquals("Anessa", p.brand)
        assertEquals(listOf(11L, 12L), p.variants.map { it.variantId })
        assertEquals(PriceConfidence.VERIFIED, p.variants[0].lowestPriceConfidence)
    }

    @Test
    fun `wishlist list`() {
        val items = Fixtures.wishlist()
        assertEquals(listOf(31L, 32L, 33L), items.map { it.id })
        assertEquals(328_000L, items[0].previousLowestPrice)
        assertEquals(41L, items[0].alertId)
        assertEquals(Marketplace.LAZADA, items[1].currentMarketplace)
        assertNull(items[2].currentLowestPrice)
        assertNull(items[2].currentPriceConfidence)
        assertNull(items[2].lastCheckedAt)
    }

    @Test
    fun `price alerts list`() {
        val alerts = DealMateJson.decodeFromString(PriceAlertListDto.serializer(), Fixtures.text("alerts_list.json")).items.map { it.toDomain() }
        assertEquals(2, alerts.size)
        assertTrue(alerts[0].isActive)
        assertNull(alerts[0].lastNotifiedAt)
        assertEquals(345_000L, alerts[1].lastNotifiedPrice)
    }

    @Test
    fun `notifications list`() {
        val items = DealMateJson.decodeFromString(NotificationListDto.serializer(), Fixtures.text("notifications_list.json")).items.map { it.toDomain() }
        assertEquals(11L, items[0].variantId)
        assertNull(items[0].readAt)
        assertNull(items[1].variantId)
        assertNotNull(items[1].readAt)
    }

    @Test
    fun `history`() {
        val h = Fixtures.history()
        assertEquals(listOf(SearchType.TEXT, SearchType.URL, SearchType.IMAGE, SearchType.TEXT), h.searches.map { it.searchType })
        assertNull(h.searches[2].lowestPrice)
        assertEquals(2, h.viewedProducts.size)
        assertNull(h.viewedProducts[1].lowestFinalPrice)
    }

    @Test
    fun `sources status`() {
        val s = Fixtures.sources()
        assertTrue(s.demoMode)
        assertFalse(s.aiAvailable)
        assertNull(s.aiModel)
        assertEquals(listOf("available", "not_configured", "not_implemented"), s.sources.map { it.status })
        assertTrue(s.sources[0].isMock)
        assertFalse(s.sources.any { it.verifiedLive })
    }

    @Test
    fun `explore - unavailable with reason and available with drops`() {
        val none = Fixtures.explore()
        assertFalse(none.available)
        assertNotNull(none.reason)
        assertTrue(none.priceDrops.isEmpty())

        val drops = Fixtures.explore("explore_drops.json")
        assertTrue(drops.available)
        assertEquals(328_000L, drops.priceDrops.single().previousLowestPrice)
        assertEquals(1, drops.recentlySeen.size)
    }

    @Test
    fun `assistant, me, health`() {
        val a = DealMateJson.decodeFromString(AssistantResponseDto.serializer(), Fixtures.text("assistant_answer.json")).toDomain()
        assertFalse(a.isAi)
        assertEquals(3, a.facts.size)
        assertTrue(a.disclaimer.isNotBlank())
        assertEquals("Gia đình", DealMateJson.decodeFromString(MeResponseDto.serializer(), Fixtures.text("me.json")).toDomain().displayName)
        val health = DealMateJson.decodeFromString(HealthDto.serializer(), Fixtures.text("health.json")).toDomain()
        assertEquals("ok", health.status)
        assertTrue(health.demoMode)
    }

    @Test
    fun `error envelope`() {
        val e = DealMateJson.decodeFromString(ErrorEnvelopeDto.serializer(), Fixtures.text("error_ai_unavailable.json")).error!!
        assertEquals("ai_unavailable", e.code)
        assertTrue(e.message!!.startsWith("Tìm kiếm bằng hình ảnh"))
        assertEquals("req_123", e.requestId)
    }

    @Test
    fun `requests serialise with the contract's snake_case names and lowercase enums`() {
        val search = DealMateJson.encodeToString(
            SearchRequestDto.serializer(),
            SearchRequestDto("bỉm Merries size L", listOf(Marketplace.SHOPEE, Marketplace.TIKTOK_SHOP)),
        )
        assertEquals(
            """{"query":"bỉm Merries size L","marketplaces":["shopee","tiktok_shop"],"quantity":1,"include_similar":true}""",
            search,
        )
        // null marketplaces (== all) is simply omitted
        assertFalse(DealMateJson.encodeToString(SearchRequestDto.serializer(), SearchRequestDto("ab")).contains("marketplaces"))
        assertEquals(
            """{"variant_id":11,"target_price":300000}""",
            DealMateJson.encodeToString(WishlistCreateDto.serializer(), WishlistCreateDto(11, 300_000)),
        )
    }

    @Test
    fun `search response survives a serialise - parse round trip (offline cache)`() {
        val dto = Fixtures.searchDto()
        val json = DealMateJson.encodeToString(SearchResponseDto.serializer(), dto)
        assertEquals(dto, DealMateJson.decodeFromString(SearchResponseDto.serializer(), json))
    }

    @Test
    fun `lenient instant parsing`() {
        assertEquals(Instant.parse("2026-01-15T10:00:00Z"), LenientInstantSerializer.parse("2026-01-15T10:00:00Z"))
        assertEquals(Instant.parse("2026-01-15T03:00:00Z"), LenientInstantSerializer.parse("2026-01-15T10:00:00+07:00"))
        assertEquals(Instant.parse("2026-01-15T10:00:00Z"), LenientInstantSerializer.parse("2026-01-15T10:00:00"))
        assertEquals(Instant.parse("2026-01-15T10:00:00.5Z"), LenientInstantSerializer.parse("2026-01-15T10:00:00.500000"))
        assertNull(LenientInstantSerializer.parse("yesterday"))
        assertNull(LenientInstantSerializer.parse(""))
    }
}
