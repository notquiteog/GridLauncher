package tgo1014.gridlauncher.ui.home

import tgo1014.gridlauncher.domain.models.App

/** Usage is already ranked most-used first. Unseen apps follow it alphabetically. */
internal fun orderApps(apps: List<App>, mode: String, ranked: List<String>): List<App> {
    val rank = ranked.withIndex().associate { it.value to it.index }
    return if (mode == "frequent" || mode == "recent")
        apps.sortedWith(compareBy<App> { rank[it.packageName] ?: Int.MAX_VALUE }.thenBy { it.name.lowercase() })
    else apps.sortedBy { it.name.lowercase() }
}

internal fun suggestedApps(apps: List<App>, ranked: List<String>, excluded: Set<String>, mode: String): List<App> {
    val installed = apps.associateBy { it.packageName }
    val selected = ranked.distinct().filterNot { it in excluded }.mapNotNull(installed::get).take(8)
    return if (mode == "alphabetical") selected.sortedBy { it.name.lowercase() } else selected
}
