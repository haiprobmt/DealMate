package com.dealmate.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dealmate.app.R
import com.dealmate.app.domain.model.MatchInfo
import com.dealmate.app.domain.model.MatchType
import com.dealmate.app.domain.model.Offer
import com.dealmate.app.domain.model.ProductGroup
import com.dealmate.app.domain.model.Seller
import com.dealmate.app.domain.util.Money
import com.dealmate.app.ui.common.labelRes
import com.dealmate.app.ui.common.relativeTimeText
import com.dealmate.app.ui.theme.DealMateTheme
import com.dealmate.app.ui.theme.PriceMedium
import com.dealmate.app.ui.theme.Spacing

@Composable
fun cardColors() = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)

@Composable
fun cardBorder() = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

/** "Đúng sản phẩm" / "Có thể đúng — thiếu thông tin" / "Sản phẩm tương tự". */
@Composable
fun MatchLabel(match: MatchInfo, modifier: Modifier = Modifier) {
    val status = DealMateTheme.status
    val (color, icon) = when (match.type) {
        MatchType.EXACT -> status.verified to Icons.Outlined.CheckCircle
        MatchType.LIKELY -> status.estimated to Icons.AutoMirrored.Outlined.HelpOutline
        else -> status.neutral to Icons.Outlined.Tune
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            stringResource(match.type.labelRes()),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** One product variant in the results list: title, match honesty, lowest price + confidence, sources. */
@Composable
fun ProductCard(group: ProductGroup, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().testTag("product_card_${group.variantId}"),
        shape = MaterialTheme.shapes.medium,
        colors = cardColors(),
        border = cardBorder(),
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Row {
                ProductThumbnail(group.imageUrl, size = 76.dp)
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        group.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    MatchLabel(group.match)
                    Spacer(Modifier.height(6.dp))
                    val lowest = group.lowestFinalPrice
                    if (lowest != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Text(
                                Money.format(lowest), style = PriceMedium, color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("group_price"),
                            )
                            PriceBadge(group.lowestPriceConfidence)
                        }
                    } else {
                        // No offer has a final price: show the known part, clearly not a final price.
                        val known = group.lowestKnownSubtotal
                        if (known != null) {
                            Text(
                                stringResource(R.string.price_from_excl_shipping, Money.format(known)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(2.dp))
                        }
                        PriceBadge(group.offers.firstOrNull()?.price?.confidence)
                    }
                }
            }
            if (group.match.reason.isNotBlank()) {
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    group.match.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                group.marketplaces.forEach { marketplace ->
                    MarketplaceLogo(marketplace, size = 20.dp, modifier = Modifier.padding(end = 4.dp))
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    pluralStringResource(R.plurals.offer_count, group.offerCount, group.offerCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (group.hasMock) DemoTag()
            }
        }
    }
}

/**
 * Seller as the marketplace describes it. The "Mall / Official store" line appears only when the
 * marketplace itself labels the shop that way, and is worded as the marketplace's label — the app
 * never makes authenticity claims of its own.
 */
@Composable
fun SellerInformation(seller: Seller, marketplaceName: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Storefront, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                seller.name.ifBlank { stringResource(R.string.seller_unknown) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (seller.rating != null) {
                Spacer(Modifier.width(Spacing.sm))
                Icon(Icons.Rounded.Star, contentDescription = null, tint = DealMateTheme.status.estimated, modifier = Modifier.size(13.dp))
                Text(
                    String.format(java.util.Locale.US, "%.1f", seller.rating),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val details = listOfNotNull(
            if (seller.isOfficialStore == true) stringResource(R.string.seller_official_label, marketplaceName) else null,
            seller.location?.takeIf { it.isNotBlank() },
        )
        if (details.isNotEmpty()) {
            Text(
                details.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 19.dp).testTag("seller_details"),
            )
        }
    }
}

/** One marketplace listing with its price, honesty labels, calculation and link to the source. */
@Composable
fun OfferCard(
    offer: Offer,
    isBest: Boolean,
    onOpenListing: (String) -> Unit,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
) {
    var expanded by rememberSaveable(offer.id) { mutableStateOf(initiallyExpanded) }
    val shape = MaterialTheme.shapes.medium
    Card(
        modifier = modifier.fillMaxWidth().testTag("offer_card_${offer.id}"),
        shape = shape,
        colors = cardColors(),
        border = if (isBest) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else cardBorder(),
    ) {
        Column {
            if (isBest) {
                Text(
                    stringResource(
                        if (offer.price.confidence == com.dealmate.app.domain.model.PriceConfidence.VERIFIED) R.string.offer_best_verified
                        else R.string.offer_best_estimated,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(bottomEnd = 10.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = Spacing.md, vertical = 4.dp)
                        .testTag("best_offer_label"),
                )
            }
            Column(Modifier.padding(Spacing.md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MarketplaceLogo(offer.marketplace, name = offer.marketplaceName)
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        offer.marketplaceName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (offer.isMock) DemoTag()
                }
                Spacer(Modifier.height(Spacing.sm))
                Row(verticalAlignment = Alignment.Top) {
                    OfferPrice(
                        offer.price,
                        emphasized = isBest,
                        originalPrice = offer.originalPrice,
                        style = if (isBest) com.dealmate.app.ui.theme.PriceLarge else PriceMedium,
                        modifier = Modifier.weight(1f),
                    )
                    PriceBadge(offer.price.confidence, Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    offer.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                SellerInformation(offer.seller, offer.marketplaceName)
                OfferFacts(offer)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { expanded = !expanded },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                        modifier = Modifier.testTag("toggle_breakdown_${offer.id}"),
                    ) {
                        Text(stringResource(R.string.offer_show_calculation))
                        Icon(
                            if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null, modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(
                        onClick = { onOpenListing(offer.url) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.md),
                        modifier = Modifier.height(36.dp).testTag("open_listing_${offer.id}"),
                    ) {
                        Text(stringResource(R.string.offer_open_listing))
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                    }
                }
                AnimatedVisibility(visible = expanded) {
                    Column(
                        Modifier
                            .padding(top = Spacing.sm)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(Spacing.md),
                    ) { PriceBreakdownView(offer.price) }
                }
            }
        }
    }
}

@Composable
private fun OfferFacts(offer: Offer) {
    val facts = mutableListOf<String>()
    offer.rating?.let { rating ->
        val base = "★ " + String.format(java.util.Locale.US, "%.1f", rating)
        facts += if (offer.ratingCount != null) "$base (${com.dealmate.app.domain.util.Money.group(offer.ratingCount.toLong())})" else base
    }
    offer.soldCount?.let { facts += stringResource(R.string.offer_sold, com.dealmate.app.domain.util.Money.group(it.toLong())) }
    if (offer.inStock == false) facts += stringResource(R.string.offer_out_of_stock)
    if (offer.overBudget) facts += stringResource(R.string.offer_over_budget)
    if (offer.match.type != MatchType.EXACT && offer.match.type != MatchType.UNKNOWN) {
        facts += stringResource(offer.match.type.labelRes())
    }
    if (facts.isNotEmpty()) {
        Text(
            facts.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
    val retrieved = relativeTimeText(offer.retrievedAt)
    if (retrieved != null) {
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.History, contentDescription = null,
                tint = if (offer.isStale) DealMateTheme.status.estimated else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(R.string.offer_retrieved, retrieved) +
                    if (offer.isStale) " · " + stringResource(R.string.offer_stale) else "",
                style = MaterialTheme.typography.bodySmall,
                color = if (offer.isStale) DealMateTheme.status.estimated else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * All current offers of one variant, in the order the backend returned them (verified, estimated,
 * then incomplete). Only [bestOfferId] — which is never an incomplete price — gets the "cheapest" label.
 */
@Composable
fun PriceComparisonSection(
    offers: List<Offer>,
    bestOfferId: Long?,
    onOpenListing: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        offers.forEach { offer ->
            val isBest = offer.id == bestOfferId && offer.price.hasFinalPrice
            OfferCard(offer = offer, isBest = isBest, onOpenListing = onOpenListing, initiallyExpanded = isBest)
        }
    }
}

