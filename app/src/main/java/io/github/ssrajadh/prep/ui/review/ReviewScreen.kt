package io.github.ssrajadh.prep.ui.review

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.ssrajadh.prep.data.content.Card
import io.github.ssrajadh.prep.data.content.Option
import io.github.ssrajadh.prep.data.content.Question
import io.github.ssrajadh.prep.data.settings.SettingsRepository
import io.github.ssrajadh.prep.domain.Fsrs.Rating
import io.github.ssrajadh.prep.domain.Grading
import io.github.ssrajadh.prep.domain.StudyRepository
import io.github.ssrajadh.prep.ui.components.ExplanationAndSources
import io.github.ssrajadh.prep.ui.components.FlagDialog
import io.github.ssrajadh.prep.ui.components.KeyPointList
import io.github.ssrajadh.prep.ui.prepApp
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Review is a quiz. For each card: answer its questions one by one with instant feedback,
 * then see a summary (score, suggested rating, key points, explanation, source) and rate.
 */
data class ReviewUi(
    val loading: Boolean = true,
    val queue: List<Card> = emptyList(),
    val newIds: Set<String> = emptySet(),
    val cardIndex: Int = 0,
    /** Index into the current card's questions; == questions.size means the summary is showing. */
    val questionIndex: Int = 0,
    /** Option order per question, shuffled once per card so it doesn't jump on recomposition. */
    val order: Map<String, List<Option>> = emptyMap(),
    val selected: Set<String> = emptySet(),
    val submitted: Boolean = false,
    val correct: List<String> = emptyList(),
    val againBelow: Float = 0.4f,
    val hardBelow: Float = 0.7f,
) {
    val card: Card? get() = queue.getOrNull(cardIndex)
    val done: Boolean get() = !loading && cardIndex >= queue.size
    val question: Question? get() = card?.questions?.getOrNull(questionIndex)
    val inSummary: Boolean get() = card != null && question == null
    val suggested: Rating?
        get() = card?.let { Grading.suggest(correct.size, it.questions.size, againBelow, hardBelow) }
}

class ReviewViewModel(private val study: StudyRepository, private val settings: SettingsRepository) : ViewModel() {
    private val _ui = MutableStateFlow(ReviewUi())
    val ui: StateFlow<ReviewUi> = _ui

    init {
        viewModelScope.launch {
            val s = settings.current()
            val plan = study.todayInfo().plan
            _ui.value = startCard(
                ReviewUi(loading = false, queue = plan.cards, newIds = plan.newIds, againBelow = s.againBelow, hardBelow = s.hardBelow),
                0,
            )
        }
    }

    private fun startCard(ui: ReviewUi, index: Int): ReviewUi {
        val card = ui.queue.getOrNull(index)
        return ui.copy(
            cardIndex = index,
            questionIndex = 0,
            order = card?.questions?.associate { it.id to it.options.shuffled() }.orEmpty(),
            selected = emptySet(),
            submitted = false,
            correct = emptyList(),
        )
    }

    /** Single-answer questions submit on tap; multi-answer ones toggle until "Check". */
    fun tapOption(optionId: String) = _ui.update { ui ->
        val q = ui.question ?: return@update ui
        if (ui.submitted) return@update ui
        if (q.isMulti) {
            ui.copy(selected = if (optionId in ui.selected) ui.selected - optionId else ui.selected + optionId)
        } else {
            submit(ui.copy(selected = setOf(optionId)))
        }
    }

    fun check() = _ui.update { ui -> if (ui.submitted || ui.selected.isEmpty()) ui else submit(ui) }

    private fun submit(ui: ReviewUi): ReviewUi {
        val q = ui.question ?: return ui
        return ui.copy(submitted = true, correct = if (q.isCorrect(ui.selected)) ui.correct + q.id else ui.correct)
    }

    fun next() = _ui.update { it.copy(questionIndex = it.questionIndex + 1, selected = emptySet(), submitted = false) }

    private var saving = false

    fun rate(rating: Rating) {
        val state = _ui.value
        val card = state.card ?: return
        if (saving) return // ignore double taps
        saving = true
        viewModelScope.launch {
            // NonCancellable: closing the screen right after rating clears this ViewModel
            // and cancels its scope; the save must still finish.
            withContext(NonCancellable) { study.recordReview(card, state.correct, rating) }
            _ui.update { startCard(it, it.cardIndex + 1) }
            saving = false
        }
    }

    fun flag(reason: String, note: String) {
        val card = _ui.value.card ?: return
        viewModelScope.launch { study.flag(card, reason, note) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(onClose: () -> Unit) {
    val app = prepApp()
    val vm: ReviewViewModel = viewModel { ReviewViewModel(app.study, app.settings) }
    val ui by vm.ui.collectAsState()
    var flagging by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { if (!ui.loading && !ui.done) Text("Card ${ui.cardIndex + 1} of ${ui.queue.size}") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") } },
                actions = {
                    if (ui.card != null) IconButton(onClick = { flagging = true }) { Icon(Icons.Outlined.Flag, "Flag this card") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (ui.queue.isNotEmpty()) ProgressBar(ui.cardIndex.toFloat() / ui.queue.size)
            val card = ui.card
            when {
                ui.loading -> Unit
                ui.done || card == null -> DoneView(ui.queue.size, onClose)
                // key(): fresh scroll position for each question and the summary.
                ui.inSummary -> key(card.id, "summary") { SummaryView(ui, card, vm) }
                else -> key(card.id, ui.questionIndex) { QuestionView(ui, card, ui.question!!, vm) }
            }
        }
    }

    if (flagging) {
        FlagDialog(
            onDismiss = { flagging = false },
            onSubmit = { reason, note ->
                vm.flag(reason, note)
                flagging = false
                scope.launch { snackbar.showSnackbar("Flagged. Export flags from Settings.") }
            },
        )
    }
}

/** A plain drawn bar: no animation, so nothing moves between taps. */
@Composable
private fun ProgressBar(fraction: Float) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(4.dp)) {
        drawRect(track)
        drawRect(fill, size = size.copy(width = size.width * fraction))
    }
}

@Composable
private fun DoneView(count: Int, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Done for today", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (count == 0) "Nothing is scheduled right now." else "You went through $count card${if (count == 1) "" else "s"}. The scheduler will bring each one back when it's due.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = onClose) { Text("Back") }
    }
}

/** Scrollable content with a fixed action area at the bottom. */
@Composable
private fun WithBottomBar(content: @Composable () -> Unit, bottom: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
        Surface(tonalElevation = 3.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { bottom() }
        }
    }
}

@Composable
private fun QuestionView(ui: ReviewUi, card: Card, q: Question, vm: ReviewViewModel) {
    val options = ui.order[q.id] ?: q.options
    val isLast = ui.questionIndex == card.questions.size - 1
    WithBottomBar(
        content = {
            Text(
                "Question ${ui.questionIndex + 1} of ${card.questions.size}" + if (card.id in ui.newIds) " · New card" else "",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(q.stem, style = MaterialTheme.typography.titleLarge)
            if (q.isMulti) {
                Text("Select all that apply", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(4.dp))
            for (o in options) OptionRow(o, q, ui.selected, ui.submitted) { vm.tapOption(o.id) }
            if (ui.submitted) {
                val right = q.isCorrect(ui.selected)
                Text(
                    if (right) "Correct" else "Not quite",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (right) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(q.why, style = MaterialTheme.typography.bodyLarge)
            }
        },
        bottom = {
            when {
                ui.submitted -> Button(onClick = vm::next, modifier = Modifier.fillMaxWidth()) {
                    Text(if (isLast) "See results" else "Next question")
                }
                q.isMulti -> Button(onClick = vm::check, enabled = ui.selected.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Check") }
                else -> Text(
                    "Tap an answer",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        },
    )
}

@Composable
private fun OptionRow(o: Option, q: Question, selected: Set<String>, submitted: Boolean, onTap: () -> Unit) {
    val isAnswer = o.id in q.answers
    val picked = o.id in selected
    val c = MaterialTheme.colorScheme
    // After submitting: correct answers are highlighted, wrong picks are marked in error color.
    val (bg, border, fg) = when {
        submitted && isAnswer -> Triple(c.primaryContainer, c.primary, c.onPrimaryContainer)
        submitted && picked -> Triple(c.errorContainer, c.error, c.onErrorContainer)
        submitted -> Triple(Color.Transparent, c.outlineVariant, c.onSurfaceVariant)
        picked -> Triple(c.secondaryContainer, c.primary, c.onSecondaryContainer)
        else -> Triple(Color.Transparent, c.outline, c.onSurface)
    }
    val tap = if (q.isMulti) {
        Modifier.toggleable(value = picked, enabled = !submitted, role = Role.Checkbox) { onTap() }
    } else {
        Modifier.selectable(selected = picked, enabled = !submitted, role = Role.RadioButton, onClick = onTap)
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bg,
        border = BorderStroke(if (picked || (submitted && isAnswer)) 2.dp else 1.dp, border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(tap.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(o.text, style = MaterialTheme.typography.bodyLarge, color = fg, modifier = Modifier.weight(1f))
            when {
                submitted && isAnswer -> Icon(Icons.Default.Check, "Correct answer", tint = c.primary, modifier = Modifier.padding(start = 8.dp).size(20.dp))
                submitted && picked -> Icon(Icons.Default.Close, "Your answer, incorrect", tint = c.error, modifier = Modifier.padding(start = 8.dp).size(20.dp))
                q.isMulti && picked -> Icon(Icons.Default.Check, "Selected", tint = c.primary, modifier = Modifier.padding(start = 8.dp).size(20.dp))
            }
        }
    }
}

@Composable
private fun SummaryView(ui: ReviewUi, card: Card, vm: ReviewViewModel) {
    WithBottomBar(
        content = {
            Text("${ui.correct.size} of ${card.questions.size} correct", style = MaterialTheme.typography.headlineSmall)
            Text(card.prompt, style = MaterialTheme.typography.titleMedium)
            Text("Key points", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            KeyPointList(card, ticked = null)
            ExplanationAndSources(card)
        },
        bottom = {
            val suggested = ui.suggested
            Text("Suggested: ${suggested?.name}. Pick another if it felt harder or easier.", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                for (r in Rating.entries) {
                    val m = Modifier.weight(1f)
                    val pad = PaddingValues(horizontal = 4.dp)
                    if (r == suggested) FilledTonalButton(onClick = { vm.rate(r) }, modifier = m, contentPadding = pad) { Text(r.name, maxLines = 1) }
                    else OutlinedButton(onClick = { vm.rate(r) }, modifier = m, contentPadding = pad) { Text(r.name, maxLines = 1) }
                }
            }
        },
    )
}
