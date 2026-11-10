package com.dealmate.app.testutil

import com.dealmate.app.data.remote.dto.AssistantResponseDto
import com.dealmate.app.data.remote.dto.DealMateJson
import com.dealmate.app.data.remote.dto.HistoryResponseDto
import com.dealmate.app.data.remote.dto.ProductDetailDto
import com.dealmate.app.data.remote.dto.ProductOffersResponseDto
import com.dealmate.app.data.remote.dto.SearchResponseDto
import com.dealmate.app.data.remote.dto.SourcesStatusResponseDto
import com.dealmate.app.data.remote.dto.TrendingResponseDto
import com.dealmate.app.data.remote.dto.WishlistListDto
import com.dealmate.app.domain.model.AssistantAnswer
import com.dealmate.app.domain.model.Explore
import com.dealmate.app.domain.model.History
import com.dealmate.app.domain.model.ProductDetail
import com.dealmate.app.domain.model.ProductOffers
import com.dealmate.app.domain.model.SearchResult
import com.dealmate.app.domain.model.SourcesStatus
import com.dealmate.app.domain.model.WishlistItem
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** JSON fixtures (src/test/resources/fixtures) that follow the backend's Pydantic contract. */
object Fixtures {
    /** "Now" for deterministic relative times; a few minutes after the fixtures' timestamps. */
    val NOW: Instant = Instant.parse("2026-01-15T10:05:00Z")
    val CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)

    fun text(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("fixtures/$name")) { "missing fixture $name" }.readText()

    fun searchDto(name: String = "search_text.json"): SearchResponseDto =
        DealMateJson.decodeFromString(SearchResponseDto.serializer(), text(name))

    fun search(name: String = "search_text.json"): SearchResult = searchDto(name).toDomain()

    fun offers(): ProductOffers =
        DealMateJson.decodeFromString(ProductOffersResponseDto.serializer(), text("offers_anessa.json")).toDomain()

    fun product(): ProductDetail =
        DealMateJson.decodeFromString(ProductDetailDto.serializer(), text("product_anessa.json")).toDomain()

    fun wishlist(): List<WishlistItem> =
        DealMateJson.decodeFromString(WishlistListDto.serializer(), text("wishlist_list.json")).items.map { it.toDomain() }

    fun history(): History =
        DealMateJson.decodeFromString(HistoryResponseDto.serializer(), text("history_full.json")).toDomain()

    fun sources(): SourcesStatus =
        DealMateJson.decodeFromString(SourcesStatusResponseDto.serializer(), text("sources_status.json")).toDomain()

    fun explore(name: String = "explore_unavailable.json"): Explore =
        DealMateJson.decodeFromString(TrendingResponseDto.serializer(), text(name)).toDomain()

    fun assistant(): AssistantAnswer =
        DealMateJson.decodeFromString(AssistantResponseDto.serializer(), text("assistant_answer.json")).toDomain()
}
