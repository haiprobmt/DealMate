package com.dealmate.app.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dealmate.app.domain.model.AppError

/** One-shot message for a snackbar. [id] makes two equal messages distinct events. */
sealed interface UiMessage {
    val id: Long

    data class Text(@StringRes val res: Int, override val id: Long = System.nanoTime()) : UiMessage
    data class Failure(val error: AppError, override val id: Long = System.nanoTime()) : UiMessage
}

@Composable
fun UiMessage.text(): String = when (this) {
    is UiMessage.Text -> stringResource(res)
    is UiMessage.Failure -> errorText(error)
}
