package tgo1014.gridlauncher.ui.home

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.widget.Toast

/** No query leaves the launcher until the user explicitly chooses the web action. */
internal object DrawerSearch {
    fun webIntent(query: String) = Intent(Intent.ACTION_WEB_SEARCH)
        .putExtra(SearchManager.QUERY, query.trim())

    fun browserIntent(query: String) = Intent(Intent.ACTION_VIEW,
        Uri.Builder().scheme("https").authority("www.google.com").path("search")
            .appendQueryParameter("q", query.trim()).build())

    fun voiceIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_PROMPT, "Search apps")
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

    fun openWeb(context: Context, query: String) {
        if (query.isBlank()) return
        val opened = runCatching { context.startActivity(webIntent(query)) }.isSuccess ||
            runCatching { context.startActivity(browserIntent(query)) }.isSuccess
        if (!opened) Toast.makeText(context, "No web search app or browser is available.", Toast.LENGTH_LONG).show()
    }
}
