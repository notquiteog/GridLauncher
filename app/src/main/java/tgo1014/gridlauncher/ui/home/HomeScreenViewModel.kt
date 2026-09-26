package tgo1014.gridlauncher.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tgo1014.gridlauncher.data.withoutAccents
import tgo1014.gridlauncher.domain.AppsManager
import tgo1014.gridlauncher.domain.SettingsRepository
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.domain.usecases.AddToGridUseCase
import tgo1014.gridlauncher.domain.usecases.GetAppListUseCase
import tgo1014.gridlauncher.domain.usecases.ItemGridSizeChangeUseCase
import tgo1014.gridlauncher.domain.usecases.MoveGridItemUseCase
import tgo1014.gridlauncher.domain.usecases.RemoveFromGridUseCase
import tgo1014.gridlauncher.domain.usecases.wallpaper.OnSystemThemeChangedUseCase
import tgo1014.gridlauncher.domain.usecases.wallpaper.RemoveWallpaperUseCase
import tgo1014.gridlauncher.domain.usecases.wallpaper.StoreWallpaperPickedUseCase
import tgo1014.gridlauncher.domain.usecases.wallpaper.UpdateWallpaperBasedOnThemeUseCase
import tgo1014.gridlauncher.ui.models.GridItem
import tgo1014.gridlauncher.ui.models.SettingsEvent
import tgo1014.gridlauncher.ui.models.TileEvent
import javax.inject.Inject
import kotlinx.coroutines.flow.first

@HiltViewModel
class HomeScreenViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val getAppListUseCase: GetAppListUseCase,
    private val addToGridUseCase: AddToGridUseCase,
    private val moveGridItemUseCase: MoveGridItemUseCase,
    private val removeFromGridUseCase: RemoveFromGridUseCase,
    private val itemGridSizeChangeUseCase: ItemGridSizeChangeUseCase,
    private val appsManager: AppsManager,
    private val settingsRepository: SettingsRepository,
    private val storeWallpaperPickedUseCase: StoreWallpaperPickedUseCase,
    private val onRemoveWallpaperUseCase: RemoveWallpaperUseCase,
    private val onSystemThemeChangedUseCase: OnSystemThemeChangedUseCase,
    private val updateWallpaperBasedOnThemeUseCase: UpdateWallpaperBasedOnThemeUseCase,
) : ViewModel() {

    private var fullAppList: List<App> = emptyList()

    private val _stateFlow = MutableStateFlow(HomeState())
    val stateFlow = combine(_stateFlow, settingsRepository.tileSettingsFlow) { state, settings ->
        state.copy(tileSettings = settings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    init {
        init()
    }

    fun onGoToHome() {
        resetState()
        _stateFlow.update { it.copy(goToHome = true) }
    }

    fun onOpenApp(app: App) {
        viewModelScope.launch {
            delay(200)
            _stateFlow.update { it.copy(goToHome = true, closeSearchFab = true) }
            resetState()
        }
        viewModelScope.launch { appsManager.openApp(app) }
    }

    fun onGridItemClicked(gridItem: GridItem) = viewModelScope.launch {
        when {
            !_stateFlow.value.isEditMode -> onOpenApp(gridItem.app)
            else -> _stateFlow.update { it.copy(itemBeingEdited = gridItem) }
        }
    }

    fun onTileDropped(item: GridItem, dx: Int, dy: Int) = viewModelScope.launch {
        val grid = appsManager.homeGridFlow.first()
        val current = grid.firstOrNull { it.id == item.id } ?: return@launch
        appsManager.setGrid(tgo1014.gridlauncher.domain.GridPlacement.update(grid, current.copy(x = current.x + dx, y = current.y + dy)))
    }

    fun onGridItemLongClicked(gridItem: GridItem) {
        _stateFlow.update { it.copy(itemBeingEdited = gridItem) }
    }


    fun onSwitchedToHome() {
        _stateFlow.update { it.copy(goToHome = false) }
    }

    fun onAddToGrid(app: App) = viewModelScope.launch {
        addToGridUseCase(app)
        _stateFlow.update { it.copy(goToHome = true) }
    }

    fun addSpecialTile(item: GridItem) = viewModelScope.launch {
        val grid = appsManager.homeGridFlow.first()
        val newItem = item.copy(id = (grid.maxOfOrNull { it.id } ?: -1) + 1)
        appsManager.setGrid(grid + tgo1014.gridlauncher.domain.GridPlacement.place(newItem, grid))
    }


    fun uninstallApp(app: App) {
        appsManager.uninstallApp(app)
    }

    fun onFilterTextChanged(filter: String) {
        if (filter.isBlank()) {
            onFilterCleared()
            return
        }
        val appList = fullAppList.filter {
            it.name.withoutAccents.contains(filter.withoutAccents.trim(), true)
        }
        _stateFlow.update { it.copy(filterString = filter, appList = appList) }
    }

    fun onFilterCleared() {
        _stateFlow.update { it.copy(filterString = "", appList = fullAppList) }
    }

    fun onFabClosed() {
        _stateFlow.update { it.copy(closeSearchFab = false) }
    }

    fun onSettingsEvent(event: SettingsEvent) = viewModelScope.launch {
        when (event) {
            SettingsEvent.OnSettingsIconClicked -> _stateFlow.update {
                it.copy(
                    isSettingsSheetShowing = true
                )
            }

            SettingsEvent.OnSettingsSheetDismissed -> _stateFlow.update {
                it.copy(
                    isSettingsSheetShowing = false
                )
            }

            is SettingsEvent.OnSettingsUpdated -> settingsRepository.updateSettings(event.tileSettings)
            is SettingsEvent.OnWallpaperPicked -> storeWallpaperPickedUseCase(event.uri)
            SettingsEvent.OnWallpaperRemoved -> onRemoveWallpaperUseCase()
        }
    }

    fun onTileEvent(event: TileEvent) = viewModelScope.launch {
        val item = _stateFlow.value.itemBeingEdited ?: return@launch
        when (event) {
            is TileEvent.OnTileMoved -> moveGridItemUseCase(item.id, event.direction)
            is TileEvent.OnSizeChange -> itemGridSizeChangeUseCase(item.id, event.tileSize)
            TileEvent.OnTileSettingsSheetDismissed -> _stateFlow.update { it.copy(itemBeingEdited = null) }
            TileEvent.OnRemoveClicked -> removeFromGridUseCase(item).onSuccess {
                if (item.widgetId >= 0) android.appwidget.AppWidgetHost(context, 1701).deleteAppWidgetId(item.widgetId)
                _stateFlow.update { it.copy(itemBeingEdited = null) }
            }
        }
    }

    private fun init() = viewModelScope.launch {
        observeSystemTheme()
        getAppListUseCase()
            .onEach { appList ->
                fullAppList = appList
                _stateFlow.update { it.copy(appList = appList) }
            }
            .launchIn(this)
        appsManager.homeGridFlow
            .onEach { grid -> _stateFlow.update { state -> state.copy(grid = grid, itemBeingEdited = grid.firstOrNull { it.id == state.itemBeingEdited?.id }) } }
            .launchIn(this)
    }

    private fun observeSystemTheme() {
        onSystemThemeChangedUseCase()
            .onEach { updateWallpaperBasedOnThemeUseCase() }
            .launchIn(viewModelScope)
    }

    private fun resetState() {
        _stateFlow.update { it.copy(itemBeingEdited = null) }
        onFilterCleared()
    }

}