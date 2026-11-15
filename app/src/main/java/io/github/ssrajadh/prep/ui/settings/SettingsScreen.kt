package io.github.ssrajadh.prep.ui.settings

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.ssrajadh.prep.data.content.Content
import io.github.ssrajadh.prep.data.settings.Settings
import io.github.ssrajadh.prep.notify.Reminder
import io.github.ssrajadh.prep.ui.prepApp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val app = prepApp()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val s by app.settings.settings.collectAsState(initial = null)
    val content by produceState<Content?>(null) { value = app.study.content() }
    val flagCount by app.study.observeFlagCount().collectAsState(initial = 0)

    // Storage Access Framework: the system file picker chooses where the export goes,
    // so the app needs no storage permission.
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val json = app.study.exportFlagsJson()
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            snackbar.showSnackbar("Exported $flagCount flag${if (flagCount == 1) "" else "s"}")
        }
    }

    // Android 13+ asks the user before an app may post notifications.
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val cur = s ?: return@rememberLauncherForActivityResult
        if (granted) scope.launch {
            app.settings.setNotify(true, cur.notifyHour, cur.notifyMinute)
            Reminder.schedule(context, true, cur.notifyHour, cur.notifyMinute)
        } else scope.launch { snackbar.showSnackbar("Notifications are off for this app in system settings.") }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        val cur = s ?: return@Scaffold
        val c = content ?: return@Scaffold
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section("Active tracks")
            for (t in c.tracks) {
                val count = c.cardsInTrack(t.id).size
                Row(
                    Modifier.fillMaxWidth().clickable {
                        scope.launch { app.settings.setActiveTracks(toggleTrack(cur, c, t.id)) }
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = cur.isTrackActive(t.id), onCheckedChange = {
                        scope.launch { app.settings.setActiveTracks(toggleTrack(cur, c, t.id)) }
                    })
                    Column {
                        Text(t.name, style = MaterialTheme.typography.bodyLarge)
                        Text("$count card${if (count == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            HorizontalDivider()
            Section("Daily limits")
            IntSlider("New cards per day", cur.newPerDay, 0..20) { scope.launch { app.settings.setNewPerDay(it) } }
            IntSlider("Review cap per day", cur.reviewCap, 5..100) { scope.launch { app.settings.setReviewCap(it) } }
            Hint("Due cards over the cap wait for the next day instead of piling up.")

            HorizontalDivider()
            Section("Grading")
            Hint("After you tick key points, the app suggests a rating. You can always pick a different one.")
            PercentSlider("Again if under", cur.againBelow) { scope.launch { app.settings.setThresholds(it, cur.hardBelow) } }
            PercentSlider("Hard if under", cur.hardBelow) { scope.launch { app.settings.setThresholds(cur.againBelow, it) } }
            Hint("Otherwise Good; all key points = Easy.")

            HorizontalDivider()
            Section("Reminder")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Daily notification", style = MaterialTheme.typography.bodyLarge)
                    Text("Only when cards are ready. No streaks.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = cur.notifyEnabled, onCheckedChange = { on ->
                    if (on && Build.VERSION.SDK_INT >= 33) {
                        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else scope.launch {
                        app.settings.setNotify(on, cur.notifyHour, cur.notifyMinute)
                        Reminder.schedule(context, on, cur.notifyHour, cur.notifyMinute)
                    }
                })
            }
            if (cur.notifyEnabled) {
                TextButton(onClick = {
                    TimePickerDialog(context, { _, h, m ->
                        scope.launch {
                            app.settings.setNotify(true, h, m)
                            Reminder.schedule(context, true, h, m)
                        }
                    }, cur.notifyHour, cur.notifyMinute, android.text.format.DateFormat.is24HourFormat(context)).show()
                }) { Text("Time: %02d:%02d".format(cur.notifyHour, cur.notifyMinute)) }
            }

            HorizontalDivider()
            Section("Flags")
            Hint("$flagCount flagged card${if (flagCount == 1) "" else "s"}. Export them to re-run the content checker on your laptop.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = flagCount > 0, onClick = { exportLauncher.launch("prep-flags.json") }) { Text("Export flags") }
                TextButton(enabled = flagCount > 0, onClick = { scope.launch { app.study.clearFlags() } }) { Text("Clear") }
            }

            HorizontalDivider()
            Section("About")
            Hint(
                "Fully offline. No account, no tracking. Every card links to its source and was checked against it by a separate pass.\n\n" +
                    "System Design cards paraphrase The System Design Primer by Donne Martin (github.com/donnemartin/system-design-primer), " +
                    "licensed CC BY 4.0. Changes were made.\n\nScheduling uses the FSRS-5 algorithm."
            )
        }
    }
}

/** Settings stores an explicit set once the user changes anything; empty means "all". */
private fun toggleTrack(cur: Settings, c: Content, id: String): Set<String> {
    val active = c.tracks.map { it.id }.filter { cur.isTrackActive(it) }.toMutableSet()
    if (id in active) active -= id else active += id
    return if (active.isEmpty()) setOf("__none__") else active
}

@Composable
private fun Section(text: String) = Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

@Composable
private fun Hint(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun IntSlider(label: String, value: Int, range: IntRange, onDone: (Int) -> Unit) {
    var v by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column {
        Text("$label: ${v.roundToInt()}", style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = v,
            onValueChange = { v = it },
            onValueChangeFinished = { onDone(v.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1,
        )
    }
}

@Composable
private fun PercentSlider(label: String, value: Float, onDone: (Float) -> Unit) {
    var v by remember(value) { mutableFloatStateOf(value) }
    Column {
        Text("$label ${(v * 100).roundToInt()}% of key points", style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = v,
            onValueChange = { v = it },
            onValueChangeFinished = { onDone((v * 20).roundToInt() / 20f) },
            valueRange = 0f..1f,
            steps = 19,
        )
    }
}
