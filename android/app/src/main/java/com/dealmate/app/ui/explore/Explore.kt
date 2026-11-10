package com.dealmate.app.ui.explore

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.SouthEast
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dealmate.app.R
import com.dealmate.app.domain.model.AppError
import com.dealmate.app.domain.model.Explore
import com.dealmate.app.domain.model.Marketplace
import com.dealmate.app.domain.model.Outcome
import com.dealmate.app.domain.model.PriceDrop
import com.dealmate.app.domain.model.VariantSummary
import com.dealmate.app.domain.repository.MetaRepository
import com.dealmate.app.domain.util.Money
import com.dealmate.app.ui.common.relativeTimeText
import com.dealmate.app.ui.components.DemoBanner
import com.dealmate.app.ui.components.DemoTag
import com.dealmate.app.ui.components.EmptyState
import com.dealmate.app.ui.components.ErrorState
import com.dealmate.app.ui.components.LoadingState
import com.dealmate.app.ui.components.MarketplaceLogo
import com.dealmate.app.ui.components.PriceBadge
import com.dealmate.app.ui.components.ProductThumbnail
import com.dealmate.app.ui.components.SectionHeader
import com.dealmate.app.ui.components.cardBorder
import com.dealmate.app.ui.components.cardColors
import com.dealmate.app.ui.theme.DealMateTheme
import com.dealmate.app.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExploreUiState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val explore: Explore? = null,
    val category: String? = null,
    val marketplace: Marketplace? = null,
)

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val repository: MetaRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ExploreUiState())
    val state: StateFlow<ExploreUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        load()
    }

    fun setCategory(category: String?) {
        _state.update { it.copy(category = category) }
        load()
    }

    fun setMarketplace(marketplace: Marketplace?) {
        _state.update { it.copy(marketplace = marketplace) }
        load()
    }

    fun load() {
        job?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            val current = _state.value
            when (val outcome = repository.explore(current.category, current.marketplace)) {
                is Outcome.Success -> _state.update { it.copy(loading = false, explore = outcome.data, error = null) }
                is Outcome.Failure -> _state.update { it.copy(loading = false, error = outcome.error, explore = null) }
            }
        }
    }
}

data class ExploreActions(
    val onRetry: () -> Unit = {},
    val onCategory: (String?) -> Unit = {},
    val onMarketplace: (Marketplace?) -> Unit = {},
    val onOpenProduct: (productId: Long, variantId: Long) -> Unit = { _, _ -> },
    val onOpenSettings: () -> Unit = {},
)

@Composable
fun ExploreRoute(
    onOpenProduct: (Long, Long) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ExploreViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ExploreScreen(
        state,
        ExploreActions(
            onRetry = viewModel::load,
            onCategory = viewModel::setCategory,
            onMarketplace = viewModel::setMarketplace,
            onOpenProduct = onOpenProduct,
            onOpenSettings = onOpenSettings,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(state: ExploreUiState, actions: ExploreActions, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.testTag("explore_screen"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_explore), style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.explore?.demoMode == true) DemoBanner()
            Filters(state, actions)
            Box(Modifier.fillMaxSize()) {
                val explore = state.explore
                when {
                    state.loading -> LoadingState()
                    state.error != null -> ErrorState(state.error, onRetry = actions.onRetry, onOpenSettings = actions.onOpenSettings)
                    explore == null -> Unit
                    // Nothing is invented: without recorded data the backend's own reason is shown.
                    !explore.available || (explore.priceDrops.isEmpty() && explore.recentlySeen.isEmpty()) -> EmptyState(
                        title = stringResource(R.string.explore_empty_title),
                        message = explore.reason?.takeIf { it.isNotBlank() } ?: stringResource(R.string.explore_empty_message),
                        icon = Icons.Outlined.Insights,
                    )
                    else -> LazyColumn(
                        Modifier.fillMaxSize().testTag("explore_list"),
                        contentPadding = PaddingValues(bottom = Spacing.xl),
                    ) {
                        if (explore.priceDrops.isNotEmpty()) {
                            item(key = "drops_header") {
                                SectionHeader(
                                    stringResource(R.string.explore_price_drops),
                                    Modifier.padding(top = Spacing.sm, bottom = Spacing.sm),
                                    subtitle = stringResource(R.string.explore_price_drops_subtitle),
                                )
                            }
                            items(explore.priceDrops, key = { "d${it.variantId}" }) { drop ->
                                PriceDropCard(drop) { actions.onOpenProduct(drop.productId, drop.variantId) }
                            }
                        }
                        if (explore.recentlySeen.isNotEmpty()) {
                            item(key = "seen_header") {
                                SectionHeader(
                                    stringResource(R.string.explore_recently_seen),
                                    Modifier.padding(top = Spacing.lg, bottom = Spacing.sm),
                                    subtitle = stringResource(R.string.explore_recently_seen_subtitle),
                                )
                            }
                            items(explore.recentlySeen, key = { "r${it.variantId}" }) { variant -> SeenRow(variant) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Filters(state: ExploreUiState, actions: ExploreActions) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        FilterChip(
            selected = state.category == null,
            onClick = { actions.onCategory(null) },
            label = { Text(stringResource(R.string.explore_all_categories)) },
        )
        stringArrayResource(R.array.home_categories).forEach { category ->
            FilterChip(
                selected = state.category == category,
                onClick = { actions.onCategory(if (state.category == category) null else category) },
                label = { Text(category) },
                modifier = Modifier.testTag("explore_category_$category"),
            )
        }
    }
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Marketplace.selectable.forEach { marketplace ->
            FilterChip(
                selected = state.marketplace == marketplace,
                onClick = { actions.onMarketplace(if (state.marketplace == marketplace) null else marketplace) },
                label = { Text(marketplace.displayName) },
                leadingIcon = { MarketplaceLogo(marketplace, size = 18.dp) },
            )
        }
    }
}

@Composable
private fun PriceDropCard(drop: PriceDrop, onClick: () -> Unit) {
    val status = DealMateTheme.status
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        shape = MaterialTheme.shapes.medium,
        colors = cardColors(),
        border = cardBorder(),
    ) {
        Row(Modifier.padding(Spacing.md)) {
            ProductThumbnail(drop.imageUrl, size = 64.dp)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Row {
                    Text(
                        drop.title, style = MaterialTheme.typography.titleSmall, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    if (drop.isMock) DemoTag(Modifier.padding(start = Spacing.sm))
                }
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(Money.format(drop.currentLowestPrice), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        Money.format(drop.previousLowestPrice),
                        style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.LineThrough),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.SouthEast, contentDescription = null, tint = status.priceDown, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    val time = relativeTimeText(drop.recordedAt)
                    Text(
                        stringResource(R.string.explore_drop_amount, Money.format(drop.previousLowestPrice - drop.currentLowestPrice)) +
                            (time?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = status.priceDown,
                        modifier = Modifier.weight(1f),
                    )
                    drop.marketplace?.let { MarketplaceLogo(it, size = 16.dp) }
                }
            }
        }
    }
}

@Composable
private fun SeenRow(variant: VariantSummary) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(variant.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                pluralStringResource(R.plurals.offer_count, variant.offerCount, variant.offerCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(Spacing.md))
        Column(horizontalAlignment = Alignment.End) {
            variant.lowestFinalPrice?.let { Text(Money.format(it), style = MaterialTheme.typography.titleSmall) }
            if (variant.lowestFinalPrice != null) PriceBadge(variant.lowestPriceConfidence)
        }
    }
}
