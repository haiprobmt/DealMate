package com.dealmate.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dealmate.app.BuildConfig
import com.dealmate.app.R
import com.dealmate.app.domain.model.Marketplace
import com.dealmate.app.domain.model.SortOption
import com.dealmate.app.domain.model.SourceStatus
import com.dealmate.app.notifications.PushConfig
import com.dealmate.app.ui.common.errorText
import com.dealmate.app.ui.common.labelRes
import com.dealmate.app.ui.common.sourceStatusRes
import com.dealmate.app.ui.components.DemoTag
import com.dealmate.app.ui.components.MarketplaceLogo
import com.dealmate.app.ui.components.cardBorder
import com.dealmate.app.ui.components.cardColors
import com.dealmate.app.ui.theme.DealMateTheme
import com.dealmate.app.ui.theme.Spacing

data class SettingsActions(
    val onBaseUrl: (String) -> Unit = {},
    val onToken: (String) -> Unit = {},
    val onCheckConnection: () -> Unit = {},
    val onLanguage: (String) -> Unit = {},
    val onToggleMarketplace: (Marketplace) -> Unit = {},
    val onSort: (SortOption) -> Unit = {},
    val onNotifications: (Boolean) -> Unit = {},
    val onLocation: (String) -> Unit = {},
    val onReloadSources: () -> Unit = {},
)

@Composable
fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionDenied = !granted
        viewModel.setNotificationsEnabled(granted)
    }

    SettingsScreen(
        state = state,
        language = LocalConfiguration.current.locales[0].language,
        pushConfiguredInApp = PushConfig.isConfigured,
        versionName = BuildConfig.VERSION_NAME,
        notificationPermissionDenied = permissionDenied,
        actions = SettingsActions(
            onBaseUrl = viewModel::setBaseUrlInput,
            onToken = viewModel::setTokenInput,
            onCheckConnection = viewModel::saveAndCheckConnection,
            onLanguage = { tag ->
                viewModel.setLanguage(tag)
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
            },
            onToggleMarketplace = viewModel::toggleMarketplace,
            onSort = viewModel::setDefaultSort,
            onNotifications = { enabled ->
                val needsPermission = enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                if (needsPermission) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    permissionDenied = false
                    viewModel.setNotificationsEnabled(enabled)
                }
            },
            onLocation = viewModel::setLocation,
            onReloadSources = viewModel::loadSources,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    language: String,
    pushConfiguredInApp: Boolean,
    versionName: String,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    notificationPermissionDenied: Boolean = false,
) {
    Scaffold(
        modifier = modifier.testTag("settings_screen"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_settings), style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            // ---- Server ----
            SettingsCard(stringResource(R.string.settings_server)) {
                OutlinedTextField(
                    value = state.baseUrlInput,
                    onValueChange = actions.onBaseUrl,
                    label = { Text(stringResource(R.string.settings_server_url)) },
                    placeholder = { Text("https://dealmate.example.com/") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth().testTag("server_url_field"),
                )
                Spacer(Modifier.height(Spacing.sm))
                var tokenVisible by rememberSaveable { mutableStateOf(false) }
                OutlinedTextField(
                    value = state.tokenInput,
                    onValueChange = actions.onToken,
                    label = { Text(stringResource(R.string.settings_token)) },
                    singleLine = true,
                    visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    trailingIcon = {
                        IconButton(onClick = { tokenVisible = !tokenVisible }) {
                            Icon(
                                if (tokenVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = stringResource(R.string.settings_token_toggle),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("token_field"),
                )
                Text(
                    stringResource(R.string.settings_token_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
                Spacer(Modifier.height(Spacing.md))
                Button(
                    onClick = actions.onCheckConnection,
                    enabled = state.connection != ConnectionState.Checking && state.baseUrlInput.isNotBlank(),
                    modifier = Modifier.testTag("check_connection"),
                ) {
                    if (state.connection == ConnectionState.Checking) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(Spacing.sm))
                    }
                    Text(stringResource(R.string.settings_check_connection))
                }
                ConnectionResult(state.connection)
            }

            // ---- Language ----
            SettingsCard(stringResource(R.string.settings_language)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    listOf("vi" to R.string.language_vi, "en" to R.string.language_en).forEach { (tag, label) ->
                        FilterChip(
                            selected = language == tag,
                            onClick = { if (language != tag) actions.onLanguage(tag) },
                            label = { Text(stringResource(label)) },
                            modifier = Modifier.testTag("language_$tag"),
                        )
                    }
                }
            }

            // ---- Search preferences ----
            SettingsCard(stringResource(R.string.settings_search)) {
                Text(stringResource(R.string.settings_marketplaces), style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Marketplace.selectable.forEach { marketplace ->
                        FilterChip(
                            selected = marketplace in state.settings.preferredMarketplaces,
                            onClick = { actions.onToggleMarketplace(marketplace) },
                            label = { Text(marketplace.displayName) },
                            leadingIcon = { MarketplaceLogo(marketplace, size = 18.dp) },
                            modifier = Modifier.testTag("pref_marketplace_${marketplace.wire}"),
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.md))
                Text(stringResource(R.string.settings_default_sort), style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SortOption.entries.forEach { option ->
                        FilterChip(
                            selected = state.settings.defaultSort == option,
                            onClick = { actions.onSort(option) },
                            label = { Text(stringResource(option.labelRes())) },
                            modifier = Modifier.testTag("pref_sort_${option.name.lowercase()}"),
                        )
                    }
                }
            }

            // ---- Notifications ----
            SettingsCard(stringResource(R.string.settings_notifications)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = Spacing.md)) {
                        Text(stringResource(R.string.settings_notifications_toggle), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.settings_notifications_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.settings.notificationsEnabled,
                        onCheckedChange = actions.onNotifications,
                        modifier = Modifier.testTag("notifications_switch"),
                    )
                }
                if (notificationPermissionDenied) {
                    Text(
                        stringResource(R.string.settings_notifications_denied),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
                Text(
                    stringResource(if (pushConfiguredInApp) R.string.settings_push_app_on else R.string.settings_push_app_off),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.sm).testTag("push_status"),
                )
            }

            // ---- Delivery location ----
            SettingsCard(stringResource(R.string.settings_location)) {
                OutlinedTextField(
                    value = state.locationInput,
                    onValueChange = actions.onLocation,
                    label = { Text(stringResource(R.string.settings_location_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("location_field"),
                )
                Text(
                    stringResource(R.string.settings_location_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }

            // ---- Source status ----
            SettingsCard(
                title = stringResource(R.string.settings_sources),
                trailing = {
                    IconButton(onClick = actions.onReloadSources, enabled = !state.sourcesLoading, modifier = Modifier.testTag("reload_sources")) {
                        Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.action_retry))
                    }
                },
            ) { SourcesSection(state, actions) }

            // ---- About ----
            SettingsCard(stringResource(R.string.settings_about)) {
                Text(stringResource(R.string.settings_version, versionName), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.settings_about_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, trailing: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, colors = cardColors(), border = cardBorder()) {
        Column(Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(32.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                trailing?.invoke()
            }
            Spacer(Modifier.height(Spacing.sm))
            content()
        }
    }
}

@Composable
private fun ConnectionResult(connection: ConnectionState) {
    val status = DealMateTheme.status
    when (connection) {
        ConnectionState.Idle, ConnectionState.Checking -> Unit
        is ConnectionState.Connected -> Row(Modifier.padding(top = Spacing.sm).testTag("connection_ok"), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = status.verified, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.sm))
            Column {
                Text(
                    stringResource(R.string.settings_connected, connection.me.displayName, connection.health.version),
                    style = MaterialTheme.typography.bodyMedium,
                    color = status.verified,
                )
                if (connection.health.demoMode || connection.me.demoMode) {
                    Text(
                        stringResource(R.string.settings_connected_demo),
                        style = MaterialTheme.typography.bodySmall,
                        color = status.estimated,
                    )
                }
            }
        }
        is ConnectionState.Failed -> Row(Modifier.padding(top = Spacing.sm).testTag("connection_failed"), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.sm))
            Column {
                Text(
                    stringResource(if (connection.serverReachable) R.string.settings_connection_token_failed else R.string.settings_connection_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    errorText(connection.error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SourcesSection(state: SettingsUiState, actions: SettingsActions) {
    val sources = state.sources
    when {
        state.sourcesLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(Spacing.md))
            Text(stringResource(R.string.settings_sources_loading), style = MaterialTheme.typography.bodyMedium)
        }
        state.sourcesError != null -> Column {
            Text(errorText(state.sourcesError), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = actions.onReloadSources) { Text(stringResource(R.string.action_retry)) }
        }
        sources == null -> Text(
            stringResource(R.string.settings_sources_unknown),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> Column(Modifier.testTag("sources_section")) {
            if (sources.demoMode) {
                Text(
                    stringResource(R.string.demo_banner),
                    style = MaterialTheme.typography.bodySmall,
                    color = DealMateTheme.status.estimated,
                    modifier = Modifier.padding(bottom = Spacing.sm),
                )
            }
            sources.sources.forEachIndexed { index, source ->
                if (index > 0) HorizontalDivider(Modifier.padding(vertical = Spacing.sm), color = MaterialTheme.colorScheme.outlineVariant)
                SourceStatusRow(source)
            }
            HorizontalDivider(Modifier.padding(vertical = Spacing.sm), color = MaterialTheme.colorScheme.outlineVariant)
            InfoRow(
                stringResource(R.string.settings_ai),
                if (sources.aiAvailable) {
                    sources.aiModel?.let { stringResource(R.string.settings_ai_on_model, it) } ?: stringResource(R.string.settings_ai_on)
                } else stringResource(R.string.settings_ai_off),
            )
            InfoRow(
                stringResource(R.string.settings_push_server),
                stringResource(if (sources.pushConfigured) R.string.settings_configured else R.string.settings_not_configured),
            )
            if (sources.priceCheckIntervalMinutes > 0) {
                InfoRow(
                    stringResource(R.string.settings_price_check),
                    stringResource(R.string.settings_price_check_value, sources.priceCheckIntervalMinutes),
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).padding(end = Spacing.md))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SourceStatusRow(source: SourceStatus) {
    val status = DealMateTheme.status
    val available = source.status == "available"
    Row(verticalAlignment = Alignment.Top) {
        MarketplaceLogo(source.marketplace, size = 24.dp, name = source.marketplaceName)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(source.marketplaceName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (source.isMock) DemoTag()
            }
            Text(
                listOf(
                    stringResource(sourceStatusRes(source.status)),
                    stringResource(if (source.isMock) R.string.source_mock else R.string.source_live),
                    stringResource(if (source.verifiedLive) R.string.source_verified_live else R.string.source_not_verified_live),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = if (available) status.verified else status.estimated,
            )
            if (source.message.isNotBlank()) {
                Text(source.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
