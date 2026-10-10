package tgo1014.gridlauncher.ui.home

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tgo1014.gridlauncher.ui.theme.LocalGlass

/** Profile apps are deliberately memory-only: no personal search index, usage log, or icon files. */
internal data class ProfileApps(
    val user: UserHandle,
    val private: Boolean,
    val locked: Boolean,
    val apps: List<LauncherActivityInfo> = emptyList(),
)

internal fun profileContentAllowed(quiet: Boolean, unlocked: Boolean, deviceLocked: Boolean) =
    !quiet && unlocked && !deviceLocked

@Composable
fun ProfileAppsButton() {
    var showing by remember { mutableStateOf(false) }
    IconButton(onClick = { showing = true }) {
        Icon(Icons.Default.AccountCircle, "Android profiles", tint = LocalGlass.current.ink)
    }
    if (showing) ProfileAppsDialog { showing = false }
}

@Composable
private fun ProfileAppsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val launcher = remember(context) { context.getSystemService(LauncherApps::class.java) }
    val users = remember(context) { context.getSystemService(UserManager::class.java) }
    val preferences = remember(context) { context.getSharedPreferences("profile-visibility", Context.MODE_PRIVATE) }
    var hidePrivate by remember { mutableStateOf(preferences.getBoolean("hide-private", false)) }
    var profiles by remember { mutableStateOf(emptyList<ProfileApps>()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var refreshJob by remember { mutableStateOf<Job?>(null) }
    var unavailable by remember { mutableStateOf(false) }

    fun error() { Toast.makeText(context, "Android could not complete this action. Check your home app and profile settings.", Toast.LENGTH_LONG).show() }
    fun accessible(user: UserHandle) = runCatching {
        profileContentAllowed(users.isQuietModeEnabled(user), users.isUserUnlocked(user),
            context.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
    }.getOrDefault(false)
    fun clear() { refreshJob?.cancel(); profiles = emptyList() }
    fun refresh() {
        // Clear synchronously before querying; a late response can never republish a locked profile.
        clear()
        loading = true
        refreshJob = scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                launcher.profiles.filter { it != Process.myUserHandle() }.mapNotNull { user ->
                    val info = launcher.getLauncherUserInfo(user) ?: return@mapNotNull null
                    val private = info.userType == UserManager.USER_TYPE_PROFILE_PRIVATE
                    val locked = !accessible(user)
                    ProfileApps(user, private, locked, if (locked || private && hidePrivate) emptyList()
                        else launcher.getActivityList(null, user).sortedBy { it.label.toString().lowercase() })
                }
            } }
            profiles = result.getOrDefault(emptyList()).map { profile ->
                if (!accessible(profile.user)) profile.copy(locked = true, apps = emptyList()) else profile
            }
            unavailable = result.isFailure
            loading = false
        }
    }
    DisposableEffect(lifecycle, launcher) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                query = ""
                if (intent?.action == Intent.ACTION_SCREEN_OFF) { clear(); onDismiss() } else refresh()
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
            addAction(Intent.ACTION_PROFILE_AVAILABLE); addAction(Intent.ACTION_PROFILE_UNAVAILABLE)
            addAction(Intent.ACTION_PROFILE_ADDED); addAction(Intent.ACTION_PROFILE_REMOVED)
            addAction(Intent.ACTION_PROFILE_ACCESSIBLE); addAction(Intent.ACTION_PROFILE_INACCESSIBLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE); addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_ADDED); addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNLOCKED); addAction(Intent.ACTION_SCREEN_OFF)
        }, ContextCompat.RECEIVER_EXPORTED)
        val callback = object : LauncherApps.Callback() {
            override fun onPackageAdded(p: String, u: UserHandle) = refresh()
            override fun onPackageRemoved(p: String, u: UserHandle) = refresh()
            override fun onPackageChanged(p: String, u: UserHandle) = refresh()
            override fun onPackagesAvailable(p: Array<out String>, u: UserHandle, replacing: Boolean) = refresh()
            override fun onPackagesUnavailable(p: Array<out String>, u: UserHandle, replacing: Boolean) = refresh()
        }
        launcher.registerCallback(callback)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> refresh()
                Lifecycle.Event.ON_STOP -> { clear(); query = "" }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        refresh()
        onDispose { clear(); lifecycle.removeObserver(observer); context.unregisterReceiver(receiver); launcher.unregisterCallback(callback) }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn)) {
        Surface(shape = RectangleShape) {
            Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).padding(20.dp)) {
                Text("Android profiles", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Light)
                Text("Apps stay in their own Android profile.", style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Show private space", modifier = Modifier.weight(1f))
                    Switch(checked = !hidePrivate, onCheckedChange = {
                        hidePrivate = !it; query = ""; preferences.edit().putBoolean("hide-private", hidePrivate).apply(); refresh()
                    })
                }
                OutlinedTextField(query, { query = it }, singleLine = true, shape = RectangleShape,
                    label = { Text("Search unlocked profiles") }, modifier = Modifier.fillMaxWidth())
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(Modifier.weight(1f, fill = false), contentPadding = PaddingValues(vertical = 12.dp)) {
                    if (!loading && profiles.isEmpty()) item {
                        Text(if (unavailable) "Profiles are unavailable. Set GridLauncher as your default home app and try again."
                            else "No additional profiles. Add a work profile through your organization, or set up private space in Android settings.")
                    }
                    profiles.filterNot { it.private && hidePrivate }.forEach { profile ->
                        item(key = "profile:${profile.user}") {
                            Column {
                                Text(if (profile.private) "Private space" else "Work / other profile",
                                    fontSize = 24.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 12.dp))
                                TextButton(onClick = {
                                    clear(); query = ""
                                    scope.launch {
                                        runCatching { withContext(Dispatchers.IO) {
                                            users.requestQuietModeEnabled(!profile.locked, profile.user)
                                        } }.onFailure { error() }
                                        refresh()
                                    }
                                }) { Text(if (profile.private) { if (profile.locked) "Unlock" else "Lock" }
                                    else { if (profile.locked) "Turn on" else "Pause" }) }
                                if (profile.locked) Text(if (profile.private) "Locked · apps are hidden" else "Paused or locked · apps are hidden")
                            }
                        }
                        val matches = profile.apps.filter { it.label.toString().contains(query.trim(), ignoreCase = true) }
                        if (!profile.locked && matches.isEmpty()) item { Text("No apps found") }
                        items(matches, key = { "${profile.user}:${it.componentName}" }) { app ->
                            Row(Modifier.fillMaxWidth().clickable {
                                if (!accessible(profile.user)) { refresh(); return@clickable }
                                runCatching { launcher.startMainActivity(app.componentName, profile.user, null, null) }.onFailure { error() }
                            }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                val icon by produceState<android.graphics.drawable.Drawable?>(null, app) {
                                    value = withContext(Dispatchers.IO) { runCatching { app.getBadgedIcon(context.resources.displayMetrics.densityDpi) }.getOrNull() }
                                }
                                AsyncImage(coil3.request.ImageRequest.Builder(context).data(icon)
                                    .memoryCachePolicy(coil3.request.CachePolicy.DISABLED)
                                    .diskCachePolicy(coil3.request.CachePolicy.DISABLED).build(), null, modifier = Modifier.size(40.dp))
                                Text(app.label.toString(), fontSize = 20.sp, fontWeight = FontWeight.Light,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 12.dp))
                                TextButton(onClick = {
                                    if (accessible(profile.user)) runCatching { launcher.startAppDetailsActivity(app.componentName, profile.user, null, null) }.onFailure { error() }
                                    else refresh()
                                }) { Text("Info") }
                            }
                        }
                    }
                }
                TextButton(onClick = {
                    runCatching {
                        val sender = launcher.privateSpaceSettingsIntent ?: error("No private space settings")
                        context.startIntentSender(sender, null, 0, 0, 0)
                    }.onFailure { error() }
                }) { Text("Private space settings") }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Done") }
            }
        }
    }
}
