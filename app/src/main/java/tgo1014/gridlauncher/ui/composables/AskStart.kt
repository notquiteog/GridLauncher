package tgo1014.gridlauncher.ui.composables

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tgo1014.gridlauncher.ui.theme.AsyncImage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.live.*
import tgo1014.gridlauncher.ui.models.GridItem
import tgo1014.gridlauncher.ui.theme.LocalGlass
import java.util.Locale

/**
 * Windows Phone's Cortana, rebuilt from what a launcher can honestly know: your apps, tiles,
 * people, notifications, calendar and device state. Speech is optional and on-device.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskStart(
    apps: List<App>, tiles: List<GridItem>, onOpenApp: (App) -> Unit, onDismiss: () -> Unit,
) {
    val glass = LocalGlass.current
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    var query by remember { mutableStateOf("") }
    var heard by remember { mutableStateOf(false) }
    val notifications by NotificationTiles.notifications.collectAsStateWithLifecycle()
    val nowPlaying by MediaTiles.now.collectAsStateWithLifecycle()
    var facts by remember { mutableStateOf(StartFacts()) }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { query = it; heard = true }
    }
    LaunchedEffect(apps, tiles, notifications, nowPlaying) {
        facts = StartFacts(
            apps = apps.map { it.name },
            tiles = tiles.map { it.app.name },
            people = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { ContactTiles.favorites(context) },
            notifications = notifications,
            events = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { queryEvents(context) },
            nowPlaying = nowPlaying?.label,
            battery = BuiltInTiles.detail(context, BuiltInTiles.BATTERY)?.let { "${it.first} · ${it.second}" },
            storage = BuiltInTiles.detail(context, BuiltInTiles.STORAGE)?.let { "${it.first} · ${it.second}" },
            clock = java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(java.util.Date()),
        )
    }
    val allowWeb = LocalGlass.current.settings.allowWebSearch
    val results = remember(query, facts, allowWeb) { StartSearch.search(query, apps, tiles, facts.people, notifications, allowWeb = allowWeb) }
    val answers = remember(query, facts) { StartQuery.answer(query, facts) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text("Ask Start", style = MaterialTheme.typography.headlineLarge, color = glass.ink)
            Text("Ask about your day, your people or your phone. Answers come from this device only.",
                style = MaterialTheme.typography.bodySmall, color = glass.ink.copy(alpha = .8f))
            OutlinedTextField(query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                placeholder = { Text("What's on today?") },
                leadingIcon = { IconButton(onClick = { heard = false }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Search, null) } },
                trailingIcon = {
                    TextButton(onClick = {
                        runCatching { voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)) }
                            .onFailure { android.widget.Toast.makeText(context, "No speech service is available", android.widget.Toast.LENGTH_SHORT).show() }
                    }, modifier = Modifier.semantics { contentDescription = "Ask by voice" }) { Text("Voice", color = glass.accent) }
                })
            val onAction: (Answer) -> Unit = { answer ->
                when (answer.action) {
                    Answer.Action.Call -> facts.people.firstOrNull { answer.text.contains(it.name) }?.phones?.firstOrNull()?.let { launch(context, StartSearch.dialIntent(it)) }
                    Answer.Action.Text -> facts.people.firstOrNull { answer.text.contains(it.name) }?.phones?.firstOrNull()?.let { launch(context, StartSearch.smsIntent(it)) }
                    Answer.Action.Email -> facts.people.firstOrNull { answer.text.contains(it.name) }?.emails?.firstOrNull()?.let { launch(context, StartSearch.mailIntent(it)) }
                    Answer.Action.Web -> launch(context, StartSearch.webIntent(query))
                    Answer.Action.OpenApp -> apps.firstOrNull { answer.text.contains(it.name) }?.let { onOpenApp(it) }
                    Answer.Action.OpenTile -> tiles.firstOrNull { answer.text.contains(it.app.name) }?.let { onOpenApp(it.app) }
                        ?: (facts.tiles.firstOrNull { answer.text.contains(it) }?.let { name -> BuiltInTiles.intent(name) }?.let { launch(context, it) })
                        ?: launch(context, StartSearch.photosIntent())
                    Answer.Action.OpenCalendar -> launch(context, BuiltInTiles.intent(BuiltInTiles.CALENDAR))
                    Answer.Action.Clock -> launch(context, BuiltInTiles.intent(BuiltInTiles.CLOCK))
                    Answer.Action.OpenNotification -> NotificationTiles.notifications.value.firstOrNull { answer.text.contains(it.title) }
                        ?.let { NotificationTiles.open(context, it.key) } ?: Unit
                    else -> Unit
                }
                onDismiss()
            }
            LazyColumn(Modifier.heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.45f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (answers.isNotEmpty()) {
                    item("answers") { Text("Answer", style = MaterialTheme.typography.labelLarge, color = glass.accent, modifier = Modifier.padding(top = 12.dp)) }
                    items(answers) { answer -> AnswerRow(answer, onAction) }
                }
                if (results.isNotEmpty()) {
                    item("results") { Text(if (heard) "Heard" else "Results", style = MaterialTheme.typography.labelLarge, color = glass.accent, modifier = Modifier.padding(top = 12.dp)) }
                    items(results) { result -> ResultRow(result, onClick = {
                        when (result) {
                            is SearchResult.AppResult -> onOpenApp(result.app)
                            is SearchResult.TileResult -> onOpenApp(result.tile.app)
                            is SearchResult.PersonResult -> result.contact.phones.firstOrNull()?.let { launch(context, StartSearch.dialIntent(it)) }
                                ?: result.contact.emails.firstOrNull()?.let { launch(context, StartSearch.mailIntent(it)) }
                            is SearchResult.NotificationResult -> NotificationTiles.open(context, result.notification.key)
                            is SearchResult.SettingResult -> launch(context, StartSearch.settingIntent(result.action))
                            is SearchResult.WebResult -> launch(context, StartSearch.webIntent(result.query))
                        }
                        onDismiss()
                    }) }
                }
                if (query.isNotBlank() && results.isEmpty() && answers.isEmpty()) {
                    item("empty") { Text("Nothing matched. Try an app name, a contact, or \"what's on today\".",
                        style = MaterialTheme.typography.bodyMedium, color = glass.ink.copy(alpha = .8f), modifier = Modifier.padding(vertical = 16.dp)) }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
    LaunchedEffect(Unit) { keyboard?.hide() }
}

private fun launch(context: android.content.Context, intent: Intent?) {
    if (intent == null) return
    runCatching { context.startActivity(intent) }.onFailure {
        android.widget.Toast.makeText(context, "No app can open that", android.widget.Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun AnswerRow(answer: Answer, onAction: (Answer) -> Unit) {
    val glass = LocalGlass.current
    Text(answer.text, color = glass.ink, fontSize = 16.sp,
        modifier = Modifier.fillMaxWidth().clickable(enabled = answer.action != Answer.Action.Nothing) { onAction(answer) }.padding(vertical = 10.dp))
}

@Composable
private fun ResultRow(result: SearchResult, onClick: () -> Unit) {
    val glass = LocalGlass.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        when (result) {
            is SearchResult.AppResult -> AsyncImage(result.app.icon.iconFile, Modifier.size(36.dp))
            is SearchResult.TileResult -> AsyncImage(result.tile.app.icon.iconFile, Modifier.size(36.dp))
            is SearchResult.PersonResult -> Text(result.contact.name.take(1).uppercase(), color = glass.ink, fontSize = 18.sp,
                modifier = Modifier.size(36.dp))
            else -> Unit
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(result.title, color = glass.ink, fontSize = 15.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            when (result) {
                is SearchResult.AppResult -> Text("App", color = glass.ink.copy(alpha = .7f), fontSize = 12.sp)
                is SearchResult.TileResult -> Text("Tile on Start", color = glass.ink.copy(alpha = .7f), fontSize = 12.sp)
                is SearchResult.PersonResult -> Text(result.latest?.title ?: "Contact", color = glass.ink.copy(alpha = .7f), fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                is SearchResult.NotificationResult -> Text(result.notification.text.take(80), color = glass.ink.copy(alpha = .7f), fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                is SearchResult.SettingResult -> Text("Android setting", color = glass.ink.copy(alpha = .7f), fontSize = 12.sp)
                is SearchResult.WebResult -> Unit
            }
        }
    }
}

/** Calendar rows for the answer engine, the same source the Calendar tile reads. */
internal fun queryEvents(context: android.content.Context): List<CalendarEvent> {
    if (!BuiltInTiles.granted(context, android.Manifest.permission.READ_CALENDAR)) return emptyList()
    val now = System.currentTimeMillis()
    val uri = android.provider.CalendarContract.Instances.CONTENT_URI.buildUpon()
    android.content.ContentUris.appendId(uri, now)
    android.content.ContentUris.appendId(uri, now + 7 * 86400000L)
    return runCatching {
        context.contentResolver.query(uri.build(), arrayOf(
            android.provider.CalendarContract.Instances.TITLE,
            android.provider.CalendarContract.Instances.BEGIN,
            android.provider.CalendarContract.Instances.ALL_DAY,
            android.provider.CalendarContract.Instances.EVENT_LOCATION), null, null, "${android.provider.CalendarContract.Instances.BEGIN} ASC")?.use {
            buildList {
                while (it.moveToNext() && size < 30) add(CalendarEvent(
                    it.getString(0).orEmpty().take(120), it.getLong(1), it.getInt(2) == 1, it.getString(3).orEmpty().take(80)))
            }
        }.orEmpty()
    }.getOrDefault(emptyList())
}
