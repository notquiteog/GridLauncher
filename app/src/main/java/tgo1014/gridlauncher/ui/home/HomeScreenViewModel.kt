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
    private val profiles: tgo1014.gridlauncher.data.LayoutProfiles,
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
    val stateFlow = combine(_stateFlow, settingsRepository.tileSettingsFlow, profiles.active) { state, settings, profile ->
        state.copy(tileSettings = settings, profile = profile)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    init {
        init()
        viewModelScope.launch {
            while (true) {
                val settings = settingsRepository.tileSettingsFlow.first()
                if (settings.workSchedule) profiles.select(tgo1014.gridlauncher.data.scheduledProfile(java.time.LocalDateTime.now(), settings.workStartHour, settings.workEndHour))
                delay(60_000)
            }
        }
    }

    fun selectProfile(name: String) = viewModelScope.launch {
        val settings = settingsRepository.tileSettingsFlow.first()
        if (settings.workSchedule) settingsRepository.updateSettings(settings.copy(workSchedule = false))
        profiles.select(name)
        resetState()
    }
    fun copyProfile(name: String) = viewModelScope.launch { profiles.duplicateInto(name) }

    fun setEditingLayout(editing: Boolean) { _stateFlow.update { it.copy(isEditingLayout = editing, itemBeingEdited = null) } }

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
        if (_stateFlow.value.isEditingLayout) _stateFlow.update { it.copy(itemBeingEdited = gridItem) }
        else if (!tgo1014.gridlauncher.live.openDestination(context, gridItem)) onOpenApp(gridItem.app)
    }

    fun onTileDropped(item: GridItem, dx: Int, dy: Int) = viewModelScope.launch {
        val grid = appsManager.homeGridFlow.first()
        val current = grid.firstOrNull { it.id == item.id } ?: return@launch
        appsManager.setGrid(tgo1014.gridlauncher.domain.GridPlacement.update(grid, current.copy(x = current.x + dx, y = current.y + dy), settingsRepository.tileSettingsFlow.first().gridColumns))
    }

    fun onGridItemLongClicked(gridItem: GridItem) {
        _stateFlow.update { it.copy(itemBeingEdited = gridItem) }
    }


    fun onSwitchedToHome() {
        _stateFlow.update { it.copy(goToHome = false) }
    }

    fun onAddToGrid(app: App) = viewModelScope.launch {
        addToGridUseCase(app, settingsRepository.tileSettingsFlow.first().gridColumns)
        _stateFlow.update { it.copy(goToHome = true) }
    }

    fun addSpecialTile(item: GridItem) = viewModelScope.launch {
        val grid = appsManager.homeGridFlow.first()
        val newItem = item.copy(id = (grid.maxOfOrNull { it.id } ?: -1) + 1)
        appsManager.setGrid(grid + tgo1014.gridlauncher.domain.GridPlacement.place(newItem, grid, settingsRepository.tileSettingsFlow.first().gridColumns))
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

            is SettingsEvent.OnSettingsUpdated -> runCatching { settingsRepository.updateSettings(event.tileSettings) }.onFailure { android.widget.Toast.makeText(context, it.message ?: "Cannot update layout", android.widget.Toast.LENGTH_LONG).show() }.let { }
            is SettingsEvent.OnWallpaperPicked -> storeWallpaperPickedUseCase(event.uri)
            SettingsEvent.OnWallpaperRemoved -> onRemoveWallpaperUseCase()
        }
    }

    fun onTileEvent(event: TileEvent) = viewModelScope.launch {
        val item = _stateFlow.value.itemBeingEdited ?: return@launch
        when (event) {
            is TileEvent.OnCellSize -> {
                val grid = appsManager.homeGridFlow.first()
                appsManager.setGrid(tgo1014.gridlauncher.domain.GridPlacement.update(grid, item.copy(width = event.width, height = event.height), settingsRepository.tileSettingsFlow.first().gridColumns))
            }
            TileEvent.OnTogglePositionPin -> {
                val grid = appsManager.homeGridFlow.first()
                appsManager.setGrid(grid.map { if (it.id == item.id) it.copy(positionPinned = !it.positionPinned) else it })
            }
            is TileEvent.OnTileMoved -> moveGridItemUseCase(item.id, event.direction, settingsRepository.tileSettingsFlow.first().gridColumns)
            is TileEvent.OnSizeChange -> itemGridSizeChangeUseCase(item.id, event.tileSize, settingsRepository.tileSettingsFlow.first().gridColumns)
            TileEvent.OnTileSettingsSheetDismissed -> _stateFlow.update { it.copy(itemBeingEdited = null) }
            TileEvent.OnRemoveClicked -> removeFromGridUseCase(item, settingsRepository.tileSettingsFlow.first().gridColumns).onSuccess {
                if (item.widgetId >= 0 && !profiles.widgetInUse(item.widgetId)) android.appwidget.AppWidgetHost(context, 1701).deleteAppWidgetId(item.widgetId)
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
        _stateFlow.update { it.copy(itemBeingEdited = null, isEditingLayout = false) }
        onFilterCleared()
    }

}