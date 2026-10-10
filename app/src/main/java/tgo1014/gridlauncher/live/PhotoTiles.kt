package tgo1014.gridlauncher.live

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore

/** One recent photo, read from the user's own library. Nothing is uploaded or copied. */
data class RecentPhoto(val id: Long, val uri: String, val takenAt: Long)

object PhotoTiles {
    const val maxRecent = 40

    /**
     * Recent images the user has already shared with the system picker or granted access to.
     * Requires READ_MEDIA_IMAGES (Android 13+) or READ_EXTERNAL_STORAGE; denial is not an error.
     */
    fun recent(context: Context, limit: Int = maxRecent): List<RecentPhoto> {
        if (!permission(context)) return emptyList()
        return runCatching {
            val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_MODIFIED, MediaStore.Images.Media.DATE_TAKEN)
            val selection = "${MediaStore.Images.Media.SIZE} > 0"
            context.contentResolver.query(collection, projection, selection, null, "${MediaStore.Images.Media.DATE_MODIFIED} DESC")?.use { cursor ->
                buildList {
                    val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val modifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                    val takenIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                    while (cursor.moveToNext() && size < limit) {
                        val id = cursor.getLong(idIndex)
                        val taken = cursor.getLong(takenIndex).takeIf { it > 0 } ?: cursor.getLong(modifiedIndex) * 1000
                        add(RecentPhoto(id, ContentUris.withAppendedId(collection, id).toString(), taken))
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    /** Either the full grant or Android 14's "selected photos" grant counts as access. */
    fun permission(context: Context): Boolean =
        BuiltInTiles.granted(context, android.Manifest.permission.READ_MEDIA_IMAGES) ||
            BuiltInTiles.granted(context, android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)

    fun permissionName(): String = android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
}
