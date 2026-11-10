package com.dealmate.app.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.dealmate.app.R
import com.dealmate.app.data.image.ImagePreparerImpl
import com.dealmate.app.domain.model.SearchType
import com.dealmate.app.domain.util.Money
import com.dealmate.app.ui.theme.Spacing
import java.io.File

/** The app's one search field (natural-language text, or a pasted link). */
@Composable
fun DealMateSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable () -> Unit = { Icon(Icons.Outlined.Search, contentDescription = null) },
    keyboardType: KeyboardType = KeyboardType.Text,
    trailing: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().testTag("search_field"),
        placeholder = {
            Text(placeholder, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        },
        leadingIcon = leadingIcon,
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (value.isNotEmpty()) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_clear))
                    }
                }
                trailing?.invoke()
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, keyboardType = keyboardType),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
    )
}

/** Văn bản / Hình ảnh / Link. */
@Composable
fun SearchMethodSelector(selected: SearchType, onSelect: (SearchType) -> Unit, modifier: Modifier = Modifier) {
    val options = listOf(
        Triple(SearchType.TEXT, R.string.method_text, Icons.Outlined.TextFields),
        Triple(SearchType.IMAGE, R.string.method_image, Icons.Outlined.Image),
        Triple(SearchType.URL, R.string.method_url, Icons.Outlined.Link),
    )
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (type, label, icon) ->
            SegmentedButton(
                selected = selected == type,
                onClick = { onSelect(type) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    activeBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier.testTag("method_${type.wire}"),
            ) { Text(stringResource(label), maxLines = 1) }
        }
    }
}

private fun captureUri(context: Context): Uri {
    val dir = File(context.cacheDir, ImagePreparerImpl.IMAGE_DIR).apply { mkdirs() }
    val file = File(dir, "capture.jpg")
    return FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
}

/**
 * Choose a product photo: system Photo Picker (no storage permission) or camera capture
 * (CAMERA permission requested only when the camera button is used).
 */
@Composable
fun ProductImagePicker(
    preparedImagePath: String?,
    busy: Boolean,
    onImagePicked: (uri: String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var cameraDenied by rememberSaveable { mutableStateOf(false) }
    val target = remember { mutableStateOf<Uri?>(null) }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onImagePicked(uri.toString())
    }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = target.value
        if (saved && uri != null) onImagePicked(uri.toString())
    }
    fun launchCamera() {
        val uri = runCatching { captureUri(context) }.getOrNull() ?: return
        target.value = uri
        runCatching { takePicture.launch(uri) }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else cameraDenied = true
    }

    Column(modifier.fillMaxWidth()) {
        if (preparedImagePath != null) {
            Box {
                AsyncImage(
                    model = File(preparedImagePath),
                    contentDescription = stringResource(R.string.image_selected),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(160.dp).clip(MaterialTheme.shapes.medium),
                )
                IconButton(onClick = onClear, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_clear))
                }
            }
            Spacer(Modifier.height(Spacing.sm))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(
                onClick = {
                    pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                enabled = !busy,
                modifier = Modifier.weight(1f).testTag("pick_gallery"),
            ) {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.image_pick_gallery), maxLines = 1)
            }
            FilledTonalButton(
                onClick = {
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED
                    if (granted) launchCamera() else cameraPermission.launch(Manifest.permission.CAMERA)
                },
                enabled = !busy,
                modifier = Modifier.weight(1f).testTag("pick_camera"),
            ) {
                Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.image_take_photo), maxLines = 1)
            }
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        if (cameraDenied) {
            Text(
                stringResource(R.string.image_camera_denied),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

@Composable
fun WishlistButton(inWishlist: Boolean, busy: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(if (inWishlist) R.string.wishlist_saved else R.string.wishlist_save)
    if (inWishlist) {
        FilledTonalButton(onClick = onClick, enabled = !busy, modifier = modifier.testTag("wishlist_button")) {
            Icon(Icons.Outlined.Favorite, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label)
        }
    } else {
        OutlinedButton(onClick = onClick, enabled = !busy, modifier = modifier.testTag("wishlist_button")) {
            Icon(Icons.Outlined.FavoriteBorder, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label)
        }
    }
}

/** Asks for the price at which the user wants to be alerted. Empty == no target. */
@Composable
fun TargetPriceDialog(
    initial: Long?,
    title: String,
    onConfirm: (Long?) -> Unit,
    onDismiss: () -> Unit,
    referencePrice: Long? = null,
) {
    var text by rememberSaveable { mutableStateOf(initial?.toString().orEmpty()) }
    val parsed = Money.parse(text)
    val invalid = text.isNotBlank() && parsed == null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    stringResource(R.string.target_dialog_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (referencePrice != null) {
                    Text(
                        stringResource(R.string.target_dialog_reference, Money.format(referencePrice)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
                Spacer(Modifier.height(Spacing.md))
                OutlinedTextField(
                    value = text,
                    onValueChange = { new -> text = new.filter { it.isDigit() }.take(11) },
                    label = { Text(stringResource(R.string.target_dialog_label)) },
                    suffix = { Text("đ") },
                    supportingText = {
                        Text(
                            when {
                                invalid -> stringResource(R.string.target_dialog_invalid)
                                parsed != null -> Money.format(parsed)
                                else -> stringResource(R.string.target_dialog_empty_hint)
                            },
                        )
                    },
                    isError = invalid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("target_price_field"),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(parsed) }, enabled = !invalid, modifier = Modifier.testTag("target_confirm")) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
