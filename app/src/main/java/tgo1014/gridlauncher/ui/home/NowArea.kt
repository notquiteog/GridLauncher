package tgo1014.gridlauncher.ui.home

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.live.*
import tgo1014.gridlauncher.ui.composables.LiveProgress
import tgo1014.gridlauncher.ui.composables.NotificationActions
import tgo1014.gridlauncher.ui.composables.NotificationPreview
import tgo1014.gridlauncher.ui.composables.semanticLabel
import tgo1014.gridlauncher.ui.theme.AppIconImage
import tgo1014.gridlauncher.ui.theme.GlassEnvironment
import tgo1014.gridlauncher.ui.theme.LocalGlass
import tgo1014.gridlauncher.ui.theme.glassSurface
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * The Windows Phone Now board: a vertical stack of cards that is only here when something is
 * happening. Each card says where its content came from, pans sideways to the rest of what it has,
 * and opens in place rather than taking the screen away.
 */
@Composable
fun NowArea(haze: HazeState) {
    val glass = LocalGlass.current
    val context = LocalContext.current
    val notifications by NotificationTiles.notifications.collectAsStateWithLifecycle()
    val nowPlaying by MediaTiles.now.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val settings = glass.settings

    var categories by remember { mutableStateOf(emptyMap<NowCategory, App>()) }
    var agenda by remember { mutableStateOf(emptyList<CalendarEvent>()) }
    var money by remember { mutableStateOf<App?>(null) }
    var mediaApp by remember { mutableStateOf<String?>(null) }
    var previewKey by remember { mutableStateOf<String?>(null) }
    var choosingMoney by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Notifications and the media session arrive as flows. The calendar and the apps behind a
    // category are only read here, on a slow tick and off the main thread, and only while the
    // launcher is in front. Quiet hours say the board has nothing to say at all.
    LaunchedEffect(settings.meetingMode, settings.quietHoursEnabled, settings.quietStartHour, settings.quietEndHour) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                if (!QuietHours.active(settings)) withContext(Dispatchers.IO) {
                    MediaTiles.refresh(context)
                    categories = CategoryApps.resolveAll(context)
                    agenda = BuiltInTiles.agendaEvents(context, NowBoard.MAX_EVENTS)
                }
                money = readMoney(context)
                delay(REFRESH_MS)
            }
        }
    }
    LaunchedEffect(nowPlaying?.packageName) {
        val packageName = nowPlaying?.packageName
        mediaApp = packageName?.let { withContext(Dispatchers.IO) { CategoryApps.label(context, it) } }
    }

    fun act(action: NowAction) = when (action) {
        is NowAction.OpenNotification -> previewKey = action.key
        is NowAction.OpenApp -> launch(context, intentFor(context, action.packageName))
        NowAction.OpenCalendar -> launch(context, BuiltInTiles.intent(BuiltInTiles.CALENDAR))
        NowAction.Expand -> Unit
    }

    val cards = NowBoard.cards(BoardInput(settings, glass.quiet, glass.locked, notifications, nowPlaying, mediaApp, agenda, categories, money))
    val duration = if (glass.motion) 240 else 0
    AnimatedVisibility(visible = cards.isNotEmpty(),
        enter = fadeIn(tween(duration)) + expandVertically(tween(duration)),
        exit = fadeOut(tween(duration)) + shrinkVertically(tween(duration))) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                Text("Now", style = MaterialTheme.typography.labelLarge, color = glass.ink, modifier = Modifier.weight(1f).padding(start = 8.dp))
                // Money is the one slot the user fills in, so the board is where they fill it in.
                IconButton(onClick = { choosingMoney = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Add, "Choose a money app for Now", tint = glass.ink)
                }
            }
            Column(Modifier.fillMaxWidth().heightIn(max = BOARD_HEIGHT).verticalScroll(rememberScrollState())) {
                cards.forEach { card -> NowCardView(card, haze, glass, ::act) }
            }
        }
    }
    previewKey?.let { NotificationPreview("Now", emptySet(), { previewKey = null }, key = it) }
    if (choosingMoney) MoneyPicker({ choosingMoney = false }) { packageName ->
        BoardSlots.pin(context, packageName)
        choosingMoney = false
        // Read it straight back rather than waiting for the next tick, so the card the user just
        // chose is on the board by the time the dialog is gone.
        scope.launch { money = readMoney(context) }
    }
}

/**
 * One card. The summary carries the card's own action, as the board has always done, and a separate
 * control opens the card up: everything past the summary is then panned sideways inside the card.
 */
@Composable
private fun NowCardView(card: NowCard, haze: HazeState, glass: GlassEnvironment, onAction: (NowAction) -> Unit) {
    var expanded by remember(card.key) { mutableStateOf(false) }
    val ink = glass.ink
    val growth: FiniteAnimationSpec<IntSize> = if (glass.motion) tween(200) else snap()
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 3.dp).animateContentSize(growth).glassSurface(haze)) {
        val page = maxWidth
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, end = 4.dp)) {
                CardMark(card, Modifier.padding(end = 10.dp))
                Column(Modifier.weight(1f).heightIn(min = 76.dp).padding(vertical = 6.dp)
                    .clickable { onAction(card.action) }
                    .semantics { contentDescription = card.describe(expanded) },
                    verticalArrangement = Arrangement.Center) {
                    Text(card.title, style = MaterialTheme.typography.titleMedium, color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    card.lines.firstOrNull()?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    card.notification?.let { LiveProgress(it, Modifier.padding(top = 6.dp), ink = ink) }
                }
                if (card.expandable) IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(48.dp)) {
                    Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        if (expanded) "Show less" else "Show more", tint = ink)
                }
            }
            card.media?.let { Transport(it, ink, Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp)) }
            if (expanded) {
                val panels = panels(card, ink, onAction)
                // Only a card with more than one page takes a horizontal drag, so a one-page card
                // never swallows the swipe the drawer itself is paged with.
                val pan = rememberScrollState()
                if (panels.isNotEmpty()) Row(if (panels.size > 1) Modifier.horizontalScroll(pan) else Modifier.fillMaxWidth()) {
                    panels.forEach { panel -> Box(Modifier.width(page).padding(horizontal = 12.dp)) { panel() } }
                }
            }
        }
    }
}

/** The app's own mark: album art while something plays, otherwise the icon of the app it came from. */
@Composable
private fun CardMark(card: NowCard, modifier: Modifier) {
    val artwork = card.media?.artwork
    val icon = card.app?.icon
    when {
        artwork != null -> Image(bitmap = artwork.asImageBitmap(), contentDescription = null, modifier = modifier.size(52.dp), contentScale = ContentScale.Crop)
        icon?.iconFile != null -> AppIconImage(icon.iconFile, modifier.size(28.dp), icon.fill)
    }
}

/** The rest of what a card has, one page at a time. */
private fun panels(card: NowCard, ink: Color, onAction: (NowAction) -> Unit): List<@Composable () -> Unit> = when (card.kind) {
    NowCardKind.NOTIFICATION -> listOf({
        Column(Modifier.padding(vertical = 6.dp)) {
            card.notification?.let { n ->
                if (n.semantic > 0) Text(semanticLabel(n.semantic), style = MaterialTheme.typography.labelSmall, color = ink)
                NotificationActions(n, ink = ink)
            }
            TextButton(onClick = { card.notificationKey?.let { onAction(NowAction.OpenNotification(it)) } }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Open notification", color = ink) }
        }
    })
    NowCardKind.AGENDA -> card.events.map { event ->
        {
            Column(Modifier.padding(vertical = 6.dp)) {
                Text(event.title, style = MaterialTheme.typography.titleMedium, color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(whenTime(event), style = MaterialTheme.typography.bodySmall, color = ink)
                if (event.location.isNotBlank()) Text(event.location, style = MaterialTheme.typography.bodySmall, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    // Music keeps its transport on the card itself, so opening it up only adds where it came from.
    NowCardKind.MEDIA -> listOf({
        Column(Modifier.padding(vertical = 6.dp)) {
            card.media?.let { Text(it.label, style = MaterialTheme.typography.bodyMedium, color = ink) }
            card.appPackage?.let { packageName -> TextButton(onClick = { onAction(NowAction.OpenApp(packageName)) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Open the app", color = ink) } }
        }
    })
    NowCardKind.CATEGORY -> listOf({
        Column(Modifier.padding(vertical = 6.dp)) {
            Text("Only what ${card.app?.name} posted is on this card. Start makes up none of it.",
                style = MaterialTheme.typography.bodySmall, color = ink)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                card.notificationKey?.let { key -> TextButton(onClick = { onAction(NowAction.OpenNotification(key)) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Preview notification", color = ink) } }
                card.appPackage?.let { packageName -> TextButton(onClick = { onAction(NowAction.OpenApp(packageName)) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Open ${card.app?.name}", color = ink) } }
            }
        }
    })
    NowCardKind.MONEY -> emptyList()
}

private fun whenTime(event: CalendarEvent) = if (event.allDay) "All day"
else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(event.begin))

/** Transport for the system's current media session, whichever app owns it. */
@Composable
private fun Transport(now: NowPlaying, ink: Color, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (now.canGoPrevious) TransportButton("⏮", ink, "Previous track") { MediaTiles.previous() }
        TransportButton(if (now.playing) "⏸" else "▶", ink, if (now.playing) "Pause" else "Play", prominent = true) { MediaTiles.togglePlayPause() }
        if (now.hasNext) TransportButton("⏭", ink, "Next track") { MediaTiles.next() }
    }
}

@Composable
private fun TransportButton(glyph: String, ink: Color, description: String, prominent: Boolean = false, onClick: () -> Unit) =
    Box(Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).clickable(onClick = onClick)
        .semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Text(glyph, color = ink, fontSize = if (prominent) 22.sp else 15.sp, maxLines = 1)
    }

/** The one slot on the board the user chooses for themselves. */
@Composable
private fun MoneyPicker(onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    val context = LocalContext.current
    val apps by produceState<List<InstalledApp>?>(null) { value = CategoryApps.launchable(context) }
    var filter by remember { mutableStateOf("") }
    val shown = remember(apps, filter) {
        apps.orEmpty().filter { filter.isBlank() || it.name.contains(filter, ignoreCase = true) }.take(200)
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Money on Now") }, text = {
        Column {
            Text("Pin the app you pay with. Start opens it and reads nothing from it.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(filter, { filter = it.take(40) }, label = { Text("Find an app") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            when {
                apps == null -> Text("Looking for your apps…", style = MaterialTheme.typography.bodySmall)
                shown.isEmpty() -> Text("No app found", style = MaterialTheme.typography.bodySmall)
                else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                    items(shown, key = { it.packageName }) { app ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onPick(app.packageName) }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            val icon by produceState<File?>(null, app.packageName) { value = CategoryApps.icon(context, app.packageName).iconFile }
                            if (icon != null) AppIconImage(icon, Modifier.size(32.dp)) else Spacer(Modifier.width(32.dp))
                            Text(app.name, Modifier.padding(start = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { onPick(null) }) { Text("No money app") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

private suspend fun readMoney(context: Context): App? = withContext(Dispatchers.IO) {
    BoardSlots.money(context)?.let { CategoryApps.app(context, it) }
}

private fun intentFor(context: Context, packageName: String): Intent? =
    BuiltInTiles.intent(packageName) ?: context.packageManager.getLaunchIntentForPackage(packageName)

private fun launch(context: Context, intent: Intent?) {
    if (intent == null) return toast(context, "This app is unavailable")
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { toast(context, "This app is unavailable") }
}

private fun toast(context: Context, message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

private const val REFRESH_MS = 30_000L
private val BOARD_HEIGHT = 300.dp
