package tgo1014.gridlauncher.ui.composables

import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Process
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import tgo1014.gridlauncher.ui.MainActivity
import tgo1014.gridlauncher.ui.models.GridItem

/** Android app-published shortcuts. Layout editing belongs exclusively to Edit layout. */
@Composable
fun NativeTileActions(item: GridItem, expanded: Boolean, onDismiss: () -> Unit, onOpen: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current as? MainActivity
    val launcher = context.getSystemService(LauncherApps::class.java)
    val packageName = if (item.widgetId >= 0) android.appwidget.AppWidgetManager.getInstance(context).getAppWidgetInfo(item.widgetId)?.provider?.packageName else item.app.packageName
    val shortcuts = remember(expanded, packageName) {
        if (!expanded || packageName == null || packageName.startsWith("grid://")) emptyList() else runCatching {
            if (!launcher.hasShortcutHostPermission()) emptyList() else launcher.getShortcuts(LauncherApps.ShortcutQuery().setPackage(packageName)
                .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST), Process.myUserHandle()).orEmpty().take(5)
        }.getOrDefault(emptyList())
    }
    DropdownMenu(expanded, onDismiss) {
        shortcuts.forEach { shortcut -> DropdownMenuItem(text = { Text(shortcut.shortLabel?.toString().orEmpty()) }, onClick = {
            onDismiss(); runCatching { launcher.startShortcut(shortcut, null, null) }.onFailure { android.widget.Toast.makeText(context, "Shortcut unavailable", android.widget.Toast.LENGTH_SHORT).show() }
        }) }
        if (shortcuts.isNotEmpty()) HorizontalDivider()
        DropdownMenuItem(text = { Text("Open") }, onClick = { onDismiss(); onOpen() })
        if (packageName != null && !packageName.startsWith("grid://")) {
            DropdownMenuItem(text = { Text("App info") }, onClick = { onDismiss(); context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) })
            if (shortcuts.isEmpty() && !launcher.hasShortcutHostPermission()) DropdownMenuItem(text = { Text("Enable app shortcuts") }, onClick = { onDismiss(); activity?.chooseDefaultLauncher() })
        }
    }
}
