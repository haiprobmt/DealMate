package com.dealmate.app.ui.wishlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.SouthEast
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dealmate.app.R
import com.dealmate.app.domain.model.AppError
import com.dealmate.app.domain.model.Outcome
import com.dealmate.app.domain.model.WishlistItem
import com.dealmate.app.domain.repository.WishlistRepository
import com.dealmate.app.domain.util.Money
import com.dealmate.app.ui.common.UiMessage
import com.dealmate.app.ui.common.relativeTimeText
import com.dealmate.app.ui.common.text
import com.dealmate.app.ui.components.DemoTag
import com.dealmate.app.ui.components.EmptyState
import com.dealmate.app.ui.components.ErrorState
import com.dealmate.app.ui.components.LoadingState
import com.dealmate.app.ui.components.MarketplaceLogo
import com.dealmate.app.ui.components.OfflineHint
import com.dealmate.app.ui.components.PriceBadge
import com.dealmate.app.ui.components.ProductThumbnail
import com.dealmate.app.ui.components.TargetPriceDialog
import com.dealmate.app.ui.components.cardBorder
import com.dealmate.app.ui.components.cardColors
import com.dealmate.app.ui.theme.DealMateTheme
import com.dealmate.app.ui.theme.PriceMedium
import com.dealmate.app.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WishlistUiState(
    val items: List<WishlistItem> = emptyList(),
    /** First load: nothing cached yet and the server has not answered. */
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    /** Last refresh failed for connectivity reasons; [items] are the cached copy. */
    val offline: Boolean = false,
    val error: AppError? = null,
    /** Item hidden while its "undo" snackbar is visible. */
    val pendingRemoval: WishlistItem? = null,
    val message: UiMessage? = null,
)

@HiltViewModel
class WishlistViewModel @Inject constructor(
    private val repository: WishlistRepository,
) : ViewModel() {

    private data class Local(
        val loading: Boolean = true,
        val refreshing: Boolean = false,
        val offline: Boolean = false,
        val error: AppError? = null,
        val pendingRemoval: WishlistItem? = null,
        val message: UiMessage? = null,
    )

    private val local = MutableStateFlow(Local())

    val state: StateFlow<WishlistUiState> = combine(repository.observe(), local) { items, l ->
        WishlistUiState(
            items = items.filter { it.id != l.pendingRemoval?.id },
            loading = l.loading && items.isEmpty(),
            refreshing = l.refreshing,
            offline = l.offline,
            error = l.error.takeIf { items.isEmpty() },
            pendingRemoval = l.pendingRemoval,
            message = l.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WishlistUiState())

    init {
        refresh(userInitiated = false)
    }

    fun refresh(userInitiated: Boolean = true) {
        local.update { it.copy(refreshing = userInitiated, error = null) }
        viewModelScope.launch {
            when (val outcome = repository.refresh()) {
                is Outcome.Success -> local.update { it.copy(loading = false, refreshing = false, offline = false, error = null) }
                is Outcome.Failure -> local.update {
                    it.copy(
                        loading = false, refreshing = false,
                        offline = outcome.error.isConnectivity,
                        error = outcome.error,
                        message = if (userInitiated) UiMessage.Failure(outcome.error) else it.message,
                    )
                }
            }
        }
    }

    /** Hides the item and waits for [confirmRemoval] / [undoRemoval] (driven by the undo snackbar). */
    fun requestRemoval(item: WishlistItem) {
        // A previous pending removal is committed before a new one starts.
        local.value.pendingRemoval?.let { commit(it) }
        local.update { it.copy(pendingRemoval = item) }
    }

    fun undoRemoval() = local.update { it.copy(pendingRemoval = null) }

    fun confirmRemoval() {
        val item = local.value.pendingRemoval ?: return
        commit(item)
    }

    private fun commit(item: WishlistItem) {
        viewModelScope.launch {
            val outcome = repository.remove(item.id)
            local.update {
                it.copy(
                    pendingRemoval = if (it.pendingRemoval?.id == item.id) null else it.pendingRemoval,
                    message = if (outcome is Outcome.Failure) UiMessage.Failure(outcome.error) else it.message,
                )
            }
        }
    }

    fun setTarget(item: WishlistItem, targetPrice: Long?) {
        if (targetPrice == item.targetPrice) return
        viewModelScope.launch {
            val outcome = repository.setTargetPrice(item.variantId, item.alertId, targetPrice)
            local.update {
                it.copy(
                    message = when (outcome) {
                        is Outcome.Success -> UiMessage.Text(if (targetPrice != null) R.string.target_saved else R.string.target_cleared)
                        is Outcome.Failure -> UiMessage.Failure(outcome.error)
                    },
                )
            }
        }
    }

    fun messageShown() = local.update { it.copy(message = null) }
}

data class WishlistActions(
    val onRefresh: () -> Unit = {},
    val onOpenProduct: (productId: Long, variantId: Long) -> Unit = { _, _ -> },
    val onRemove: (WishlistItem) -> Unit = {},
    val onEditTarget: (WishlistItem) -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onGoSearch: () -> Unit = {},
)

@Composable
fun WishlistRoute(
    onOpenProduct: (Long, Long) -> Unit,
    onOpenSettings: () -> Unit,
    onGoSearch: () -> Unit,
    viewModel: WishlistViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<WishlistItem?>(null) }

    val pending = state.pendingRemoval
    val removedText = stringResource(R.string.wishlist_removed)
    val undoText = stringResource(R.string.action_undo)
    LaunchedEffect(pending?.id) {
        if (pending != null) {
            val result = snackbar.showSnackbar(removedText, actionLabel = undoText, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) viewModel.undoRemoval() else viewModel.confirmRemoval()
        }
    }
    val message = state.message
    val messageText = message?.text()
    LaunchedEffect(message?.id) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            viewModel.messageShown()
        }
    }

    WishlistScreen(
        state = state,
        snackbar = snackbar,
        actions = WishlistActions(
            onRefresh = { viewModel.refresh() },
            onOpenProduct = onOpenProduct,
            onRemove = viewModel::requestRemoval,
            onEditTarget = { editing = it },
            onOpenSettings = onOpenSettings,
            onGoSearch = onGoSearch,
        ),
    )

    editing?.let { item ->
        TargetPriceDialog(
            initial = item.targetPrice,
            title = stringResource(R.string.target_dialog_title_edit),
            referencePrice = item.currentLowestPrice,
            onConfirm = { target ->
                editing = null
                viewModel.setTarget(item, target)
            },
            onDismiss = { editing = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistScreen(
    state: WishlistUiState,
    actions: WishlistActions,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier.testTag("wishlist_screen"),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_wishlist), style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = actions.onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                state.loading -> LoadingState()
                state.items.isEmpty() && state.error != null && state.pendingRemoval == null ->
                    ScrollableBox { ErrorState(state.error, onRetry = actions.onRefresh, onOpenSettings = actions.onOpenSettings) }
                state.items.isEmpty() -> ScrollableBox {
                    EmptyState(
                        title = stringResource(R.string.wishlist_empty_title),
                        message = stringResource(R.string.wishlist_empty_message),
                        icon = Icons.Outlined.FavoriteBorder,
                        actionLabel = stringResource(R.string.wishlist_empty_action),
                        onAction = actions.onGoSearch,
                    )
                }
                else -> LazyColumn(
                    Modifier.fillMaxSize().testTag("wishlist_list"),
                    contentPadding = PaddingValues(bottom = Spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    if (state.offline) item(key = "offline") { OfflineHint(stringResource(R.string.wishlist_offline)) }
                    items(state.items, key = { it.id }) { item ->
                        WishlistCard(item, actions, Modifier.padding(horizontal = Spacing.lg))
                    }
                }
            }
        }
    }
}

/** Keeps pull-to-refresh usable on empty / error content. */
@Composable
private fun ScrollableBox(content: @Composable () -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) { item { Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { content() } } }
}

@Composable
private fun WishlistCard(item: WishlistItem, actions: WishlistActions, modifier: Modifier = Modifier) {
    val status = DealMateTheme.status
    Card(
        onClick = { actions.onOpenProduct(item.productId, item.variantId) },
        modifier = modifier.fillMaxWidth().testTag("wishlist_item_${item.id}"),
        shape = MaterialTheme.shapes.medium,
        colors = cardColors(),
        border = cardBorder(),
    ) {
        Column(Modifier.padding(start = Spacing.md, top = Spacing.md, end = Spacing.xs, bottom = Spacing.xs)) {
            Row {
                ProductThumbnail(item.imageUrl, size = 64.dp)
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f).padding(end = Spacing.sm)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            item.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (item.isMock) DemoTag(Modifier.padding(start = Spacing.sm, top = 2.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    val current = item.currentLowestPrice
                    if (current != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Text(Money.format(current), style = PriceMedium, modifier = Modifier.testTag("wishlist_price"))
                            PriceBadge(item.currentPriceConfidence)
                        }
                        val previous = item.previousLowestPrice
                        if (previous != null && previous != current) {
                            val down = current < previous
                            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (down) Icons.Outlined.SouthEast else Icons.Outlined.NorthEast,
                                    contentDescription = null,
                                    tint = if (down) status.priceDown else status.priceUp,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    stringResource(
                                        if (down) R.string.wishlist_price_down else R.string.wishlist_price_up,
                                        Money.format(kotlin.math.abs(previous - current)),
                                        Money.format(previous),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (down) status.priceDown else status.priceUp,
                                    modifier = Modifier.testTag("wishlist_change"),
                                )
                            }
                        }
                    } else {
                        Text(
                            stringResource(R.string.wishlist_no_price),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val checked = relativeTimeText(item.lastCheckedAt)
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        item.currentMarketplace?.let {
                            MarketplaceLogo(it, size = 16.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            if (checked != null) stringResource(R.string.wishlist_checked, checked)
                            else stringResource(R.string.wishlist_not_checked),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { actions.onEditTarget(item) },
                    contentPadding = PaddingValues(horizontal = Spacing.xs),
                    modifier = Modifier.testTag("wishlist_target_${item.id}"),
                ) {
                    Icon(Icons.Outlined.NotificationsNone, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        item.targetPrice?.let { stringResource(R.string.target_value, Money.format(it)) }
                            ?: stringResource(R.string.target_set),
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { actions.onRemove(item) }, modifier = Modifier.testTag("wishlist_remove_${item.id}")) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = stringResource(R.string.wishlist_remove),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

