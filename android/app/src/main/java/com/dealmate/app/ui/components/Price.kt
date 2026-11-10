package com.dealmate.app.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.dealmate.app.R
import com.dealmate.app.domain.model.PriceBreakdown
import com.dealmate.app.domain.model.PriceConfidence
import com.dealmate.app.domain.model.ShippingStatus
import com.dealmate.app.domain.model.Voucher
import com.dealmate.app.domain.util.Money
import com.dealmate.app.ui.common.labelRes
import com.dealmate.app.ui.theme.DealMateTheme
import com.dealmate.app.ui.theme.PriceMedium
import com.dealmate.app.ui.theme.Spacing

/** Honesty label of a price: "Đã xác minh" / "Ước tính" / "Thiếu phí vận chuyển". */
@Composable
fun PriceBadge(confidence: PriceConfidence?, modifier: Modifier = Modifier) {
    val status = DealMateTheme.status
    val (fg, bg, icon) = when (confidence) {
        PriceConfidence.VERIFIED -> Triple(status.verified, status.verifiedContainer, Icons.Outlined.Verified)
        PriceConfidence.ESTIMATED -> Triple(status.estimated, status.estimatedContainer, Icons.Outlined.Calculate)
        PriceConfidence.INCOMPLETE -> Triple(status.neutral, status.neutralContainer, Icons.Outlined.LocalShipping)
        else -> Triple(status.neutral, status.neutralContainer, Icons.AutoMirrored.Outlined.HelpOutline)
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(start = 6.dp, end = 8.dp, top = 2.dp, bottom = 2.dp)
            .testTag("price_badge_${confidence?.name?.lowercase() ?: "unknown"}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(stringResource(confidence.labelRes()), style = MaterialTheme.typography.labelSmall, color = fg, maxLines = 1)
    }
}

/**
 * The price of one offer. With a final price: the figure. Without one (incomplete): the known
 * subtotal in a muted colour, explicitly captioned "Chưa gồm phí vận chuyển" — never a final price.
 */
@Composable
fun OfferPrice(
    price: PriceBreakdown,
    modifier: Modifier = Modifier,
    style: TextStyle = PriceMedium,
    emphasized: Boolean = false,
    originalPrice: Long? = null,
) {
    Column(modifier) {
        if (price.hasFinalPrice) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = Money.format(price.finalPrice ?: 0),
                    style = style,
                    color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("final_price"),
                )
                if (originalPrice != null && originalPrice > (price.finalPrice ?: 0)) {
                    Text(
                        text = Money.format(originalPrice),
                        style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.LineThrough),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            }
        } else {
            Text(
                text = Money.format(price.knownSubtotal),
                style = style.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("known_subtotal"),
            )
            Text(
                text = stringResource(R.string.price_excludes_shipping),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BreakdownRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Text(
            label,
            style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            color = if (bold) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(end = Spacing.md),
        )
        Text(
            value,
            style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            color = valueColor,
        )
    }
}

@Composable
private fun voucherName(voucher: Voucher): String {
    val source = stringResource(if (voucher.source == "platform") R.string.voucher_platform else R.string.voucher_store)
    val detail = voucher.code?.takeIf { it.isNotBlank() } ?: voucher.description?.takeIf { it.isNotBlank() }
    return if (detail != null) "$source · $detail" else source
}

/** The full calculation behind a price: listed price, vouchers, shipping, fees, final + notes. */
@Composable
fun PriceBreakdownView(price: PriceBreakdown, modifier: Modifier = Modifier) {
    val status = DealMateTheme.status
    Column(modifier.fillMaxWidth().testTag("price_breakdown")) {
        BreakdownRow(
            label = if (price.quantity > 1) stringResource(R.string.breakdown_listed_qty, price.quantity)
            else stringResource(R.string.breakdown_listed),
            value = Money.format(price.subtotal),
        )
        if (price.voucherDiscount > 0) {
            BreakdownRow(
                label = stringResource(R.string.breakdown_voucher),
                value = "−" + Money.format(price.voucherDiscount),
                valueColor = status.verified,
            )
        }
        price.appliedVouchers.forEach { applied ->
            Row(Modifier.fillMaxWidth().padding(start = Spacing.md, top = 1.dp, bottom = 1.dp)) {
                Text(
                    text = voucherName(applied.voucher) +
                        if (applied.estimated) " · " + stringResource(R.string.voucher_estimated) else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(end = Spacing.md),
                )
                Text(
                    "−" + Money.format(applied.discount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        BreakdownRow(
            label = stringResource(R.string.breakdown_shipping),
            value = when {
                price.shippingFee == null -> stringResource(R.string.breakdown_shipping_unknown)
                price.shippingFee == 0L -> stringResource(R.string.breakdown_shipping_free)
                price.shippingStatus == ShippingStatus.ESTIMATED ->
                    stringResource(R.string.breakdown_shipping_estimated, Money.format(price.shippingFee))
                else -> Money.format(price.shippingFee)
            },
            valueColor = if (price.shippingFee == null) status.estimated else MaterialTheme.colorScheme.onSurface,
        )
        if (price.mandatoryFees > 0) {
            BreakdownRow(stringResource(R.string.breakdown_fees), Money.format(price.mandatoryFees))
        }
        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
        if (price.hasFinalPrice) {
            BreakdownRow(stringResource(R.string.breakdown_final), Money.format(price.finalPrice ?: 0), bold = true)
        } else {
            BreakdownRow(stringResource(R.string.breakdown_known_subtotal), Money.format(price.knownSubtotal), bold = true)
            Text(
                stringResource(R.string.breakdown_no_final),
                style = MaterialTheme.typography.bodySmall,
                color = status.estimated,
            )
        }
        if (price.skippedVouchers.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                stringResource(R.string.breakdown_skipped_vouchers),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            price.skippedVouchers.forEach { skipped ->
                Text(
                    "• " + voucherName(skipped.voucher) + " — " + skipped.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (price.notes.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.sm))
            price.notes.forEach { note ->
                Text(
                    "• $note",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
