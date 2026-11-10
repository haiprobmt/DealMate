package com.dealmate.app.ui.product

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dealmate.app.R
import com.dealmate.app.domain.model.ProductOffers
import com.dealmate.app.domain.util.Money
import com.dealmate.app.ui.assistant.AssistantSheet
import com.dealmate.app.ui.common.chips
import com.dealmate.app.ui.common.text
import com.dealmate.app.ui.components.DemoBanner
import com.dealmate.app.ui.components.EmptyState
import com.dealmate.app.ui.components.ErrorState
import com.dealmate.app.ui.components.LoadingState
import com.dealmate.app.ui.components.PriceComparisonSection
import com.dealmate.app.ui.components.PriceHistoryChart
import com.dealmate.app.ui.components.ProductThumbnail
import com.dealmate.app.ui.components.SectionHeader
import com.dealmate.app.ui.components.TargetPriceDialog
import com.dealmate.app.ui.components.WishlistButton
import com.dealmate.app.ui.components.rememberListingOpener
import com.dealmate.app.ui.theme.Spacing

data class ProductActions(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onSelectVariant: (Long) -> Unit = {},
    val onWishlistClick: () -> Unit = {},
    val onEditTarget: () -> Unit = {},
    val onOpenListing: (String) -> Unit = {},
    val onAskAssistant: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
)

private enum class TargetDialog { ADD, EDIT }

@Composable
fun ProductRoute(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ProductViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var dialog by rememberSaveable { mutableStateOf<TargetDialog?>(null) }
    var showAssistant by rememberSaveable { mutableStateOf(false) }
    val openListing = rememberListingOpener()

    val message = state.message
    val messageText = message?.text()
    LaunchedEffect(message?.id) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            viewModel.messageShown()
        }
    }

    ProductScreen(
        state = state,
        snackbar = snackbar,
        actions = ProductActions(
            onBack = onBack,
            onRetry = viewModel::load,
            onSelectVariant = viewModel::selectVariant,
            onWishlistClick = {
                if (state.product?.inWishlist == true) viewModel.removeFromWishlist() else dialog = TargetDialog.ADD
            },
            onEditTarget = { dialog = TargetDialog.EDIT },
            onOpenListing = openListing,
            onAskAssistant = { showAssistant = true },
            onOpenSettings = onOpenSettings,
        ),
    )

    dialog?.let { kind ->
        TargetPriceDialog(
            initial = state.product?.targetPrice,
            title = stringResource(if (kind == TargetDialog.ADD) R.string.target_dialog_title_add else R.string.target_dialog_title_edit),
            referencePrice = state.bestOffer?.price?.finalPrice,
            onConfirm = { target ->
                dialog = null
                when {
                    kind == TargetDialog.ADD -> viewModel.addToWishlist(target)
                    target != null -> viewModel.setTargetPrice(target)
                }
            },
            onDismiss = { dialog = null },
        )
    }
    val product = state.product
    if (showAssistant && product != null) {
        AssistantSheet(variantIds = listOf(product.variantId), onDismiss = { showAssistant = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductScreen(
    state: ProductUiState,
    actions: ProductActions,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier.testTag("product_screen"),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.product_title), style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (state.product != null) {
                        IconButton(onClick = actions.onAskAssistant, modifier = Modifier.testTag("assistant_button")) {
                            Icon(Icons.Outlined.AutoAwesome, contentDescription = stringResource(R.string.assistant_title))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val product = state.product
            when {
                state.loading -> LoadingState()
                state.error != null -> ErrorState(state.error, onRetry = actions.onRetry, onOpenSettings = actions.onOpenSettings)
                product != null -> ProductContent(state, product, actions)
            }
        }
    }
}

@Composable
private fun ProductContent(state: ProductUiState, product: ProductOffers, actions: ProductActions) {
    LazyColumn(Modifier.fillMaxSize().testTag("product_list"), contentPadding = PaddingValues(bottom = Spacing.xl)) {
        if (product.demoMode) item(key = "demo") { DemoBanner() }

        item(key = "header") {
            Row(Modifier.padding(horizontal = Spacing.lg).padding(top = Spacing.md)) {
                ProductThumbnail(product.imageUrl, size = 88.dp)
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        product.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("product_title"),
                    )
                    val chips = product.attributes.chips()
                    if (chips.isNotEmpty()) {
                        Text(
                            chips.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                    }
                }
            }
        }

        if (state.variants.size > 1) {
            item(key = "variants") {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.lg).padding(top = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    state.variants.forEach { variant ->
                        val label = variant.attributes.chips().drop(if (variant.attributes.brand != null) 1 else 0)
                            .joinToString(" · ").ifBlank { variant.title }
                        FilterChip(
                            selected = variant.variantId == product.variantId,
                            onClick = { actions.onSelectVariant(variant.variantId) },
                            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.testTag("variant_${variant.variantId}"),
                        )
                    }
                }
            }
        }

        item(key = "wishlist") {
            Row(
                Modifier.padding(horizontal = Spacing.lg).padding(top = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WishlistButton(product.inWishlist, state.wishlistBusy, actions.onWishlistClick)
                if (product.inWishlist) {
                    Spacer(Modifier.width(Spacing.sm))
                    TextButton(onClick = actions.onEditTarget, enabled = !state.wishlistBusy, modifier = Modifier.testTag("edit_target")) {
                        Text(
                            product.targetPrice?.let { stringResource(R.string.target_value, Money.format(it)) }
                                ?: stringResource(R.string.target_set),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        item(key = "offers_header") {
            SectionHeader(
                title = stringResource(R.string.product_offers_title),
                subtitle = pluralStringResource(R.plurals.product_offers_subtitle, product.offers.size, product.offers.size),
                modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.sm),
            )
        }
        if (product.offers.isEmpty()) {
            item(key = "no_offers") {
                EmptyState(
                    title = stringResource(R.string.product_no_offers_title),
                    message = stringResource(R.string.product_no_offers_message),
                    icon = Icons.Outlined.Inventory2,
                )
            }
        } else {
            if (state.bestOffer == null) {
                item(key = "no_best") {
                    Text(
                        stringResource(R.string.results_no_best),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.sm),
                    )
                }
            }
            item(key = "offers") {
                PriceComparisonSection(
                    offers = product.offers,
                    bestOfferId = state.bestOffer?.id,
                    onOpenListing = actions.onOpenListing,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
            }
        }

        item(key = "history") {
            Spacer(Modifier.height(Spacing.lg))
            PriceHistoryChart(product.priceHistory, Modifier.padding(horizontal = Spacing.lg))
        }
    }
}
