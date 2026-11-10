package com.dealmate.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.dealmate.app.R
import com.dealmate.app.domain.model.Marketplace
import com.dealmate.app.domain.model.MatchType
import com.dealmate.app.domain.model.SortOption
import com.dealmate.app.domain.usecase.ResultFilter
import com.dealmate.app.ui.common.labelRes
import com.dealmate.app.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchFilterSheet(
    filter: ResultFilter,
    marketplaces: List<Marketplace>,
    onChange: (ResultFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        SearchFilterContent(filter, marketplaces, onChange, onDismiss, Modifier.navigationBarsPadding())
    }
}

private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item

/** Sheet body (separate so it can be rendered directly in tests and screenshots). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchFilterContent(
    filter: ResultFilter,
    marketplaces: List<Marketplace>,
    onChange: (ResultFilter) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = Spacing.lg).padding(bottom = Spacing.lg).testTag("filter_sheet")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.filter_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { onChange(ResultFilter(sort = filter.sort)) }, modifier = Modifier.testTag("filter_reset")) {
                Text(stringResource(R.string.filter_reset))
            }
        }
        Spacer(Modifier.height(Spacing.md))

        Text(stringResource(R.string.filter_sort), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SortOption.entries.forEach { option ->
                FilterChip(
                    selected = filter.sort == option,
                    onClick = { onChange(filter.copy(sort = option)) },
                    label = { Text(stringResource(option.labelRes())) },
                    modifier = Modifier.testTag("sort_${option.name.lowercase()}"),
                )
            }
        }
        if (filter.sort == SortOption.PRICE) {
            Text(
                stringResource(R.string.filter_sort_price_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.lg))

        Text(stringResource(R.string.filter_marketplace), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            marketplaces.forEach { marketplace ->
                FilterChip(
                    selected = marketplace in filter.marketplaces,
                    onClick = { onChange(filter.copy(marketplaces = filter.marketplaces.toggle(marketplace))) },
                    label = { Text(marketplace.displayName) },
                    leadingIcon = { MarketplaceLogo(marketplace, size = androidx.compose.material3.FilterChipDefaults.IconSize) },
                    modifier = Modifier.testTag("filter_marketplace_${marketplace.wire}"),
                )
            }
        }
        Spacer(Modifier.height(Spacing.lg))

        Text(stringResource(R.string.filter_match), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            listOf(MatchType.EXACT, MatchType.LIKELY).forEach { type ->
                FilterChip(
                    selected = type in filter.matchTypes,
                    onClick = { onChange(filter.copy(matchTypes = filter.matchTypes.toggle(type))) },
                    label = { Text(stringResource(type.labelRes())) },
                    modifier = Modifier.testTag("filter_match_${type.name.lowercase()}"),
                )
            }
        }
        Spacer(Modifier.height(Spacing.md))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.filter_hide_over_budget),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = filter.hideOverBudget,
                onCheckedChange = { onChange(filter.copy(hideOverBudget = it)) },
                modifier = Modifier.testTag("filter_over_budget"),
            )
        }
        Spacer(Modifier.height(Spacing.lg))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().testTag("filter_done")) {
            Text(stringResource(R.string.filter_done))
        }
    }
}
