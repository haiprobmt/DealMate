package com.dealmate.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.NorthWest
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dealmate.app.R
import com.dealmate.app.domain.model.SearchRequest
import com.dealmate.app.domain.model.SearchType
import com.dealmate.app.domain.model.ViewedProduct
import com.dealmate.app.domain.util.Money
import com.dealmate.app.ui.common.errorText
import com.dealmate.app.ui.components.DealMateSearchBar
import com.dealmate.app.ui.components.ProductImagePicker
import com.dealmate.app.ui.components.ProductThumbnail
import com.dealmate.app.ui.components.SearchMethodSelector
import com.dealmate.app.ui.components.SectionHeader
import com.dealmate.app.ui.components.cardBorder
import com.dealmate.app.ui.components.cardColors
import com.dealmate.app.ui.theme.Spacing

data class HomeActions(
    val onMethod: (SearchType) -> Unit = {},
    val onQuery: (String) -> Unit = {},
    val onUrl: (String) -> Unit = {},
    val onImageHint: (String) -> Unit = {},
    val onImagePicked: (String) -> Unit = {},
    val onClearImage: () -> Unit = {},
    val onSearch: (SearchRequest) -> Unit = {},
    val onOpenProduct: (productId: Long, variantId: Long) -> Unit = { _, _ -> },
    val onOpenWishlist: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
)

@Composable
fun HomeRoute(
    onSearch: (SearchRequest) -> Unit,
    onOpenProduct: (Long, Long) -> Unit,
    onOpenWishlist: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        actions = HomeActions(
            onMethod = viewModel::setMethod,
            onQuery = viewModel::setQuery,
            onUrl = viewModel::setUrl,
            onImageHint = viewModel::setImageHint,
            onImagePicked = viewModel::onImagePicked,
            onClearImage = viewModel::clearImage,
            onSearch = onSearch,
            onOpenProduct = onOpenProduct,
            onOpenWishlist = onOpenWishlist,
            onOpenSettings = onOpenSettings,
        ),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(state: HomeUiState, actions: HomeActions, modifier: Modifier = Modifier) {
    val submit = { state.request?.let(actions.onSearch) ?: Unit }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(vertical = Spacing.lg)
            .testTag("home_screen"),
    ) {
        Column(Modifier.padding(horizontal = Spacing.lg)) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.home_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.lg))

            if (!state.configured) {
                NotConfiguredCard(actions.onOpenSettings)
                Spacer(Modifier.height(Spacing.lg))
            }

            SearchMethodSelector(state.method, actions.onMethod)
            Spacer(Modifier.height(Spacing.md))

            when (state.method) {
                SearchType.TEXT -> {
                    DealMateSearchBar(
                        value = state.query,
                        onValueChange = actions.onQuery,
                        onSearch = submit,
                        placeholder = stringResource(R.string.home_search_placeholder),
                    )
                }
                SearchType.URL -> {
                    // Synchronous one-shot read on tap; the suspend-based LocalClipboard adds nothing here.
                    @Suppress("DEPRECATION")
                    val clipboard = LocalClipboardManager.current
                    DealMateSearchBar(
                        value = state.url,
                        onValueChange = actions.onUrl,
                        onSearch = submit,
                        placeholder = stringResource(R.string.home_url_placeholder),
                        leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                        keyboardType = KeyboardType.Uri,
                        trailing = {
                            IconButton(
                                onClick = { clipboard.getText()?.text?.trim()?.takeIf { it.isNotEmpty() }?.let(actions.onUrl) },
                                modifier = Modifier.testTag("paste_button"),
                            ) { Icon(Icons.Outlined.ContentPaste, contentDescription = stringResource(R.string.action_paste)) }
                        },
                    )
                    Text(
                        stringResource(R.string.home_url_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
                SearchType.IMAGE -> {
                    ProductImagePicker(
                        preparedImagePath = state.imagePath,
                        busy = state.preparingImage,
                        onImagePicked = actions.onImagePicked,
                        onClear = actions.onClearImage,
                    )
                    state.imageError?.let {
                        Text(
                            errorText(it), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = Spacing.xs),
                        )
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    OutlinedTextField(
                        value = state.imageHint,
                        onValueChange = actions.onImageHint,
                        label = { Text(stringResource(R.string.home_image_hint_label)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(R.string.home_image_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.md))
            Button(
                onClick = submit,
                enabled = state.request != null,
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("search_button"),
                shape = MaterialTheme.shapes.large,
            ) { Text(stringResource(R.string.home_search_button)) }
        }

        if (state.method == SearchType.TEXT) {
            Spacer(Modifier.height(Spacing.xl))
            SectionHeader(stringResource(R.string.home_examples_title))
            Column(Modifier.padding(horizontal = Spacing.sm)) {
                stringArrayResource(R.array.home_example_queries).forEach { example ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { actions.onQuery(example) }
                            .padding(horizontal = Spacing.sm, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            example,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.Outlined.NorthWest, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.lg))
        SectionHeader(stringResource(R.string.home_categories_title))
        Spacer(Modifier.height(Spacing.xs))
        FlowRow(
            Modifier.padding(horizontal = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // Shortcuts only run a search for the category word; they carry no product data.
            stringArrayResource(R.array.home_categories).forEach { category ->
                SuggestionChip(
                    onClick = { actions.onSearch(SearchRequest(SearchType.TEXT, category)) },
                    label = { Text(category) },
                    colors = SuggestionChipDefaults.suggestionChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = cardBorder(),
                    modifier = Modifier.testTag("category_$category"),
                )
            }
        }

        if (state.recentSearches.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.lg))
            SectionHeader(stringResource(R.string.home_recent_searches))
            Column(Modifier.padding(horizontal = Spacing.sm)) {
                state.recentSearches.take(5).forEach { recent ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { actions.onSearch(SearchRequest(recent.type, recent.query)) }
                            .padding(horizontal = Spacing.sm, vertical = 10.dp)
                            .testTag("recent_search"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (recent.type == SearchType.URL) Icons.Outlined.Link else Icons.Outlined.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(Spacing.md))
                        Text(
                            recent.query,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        if (state.recentlyViewed.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.lg))
            SectionHeader(stringResource(R.string.home_recently_viewed))
            Spacer(Modifier.height(Spacing.sm))
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                items(state.recentlyViewed, key = { it.variantId }) { viewed ->
                    ViewedCard(viewed) { actions.onOpenProduct(viewed.productId, viewed.variantId) }
                }
            }
        }

        Spacer(Modifier.height(Spacing.lg))
        Card(
            onClick = actions.onOpenWishlist,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).testTag("home_wishlist_link"),
            shape = MaterialTheme.shapes.medium,
            colors = cardColors(),
            border = cardBorder(),
        ) {
            Row(Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.FavoriteBorder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.home_wishlist_title), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.home_wishlist_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NotConfiguredCard(onOpenSettings: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("not_configured_card"),
        shape = MaterialTheme.shapes.medium,
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(Modifier.padding(start = Spacing.lg, top = Spacing.md, bottom = Spacing.md, end = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.not_configured_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    stringResource(R.string.not_configured_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.action_open_settings)) }
        }
    }
}

@Composable
private fun ViewedCard(viewed: ViewedProduct, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(132.dp),
        shape = MaterialTheme.shapes.medium,
        colors = cardColors(),
        border = cardBorder(),
    ) {
        Column(Modifier.padding(Spacing.sm)) {
            ProductThumbnail(viewed.imageUrl, size = 116.dp)
            Spacer(Modifier.height(6.dp))
            Text(
                viewed.title,
                style = MaterialTheme.typography.bodySmall,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // The price seen at the time of viewing; the detail screen fetches the current one.
            Text(
                viewed.lowestFinalPrice?.let { Money.format(it) } ?: "—",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

