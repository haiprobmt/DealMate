package com.dealmate.app.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dealmate.app.R
import com.dealmate.app.domain.model.AppError
import com.dealmate.app.domain.model.AssistantAnswer
import com.dealmate.app.domain.model.Outcome
import com.dealmate.app.domain.repository.AssistantRepository
import com.dealmate.app.ui.common.errorText
import com.dealmate.app.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AssistantUiState(
    val question: String = "",
    /** The question the shown answer / error belongs to. */
    val asked: String? = null,
    val loading: Boolean = false,
    val answer: AssistantAnswer? = null,
    val error: AppError? = null,
)

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val repository: AssistantRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AssistantUiState())
    val state: StateFlow<AssistantUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun setQuestion(text: String) = _state.update { it.copy(question = text.take(500)) }

    fun ask(question: String, variantIds: List<Long>) {
        val q = question.trim()
        if (q.length < 2 || variantIds.isEmpty()) return
        job?.cancel()
        _state.update { it.copy(question = "", asked = q, loading = true, answer = null, error = null) }
        job = viewModelScope.launch {
            when (val outcome = repository.ask(q, variantIds)) {
                is Outcome.Success -> _state.update { it.copy(loading = false, answer = outcome.data) }
                is Outcome.Failure -> _state.update { it.copy(loading = false, error = outcome.error) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantSheet(variantIds: List<Long>, onDismiss: () -> Unit) {
    val viewModel: AssistantViewModel = hiltViewModel(key = "assistant-" + variantIds.joinToString(","))
    val state by viewModel.state.collectAsStateWithLifecycle()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        AssistantContent(
            state = state,
            onQuestionChange = viewModel::setQuestion,
            onAsk = { viewModel.ask(it, variantIds) },
            modifier = Modifier.navigationBarsPadding().imePadding(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantContent(
    state: AssistantUiState,
    onQuestionChange: (String) -> Unit,
    onAsk: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg)
            .padding(bottom = Spacing.lg)
            .testTag("assistant_sheet"),
    ) {
        Text(stringResource(R.string.assistant_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.assistant_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.md))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            stringArrayResource(R.array.assistant_questions).forEach { question ->
                SuggestionChip(
                    onClick = { onAsk(question) },
                    label = { Text(question) },
                    enabled = !state.loading,
                    modifier = Modifier.testTag("assistant_chip"),
                )
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        OutlinedTextField(
            value = state.question,
            onValueChange = onQuestionChange,
            placeholder = { Text(stringResource(R.string.assistant_placeholder)) },
            trailingIcon = {
                IconButton(onClick = { onAsk(state.question) }, enabled = state.question.trim().length >= 2 && !state.loading) {
                    Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = stringResource(R.string.assistant_send))
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onAsk(state.question) }),
            modifier = Modifier.fillMaxWidth().testTag("assistant_field"),
        )

        if (state.asked != null) {
            Spacer(Modifier.height(Spacing.lg))
            Text(state.asked, style = MaterialTheme.typography.titleSmall)
        }
        when {
            state.loading -> Row(Modifier.padding(vertical = Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(Spacing.md))
                Text(stringResource(R.string.assistant_loading), style = MaterialTheme.typography.bodyMedium)
            }
            state.error != null -> Column(Modifier.padding(top = Spacing.sm)) {
                Text(errorText(state.error), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { state.asked?.let(onAsk) }) { Text(stringResource(R.string.action_retry)) }
            }
            state.answer != null -> AnswerView(state.answer)
        }
    }
}

@Composable
private fun AnswerView(answer: AssistantAnswer) {
    Column(
        Modifier
            .padding(top = Spacing.sm)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(Spacing.lg)
            .testTag("assistant_answer"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (answer.isAi) Icons.Outlined.AutoAwesome else Icons.AutoMirrored.Outlined.Rule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(if (answer.isAi) R.string.assistant_by_ai else R.string.assistant_by_rules),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("assistant_generated_by"),
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(answer.answer, style = MaterialTheme.typography.bodyMedium)
        if (answer.facts.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.md))
            Text(
                stringResource(R.string.assistant_facts),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            answer.facts.forEach { fact ->
                Text(
                    "• $fact",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (answer.disclaimer.isNotBlank()) {
            Spacer(Modifier.height(Spacing.md))
            Text(
                answer.disclaimer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
