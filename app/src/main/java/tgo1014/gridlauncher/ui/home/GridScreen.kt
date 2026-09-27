package tgo1014.gridlauncher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import tgo1014.gridlauncher.ui.theme.LocalGlass
import tgo1014.gridlauncher.data.profileNames
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.domain.models.TileSettings
import tgo1014.gridlauncher.ui.composables.TileLayout
import tgo1014.gridlauncher.ui.composables.sheets.SettingsBottomSheet
import tgo1014.gridlauncher.ui.composables.sheets.TileSettingsBottomSheet
import tgo1014.gridlauncher.ui.models.GridItem
import tgo1014.gridlauncher.ui.models.SettingsEvent
import tgo1014.gridlauncher.ui.models.TileEvent
import tgo1014.gridlauncher.ui.theme.GridLauncherTheme
import tgo1014.gridlauncher.ui.theme.conditional

@Composable
fun GridScreenScreen(
    state: HomeState,
    hazeState: HazeState = remember { HazeState() },
    onItemClicked: (item: GridItem) -> Unit = {},
    onItemDropped: (GridItem, Int, Int) -> Unit = { _, _, _ -> },
    onItemLongClicked: (item: GridItem) -> Unit = {},
    onFooterClicked: () -> Unit = {},
    onTileEvent: (TileEvent) -> Unit = {},
    onSettingsEvent: (SettingsEvent) -> Unit = {},
    onAddApp: (App) -> Unit = {},
    onSpecialTile: (GridItem) -> Unit = {},
    onProfile: (String) -> Unit = {},
    onCopyProfile: (String) -> Unit = {},
    onEditLayout: (Boolean) -> Unit = {},
) {
    var isOnTop by remember { mutableStateOf(true) }
    val glass = LocalGlass.current
    androidx.activity.compose.BackHandler(enabled = state.isEditingLayout && state.itemBeingEdited == null) { onEditLayout(false) }
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
    Box(Modifier.fillMaxWidth().animateContentSize(if (glass.motion) spring(dampingRatio = .9f, stiffness = 350f) else snap())) {
        if (state.tileSettings.oneHanded) Spacer(Modifier.height(100.dp))
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 10.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(java.text.SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), "EEEMMMd"), java.util.Locale.getDefault()).format(java.util.Date()),
            color = glass.ink, fontSize = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        TextButton(onClick = { onEditLayout(!state.isEditingLayout) }) { Text(if (state.isEditingLayout) "Done" else "Edit layout", color = glass.ink) }
        IconButton(onClick = { onSettingsEvent(SettingsEvent.OnSettingsIconClicked) }) { Icon(Icons.Default.Settings, "Customize Start", tint = glass.ink) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        profileNames.forEach { name -> FilterChip(selected = state.profile == name, onClick = { onProfile(name) }, label = { Text(name) }, colors = FilterChipDefaults.filterChipColors(containerColor = androidx.compose.ui.graphics.Color.Transparent, selectedContainerColor = glass.accent.copy(alpha = .22f), labelColor = glass.ink, selectedLabelColor = glass.ink)) }
    }
    NowArea(hazeState)
    if (state.grid.isEmpty()) Text("Add apps to ${state.profile} using All apps below.", color = glass.ink, modifier = Modifier.padding(12.dp))
    TileLayout(
        grid = state.grid,
        columns = state.tileSettings.gridColumns,
        tileSettings = state.tileSettings,
        hazeState = hazeState,
        itemBeingEdited = state.itemBeingEdited,
        editingLayout = state.isEditingLayout,
        footer = { modifier ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
            ) {

                Footer(
                    modifier = modifier,
                    onFooterClicked = onFooterClicked,
                    tileSettings = state.tileSettings
                )
            }
        },
        onItemLongClicked = onItemLongClicked,
        isOnTop = { isOnTop = it },
        onItemClicked = onItemClicked,
        onItemDropped = onItemDropped,
        contentPadding = if (state.itemBeingEdited == null) PaddingValues(0.dp) else PaddingValues(
            bottom = 200.dp
        ),
        modifier = Modifier
            .fillMaxWidth().weight(1f)
    )
    }
    TileSettingsBottomSheet(
        isShowing = state.isEditingLayout && state.itemBeingEdited != null,
        item = state.itemBeingEdited,
        onTileEvent = onTileEvent,
    )
    SettingsBottomSheet(
        tileSettings = state.tileSettings,
        isShowing = state.isSettingsSheetShowing,
        onSettingsEvent = onSettingsEvent,
        apps = state.appList,
        onAddApp = onAddApp,
        onAddSpecial = onSpecialTile,
        currentProfile = state.profile,
        onCopyProfile = onCopyProfile
    )
}

@Composable
@Preview
private fun Footer(
    modifier: Modifier = Modifier,
    onFooterClicked: () -> Unit = {},
    tileSettings: TileSettings = TileSettings()
) {
    val bgColor = MaterialTheme.colorScheme.primaryContainer
    val _modifier = Modifier
        .then(modifier)
        .clickable { onFooterClicked() }
        .padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 3.dp)
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = _modifier
    ) {
        val contentColor = LocalGlass.current.ink
        Text(text = "All apps", color = contentColor)
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = contentColor,
        )
    }
}

@Composable
@Preview
private fun SettingsIcon(
    modifier: Modifier = Modifier,
    onClicked: () -> Unit = {},
    tileSettings: TileSettings = TileSettings()
) {
    Box(
        modifier = Modifier
            .height(40.dp)
            .aspectRatio(1f)
    ) {
        val contentColor = contentColorFor(MaterialTheme.colorScheme.primaryContainer)
        if (tileSettings.isTransparencyEnabled) {
            Box(
                modifier = modifier
                    .clickable { onClicked() }
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Customize Start",
                    tint = contentColor
                )
            }
        } else {
            Box(
                modifier = modifier
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable { onClicked() }
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Customize Start",
                    tint = contentColor
                )
            }
        }

    }
}

@Composable
@Preview
private fun PreviewSmallTile() = GridLauncherTheme {
    GridScreenScreen(
        state = HomeState(
            grid = listOf(
                GridItem(app = App("はい"), width = 1),
            )
        ),
    )
}

@Composable
@Preview
private fun Preview() = GridLauncherTheme {
    GridScreenScreen(
        state = HomeState(
            grid = listOf(
                GridItem(app = App("وأصدقاؤك"), width = 2),
                GridItem(app = App("123"), width = 2, x = 2),
                GridItem(app = App("#1231"), width = 2, x = 4),
                GridItem(app = App("$$$$"), width = 2, y = 2),
                GridItem(app = App("FooBar"), width = 2, y = 2, x = 2),
                GridItem(app = App("Aaaa"), width = 2, y = 2, x = 4),
                GridItem(app = App("AAb"), width = 2, y = 4),
                GridItem(app = App("はい"), width = 2, y = 4, x = 2),
            )
        ),
    )
}