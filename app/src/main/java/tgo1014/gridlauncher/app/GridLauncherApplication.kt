package tgo1014.gridlauncher.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GridLauncherApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // The drawer indexes here rather than per keystroke; this is the earliest honest moment.
        tgo1014.gridlauncher.live.StartSearchIndex.start(this)
    }
}