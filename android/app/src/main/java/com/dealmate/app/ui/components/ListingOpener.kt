package com.dealmate.app.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.dealmate.app.R
import com.dealmate.app.domain.util.Listings

private enum class ListingDialog { SAMPLE, CANNOT_OPEN }

/**
 * Returns the "Mở trang gốc" handler. Real http(s) listings open with ACTION_VIEW; sample-data links
 * (`*.invalid`) never leave the app — a dialog explains that the listing does not exist.
 */
@Composable
fun rememberListingOpener(): (String) -> Unit {
    val context = LocalContext.current
    var dialog by rememberSaveable { mutableStateOf<ListingDialog?>(null) }

    dialog?.let { kind ->
        AlertDialog(
            onDismissRequest = { dialog = null },
            modifier = Modifier.testTag(if (kind == ListingDialog.SAMPLE) "sample_dialog" else "cannot_open_dialog"),
            title = {
                Text(stringResource(if (kind == ListingDialog.SAMPLE) R.string.sample_dialog_title else R.string.cannot_open_title))
            },
            text = {
                Text(stringResource(if (kind == ListingDialog.SAMPLE) R.string.sample_dialog_message else R.string.cannot_open_message))
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.action_understood)) } },
        )
    }

    return { url ->
        when {
            Listings.isSampleUrl(url) -> dialog = ListingDialog.SAMPLE
            !Listings.isOpenable(url) -> dialog = ListingDialog.CANNOT_OPEN
            else -> try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url.trim())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: ActivityNotFoundException) {
                dialog = ListingDialog.CANNOT_OPEN
            }
        }
    }
}
