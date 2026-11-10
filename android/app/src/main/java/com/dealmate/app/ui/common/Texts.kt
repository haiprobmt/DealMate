package com.dealmate.app.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.dealmate.app.R
import com.dealmate.app.domain.model.AppError
import com.dealmate.app.domain.model.MatchType
import com.dealmate.app.domain.model.PriceConfidence
import com.dealmate.app.domain.model.ProductAttributes
import com.dealmate.app.domain.model.QuerySpec
import com.dealmate.app.domain.model.SortOption
import com.dealmate.app.domain.util.Money
import com.dealmate.app.domain.util.RelativeTime
import java.time.Clock
import java.time.Instant

/** Source of "now" for relative times; replaced by a fixed clock in screenshot tests. */
val LocalClock = staticCompositionLocalOf<Clock> { Clock.systemDefaultZone() }

@Composable
fun relativeTimeText(instant: Instant?): String? {
    if (instant == null) return null
    val clock = LocalClock.current
    return when (val r = RelativeTime.between(instant, clock.instant(), clock.zone)) {
        RelativeTime.JustNow -> stringResource(R.string.time_just_now)
        is RelativeTime.Minutes -> pluralStringResource(R.plurals.time_minutes_ago, r.value, r.value)
        is RelativeTime.Hours -> pluralStringResource(R.plurals.time_hours_ago, r.value, r.value)
        is RelativeTime.Days -> pluralStringResource(R.plurals.time_days_ago, r.value, r.value)
        is RelativeTime.OnDate -> r.text
    }
}

@StringRes
fun AppError.Kind.defaultMessageRes(): Int = when (this) {
    AppError.Kind.NOT_CONFIGURED -> R.string.error_not_configured
    AppError.Kind.UNAUTHORIZED -> R.string.error_unauthorized
    AppError.Kind.OFFLINE -> R.string.error_offline
    AppError.Kind.TIMEOUT -> R.string.error_timeout
    AppError.Kind.RATE_LIMITED -> R.string.error_rate_limited
    AppError.Kind.UNAVAILABLE -> R.string.error_unavailable
    AppError.Kind.NOT_FOUND -> R.string.error_not_found
    AppError.Kind.VALIDATION -> R.string.error_validation
    AppError.Kind.SERVER -> R.string.error_server
    AppError.Kind.UNKNOWN -> R.string.error_unknown
}

/** The server's own (Vietnamese) message when present, else a local fallback for the error kind. */
@Composable
fun errorText(error: AppError): String = when {
    error.code == "image_unreadable" -> stringResource(R.string.error_image_unreadable)
    !error.message.isNullOrBlank() -> error.message
    else -> stringResource(error.kind.defaultMessageRes())
}

@StringRes
fun PriceConfidence?.labelRes(): Int = when (this) {
    PriceConfidence.VERIFIED -> R.string.confidence_verified
    PriceConfidence.ESTIMATED -> R.string.confidence_estimated
    PriceConfidence.INCOMPLETE -> R.string.confidence_incomplete
    PriceConfidence.UNKNOWN, null -> R.string.confidence_unknown
}

@StringRes
fun MatchType.labelRes(): Int = when (this) {
    MatchType.EXACT -> R.string.match_exact
    MatchType.LIKELY -> R.string.match_likely
    MatchType.SIMILAR -> R.string.match_similar
    MatchType.DIFFERENT -> R.string.match_different
    MatchType.UNKNOWN -> R.string.match_unknown
}

@StringRes
fun SortOption.labelRes(): Int = when (this) {
    SortOption.PRICE -> R.string.sort_price
    SortOption.RATING -> R.string.sort_rating
}

@StringRes
fun sourceStatusRes(status: String): Int = when (status) {
    "ok", "available" -> R.string.source_ok
    "timeout" -> R.string.source_timeout
    "error" -> R.string.source_error
    "not_configured" -> R.string.source_not_configured
    "not_implemented" -> R.string.source_not_implemented
    "skipped" -> R.string.source_skipped
    else -> R.string.source_unknown
}

private fun number(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

/** Short factual chips for a variant ("Anessa", "60ml", "Size L"). Only what the backend sent. */
@Composable
fun ProductAttributes.chips(): List<String> {
    val out = mutableListOf<String>()
    brand?.takeIf { it.isNotBlank() }?.let(out::add)
    model?.takeIf { it.isNotBlank() }?.let(out::add)
    volumeMl?.let { out.add(number(it) + "ml") }
    weightG?.let { out.add(if (it >= 1000) number(it / 1000) + "kg" else number(it) + "g") }
    storageGb?.let { out.add("${it}GB") }
    size?.takeIf { it.isNotBlank() }?.let { out.add(stringResource(R.string.query_size, it)) }
    count?.let { out.add(stringResource(R.string.query_count, it)) }
    packQuantity?.takeIf { it > 1 }?.let { out.add(stringResource(R.string.query_quantity, it)) }
    color?.takeIf { it.isNotBlank() }?.let(out::add)
    return out
}

/** What the backend understood from the query, as short chips. */
@Composable
fun QuerySpec.chips(): List<String> {
    val out = mutableListOf<String>()
    brand?.takeIf { it.isNotBlank() }?.let(out::add)
    productName?.takeIf { it.isNotBlank() }?.let(out::add)
    model?.takeIf { it.isNotBlank() }?.let(out::add)
    variant?.takeIf { it.isNotBlank() }?.let(out::add)
    volume?.takeIf { it.isNotBlank() }?.let(out::add)
    weight?.takeIf { it.isNotBlank() }?.let(out::add)
    size?.takeIf { it.isNotBlank() }?.let { out.add(stringResource(R.string.query_size, it)) }
    storage?.takeIf { it.isNotBlank() }?.let(out::add)
    color?.takeIf { it.isNotBlank() }?.let(out::add)
    count?.let { out.add(stringResource(R.string.query_count, it)) }
    quantity?.takeIf { it > 1 }?.let { out.add(stringResource(R.string.query_quantity, it)) }
    maxPrice?.let { out.add(stringResource(R.string.query_max_price, Money.format(it))) }
    minPrice?.let { out.add(stringResource(R.string.query_min_price, Money.format(it))) }
    constraints.filter { it.isNotBlank() }.forEach(out::add)
    return out
}
