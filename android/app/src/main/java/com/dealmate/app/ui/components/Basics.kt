package com.dealmate.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.dealmate.app.R
import com.dealmate.app.domain.model.AppError
import com.dealmate.app.domain.model.Marketplace
import com.dealmate.app.ui.common.errorText
import com.dealmate.app.ui.theme.DealMateTheme
import com.dealmate.app.ui.theme.Spacing

/** Persistent, unobtrusive notice shown whenever the backend answers with `demo_mode = true`. */
@Composable
fun DemoBanner(modifier: Modifier = Modifier) {
    val colors = DealMateTheme.status
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.estimatedContainer)
            .padding(horizontal = Spacing.lg, vertical = 6.dp)
            .testTag("demo_banner"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Science, contentDescription = null, tint = colors.estimated, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = stringResource(R.string.demo_banner),
            style = MaterialTheme.typography.labelMedium,
            color = colors.estimated,
        )
    }
}

/** Small tag on every offer / item whose data is mock (`is_mock = true`). */
@Composable
fun DemoTag(modifier: Modifier = Modifier) {
    val colors = DealMateTheme.status
    Text(
        text = stringResource(R.string.demo_tag),
        style = MaterialTheme.typography.labelSmall,
        color = colors.estimated,
        modifier = modifier
            .border(1.dp, colors.estimated.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp)
            .testTag("demo_tag"),
    )
}

/** Hint that the content is a locally cached copy. */
@Composable
fun OfflineHint(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = Spacing.lg, vertical = 6.dp)
            .testTag("offline_hint"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.CloudOff, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Neutral coloured chip with the marketplace's initial. Deliberately NOT the marketplaces' logos or
 * trademarks — just a compact way to tell sources apart; the full name is always shown nearby or
 * exposed as content description.
 */
@Composable
fun MarketplaceLogo(marketplace: Marketplace, modifier: Modifier = Modifier, size: Dp = 22.dp, name: String? = null) {
    val (background, letter) = when (marketplace) {
        Marketplace.SHOPEE -> Color(0xFFE2572B) to "S"
        Marketplace.LAZADA -> Color(0xFF3F46C8) to "L"
        Marketplace.TIKTOK_SHOP -> Color(0xFF2B2B2B) to "T"
        Marketplace.UNKNOWN -> Color(0xFF8A8078) to "?"
    }
    val label = name ?: marketplace.displayName
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3.5f))
            .background(background)
            .border(0.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(size / 3.5f))
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.52f).sp,
            lineHeight = (size.value * 0.6f).sp,
        )
    }
}

@Composable
fun ProductThumbnail(imageUrl: String?, modifier: Modifier = Modifier, size: Dp = 72.dp) {
    val shape = MaterialTheme.shapes.small
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.Image, contentDescription = null,
            tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(size / 3),
        )
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier, message: String? = null) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.xl).testTag("loading"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
        if (message != null) {
            Spacer(Modifier.height(Spacing.lg))
            Text(
                message, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: ImageVector = Icons.Outlined.Info,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    StateLayout(
        modifier = modifier.testTag("empty_state"),
        icon = icon,
        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
        iconBackground = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = title,
        message = message,
    ) {
        if (actionLabel != null && onAction != null) {
            OutlinedButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Error with retry. A "server not configured" error links to Settings instead. */
@Composable
fun ErrorState(
    error: AppError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: (() -> Unit)? = null,
) {
    val notConfigured = error.kind == AppError.Kind.NOT_CONFIGURED
    val authProblem = error.kind == AppError.Kind.UNAUTHORIZED
    StateLayout(
        modifier = modifier.testTag("error_state"),
        icon = when {
            notConfigured -> Icons.Outlined.Settings
            error.kind == AppError.Kind.OFFLINE -> Icons.Outlined.CloudOff
            else -> Icons.Outlined.ErrorOutline
        },
        iconTint = if (notConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        iconBackground = if (notConfigured) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
        title = stringResource(if (notConfigured) R.string.not_configured_title else R.string.error_title),
        message = errorText(error),
    ) {
        if ((notConfigured || authProblem) && onOpenSettings != null) {
            Button(onClick = onOpenSettings) { Text(stringResource(R.string.action_open_settings)) }
        }
        if (!notConfigured) {
            OutlinedButton(onClick = onRetry, modifier = Modifier.testTag("retry_button")) {
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}

@Composable
private fun StateLayout(
    modifier: Modifier,
    icon: ImageVector,
    iconTint: Color,
    iconBackground: Color,
    title: String,
    message: String?,
    actions: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(iconBackground),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(28.dp)) }
        Spacer(Modifier.height(Spacing.lg))
        Text(
            title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(max = 320.dp),
                maxLines = 6, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(Spacing.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) { actions() }
    }
}
