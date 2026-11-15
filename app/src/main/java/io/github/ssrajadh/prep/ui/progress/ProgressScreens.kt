package io.github.ssrajadh.prep.ui.progress

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.ssrajadh.prep.data.content.Card as ContentCard
import io.github.ssrajadh.prep.data.content.Content
import io.github.ssrajadh.prep.data.db.ReviewState
import io.github.ssrajadh.prep.ui.components.ExplanationAndSources
import io.github.ssrajadh.prep.ui.components.FlagDialog
import io.github.ssrajadh.prep.ui.components.KeyPointList
import io.github.ssrajadh.prep.ui.prepApp
import kotlinx.coroutines.launch

/** A card counts as "solid" once FSRS expects to keep it for 3+ weeks. */
private const val SOLID_DAYS = 21.0

private data class Counts(val total: Int, val seen: Int, val solid: Int, val due: Int)

private fun counts(cards: List<ContentCard>, states: Map<String, ReviewState>, today: Long) = Counts(
    total = cards.size,
    seen = cards.count { it.id in states },
    solid = cards.count { (states[it.id]?.stability ?: 0.0) >= SOLID_DAYS },
    due = cards.count { (states[it.id]?.due ?: Long.MAX_VALUE) <= today },
)

@Composable
private fun rememberContent(): Content? {
    val app = prepApp()
    val c by produceState<Content?>(null) { value = app.study.content() }
    return c
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackScaffold(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}, snackbar: SnackbarHostState? = null, body: @Composable (Modifier) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { actions() },
            )
        },
        snackbarHost = { snackbar?.let { SnackbarHost(it) } },
    ) { pad -> body(Modifier.padding(pad)) }
}

@Composable
private fun CountsRow(c: Counts) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LinearProgressIndicator(
            progress = { if (c.total == 0) 0f else c.seen.toFloat() / c.total },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "${c.seen}/${c.total} started · ${c.solid} solid · ${c.due} due",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun ProgressScreen(onBack: () -> Unit, onTopic: (String) -> Unit) {
    val app = prepApp()
    val content = rememberContent()
    val states by app.study.observeStates().collectAsState(initial = emptyList())
    val byId = states.associateBy { it.cardId }
    val today = app.study.today()
    BackScaffold("Progress", onBack) { mod ->
        if (content == null) return@BackScaffold
        Column(
            mod.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "\"Solid\" means the scheduler expects you to remember it for 3+ weeks.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for (track in content.tracks) {
                val cards = content.cardsInTrack(track.id)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(track.name, style = MaterialTheme.typography.titleMedium)
                        if (cards.isEmpty()) {
                            Text("No checked cards yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            CountsRow(counts(cards, byId, today))
                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            for (topic in content.topicsInTrack(track.id)) {
                                val tc = cards.filter { it.topicId == topic.id }
                                if (tc.isEmpty()) continue
                                val c = counts(tc, byId, today)
                                Row(
                                    Modifier.fillMaxWidth().clickable { onTopic(topic.id) }.padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(topic.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                    Text("${c.seen}/${c.total}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TopicScreen(topicId: String, onBack: () -> Unit, onCard: (String) -> Unit) {
    val app = prepApp()
    val content = rememberContent()
    val states by app.study.observeStates().collectAsState(initial = emptyList())
    val byId = states.associateBy { it.cardId }
    val today = app.study.today()
    val topic = content?.topicsById?.get(topicId)
    BackScaffold(topic?.name ?: "", onBack) { mod ->
        if (content == null) return@BackScaffold
        val cards = content.cards.filter { it.topicId == topicId }
        LazyColumn(mod.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            item { CountsRow(counts(cards, byId, today)) }
            items(cards, key = { it.id }) { card ->
                val s = byId[card.id]
                val status = when {
                    s == null -> "New"
                    s.due <= today -> "Due"
                    else -> "Next in ${s.due - today} day${if (s.due - today == 1L) "" else "s"}"
                }
                Column(Modifier.fillMaxWidth().clickable { onCard(card.id) }.padding(vertical = 12.dp)) {
                    Text(card.prompt, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                    Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
            }
        }
    }
}

/** Read-only view of a card, for browsing. Doesn't affect scheduling. */
@Composable
fun CardDetailScreen(cardId: String, onBack: () -> Unit) {
    val app = prepApp()
    val content = rememberContent()
    val card = content?.cardsById?.get(cardId)
    var flagging by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    BackScaffold(
        "Card", onBack,
        actions = { if (card != null) IconButton(onClick = { flagging = true }) { Icon(Icons.Outlined.Flag, "Flag this card") } },
        snackbar = snackbar,
    ) { mod ->
        if (card == null) return@BackScaffold
        Column(
            mod.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(card.prompt, style = MaterialTheme.typography.titleLarge)
            KeyPointList(card, ticked = null)
            ExplanationAndSources(card)
        }
    }
    if (flagging && card != null) {
        FlagDialog(
            onDismiss = { flagging = false },
            onSubmit = { reason, note ->
                flagging = false
                scope.launch {
                    app.study.flag(card, reason, note)
                    snackbar.showSnackbar("Flagged. Export flags from Settings.")
                }
            },
        )
    }
}
