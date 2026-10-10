package tgo1014.gridlauncher.domain.usecases

import tgo1014.gridlauncher.domain.AppsManager
import javax.inject.Inject

class UpdateAppListUseCase @Inject constructor(private val appsManager: AppsManager) {
    suspend operator fun invoke() { appsManager.updateAppsList() }
}
