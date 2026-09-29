package tgo1014.gridlauncher.domain.models

import kotlinx.serialization.Serializable
import java.io.File

/**
 * A cached copy of the app's own, unmodified default icon plus the opaque colour of its outer
 * edge, which becomes the flat tile colour. `bgFilePath` is kept only so older stored layouts
 * still decode; no replacement background has been shipped since the first release.
 */
@Serializable
data class Icon(
    val iconFilePath: String? = null,
    val bgFilePath: String? = null,
    val edgeColor: Long? = null,
) {
    val iconFile: File? get() = iconFilePath?.let { File(it) }
}
