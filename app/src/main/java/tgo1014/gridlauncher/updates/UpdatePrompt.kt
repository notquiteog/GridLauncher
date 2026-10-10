package tgo1014.gridlauncher.updates

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tgo1014.gridlauncher.ui.MainActivity

@Composable
fun UpdatePrompt(updater: GitHubUpdater, activity: MainActivity) {
    val state by updater.state.collectAsStateWithLifecycle()
    val update = state.update
    if (update == null && state.message == null) return
    AlertDialog(onDismissRequest = updater::dismiss,
        title = { Text(if (update == null) "GitHub updates" else "Update available") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (update != null) Text("GridLauncher ${update.versionName} · build ${update.versionCode}\nYour tiles and settings will be kept.")
            state.message?.let { Text(it) }
            if (state.downloading) { LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth()); Text("Downloading ${state.progress}%") }
            if (state.apk != null) Text("Download verified. Android will ask you to confirm installation.")
        } },
        confirmButton = { if (update != null) TextButton(enabled = !state.downloading, onClick = {
            if (state.apk != null) activity.installGitHubUpdate() else updater.download()
        }) { Text(if (state.apk != null) "Install update" else "Download update") }
        else TextButton(onClick = updater::dismiss) { Text("OK") } },
        dismissButton = { if (update != null) TextButton(enabled = !state.downloading, onClick = updater::dismiss) { Text("Later") } })
}
