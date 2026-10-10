package tgo1014.gridlauncher.live

/** One person's latest conversation, assembled from app-supplied notification person URIs. */
data class PersonRow(val contact: PinnedContact, val latest: TileNotification?)

object PeopleTiles {
    /**
     * Folds every visible conversation onto the people you chose. Matching uses the URIs the
     * messaging app supplied, so an app that does not identify its sender simply has no row.
     */
    fun hub(contacts: List<PinnedContact>, notifications: List<TileNotification>): List<PersonRow> = contacts.map { contact ->
        val conversation = notifications.filter { contact.matches(it) }
            .minByOrNull { -it.time }
        PersonRow(contact, conversation)
    }.sortedWith(compareByDescending<PersonRow> { it.latest?.time ?: 0L }.thenBy { it.contact.name.lowercase() })
}
