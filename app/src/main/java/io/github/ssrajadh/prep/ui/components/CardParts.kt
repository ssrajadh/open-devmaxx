package io.github.ssrajadh.prep.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.ssrajadh.prep.data.content.Card

/** Key points, grouped by section on design cards. If [ticked] is null, shows a plain list. */
@Composable
fun KeyPointList(card: Card, ticked: Set<String>?, onToggle: (String) -> Unit = {}) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        var lastGroup: String? = null
        for (kp in card.keyPoints) {
            if (kp.group != null && kp.group != lastGroup) {
                Text(
                    kp.group,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                )
                lastGroup = kp.group
            }
            if (ticked != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(kp.id) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Checkbox(checked = kp.id in ticked, onCheckedChange = { onToggle(kp.id) })
                    Text(kp.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
                }
            } else {
                Row(Modifier.padding(vertical = 4.dp)) {
                    Icon(Icons.Default.Circle, null, Modifier.padding(top = 9.dp, end = 10.dp).size(6.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(kp.text, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
fun ExplanationAndSources(card: Card) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider()
        Markdown(card.explanation)
        val uri = LocalUriHandler.current
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (s in card.sources) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { uri.openUri(s.url) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.padding(end = 8.dp), tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(s.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text("Section: ${s.section}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        val status = when (card.verified.status) {
            "verified" -> "Checked against the source on ${card.verified.checkedAt}"
            "partial" -> "Checked against the source on ${card.verified.checkedAt}; unsupported parts were removed"
            else -> "Not yet checked"
        }
        Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val flagReasons = listOf("Seems wrong", "Unclear", "Source link broken", "Other")

@Composable
fun FlagDialog(onDismiss: () -> Unit, onSubmit: (reason: String, note: String) -> Unit) {
    var reason by remember { mutableStateOf(flagReasons.first()) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Flag this card") },
        text = {
            Column {
                for (r in flagReasons) {
                    Row(
                        Modifier.fillMaxWidth().clickable { reason = r },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = reason == r, onClick = { reason = r })
                        Text(r)
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("What's off? (optional)") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(reason, note.trim()) }) { Text("Flag") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
