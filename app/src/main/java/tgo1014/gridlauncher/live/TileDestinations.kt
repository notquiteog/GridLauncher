package tgo1014.gridlauncher.live

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Process
import android.widget.Toast
import tgo1014.gridlauncher.ui.models.GridItem

fun openDestination(context: Context, item: GridItem): Boolean {
    if (item.shortcutId == null && item.destination == null) return false
    runCatching {
        if (item.shortcutId != null) context.getSystemService(LauncherApps::class.java).startShortcut(item.app.packageName, item.shortcutId, null, null, Process.myUserHandle())
        else {
            val uri = Uri.parse(item.destination)
            require(uri.scheme in listOf("https", "http", "content", "geo"))
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }
    }.onFailure { Toast.makeText(context, "Destination unavailable. It may need to be pinned again.", Toast.LENGTH_LONG).show() }
    return true
}
