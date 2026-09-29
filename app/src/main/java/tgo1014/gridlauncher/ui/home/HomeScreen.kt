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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.util.lerp
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
        onHandoffFocusHandled = viewModel::onHandoffFocusHandled,
        onPinToHotseat = { viewModel.pinToHotseat(it) },
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
    onHandoffFocusHandled: () -> Unit = {},
    onPinToHotseat: (String) -> Unit = {},
    onSearchRowClicked: (SearchRow) -> Unit = {},
    frequent: List<String> = emptyList(),
    onEditLayout: (Boolean) -> Unit = {},
) {
    val glass = rememberGlass(state.tileSettings)
    val accent = glass.accent
    CompositionLocalProvider(LocalGlass provides glass, androidx.compose.material3.LocalContentColor provides glass.ink) {
    androidx.compose.material3.MaterialTheme(
    colorScheme = if (state.tileSettings.darkTheme) androidx.compose.material3.darkColorScheme(
        primary = accent, onPrimary = Color.White, primaryContainer = accent, onPrimaryContainer = Color.White,
        background = Color.Black, surface = Color(0xFF141414))
    else androidx.compose.material3.lightColorScheme(primary = accent, onPrimary = Color.White, primaryContainer = accent, onPrimaryContainer = Color.White)
) { Box(Modifier.background(androidx.compose.material3.MaterialTheme.colorScheme.background)) {
    // Continuum: a desktop window or large screen gets Start and All apps side by side, the way
    // Continuum gave a phone a desktop-sized shell. Touch layouts keep the single-column pager.
    val continuum = LocalConfiguration.current.screenWidthDp >= CONTINUUM_WIDTH_DP
    val pagerState = rememberPagerState(initialPage = 0) { 2 }
    val scope = rememberCoroutineScope()
    var pagerWidth by remember { mutableIntStateOf(1) }
    val scrollOffset by remember(pagerWidth) {
        derivedStateOf { (pagerState.currentPage + pagerState.currentPageOffsetFraction) * pagerWidth }
    }
    val alpha = lerp(
        start = 0f,
        stop = 0.7f,
        fraction = (scrollOffset / pagerWidth.toFloat()).coerceIn(0f, 1f)
    )
    LaunchedIfTrueEffect(state.goToHome) {
        pagerState.scrollToPage(0)
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
            onOpenApp = onAppClicked,
            onFooterClicked = { scope.launch { pagerState.animateScrollToPage(1) } })
    }
    val allApps: @Composable () -> Unit = {
        AppListScreen(state = state, hazeState = hazeState, onAppClicked = onAppClicked, onAddToGrid = onAddToGrid, onPinToHotseat = onPinToHotseat,
            onFilterTextChanged = onFilterTextChanged, onFilterClearPressed = onFilterClearPressed,
            onUninstall = onUninstall, onSettingsEvent = onSettingsEvent,
            onEditLayout = { editing -> onEditLayout(editing); scope.launch { pagerState.animateScrollToPage(0) } },
            onProfile = { name -> onProfile(name); scope.launch { pagerState.animateScrollToPage(0) } },
            onCreateLayout = { name, copy -> onCreateLayout(name, copy); scope.launch { pagerState.animateScrollToPage(0) } },
            onRenameLayout = onRenameLayout, onDeleteLayout = onDeleteLayout,
            onSearchRowClicked = onSearchRowClicked,
            onBackPressed = { scope.launch { pagerState.animateScrollToPage(0) } })
    }
    if (continuum) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxHeight().weight(0.46f)) { start(false) }
            VerticalDivider(color = glassColor(state))
            Box(Modifier.fillMaxHeight().weight(0.54f)) { allApps() }
        }
    } else {
    HorizontalPager(
        state = pagerState,
        flingBehavior = PagerDefaults.flingBehavior(state = pagerState, pagerSnapDistance = PagerSnapDistance.atMost(2)),
        modifier = Modifier
            .fillMaxSize()
            .background(if (state.tileSettings.isTransparencyEnabled) Color.Black.copy(alpha) else Color.Transparent)
            .onSizeChanged { pagerWidth = it.width }
    ) {
        when (it) {
            0 -> start(true)
            1 -> allApps()
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

private const val CONTINUUM_WIDTH_DP = 840
private fun glassColor(state: HomeState) = if (state.tileSettings.darkTheme) Color.White.copy(alpha = .12f) else Color.Black.copy(alpha = .12f)
