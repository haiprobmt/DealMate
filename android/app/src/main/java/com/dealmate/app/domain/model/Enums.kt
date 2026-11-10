package com.dealmate.app.domain.model

enum class Marketplace(val wire: String, val displayName: String) {
    SHOPEE("shopee", "Shopee"),
    LAZADA("lazada", "Lazada"),
    TIKTOK_SHOP("tiktok_shop", "TikTok Shop"),
    UNKNOWN("unknown", "?");

    companion object {
        /** Marketplaces the user can choose between (UNKNOWN is only a parsing fallback). */
        val selectable: List<Marketplace> = listOf(SHOPEE, LAZADA, TIKTOK_SHOP)

        fun fromWire(value: String?): Marketplace = entries.firstOrNull { it.wire == value } ?: UNKNOWN
    }
}

enum class MatchType { EXACT, LIKELY, SIMILAR, DIFFERENT, UNKNOWN }

/**
 * How trustworthy a final price is. [UNKNOWN] is a parsing fallback for values this app version
 * does not know; it is always treated as the least trustworthy tier (like [INCOMPLETE]).
 */
enum class PriceConfidence { VERIFIED, ESTIMATED, INCOMPLETE, UNKNOWN }

enum class ShippingStatus { VERIFIED, ESTIMATED, UNKNOWN }

enum class SearchType(val wire: String) {
    TEXT("text"), IMAGE("image"), URL("url");

    companion object {
        fun fromWire(value: String?): SearchType = entries.firstOrNull { it.wire == value } ?: TEXT
    }
}

enum class SortOption { PRICE, RATING }
