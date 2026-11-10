package com.dealmate.app.ui.history

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dealmate.app.R
import com.dealmate.app.domain.model.AppError
import com.dealmate.app.domain.model.History
import com.dealmate.app.domain.model.Outcome
import com.dealmate.app.domain.model.SearchHistoryItem
import com.dealmate.app.domain.model.SearchRequest
import com.dealmate.app.domain.model.SearchType
import com.dealmate.app.domain.model.ViewedProduct
import com.dealmate.app.domain.repository.HistoryRepository
import com.dealmate.app.domain.util.Money
import com.dealmate.app.ui.common.UiMessage
import com.dealmate.app.ui.common.relativeTimeText
import com.dealmate.app.ui.common.text
import com.dealmate.app.ui.components.EmptyState
import com.dealmate.app.ui.components.ErrorState
import com.dealmate.app.ui.components.LoadingState
import com.dealmate.app.ui.components.ProductThumbnail
import com.dealmate.app.ui.components.SectionHeader
import com.dealmate.app.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val history: History? = null,
    val clearing: Boolean = false,
    val message: UiMessage? = null,
) {
    val isEmpty: Boolean get() = history?.let { it.searches.isEmpty() && it.viewedProducts.isEmpty() } ?: true
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: HistoryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = it.history == null, error = null) }
        viewModelScope.launch {
            when (val outcome = repository.history()) {
                is Outcome.Success -> _state.update { it.copy(loading = false, history = outcome.data, error = null) }
                is Outcome.Failure -> _state.update { it.copy(loading = false, error = outcome.error, history = null) }
            }
        }
    }

    fun clear() {
        _state.update { it.copy(clearing = true) }
        viewModelScope.launch {
            when (val outcome = repository.clear()) {
                is Outcome.Success -> _state.update {
                    it.copy(clearing = false, history = History(), message = UiMessage.Text(R.string.history_cleared))
                }
                is Outcome.Failure -> _state.update { it.copy(clearing = false, message = UiMessage.Failure(outcome.error)) }
            }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}

data class HistoryActions(
    val onRetry: () -> Unit = {},
    val onSearch: (SearchRequest) -> Unit = {},
    val onOpenProduct: (productId: Long, variantId: Long) -> Unit = { _, _ -> },
    val onClear: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
)

@Composable
fun HistoryRoute(
    onSearch: (SearchRequest) -> Unit,
    onOpenProduct: (Long, Long) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirm by rememberSaveable { mutableStateOf(false) }
    // Coming back to the tab shows searches made in the meantime.
    LaunchedEffect(Unit) { if (!state.loading) viewModel.load() }

    val message = state.message
    val messageText = message?.text()
    LaunchedEffect(message?.id) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            viewModel.messageShown()
        }
    }

    HistoryScreen(
        state = state,
        snackbar = snackbar,
        actions = HistoryActions(
            onRetry = viewModel::load,
            onSearch = onSearch,
            onOpenProduct = onOpenProduct,
            onClear = { confirm = true },
            onOpenSettings = onOpenSettings,
        ),
    )

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.history_clear_title)) },
            text = { Text(stringResource(R.string.history_clear_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirm = false
                        viewModel.clear()
                    },
                    modifier = Modifier.testTag("history_clear_confirm"),
                ) { Text(stringResource(R.string.history_clear_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    actions: HistoryActions,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier.testTag("history_screen"),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_history), style = MaterialTheme.typography.titleLarge) },
                actions = {
                    if (!state.isEmpty) {
                        IconButton(onClick = actions.onClear, enabled = !state.clearing, modifier = Modifier.testTag("history_clear")) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = stringResource(R.string.history_clear_title))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val history = state.history
            when {
                state.loading -> LoadingState()
                state.error != null -> ErrorState(state.error, onRetry = actions.onRetry, onOpenSettings = actions.onOpenSettings)
                history == null || state.isEmpty -> EmptyState(
                    title = stringResource(R.string.history_empty_title),
                    message = stringResource(R.string.history_empty_message),
                    icon = Icons.Outlined.History,
                )
                else -> LazyColumn(Modifier.fillMaxSize().testTag("history_list"), contentPadding = PaddingValues(bottom = Spacing.xl)) {
                    if (history.searches.isNotEmpty()) {
                        item(key = "searches_header") {
                            SectionHeader(stringResource(R.string.history_searches), Modifier.padding(top = Spacing.sm, bottom = Spacing.xs))
                        }
                        items(history.searches, key = { "s${it.id}" }) { item ->
                            SearchRow(item) { actions.onSearch(SearchRequest(item.searchType, item.query)) }
                            HorizontalDivider(Modifier.padding(start = 54.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                    if (history.viewedProducts.isNotEmpty()) {
                        item(key = "viewed_header") {
                            SectionHeader(stringResource(R.string.history_viewed), Modifier.padding(top = Spacing.xl, bottom = Spacing.xs))
                        }
                        items(history.viewedProducts, key = { "v${it.variantId}" }) { item ->
                            ViewedRow(item) { actions.onOpenProduct(item.productId, item.variantId) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchRow(item: SearchHistoryItem, onClick: () -> Unit) {
    // Image searches cannot be repeated (the picture is not kept), so they are not clickable.
    val repeatable = item.searchType != SearchType.IMAGE
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (repeatable) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            .testTag("history_search_${item.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            when (item.searchType) {
                SearchType.TEXT -> Icons.Outlined.Search
                SearchType.IMAGE -> Icons.Outlined.Image
                SearchType.URL -> Icons.Outlined.Link
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(Spacing.lg))
        Column(Modifier.weight(1f)) {
            Text(
                item.query.ifBlank { stringResource(R.string.history_image_search) },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val parts = listOfNotNull(
                relativeTimeText(item.createdAt),
                pluralStringResource(R.plurals.history_result_count, item.resultCount, item.resultCount),
                item.lowestPrice?.let { stringResource(R.string.history_lowest, Money.format(it)) },
            )
            Text(
                parts.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ViewedRow(item: ViewedProduct, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProductThumbnail(item.imageUrl, size = 48.dp)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val parts = listOfNotNull(
                item.lowestFinalPrice?.let { stringResource(R.string.history_lowest, Money.format(it)) },
                relativeTimeText(item.viewedAt),
            )
            if (parts.isNotEmpty()) {
                Text(
                    parts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
