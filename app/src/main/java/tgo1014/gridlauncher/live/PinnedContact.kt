package tgo1014.gridlauncher.live

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract as Contacts
import android.provider.ContactsPickerSessionContract as Picker
import kotlinx.serialization.Serializable

@Serializable
data class PinnedContact(val key: String, val name: String, val phones: List<String> = emptyList(), val emails: List<String> = emptyList(), val photo: String? = null) {
    fun matches(notification: TileNotification): Boolean = notification.people.any { person ->
        person == key || phones.any { person == "tel:$it" } || emails.any { person == "mailto:$it" }
    }
}

object ContactTiles {
    fun picker(): Intent = Intent(Picker.ACTION_PICK_CONTACTS)
        .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true).putExtra(Picker.EXTRA_PICK_CONTACTS_SELECTION_LIMIT, 12)
        .putStringArrayListExtra(Picker.EXTRA_PICK_CONTACTS_REQUESTED_DATA_FIELDS, arrayListOf(Contacts.CommonDataKinds.Phone.CONTENT_ITEM_TYPE, Contacts.CommonDataKinds.Email.CONTENT_ITEM_TYPE))

    /** Copy only explicitly shared fields: picker session access expires with the process. */
    fun read(context: Context, uri: Uri): List<PinnedContact> {
        val result = linkedMapOf<String, PinnedContact>()
        val fields = arrayOf(Contacts.Contacts.LOOKUP_KEY, Contacts.Contacts.DISPLAY_NAME_PRIMARY, Contacts.Data.MIMETYPE, Contacts.Data.DATA1)
        context.contentResolver.query(uri, fields, null, null, null)?.use { c ->
            while (c.moveToNext() && result.size < 12) {
                val key = c.getString(0).orEmpty(); val name = c.getString(1).orEmpty().take(100)
                val mime = c.getString(2)
                val value = c.getString(3).orEmpty().take(200)
                val old = result[key] ?: PinnedContact(key, name)
                result[key] = when (mime) {
                    Contacts.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> old.copy(phones = (old.phones + value).distinct())
                    Contacts.CommonDataKinds.Email.CONTENT_ITEM_TYPE -> old.copy(emails = (old.emails + value).distinct())
                    else -> old
                }
            }
        }
        return result.values.toList()
    }

    /** Starred contacts, with the numbers and addresses a notification's person URI would carry, so tiles can match them. */
    fun favorites(context: Context): List<PinnedContact> {
        if (!BuiltInTiles.granted(context, android.Manifest.permission.READ_CONTACTS)) return emptyList()
        return runCatching {
            val starred = buildList {
                context.contentResolver.query(Contacts.Contacts.CONTENT_URI, arrayOf(Contacts.Contacts._ID, Contacts.Contacts.LOOKUP_KEY, Contacts.Contacts.DISPLAY_NAME_PRIMARY, Contacts.Contacts.PHOTO_THUMBNAIL_URI), "${Contacts.Contacts.STARRED} = 1", null, "${Contacts.Contacts.DISPLAY_NAME_PRIMARY} ASC")?.use { c ->
                    while (c.moveToNext() && size < 12) add(Triple(c.getLong(0), PinnedContact(c.getString(1).orEmpty(), c.getString(2).orEmpty().take(100)), c.getString(3)))
                }
            }
            if (starred.isEmpty()) return@runCatching emptyList()
            val phones = mutableMapOf<Long, MutableList<String>>()
            val emails = mutableMapOf<Long, MutableList<String>>()
            fun read(uri: android.net.Uri, column: String, into: MutableMap<Long, MutableList<String>>) = runCatching {
                context.contentResolver.query(uri, arrayOf(Contacts.Data.CONTACT_ID, column),
                    "${Contacts.Data.CONTACT_ID} IN (${starred.joinToString(",") { it.first.toString() }})", null, null)?.use { c ->
                    while (c.moveToNext()) into.getOrPut(c.getLong(0)) { mutableListOf() } += c.getString(1).orEmpty().take(200)
                }
            }
            read(Contacts.CommonDataKinds.Phone.CONTENT_URI, Contacts.CommonDataKinds.Phone.NUMBER, phones)
            read(Contacts.CommonDataKinds.Email.CONTENT_URI, Contacts.CommonDataKinds.Email.ADDRESS, emails)
            starred.map { (id, contact, photo) -> contact.copy(photo = photo, phones = phones[id].orEmpty().distinct(), emails = emails[id].orEmpty().distinct()) }
        }.getOrDefault(emptyList())
    }
}
