package tgo1014.gridlauncher.domain

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import tgo1014.gridlauncher.FakeAppsManager
import tgo1014.gridlauncher.domain.models.App
import tgo1014.gridlauncher.domain.usecases.AddToGridUseCase
import tgo1014.gridlauncher.ui.models.GridItem

class AddToGridUseCaseTest {
    @Test fun insertWhenGridIsEmpty() = runTest {
        val manager = FakeAppsManager(); AddToGridUseCase(manager)(App())
        assertEquals(1, manager.homeGridFlow.first().single().width)
    }
    @Test fun nextRowWhenWholeCellGridIsFull() = runTest {
        val manager = FakeAppsManager()
        manager.setGrid((0..2).map { GridItem(it, App(), 1, x = it) })
        AddToGridUseCase(manager)(App())
        assertEquals(1, manager.homeGridFlow.first().first { it.id == 3 }.y)
    }
    @Test fun fillsGapAndSupportsSixColumns() = runTest {
        val manager = FakeAppsManager()
        manager.setGrid((0..4).map { GridItem(it, App(), 1, x = it) })
        AddToGridUseCase(manager)(App(), 6)
        assertEquals(5, manager.homeGridFlow.first().first { it.id == 5 }.x)
    }
}
