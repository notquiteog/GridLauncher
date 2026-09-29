package tgo1014.gridlauncher.ui.composables

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.ui.theme.AsyncImage
import tgo1014.gridlauncher.ui.theme.LocalGlass
import tgo1014.gridlauncher.ui.theme.glassSurface

/**
 * The hotseat: a fixed row at the bottom of Start holding the apps you always want, plus the
 * search pill and the way into Customize Start. It sits outside the tile grid, so packing,
 * pinning and column width never move it.
 */
@Composable
fun Hotseat(
    apps: List<App>, pinned: List<String>, onOpen: (App) -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = LocalGlass.current
    val slots = 4
    val resolved = pinned.take(slots).mapNotNull { name -> apps.firstOrNull { it.packageName == name } }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            resolved.forEach { app ->
                Box(Modifier.size(56.dp).clip(CircleShape).clickable { onOpen(app) }
                    .semantics { contentDescription = "Hotseat ${app.name}" }, contentAlignment = Alignment.Center) {
                    AsyncImage(app.icon.iconFile, Modifier.size(48.dp).clip(CircleShape))
                }
            }
            repeat(slots - resolved.size) { Spacer(Modifier.size(56.dp)) }
        }
    }
}

/** The apps you reach for most, offered above the alphabetical list. */
@Composable
fun FrequentRow(apps: List<App>, frequent: List<String>, sort: String, onOpen: (App) -> Unit, onPin: (App) -> Unit, modifier: Modifier = Modifier) {
    val glass = LocalGlass.current
    val suggestions = frequent.asSequence().mapNotNull { name -> apps.firstOrNull { it.packageName == name } }
        .let { if (sort == "alphabetical") it.sortedBy { app -> app.name.lowercase() } else it }
        .take(8).toList()
    if (suggestions.size < 2) return
    Column(modifier.fillMaxWidth()) {
        Text(if (sort == "alphabetical") "Apps" else if (sort == "recent") "Recent" else "Most used", color = glass.ink.copy(alpha = .7f), fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(suggestions, key = { it.packageName }) { app ->
                Column(Modifier.width(72.dp).clickable { onOpen(app) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box {
                        AsyncImage(app.icon.iconFile, Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)))
                        Box(Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape)
                            .background(Color.Black.copy(alpha = .45f)).border(1.dp, Color.White.copy(alpha = .4f), CircleShape)
                            .clickable { onPin(app) }, contentAlignment = Alignment.Center) {
                            Text("+", color = Color.White, fontSize = 13.sp, lineHeight = 13.sp)
                        }
                    }
                    Text(app.name, color = glass.ink, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}
