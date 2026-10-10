package tgo1014.gridlauncher.ui.composables

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.ui.models.GridItem
import tgo1014.gridlauncher.ui.theme.AppIconImage

/**
 * One line of a folder's jump list. A folder row [opens] rather than launches, which is the whole
 * reason the two are different types here and not just a list of apps.
 *
 * [open] calls the launcher's own drill-down, the same path a plain tap on the folder takes, so a
 * jump list and an inline expansion can never end up in different places.
 */
internal data class JumpRow(val app: App, val open: GridItem? = null)

/**
 * What a folder's jump list offers, in the order the tile itself previews them: its apps first, then
 * the folders it holds, each in the order they were filed. Windows Phone's jump list was a menu of
 * the folder's contents rather than a way into the folder, and the order is the one the user last
 * saw on the tile, so the menu and the mosaic never disagree.
 */
internal fun jumpRows(folder: GridItem): List<JumpRow> =
    folder.children.map { JumpRow(it) } + folder.childFolders.map { JumpRow(it.app, it) }

/** The tile that stands for one filed app, wherever it is shown from. The id is derived, not stored. */
internal fun filedTile(app: App) = GridItem(-1_000_000 - app.packageName.hashCode(), app, 1)

/**
 * The Windows Phone folder menu: long press a folder and its contents arrive, each one launching
 * straight away instead of opening the folder first.
 *
 * Its own file rather than a branch inside [NativeTileActions], which stays the one thing its own
 * KDoc says it is: the shortcuts an app published to the system, read from `LauncherApps` for a
 * package. A folder has no package - it is the launcher's own `grid://folder` - so that list is
 * always empty there and the menu a folder shows today is one lone "Open". Merging them would mean
 * one file reading two unrelated sources and answering two different questions: what this app can
 * do, against what is in here.
 */
@Composable
fun FolderJumpList(folder: GridItem, onOpen: (App) -> Unit, onOpenFolder: (GridItem) -> Unit, onDismiss: () -> Unit) {
    val rows = jumpRows(folder)
    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        rows.forEach { row ->
            val target = row.open
            DropdownMenuItem(
                text = { Text(row.app.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                onClick = {
                    onDismiss()
                    if (target != null) onOpenFolder(target) else onOpen(row.app)
                },
                modifier = Modifier.heightIn(min = 48.dp)
                    .semantics { contentDescription = if (target != null) "Open ${row.app.name} folder" else "Open ${row.app.name}" },
                leadingIcon = {
                    val ink = LocalContentColor.current
                    if (target != null) HubGlyph(row.app.packageName, Modifier.size(24.dp), ink)
                    else AppIconImage(row.app.icon.iconFile, Modifier.size(24.dp), row.app.icon.fill)
                },
                trailingIcon = if (target != null) {
                    { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, modifier = Modifier.size(18.dp)) }
                } else null,
            )
        }
        HorizontalDivider()
        // The folder itself stays one long press away, so nothing the old menu offered is lost.
        DropdownMenuItem(text = { Text("Open ${folder.app.name}") },
            onClick = { onDismiss(); onOpenFolder(folder) },
            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Open ${folder.app.name} folder" },
            leadingIcon = { HubGlyph(folder.app.packageName, Modifier.size(24.dp), LocalContentColor.current) },
            trailingIcon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, modifier = Modifier.size(18.dp)) })
    }
}
