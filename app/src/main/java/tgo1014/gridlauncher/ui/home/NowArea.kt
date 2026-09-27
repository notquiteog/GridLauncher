package tgo1014.gridlauncher.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import tgo1014.gridlauncher.live.NotificationTiles
import tgo1014.gridlauncher.ui.composables.*
import tgo1014.gridlauncher.ui.theme.*

@Composable
fun NowArea(haze: HazeState) {
    val notifications by NotificationTiles.notifications.collectAsStateWithLifecycle()
    val glass = LocalGlass.current
    val ongoing = notifications.filter { it.isNow && it.packageName !in glass.settings.hiddenPreviewApps }.take(6)
    var selected by remember { mutableStateOf<String?>(null) }
    val duration = if (glass.motion) 240 else 0
    AnimatedVisibility(visible = glass.settings.showNow && glass.settings.liveTilesEnabled && ongoing.isNotEmpty(), enter = fadeIn(tween(duration)) + expandVertically(tween(duration)), exit = fadeOut(tween(duration)) + shrinkVertically(tween(duration))) {
    Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
        Text("Now", style = MaterialTheme.typography.labelLarge, color = glass.ink, modifier = Modifier.padding(bottom = 6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ongoing, key = { it.key }) { n -> Column(Modifier.width(280.dp).glassSurface(haze).clickable { selected = n.key }.padding(12.dp)) {
                Text(if (!glass.locked && glass.settings.showNotificationText) n.title.ifBlank { "Ongoing activity" } else "Ongoing activity", style = MaterialTheme.typography.titleMedium, color = glass.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!glass.locked && glass.settings.showNotificationText) Text(n.text, style = MaterialTheme.typography.bodySmall, color = glass.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!glass.locked && glass.settings.showNotificationText && n.semantic > 0) Text(semanticLabel(n.semantic), color = glass.ink, style = MaterialTheme.typography.labelSmall)
                if (!glass.locked && glass.settings.showNotificationText) LiveProgress(n, Modifier.padding(top = 8.dp))
            } }
        }
    }
    }
    selected?.let { NotificationPreview("Now", emptySet(), { selected = null }, key = it) }
}
