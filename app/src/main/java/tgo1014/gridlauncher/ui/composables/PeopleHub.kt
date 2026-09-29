package tgo1014.gridlauncher.ui.composables

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tgo1014.gridlauncher.live.NotificationTiles
import tgo1014.gridlauncher.live.PinnedContact
import tgo1014.gridlauncher.live.PeopleTiles
import tgo1014.gridlauncher.ui.theme.AsyncImage
import tgo1014.gridlauncher.ui.theme.LocalGlass
import tgo1014.gridlauncher.ui.theme.TileTurn

/** The Windows Phone People hub: one row per person, newest conversation first. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PeopleHub(contacts: List<PinnedContact>, onDismiss: () -> Unit, onOpenApp: (String) -> Unit = {}) {
    val glass = LocalGlass.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val view = LocalView.current
    val notifications by NotificationTiles.notifications.collectAsStateWithLifecycle()
    val rows = remember(contacts, notifications) { PeopleTiles.hub(contacts, notifications) }
    fun launch(intent: Intent) = runCatching { context.startActivity(intent) }
        .onFailure { android.widget.Toast.makeText(context, "No app can open this action", android.widget.Toast.LENGTH_SHORT).show() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text("People", style = MaterialTheme.typography.headlineLarge, color = glass.ink)
            if (rows.isEmpty()) {
                Text("Star contacts, or choose people in Customize Start, to see them here.", style = MaterialTheme.typography.bodyMedium, color = glass.ink)
                Spacer(Modifier.height(24.dp))
            }
            LazyColumn(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(rows, key = { it.contact.key }) { row ->
                    val person = row.contact
                    val initials = remember(person.name) { person.name.split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1) }.uppercase() }
                    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                        .background(glass.accent.copy(alpha = .12f)).padding(12.dp)
                        // Press, hold and drag a number or address out into any other app.
                        .pointerInput(person.key) {
                            val draggable = person.phones.firstOrNull()?.let { "tel:$it" to it }
                                ?: person.emails.firstOrNull()?.let { "mailto:$it" to it }
                            if (draggable == null) return@pointerInput
                            detectDragGesturesAfterLongPress(onDrag = { _, _ -> }, onDragStart = {
                                val (uri, label) = draggable
                                val clip = android.content.ClipData.newUri(context.contentResolver, label, android.net.Uri.parse(uri))
                                    .also { it.addItem(android.content.ClipData.Item(label)) }
                                val shadow = android.widget.TextView(context).apply {
                                    text = label; setPadding(24, 12, 24, 12)
                                    setTextColor(android.graphics.Color.WHITE)
                                    setBackgroundColor(glass.accent.toArgb())
                                }
                                view.startDragAndDrop(clip, android.view.View.DragShadowBuilder(shadow), label,
                                    android.view.View.DRAG_FLAG_GLOBAL or android.view.View.DRAG_FLAG_GLOBAL_URI_READ)
                            })
                        }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (person.photo != null) AsyncImage(person.photo, Modifier.size(44.dp).clip(CircleShape))
                            else Box(Modifier.size(44.dp).clip(CircleShape).background(glass.accent.copy(alpha = .35f)), contentAlignment = Alignment.Center) {
                                Text(initials, color = Color.White, fontSize = 16.sp)
                            }
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(person.name, color = glass.ink, fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(row.latest?.title?.takeIf { it.isNotBlank() } ?: row.latest?.text.orEmpty(),
                                    color = glass.ink.copy(alpha = .8f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (person.phones.isNotEmpty()) {
                                TextButton(onClick = { launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${person.phones.first()}"))) }) { Text("Call", color = glass.ink) }
                            } else if (person.emails.isNotEmpty()) {
                                TextButton(onClick = { launch(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${person.emails.first()}"))) }) { Text("Email", color = glass.ink) }
                            }
                        }
                        if (row.latest != null) {
                            TileTurn(key = row.latest.key, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                                Column(Modifier.fillMaxWidth().clickable { onOpenApp(row.latest.packageName) }) {
                                    Text(row.latest.title.takeIf { it.isNotBlank() } ?: "Open", color = glass.ink, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(row.latest.text, color = glass.ink.copy(alpha = .75f), fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            person.phones.forEach { number -> TextButton(onClick = { launch(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number"))) }) { Text("Message", color = glass.ink.copy(alpha = .9f), fontSize = 12.sp) } }
                            person.emails.forEach { address -> TextButton(onClick = { launch(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address"))) }) { Text("Email", color = glass.ink.copy(alpha = .9f), fontSize = 12.sp) } }
                            TextButton(onClick = { launch(Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.contacts/contacts/lookup/${person.key}"))) }) { Text("Contact", color = glass.ink.copy(alpha = .9f), fontSize = 12.sp) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
