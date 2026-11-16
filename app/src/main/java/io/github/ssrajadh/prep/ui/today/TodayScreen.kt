package io.github.ssrajadh.prep.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.ssrajadh.prep.domain.StudyRepository
import io.github.ssrajadh.prep.ui.prepApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class TodayUi(
    val loading: Boolean = true,
    val reviews: Int = 0,
    val fresh: Int = 0,
    val doneToday: Int = 0,
    val overCap: Int = 0,
    val totalCards: Int = 0,
)

// A ViewModel survives configuration changes (like rotation) and holds screen state.
// Compose observes the StateFlow and redraws when it changes.
class TodayViewModel(private val study: StudyRepository) : ViewModel() {
    private val _ui = MutableStateFlow(TodayUi())
    val ui: StateFlow<TodayUi> = _ui
    private var synced = false

    fun refresh() = viewModelScope.launch {
        if (!synced) { study.syncContent(); synced = true }
        val info = study.todayInfo()
        _ui.value = TodayUi(
            loading = false,
            reviews = info.plan.cards.size - info.plan.newIds.size,
            fresh = info.plan.newIds.size,
            doneToday = info.doneToday,
            overCap = info.plan.reviewsLeftOverCap,
            totalCards = study.content().cards.size,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(onStart: () -> Unit, onProgress: () -> Unit, onSettings: () -> Unit) {
    val app = prepApp()
    val vm: TodayViewModel = viewModel { TodayViewModel(app.study) }
    val ui by vm.ui.collectAsState()
    // Re-count whenever this screen comes back into view (e.g. after a session).
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Today") },
                actions = {
                    IconButton(onClick = onProgress) { Icon(Icons.Default.BarChart, "Progress") }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (ui.loading) return@Column
            val total = ui.reviews + ui.fresh
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (total == 0) {
                        Text(
                            if (ui.doneToday > 0) "That's it for today" else "Nothing scheduled today",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            if (ui.doneToday > 0) "You reviewed ${ui.doneToday} card${if (ui.doneToday == 1) "" else "s"}. Come back tomorrow."
                            else if (ui.totalCards == 0) "No checked cards are bundled for your active tracks yet."
                            else "Check Settings if you want more new cards per day.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    } else {
                        Text("$total card${if (total == 1) "" else "s"} for today", style = MaterialTheme.typography.headlineSmall)
                        Text("${ui.reviews} review${if (ui.reviews == 1) "" else "s"} · ${ui.fresh} new", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text(if (ui.doneToday > 0) "Continue" else "Start") }
                    }
                }
            }
            if (ui.overCap > 0) {
                Text(
                    "${ui.overCap} more due card${if (ui.overCap == 1) " is" else "s are"} spread over the next few days by your daily review cap.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "Each card is a short quiz. After its questions you'll see the key points and the source.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
