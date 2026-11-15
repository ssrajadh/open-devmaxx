package io.github.ssrajadh.prep.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.ssrajadh.prep.data.content.Card
import io.github.ssrajadh.prep.data.settings.SettingsRepository
import io.github.ssrajadh.prep.domain.Fsrs.Rating
import io.github.ssrajadh.prep.domain.Grading
import io.github.ssrajadh.prep.domain.StudyRepository
import io.github.ssrajadh.prep.ui.components.ExplanationAndSources
import io.github.ssrajadh.prep.ui.components.FlagDialog
import io.github.ssrajadh.prep.ui.components.KeyPointList
import io.github.ssrajadh.prep.ui.prepApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReviewUi(
    val loading: Boolean = true,
    val queue: List<Card> = emptyList(),
    val newIds: Set<String> = emptySet(),
    val index: Int = 0,
    val revealed: Boolean = false,
    val ticked: Set<String> = emptySet(),
    val againBelow: Float = 0.4f,
    val hardBelow: Float = 0.7f,
) {
    val card: Card? get() = queue.getOrNull(index)
    val done: Boolean get() = !loading && index >= queue.size
    val suggested: Rating?
        get() = card?.let { Grading.suggest(ticked.size, it.keyPoints.size, againBelow, hardBelow) }
}

class ReviewViewModel(private val study: StudyRepository, private val settings: SettingsRepository) : ViewModel() {
    private val _ui = MutableStateFlow(ReviewUi())
    val ui: StateFlow<ReviewUi> = _ui

    init {
        viewModelScope.launch {
            val s = settings.current()
            val plan = study.todayInfo().plan
            _ui.value = ReviewUi(loading = false, queue = plan.cards, newIds = plan.newIds, againBelow = s.againBelow, hardBelow = s.hardBelow)
        }
    }

    fun reveal() = _ui.update { it.copy(revealed = true) }

    fun toggle(kpId: String) = _ui.update { it.copy(ticked = if (kpId in it.ticked) it.ticked - kpId else it.ticked + kpId) }

    fun rate(rating: Rating) {
        val state = _ui.value
        val card = state.card ?: return
        // Advance the UI first so the screen feels instant; the write happens in the background.
        _ui.update { it.copy(index = it.index + 1, revealed = false, ticked = emptySet()) }
        viewModelScope.launch {
            study.recordReview(card, card.keyPoints.filter { it.id in state.ticked }.map { it.id }, rating)
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
                title = { if (!ui.loading && !ui.done) Text("${ui.index + 1} of ${ui.queue.size}") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") } },
                actions = {
                    if (ui.card != null) IconButton(onClick = { flagging = true }) { Icon(Icons.Outlined.Flag, "Flag this card") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (ui.queue.isNotEmpty()) {
                LinearProgressIndicator(progress = { ui.index.toFloat() / ui.queue.size }, modifier = Modifier.fillMaxWidth())
            }
            val card = ui.card
            when {
                ui.loading -> Unit
                ui.done || card == null -> DoneView(ui.queue.size, onClose)
                else -> CardView(ui, card, vm)
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

@Composable
private fun CardView(ui: ReviewUi, card: Card, vm: ReviewViewModel) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (card.id in ui.newIds) AssistChip(onClick = {}, label = { Text("New") })
                if (card.isDesign) AssistChip(onClick = {}, label = { Text("Design walkthrough") })
            }
            Text(card.prompt, style = MaterialTheme.typography.titleLarge)
            if (!ui.revealed) {
                Text(
                    if (card.isDesign) "Take your time. Talk through each part before revealing the checklist."
                    else "Answer from memory first, out loud or on paper.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text("Tick what you covered", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                KeyPointList(card, ui.ticked, vm::toggle)
                ExplanationAndSources(card)
                Spacer(Modifier.height(8.dp))
            }
        }
        Surface(tonalElevation = 3.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!ui.revealed) {
                    Button(onClick = vm::reveal, modifier = Modifier.fillMaxWidth()) { Text("Reveal key points") }
                } else {
                    val suggested = ui.suggested
                    Text(
                        "${ui.ticked.size} of ${card.keyPoints.size} covered · suggested: ${suggested?.name}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        for (r in Rating.entries) {
                            val m = Modifier.weight(1f)
                            if (r == suggested) FilledTonalButton(onClick = { vm.rate(r) }, modifier = m, contentPadding = PaddingValues(horizontal = 4.dp)) { Text(r.name, maxLines = 1) }
                            else OutlinedButton(onClick = { vm.rate(r) }, modifier = m, contentPadding = PaddingValues(horizontal = 4.dp)) { Text(r.name, maxLines = 1) }
                        }
                    }
                }
            }
        }
    }
}
