package tgo1014.gridlauncher.ui.home

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.CompositionLocalProvider
import tgo1014.gridlauncher.ui.theme.LocalGlass
import tgo1014.gridlauncher.ui.theme.rememberGlass
import tgo1014.gridlauncher.ui.theme.GlassBackdrop

import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.launch
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.live.SearchRow
import tgo1014.gridlauncher.ui.composables.LaunchedIfTrueEffect
import tgo1014.gridlauncher.ui.models.GridItem
import tgo1014.gridlauncher.ui.models.SettingsEvent
import tgo1014.gridlauncher.ui.models.TileEvent
import tgo1014.gridlauncher.ui.PaneSplit
import tgo1014.gridlauncher.ui.WindowPosture
import tgo1014.gridlauncher.ui.drawerScrimFor
import tgo1014.gridlauncher.ui.postureFor
import tgo1014.gridlauncher.ui.rememberHinge
import tgo1014.gridlauncher.ui.theme.AsyncImage

@Composable
fun HomeScreen(
    viewModel: HomeScreenViewModel = hiltViewModel()
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val frequent by viewModel.frequentApps.collectAsStateWithLifecycle(emptyList())
    val activity = LocalActivity.current as? androidx.activity.ComponentActivity
    LaunchedEffect(state.tileSettings.darkTheme) {
        val style = if (state.tileSettings.darkTheme) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        activity?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
    // Fullscreen hides the system bars; Android brings them back with a swipe from the edge.
    LaunchedEffect(state.tileSettings.fullscreen) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        if (state.tileSettings.fullscreen) {
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
    }
    HomeScreen(
        state = state,
        onAppClicked = viewModel::onOpenApp,
        onAddToGrid = viewModel::onAddToGrid,
        onHome = viewModel::onSwitchedToHome,
        onFilterTextChanged = viewModel::onFilterTextChanged,
        onFilterClearPressed = viewModel::onFilterCleared,
        onUninstall = viewModel::uninstallApp,
        onItemClicked = viewModel::onGridItemClicked,
        onItemDropped = viewModel::onTileDropped,
        onItemLongClicked = viewModel::onGridItemLongClicked,
        onSettingsEvent = viewModel::onSettingsEvent,
        onSpecialTile = viewModel::addSpecialTile,
        onTileEvent = viewModel::onTileEvent,
        onProfile = viewModel::selectProfile,
        onCopyProfile = viewModel::copyProfile,
        onCreateLayout = viewModel::createLayout,
        onRenameLayout = viewModel::renameLayout,
        onDeleteLayout = viewModel::deleteLayout,
        onReorderLayouts = viewModel::reorderLayouts,
        onHandoffFocusHandled = viewModel::onHandoffFocusHandled,
        onPinToHotseat = { viewModel.pinToHotseat(it) },
        onFolderChanged = viewModel::onFolderChanged,
        onSearchRowClicked = viewModel::onSearchRowClicked,
        frequent = frequent,
        onEditLayout = viewModel::setEditingLayout
    )
}

@Composable
private fun HomeScreen(
    state: HomeState,
    onAppClicked: (App) -> Unit = {},
    onAddToGrid: (App) -> Unit = {},
    onHome: () -> Unit = {},
    onFilterTextChanged: (String) -> Unit = {},
    onFilterClearPressed: () -> Unit = {},
    onUninstall: (App) -> Unit = {},
    onItemClicked: (item: GridItem) -> Unit = {},
    onItemDropped: (GridItem, Int, Int) -> Unit = { _, _, _ -> },
    onItemLongClicked: (item: GridItem) -> Unit = {},
    onSettingsEvent: (SettingsEvent) -> Unit = {},
    onTileEvent: (TileEvent) -> Unit = {},
    onSpecialTile: (GridItem) -> Unit = {},
    onProfile: (String) -> Unit = {},
    onCopyProfile: (String) -> Unit = {},
    onCreateLayout: (String, Boolean) -> Unit = { _, _ -> },
    onRenameLayout: (String, String) -> Unit = { _, _ -> },
    onDeleteLayout: (String) -> Unit = {},
    onReorderLayouts: (List<String>) -> Unit = {},
    onHandoffFocusHandled: () -> Unit = {},
    onPinToHotseat: (String) -> Unit = {},
    onFolderChanged: (GridItem) -> Unit = {},
    onSearchRowClicked: (SearchRow) -> Unit = {},
    frequent: List<String> = emptyList(),
    onEditLayout: (Boolean) -> Unit = {},
) {
    val glass = rememberGlass(state.tileSettings)
    val accent = glass.accent
    // Only used to ask the window manager what the window's posture is; a window that is not an
    // activity simply reports no fold and the width rule decides, which is the old behaviour.
    val windowActivity = LocalActivity.current as? android.app.Activity
    CompositionLocalProvider(LocalGlass provides glass, androidx.compose.material3.LocalContentColor provides glass.ink) {
    androidx.compose.material3.MaterialTheme(
    colorScheme = if (state.tileSettings.darkTheme) androidx.compose.material3.darkColorScheme(
        primary = accent, onPrimary = Color.White, primaryContainer = accent, onPrimaryContainer = Color.White,
        background = Color.Black, surface = Color(0xFF141414))
    else androidx.compose.material3.lightColorScheme(primary = accent, onPrimary = Color.White, primaryContainer = accent, onPrimaryContainer = Color.White)
) { Box(Modifier.background(androidx.compose.material3.MaterialTheme.colorScheme.background)) {
    // Continuum: a desktop window or large screen gets Start and All apps side by side, the way
    // Continuum gave a phone a desktop-sized shell. Touch layouts keep the single-column pager.
    val pagerState = rememberPagerState(initialPage = 0) { 2 }
    val scope = rememberCoroutineScope()
    var pagerWidth by remember { mutableIntStateOf(1) }
    val scrollOffset by remember(pagerWidth) {
        derivedStateOf { (pagerState.currentPage + pagerState.currentPageOffsetFraction) * pagerWidth }
    }
    val scrimFraction = (scrollOffset / pagerWidth.toFloat()).coerceIn(0f, 1f)
    val scrim = drawerScrimFor(state.tileSettings.darkTheme, scrimFraction)
    LaunchedIfTrueEffect(state.goToHome) {
        pagerState.scrollToPage(0)
    }
    // Windows Phone's pages move, but they do not move when the user has asked for no animation.
    // animateScrollToPage runs on the frame clock and never consults the system animation scale, so
    // the choice has to be made here: with motion off the pager lands on the other page at once and
    // the reveal below has nothing to ride.
    fun goToPage(page: Int) = scope.launch {
        if (glass.motion) pagerState.animateScrollToPage(page) else pagerState.scrollToPage(page)
    }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedIfTrueEffect(pagerState.settledPage == 0) {
        onHome()
        keyboardController?.hide()
    }
    val hazeState = remember { HazeState() }
    if (!state.tileSettings.isTransparencyEnabled) GlassBackdrop(Modifier.haze(state = hazeState))
    if (state.tileSettings.isTransparencyEnabled) {
        key(state.tileSettings.wallpaperFile) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(state.tileSettings.wallpaperFile)
                    .crossfade(true)
                    .build(),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .wallpaperParallax()
                    .haze(state = hazeState)
            )
        }
    }
    val start: @Composable (Boolean) -> Unit = { showFooter ->
        GridScreenScreen(state = state, hazeState = hazeState, onItemClicked = onItemClicked,
            onItemDropped = onItemDropped, onItemLongClicked = onItemLongClicked, onTileEvent = onTileEvent,
            onEditLayout = onEditLayout, showAllAppsLink = showFooter, onHandoffFocusHandled = onHandoffFocusHandled,
            onOpenApp = onAppClicked, onFolderChanged = onFolderChanged,
            onFooterClicked = { goToPage(1) })
    }
    val allApps: @Composable () -> Unit = {
        AppListScreen(state = state, hazeState = hazeState, onAppClicked = onAppClicked, onAddToGrid = onAddToGrid, onPinToHotseat = onPinToHotseat,
            onFilterTextChanged = onFilterTextChanged, onFilterClearPressed = onFilterClearPressed,
            onUninstall = onUninstall, onSettingsEvent = onSettingsEvent,
            onEditLayout = { editing -> onEditLayout(editing); goToPage(0) },
            onProfile = { name -> onProfile(name); goToPage(0) },
            onCreateLayout = { name, copy -> onCreateLayout(name, copy); goToPage(0) },
            onRenameLayout = onRenameLayout, onDeleteLayout = onDeleteLayout, onReorderLayouts = onReorderLayouts,
            onSearchRowClicked = onSearchRowClicked,
            onBackPressed = { goToPage(0) })
    }
    // The posture is read from the window the launcher actually occupies, not from the screen behind
    // it, so it is the first thing decided and the only thing the two branches below disagree about.
    // A phone window measures 411dp and takes the pager branch, which is the whole of the old screen
    // width test with nothing else changed about it.
    BoxWithConstraints {
    val posture = postureFor(maxWidth, maxHeight, rememberHinge(maxWidth, maxHeight, windowActivity))
    when (posture) {
    is WindowPosture.Expanded -> {
    val split = posture.split
    // The drawer needs a scrim in the light theme and has never needed one in the dark: the light
    // ink is #142C42 and a photograph can be any colour at all, while the dark ink is white and is
    // already legible on the wallpaper. That is why this is a branch on the theme and not a
    // constant, and it is why nothing about the dark drawer moves. See drawerScrimFor.
    val drawerPane = Modifier.background(if (state.tileSettings.darkTheme) Color.Transparent else drawerScrimFor(false, 1f).paint())
    if (split.stacked) {
        // A hinge that runs across the window, which is the tabletop posture. A left-right split
        // here would cut the divider straight along the seam, so the panes are stacked instead: the
        // one you are looking at on top, the one you reach for below it, and the seam between.
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(split.start).padding(bottom = PANE_GUTTER)) { start(false) }
            HorizontalDivider(color = glassColor(state), thickness = split.divider)
            Box(Modifier.fillMaxWidth().weight(1f).then(drawerPane)) { allApps() }
        }
    } else {
        Row(Modifier.fillMaxSize()) {
            // Start runs its tiles out to the pane edge on a phone, and it still does on the left
            // here, but the seam end is held back. The app list keeps 20dp of its own on both sides,
            // so without this the tiles would end up hard against the divider while the list stood
            // well off it - and the divider is exactly where the hardware gap is, which is not a
            // place to leave a tile's edge.
            Box(Modifier.fillMaxHeight().then(paneWidth(split, start = true)).padding(end = PANE_GUTTER)) { start(false) }
            // On a window with no fold this is the even split it has always been, unchanged. With a
            // fold it is the fold's own bounds: the pane on one side of the seam, the pane on the
            // other, and the divider occupying the seam itself rather than the middle of the window.
            // Neither side is weighted, because a weighted split would put the divider back at the
            // midpoint and cut through whichever pane the hinge is not in.
            VerticalDivider(color = glassColor(state), thickness = split.divider)
            Box(Modifier.fillMaxHeight().then(paneWidth(split, start = false)).then(drawerPane)) { allApps() }
        }
    }
    }
    WindowPosture.Compact ->
    HorizontalPager(
        state = pagerState,
        flingBehavior = PagerDefaults.flingBehavior(state = pagerState, pagerSnapDistance = PagerSnapDistance.atMost(2)),
        modifier = Modifier
            .fillMaxSize()
            .background(if (state.tileSettings.isTransparencyEnabled) scrim.paint() else Color.Transparent)
            .onSizeChanged { pagerWidth = it.width }
    ) {
        when (it) {
            // clipToBounds() sits outside the reveal on purpose: it is the one thing that keeps the
            // transformed pane inside its own page. See startReveal.
            0 -> Box(Modifier.fillMaxSize().clipToBounds().startReveal(pagerState, glass.motion, pagerWidth)) { start(true) }
            1 -> allApps()
        }
    }
    }
    }
    tgo1014.gridlauncher.ui.composables.sheets.SettingsBottomSheet(
        tileSettings = state.tileSettings, isShowing = state.isSettingsSheetShowing,
        onSettingsEvent = onSettingsEvent, apps = state.appList, onAddApp = onAddToGrid,
        onAddSpecial = onSpecialTile, currentProfile = state.profile, layouts = state.layouts, onCopyProfile = onCopyProfile)
}
}

} }

private fun glassColor(state: HomeState) = if (state.tileSettings.darkTheme) Color.White.copy(alpha = .12f) else Color.Black.copy(alpha = .12f)

/** What the expanded layout holds Start back from the divider, matched to the app list's own inset. */
private val PANE_GUTTER = 20.dp

/**
 * How wide one pane of an expanded window is.
 *
 * With no fold this is the `weight(0.5f)` the layout has always used, and it is kept as a weight on
 * purpose rather than computed: a weight divides whatever the row has left after the divider, so a
 * window with no hinge lands on exactly the same pixels it did before this was fold-aware, which is
 * the property worth protecting on a device that reports nothing.
 *
 * With a fold the pane is the hinge bound instead, because the seam is where the hardware is and the
 * pane has to end at it rather than somewhere near it.
 */
private fun RowScope.paneWidth(split: PaneSplit, start: Boolean): Modifier =
    if (split.hinge == null) Modifier.weight(0.5f) else Modifier.width(if (start) split.start else split.appList)

/** How far back Start sits, as a share of the screen, once the app list owns it. */
private const val REVEAL_SHIFT = 0.09f
/** How far it is scaled down on the way out, which is what gives the return its settle. */
private const val REVEAL_SCALE = 0.05f

/**
 * Windows Phone's way back to Start: the tile pane does not simply appear, it comes in from a
 * little to one side and slightly small and settles onto the screen as the app list pans away.
 *
 * The offset is read straight off the pager's own position, so the reveal is a pure function of
 * where the pager already is: there is no second animation to start, cancel or fight, nothing is
 * written back into the pager's state, and no frame allocates. Scrolling inside Start cannot start
 * it, because only the pager's page position feeds it, and at rest on Start the numbers land on an
 * exact identity so a launcher left sitting there draws no transform at all.
 *
 * With motion off the layer stays at its default identity and the pager is told to snap, so the
 * transition is one frame rather than a fast one.
 *
 * Two things about it are load bearing rather than taste, and both were wrong the first time.
 *
 * The shift is negative. Start moves the way the pager has just moved it, further back off the
 * left, which is what opens the gap the effect is for. Shifting it the other way pushed page 0
 * towards page 1, so the two panes never separated at all.
 *
 * And the layer is clipped to the page. Nothing in the pager clips: a page is a slot in a
 * LazyLayout, and the pager measures it, places it and moves it without ever issuing a clip, so a
 * translated page paints wherever the translation takes it, over the next page's slot. The
 * modifier order is the whole fix - `clipToBounds()` is outside `startReveal` because the clip
 * node is the outer one, and an outer clip is what bounds an inner transform. Swapping them would
 * move the clip along with the content and put the overlap straight back. The clip node is at the
 * page's own bounds, so the reveal is now a window that pans across Start and the wallpaper shows
 * through it, and it cannot reach a single pixel of the app list. Dropdown menus and sheets are
 * separate windows and the tile peek is a dialog, so nothing that deliberately leaves the page is
 * caught by it either.
 */
private fun Modifier.startReveal(pager: PagerState, motion: Boolean, pagerWidth: Int): Modifier =
    graphicsLayer {
        if (!motion) return@graphicsLayer
        // Zero on Start, one on the drawer, and whatever lies between while a page is in motion, so
        // the pane lags the drag and then catches up with it.
        val away = (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
        translationX = -REVEAL_SHIFT * pagerWidth * away
        val scale = 1f - REVEAL_SCALE * away
        scaleX = scale
        scaleY = scale
    }
