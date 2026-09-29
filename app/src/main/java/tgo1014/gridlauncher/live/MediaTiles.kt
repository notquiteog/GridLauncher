package tgo1014.gridlauncher.live

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/** What is playing right now, read from the system's own media session. No mock data. */
data class NowPlaying(
    val packageName: String, val title: String, val artist: String, val album: String,
    val playing: Boolean, val artwork: Bitmap?, val hasNext: Boolean, val canGoPrevious: Boolean,
) {
    val label: String get() = when {
        title.isBlank() && artist.isBlank() -> "Nothing playing"
        album.isBlank() || album == title -> listOf(title, artist).filter { it.isNotBlank() }.joinToString(" · ")
        else -> listOf(artist, album).filter { it.isNotBlank() }.joinToString(" · ")
    }
}

object MediaTiles {
    private val _now = MutableStateFlow<NowPlaying?>(null)
    val now = _now.asStateFlow()
    private val main = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null
    private var boundToken: android.media.session.MediaSession.Token? = null
    private val registered = AtomicBoolean(false)

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() { unbind(); appContext?.let(::refresh) }
    }

    private fun unbind() {
        runCatching { controller?.unregisterCallback(callback) }
        controller = null
        boundToken = null
        registered.set(false)
        _now.value = null
    }

    /** Sessions are visible through our own notification listener, so notification access is required. */
    private fun activeSession(context: Context) = runCatching {
        context.getSystemService(MediaSessionManager::class.java)
            .getActiveSessions(ComponentName(context, LiveNotificationService::class.java))
            .filter { session -> session.playbackState != null || session.metadata != null }
            // Spotify, YouTube Music and a paused podcast can all hold a session at once, and the
            // order the platform returns them in is not promised. The one actually playing wins, and
            // the rest are ordered by name so the card does not hop between them on every refresh.
            .sortedWith(compareByDescending<MediaController> { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                .thenBy { it.packageName })
            .firstOrNull()
    }.getOrNull()

    fun refresh(context: Context) {
        if (appContext == null) appContext = context.applicationContext
        val session = activeSession(context) ?: run { if (boundToken != null) unbind(); return }
        if (session.sessionToken == boundToken) return
        unbind()
        runCatching {
            controller = MediaController(context, session.sessionToken).also { it.registerCallback(callback, main) }
            boundToken = session.sessionToken
        }.onFailure { unbind() }
        publish()
    }

    private fun publish() {
        val current = controller ?: return
        val metadata = current.metadata
        val state = current.playbackState
        _now.value = runCatching { NowPlaying(
            packageName = current.packageName,
            title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty().take(120),
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE).orEmpty().take(120),
            album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty().take(120),
            playing = state?.state == PlaybackState.STATE_PLAYING,
            artwork = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON),
            hasNext = state?.let { it.actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L } == true,
            canGoPrevious = state?.let { it.actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L } == true,
        ) }.getOrNull()
    }

    fun togglePlayPause() = runCatching { controller?.transportControls?.let { if (_now.value?.playing == true) it.pause() else it.play() } }
    fun next() = runCatching { controller?.transportControls?.skipToNext() }
    fun previous() = runCatching { controller?.transportControls?.skipToPrevious() }
    private var appContext: Context? = null
}
