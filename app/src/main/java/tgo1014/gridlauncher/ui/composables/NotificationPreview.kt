package tgo1014.gridlauncher.ui.composables

import android.text.format.DateUtils
import androidx.core.graphics.drawable.toBitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tgo1014.gridlauncher.live.*
import tgo1014.gridlauncher.ui.theme.LocalGlass

fun semanticLabel(style: Int) = when (style) { 2 -> "Safe"; 3 -> "Caution"; 4 -> "Urgent"; else -> "Live" }
fun semanticColor(style: Int, fallback: Color) = when (style) { 2 -> Color(0xFF64D9A2); 3 -> Color(0xFFFFC66D); 4 -> Color(0xFFFF8390); else -> fallback }

@Composable
fun LiveProgress(n: TileNotification, modifier: Modifier = Modifier) {
    val color = semanticColor(n.semantic, LocalGlass.current.accent)
    if (n.indeterminate) LinearProgressIndicator(modifier.fillMaxWidth(), color = color)
    else if (n.progressMax > 0) LinearProgressIndicator(progress = { (n.progress.toFloat() / n.progressMax).coerceIn(0f, 1f) }, modifier.fillMaxWidth(), color = color, trackColor = LocalGlass.current.ink.copy(alpha = .15f))
}

@Composable
fun NotificationActions(n: TileNotification, compact: Boolean = false) {
    val context = LocalContext.current
    val glass = LocalGlass.current
    var replying by remember(n.key) { mutableStateOf<android.app.Notification.Action?>(null) }
    var reply by remember(n.key) { mutableStateOf("") }
    var result by remember(n.key) { mutableStateOf("") }
    if (glass.locked || !glass.settings.showNotificationText || n.packageName in glass.settings.hiddenPreviewApps) return
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        n.actions.take(if (compact) 2 else 3).forEach { action ->
            TextButton(onClick = {
                if (action.remoteInputs?.any { it.allowFreeFormInput } == true) replying = action
                else { result = if (NotificationTiles.act(context, n.key, action)) "Action sent" else "Action unavailable"; Toast.makeText(context, result, Toast.LENGTH_SHORT).show() }
            }, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.weight(1f)) { Text(action.title.toString(), fontSize = 12.sp, color = glass.ink, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
    if (replying != null) AlertDialog(onDismissRequest = { replying = null; reply = "" }, title = { Text("Reply") }, text = {
        Column { OutlinedTextField(reply, { reply = it.take(2000) }, label = { Text("Message") }, modifier = Modifier.fillMaxWidth()); if (result.isNotEmpty()) Text(result) }
    }, confirmButton = { TextButton(enabled = reply.isNotBlank(), onClick = {
        if (NotificationTiles.act(context, n.key, replying!!, reply)) { replying = null; reply = ""; Toast.makeText(context, "Reply sent", Toast.LENGTH_SHORT).show() }
        else result = "Reply unavailable. Open the app to continue."
    }) { Text("Send") } }, dismissButton = { TextButton(onClick = { replying = null; reply = "" }) { Text("Cancel") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationPreview(title: String, packages: Set<String>, onDismiss: () -> Unit, key: String? = null) {
    val notifications by NotificationTiles.notifications.collectAsStateWithLifecycle()
    val glass = LocalGlass.current
    val context = LocalContext.current
    val items = notifications.filter { (key == null && it.packageName in packages) || it.key == key }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item { Text(title, style = MaterialTheme.typography.headlineLarge) }
            if (items.isEmpty()) item { Text("No active notifications") }
            items(items, key = { it.key }) { n ->
                if (!glass.locked && glass.settings.showNotificationText && n.packageName !in glass.settings.hiddenPreviewApps) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(n.title.ifBlank { "Notification" }, style = MaterialTheme.typography.titleLarge)
                        Text(DateUtils.getRelativeTimeSpanString(n.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(), style = MaterialTheme.typography.labelSmall)
                        val iconBitmap by produceState<android.graphics.Bitmap?>(null, n.artworkIcon) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { n.artworkIcon?.loadDrawable(context)?.toBitmap(256, 256) }.getOrNull() } }
                        (n.artwork ?: iconBitmap)?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxWidth().heightIn(max = 180.dp)) }
                        Text(if (n.messages.isNotEmpty()) n.messages.joinToString("\n") else n.text)
                        if (n.semantic > 0) Text(semanticLabel(n.semantic))
                        LiveProgress(n)
                        NotificationActions(n)
                        TextButton(onClick = { if (NotificationTiles.open(context, n.key)) onDismiss() else Toast.makeText(context, "Open the app from Start", Toast.LENGTH_SHORT).show() }) { Text("Open notification") }
                    }
                } else Text("Preview hidden. Unlock and enable previews in Customize Start.")
                HorizontalDivider()
            }
            item { TextButton(onClick = onDismiss) { Text("Close") } }
        }
    }
}
