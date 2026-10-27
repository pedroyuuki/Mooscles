package com.mooscles.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mooscles.backup.BackupTexts
import com.mooscles.backup.suggestedBackupFileName
import java.time.LocalDate

/** Settings card for JSON export and import. Cancelling a picker (null URI) does nothing. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DataSection(state: BackupUi, workoutActive: Boolean, actions: DataActions, modifier: Modifier = Modifier) {
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) actions.exportData(uri.toString())
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) actions.prepareImport(uri.toString())
    }
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Your data", style = MaterialTheme.typography.titleMedium)
            Text(
                "Backups contain your full workout history without encryption. Keep the file somewhere private.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { exportLauncher.launch(suggestedBackupFileName(LocalDate.now())) },
                    enabled = !state.busy, modifier = Modifier.testTag("data-export"),
                ) { Text("Export data") }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                    enabled = !state.busy && !workoutActive, modifier = Modifier.testTag("data-import"),
                ) { Text("Import data") }
            }
            if (workoutActive) {
                Text("Finish or discard the active workout to import", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("data-progress").semantics { contentDescription = "Working…" })
            state.message?.let { message ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(message, Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = actions::clearBackupMessage, modifier = Modifier.testTag("data-message-dismiss")) { Text("Dismiss") }
                }
            }
        }
    }
    state.pendingImport?.let { summary ->
        AlertDialog(
            onDismissRequest = actions::cancelImport,
            title = { Text("Import backup?") },
            text = { Text(BackupTexts.summary(summary)) },
            confirmButton = { TextButton(onClick = actions::confirmImport) { Text("Import") } },
            dismissButton = { TextButton(onClick = actions::cancelImport) { Text("Cancel") } },
        )
    }
}
