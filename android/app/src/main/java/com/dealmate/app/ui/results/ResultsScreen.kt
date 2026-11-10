package com.dealmate.app.ui.results

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dealmate.app.R
import com.dealmate.app.domain.model.BestOffer
import com.dealmate.app.domain.model.InterpretedQuery
import com.dealmate.app.domain.model.MatchType
import com.dealmate.app.domain.model.ProductGroup
import com.dealmate.app.domain.model.SearchResult
import com.dealmate.app.domain.model.SearchType
import com.dealmate.app.domain.model.SourceResult
import com.dealmate.app.domain.usecase.ResultFilter
import com.dealmate.app.domain.util.Money
import com.dealmate.app.ui.assistant.AssistantSheet
import com.dealmate.app.ui.common.chips
import com.dealmate.app.ui.common.labelRes
import com.dealmate.app.ui.common.relativeTimeText
import com.dealmate.app.ui.common.sourceStatusRes
import com.dealmate.app.ui.components.DemoBanner
import com.dealmate.app.ui.components.DemoTag
import com.dealmate.app.ui.components.EmptyState
import com.dealmate.app.ui.components.ErrorState
import com.dealmate.app.ui.components.LoadingState
import com.dealmate.app.ui.components.MarketplaceLogo
import com.dealmate.app.ui.components.OfflineHint
import com.dealmate.app.ui.components.PriceBadge
import com.dealmate.app.ui.components.ProductCard
import com.dealmate.app.ui.components.SearchFilterSheet
import com.dealmate.app.ui.components.SectionHeader
import com.dealmate.app.ui.components.cardBorder
import com.dealmate.app.ui.components.cardColors
import com.dealmate.app.ui.theme.DealMateTheme
import com.dealmate.app.ui.theme.PriceLarge
import com.dealmate.app.ui.theme.Spacing

data class ResultsActions(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onOpenProduct: (productId: Long, variantId: Long) -> Unit = { _, _ -> },
    val onFilterChange: (ResultFilter) -> Unit = {},
    val onSuggestion: (String) -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onShowFilter: () -> Unit = {},
    val onAskAssistant: () -> Unit = {},
)

@Composable
fun ResultsRoute(
    onBack: () -> Unit,
    onOpenProduct: (Long, Long) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ResultsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showFilter by rememberSaveable { mutableStateOf(false) }
    var showAssistant by rememberSaveable { mutableStateOf(false) }

    ResultsScreen(
        state = state,
        actions = ResultsActions(
            onBack = onBack,
            onRetry = viewModel::retry,
            onOpenProduct = onOpenProduct,
            onFilterChange = viewModel::setFilter,
            onSuggestion = viewModel::searchSuggestion,
            onOpenSettings = onOpenSettings,
            onShowFilter = { showFilter = true },
            onAskAssistant = { showAssistant = true },
        ),
    )
    if (showFilter) {
        SearchFilterSheet(
            filter = state.filter,
            marketplaces = state.availableMarketplaces,
            onChange = viewModel::setFilter,
            onDismiss = { showFilter = false },
        )
    }
    if (showAssistant && state.assistantVariantIds.isNotEmpty()) {
        AssistantSheet(variantIds = state.assistantVariantIds, onDismiss = { showAssistant = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(state: ResultsUiState, actions: ResultsActions, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.testTag("results_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.title.ifBlank { stringResource(R.string.results_title) },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (state.result != null && state.assistantVariantIds.isNotEmpty()) {
                        IconButton(onClick = actions.onAskAssistant, modifier = Modifier.testTag("assistant_button")) {
                            Icon(Icons.Outlined.AutoAwesome, contentDescription = stringResource(R.string.assistant_title))
                        }
                    }
                    if (state.result != null) {
                        IconButton(onClick = actions.onShowFilter, modifier = Modifier.testTag("filter_button")) {
                            BadgedBox(badge = { if (state.filter.activeCount > 0) Badge { Text(state.filter.activeCount.toString()) } }) {
                                Icon(Icons.Outlined.Tune, contentDescription = stringResource(R.string.filter_title))
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val result = state.result
            when {
                state.loading -> LoadingState(
                    message = stringResource(
                        if (state.request.type == SearchType.IMAGE) R.string.results_loading_image else R.string.results_loading,
                    ),
                )
                state.error != null -> ErrorState(state.error, onRetry = actions.onRetry, onOpenSettings = actions.onOpenSettings)
                result != null -> ResultsContent(state, result, actions)
            }
        }
    }
}

@Composable
private fun ResultsContent(state: ResultsUiState, result: SearchResult, actions: ResultsActions) {
    val groups = state.groups
    val similar = state.similarGroups
    val hasAnyResult = result.groups.isNotEmpty() || result.similarGroups.isNotEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("results_list"),
        contentPadding = PaddingValues(bottom = Spacing.xl),
    ) {
        if (result.demoMode) item(key = "demo") { DemoBanner() }
        if (state.fromCache) {
            item(key = "offline") {
                val saved = relativeTimeText(state.cachedAt)
                OfflineHint(
                    if (saved != null) stringResource(R.string.results_offline_saved, saved)
                    else stringResource(R.string.results_offline),
                )
            }
        }
        item(key = "query") { QuerySummary(result.query) }
        item(key = "sources") { SourceRow(result.sources, result.partial) }

        if (result.clarification.needed || !result.clarification.message.isNullOrBlank()) {
            item(key = "clarification") {
                ClarificationCard(result.clarification.message, result.clarification.suggestions, actions.onSuggestion)
            }
        }

        val best = result.best
        when {
            best != null && state.bestVisible -> item(key = "best") {
                BestOfferBanner(
                    best = best,
                    group = state.bestGroup,
                    onClick = { state.bestGroup?.let { actions.onOpenProduct(it.productId, it.variantId) } },
                )
            }
            best != null && result.groups.isNotEmpty() -> item(key = "best_hidden") {
                NoticeCard(
                    text = stringResource(R.string.results_best_hidden),
                    actionLabel = stringResource(R.string.filter_reset),
                    onAction = { actions.onFilterChange(ResultFilter(sort = state.filter.sort)) },
                )
            }
            best == null && result.groups.isNotEmpty() -> item(key = "no_best") {
                NoticeCard(text = stringResource(R.string.results_no_best))
            }
        }

        if (!hasAnyResult) {
            if (!result.clarification.needed) {
                item(key = "empty") {
                    EmptyState(
                        title = stringResource(R.string.results_empty_title),
                        message = if (result.excludedCount > 0) {
                            pluralStringResource(R.plurals.results_empty_excluded, result.excludedCount, result.excludedCount)
                        } else stringResource(R.string.results_empty_message),
                        icon = Icons.Outlined.SearchOff,
                    )
                }
            }
        } else if (groups.isEmpty() && result.groups.isNotEmpty()) {
            item(key = "filtered_out") {
                EmptyState(
                    title = stringResource(R.string.results_filtered_title),
                    message = stringResource(R.string.results_filtered_message),
                    icon = Icons.Outlined.Tune,
                    actionLabel = stringResource(R.string.filter_reset),
                    onAction = { actions.onFilterChange(ResultFilter(sort = state.filter.sort)) },
                )
            }
        }

        // Main results, grouped by how sure the match is.
        listOf(MatchType.EXACT, MatchType.LIKELY).forEach { type ->
            val section = groups.filter { it.match.type == type }
            if (section.isNotEmpty()) {
                item(key = "header_$type") {
                    SectionHeader(
                        title = stringResource(type.labelRes()),
                        subtitle = if (type == MatchType.LIKELY) stringResource(R.string.results_likely_subtitle) else null,
                        modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.sm),
                    )
                }
                groupItems(section, "main", actions)
            }
        }
        val other = groups.filter { it.match.type != MatchType.EXACT && it.match.type != MatchType.LIKELY }
        if (other.isNotEmpty()) groupItems(other, "other", actions)

        if (similar.isNotEmpty()) {
            item(key = "header_similar") {
                Column(Modifier.padding(top = Spacing.xl).testTag("similar_section")) {
                    SectionHeader(
                        title = stringResource(R.string.results_similar_title),
                        subtitle = stringResource(R.string.results_similar_subtitle),
                        modifier = Modifier.padding(bottom = Spacing.sm),
                    )
                }
            }
            groupItems(similar, "similar", actions)
        }

        if (result.excludedCount > 0 && hasAnyResult) {
            item(key = "excluded") {
                Text(
                    pluralStringResource(R.plurals.results_excluded, result.excludedCount, result.excludedCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.lg),
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.groupItems(
    groups: List<ProductGroup>,
    prefix: String,
    actions: ResultsActions,
) {
    items(groups, key = { "$prefix-${it.variantId}" }) { group ->
        ProductCard(
            group = group,
            onClick = { actions.onOpenProduct(group.productId, group.variantId) },
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        )
    }
}

@Composable
private fun QuerySummary(query: InterpretedQuery) {
    val chips = query.spec.chips()
    Column(Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                stringResource(if (query.interpreter == "ai") R.string.results_understood_ai else R.string.results_understood_rules),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            chips.forEach { chip ->
                Text(
                    chip,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .testTag("query_chip"),
                )
            }
            if (chips.isEmpty()) {
                Text(
                    stringResource(R.string.results_understood_none),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        query.notes.filter { it.isNotBlank() }.forEach { note ->
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.lg).padding(top = Spacing.xs),
            )
        }
    }
}

/** Which marketplaces answered. Failures are named so a partial result is never silently partial. */
@Composable
private fun SourceRow(sources: List<SourceResult>, partial: Boolean) {
    if (sources.isEmpty()) return
    val status = DealMateTheme.status
    Column(Modifier.fillMaxWidth().padding(top = Spacing.md).testTag("source_row")) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            sources.forEach { source ->
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MarketplaceLogo(source.marketplace, size = 20.dp, name = source.marketplaceName)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (source.isOk) source.offersFound.toString() else stringResource(sourceStatusRes(source.status)),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (source.isOk) MaterialTheme.colorScheme.onSurface else status.estimated,
                    )
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        if (source.isOk) Icons.Outlined.Check else Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = if (source.isOk) status.verified else status.estimated,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        val failed = sources.filter { !it.isOk && it.status != "skipped" }
        if (partial || failed.isNotEmpty()) {
            val labels = failed.map { "${it.marketplaceName} (${stringResource(sourceStatusRes(it.status)).lowercase()})" }
            val names = labels.joinToString(", ")
            Row(
                Modifier.padding(horizontal = Spacing.lg).padding(top = Spacing.sm).testTag("partial_notice"),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = status.estimated, modifier = Modifier.size(15.dp).padding(top = 1.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (names.isNotEmpty()) stringResource(R.string.results_partial_named, names)
                    else stringResource(R.string.results_partial),
                    style = MaterialTheme.typography.bodySmall,
                    color = status.estimated,
                )
            }
        }
    }
}

/** The headline answer: the cheapest offer of the best-matching product (as decided by the backend). */
@Composable
private fun BestOfferBanner(best: BestOffer, group: ProductGroup?, onClick: () -> Unit) {
    val container = MaterialTheme.colorScheme.primaryContainer
    val content = MaterialTheme.colorScheme.onPrimaryContainer
    val isMock = group?.offers?.firstOrNull { it.id == best.offerId }?.isMock == true
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).padding(top = Spacing.lg).testTag("best_banner"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.best_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                if (isMock) DemoTag()
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(Money.format(best.finalPrice), style = PriceLarge, color = content, modifier = Modifier.testTag("best_price"))
                PriceBadge(best.confidence)
            }
            Spacer(Modifier.height(Spacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MarketplaceLogo(best.marketplace, size = 20.dp, name = best.marketplaceName)
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.best_at_marketplace, best.marketplaceName),
                    style = MaterialTheme.typography.titleSmall,
                    color = content,
                )
            }
            if (group != null) {
                Text(
                    group.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = content,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (best.explanation.isNotBlank()) {
                Text(
                    best.explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (!best.caveat.isNullOrBlank()) {
                Row(Modifier.padding(top = Spacing.sm).testTag("best_caveat"), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = content, modifier = Modifier.size(15.dp).padding(top = 1.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(best.caveat, style = MaterialTheme.typography.bodySmall, color = content)
                }
            }
            if (group != null) {
                Row(Modifier.padding(top = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.best_view_detail),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun NoticeCard(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).padding(top = Spacing.lg).testTag("notice_card"),
        shape = MaterialTheme.shapes.medium,
        colors = cardColors(),
        border = cardBorder(),
    ) {
        Row(Modifier.padding(start = Spacing.lg, end = Spacing.sm, top = Spacing.sm, bottom = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.md))
            Text(
                text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).padding(vertical = Spacing.sm),
            )
            if (actionLabel != null && onAction != null) TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClarificationCard(message: String?, suggestions: List<String>, onSuggestion: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).padding(top = Spacing.lg).testTag("clarification_card"),
        shape = MaterialTheme.shapes.medium,
        colors = cardColors(),
        border = cardBorder(),
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            Text(stringResource(R.string.clarification_title), style = MaterialTheme.typography.titleSmall)
            if (!message.isNullOrBlank()) {
                Text(
                    message, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            if (suggestions.isNotEmpty()) {
                FlowRow(Modifier.padding(top = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    suggestions.forEach { suggestion ->
                        SuggestionChip(
                            onClick = { onSuggestion(suggestion) },
                            label = { Text(suggestion, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.testTag("suggestion_chip"),
                        )
                    }
                }
            }
        }
    }
}
