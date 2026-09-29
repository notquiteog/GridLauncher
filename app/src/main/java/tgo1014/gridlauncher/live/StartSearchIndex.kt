package tgo1014.gridlauncher.live

import android.Manifest
import android.app.appsearch.AppSearchBatchResult
import android.app.appsearch.AppSearchManager
import android.app.appsearch.AppSearchResult
import android.app.appsearch.AppSearchSchema
import android.app.appsearch.AppSearchSchema.PropertyConfig
import android.app.appsearch.AppSearchSession
import android.app.appsearch.BatchResultCallback
import android.app.appsearch.GenericDocument
import android.app.appsearch.PutDocumentsRequest
import android.app.appsearch.RemoveByDocumentIdRequest
import android.app.appsearch.SearchSpec
import android.app.appsearch.SetSchemaRequest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.widget.Toast
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import tgo1014.gridlauncher.domain.models.TileSettings
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The drawer's search, answered by `android.app.appsearch`.
 *
 * One private session, one schema, and a small snapshot of what the launcher is already allowed
 * to read. AppSearch is a system service, so whether an ordinary sideloaded build gets a working
 * private index is not something the app can assume: every entry point here is wrapped, a failure
 * just leaves the in-memory rows to answer instead, and the drawer is written against [SearchRow]
 * alone so it cannot tell the difference.
 */
object StartSearchIndex {
    const val DATABASE = "gridlauncher-start"
    const val NAMESPACE = "start-items"
    const val SCHEMA = "StartItem"

    private const val SCHEMA_VERSION = 1
    private const val PROPERTY_TITLE = "title"
    private const val PROPERTY_TEXT = "text"
    private const val PROPERTY_SUBTITLE = "subtitle"
    private const val PROPERTY_SOURCE = "source"
    private const val PROPERTY_STAMP = "stamp"
    private const val PROPERTY_REF = "ref"

    /** Long enough for a burst of notifications to settle, short enough to feel current. */
    private const val DEBOUNCE_MS = 3_000L
    private const val SWEEP_MS = 15 * 60_000L
    private const val RESULTS_PER_PAGE = 20

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val direct = Executor { it.run() }
    private val work = Mutex()

    private val _rows = MutableStateFlow<List<SearchRow>>(emptyList())

    /** Everything the launcher may search over, whether or not the index is there. */
    val rows = _rows.asStateFlow()

    @Volatile private var session: AppSearchSession? = null
    @Volatile private var pending = true
    @Volatile private var started = false
    @Volatile private var fingerprint: Int? = null
    @Volatile private var indexedIds: Set<String> = emptySet()
    @Volatile private var cleared = false

    /** Call from the application and from the launcher's resume. Idempotent. */
    @Synchronized fun start(context: Context) {
        pending = true
        if (started) return
        started = true
        val app = context.applicationContext
        openSession(app)
        scope.launch {
            while (true) {
                // Idle: look back in a quarter of an hour. Told to work: debounce first, so a
                // burst of notification ticks costs one re-index rather than one each.
                if (!pending) {
                    delay(SWEEP_MS)
                    if (!pending) continue
                }
                delay(DEBOUNCE_MS)
                pending = false
                runCatching { refreshNow(app) }
            }
        }
    }

    /** Asks for a re-read of the underlying data. */
    fun refresh(context: Context) {
        val app = context.applicationContext
        start(app)
        pending = true
        scope.launch { runCatching { refreshNow(app) } }
    }

    /** The cheap half of a refresh, for the places that only know their own data changed. */
    fun invalidate() {
        pending = true
    }

    /**
     * Answers a drawer query. Uses the index when one is open, and the same rows scanned by hand
     * when it is not, so a device without AppSearch searches exactly as well as one with it.
     */
    suspend fun search(query: String): List<SearchRow> {
        val phrase = StartSearch.phrase(query)
        if (phrase.isEmpty()) return emptyList()
        val open = session ?: return StartSearch.filter(rows.value, query)
        return withContext(Dispatchers.IO) {
            val hits = runCatching { runQuery(open, phrase) }.getOrElse { abandonSession(); null }
            // The hand-written scan is a superset of the index, so an empty page means the index
            // is behind the data rather than that nothing matched.
            if (hits.isNullOrEmpty()) StartSearch.filter(rows.value, query) else hits
        }
    }

    fun open(context: Context, row: SearchRow): Boolean = when (row.source) {
        SearchSource.NOTIFICATION -> NotificationTiles.open(context, row.id.removePrefix("n:"))
        SearchSource.PERSON -> launch(context, Intent(Intent.ACTION_VIEW,
            ContactsContract.Contacts.CONTENT_LOOKUP_URI.buildUpon().appendPath(row.id.removePrefix("c:")).build()))
        SearchSource.EVENT -> if (row.stamp > 0) launch(context, Intent(Intent.ACTION_VIEW,
            CalendarContract.CONTENT_URI.buildUpon().appendPath("time").appendPath(row.stamp.toString()).build())) else false
        else -> false
    }

    private fun launch(context: Context, intent: Intent): Boolean = runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.onFailure { Toast.makeText(context, "No app can open that", Toast.LENGTH_SHORT).show() }.getOrDefault(false)

    // ---- indexing -------------------------------------------------------------------------------

    private suspend fun refreshNow(context: Context) = work.withLock {
        val snapshot = collect(context, readSettings(context))
        _rows.value = snapshot
        val open = session
        val digest = StartSearch.digest(snapshot)
        if (open != null && digest != fingerprint) {
            runCatching { write(open, snapshot) }
                .onSuccess { fingerprint = digest; indexedIds = snapshot.map { it.id }.toSet() }
                .onFailure { abandonSession() }
        }
    }

    private fun collect(context: Context, settings: TileSettings): List<SearchRow> =
        (notificationRows(settings, NotificationTiles.notifications.value)
            + contactRows(ContactTiles.favorites(context))
            + eventRows(upcomingEvents(context)))
            .sortedBy { it.id }

    /** The same week-long Instances query the Calendar tile already runs, permission or no. */
    private fun upcomingEvents(context: Context): List<CalendarEvent> {
        if (!BuiltInTiles.granted(context, Manifest.permission.READ_CALENDAR)) return emptyList()
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(uri, now)
        ContentUris.appendId(uri, now + 7 * 86_400_000L)
        return runCatching {
            context.contentResolver.query(uri.build(), arrayOf(
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.EVENT_LOCATION), null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                buildList {
                    while (c.moveToNext() && size < 30) add(CalendarEvent(c.getString(0).orEmpty().take(120), c.getLong(1), c.getInt(2) == 1, c.getString(3).orEmpty().take(80)))
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    /** The app's single DataStore instance; a second factory over the same file is illegal. */
    private suspend fun readSettings(context: Context): TileSettings = runCatching {
        EntryPointAccessors.fromApplication(context.applicationContext, SettingsEntryPoint::class.java).dataStore()
            .data.first()[stringPreferencesKey("settingsKey")]?.let { Json.decodeFromString<TileSettings>(it) } ?: TileSettings()
    }.getOrDefault(TileSettings())

    // ---- AppSearch ------------------------------------------------------------------------------

    private fun openSession(context: Context) {
        val manager = runCatching { context.getSystemService(AppSearchManager::class.java) }.getOrNull() ?: return
        // The index is a JNI library the calling process loads itself. A build without it throws
        // here and never gets a session, which is exactly the fallback case.
        runCatching { System.loadLibrary("appsearch") }
        runCatching {
            manager.createSearchSession(AppSearchManager.SearchContext.Builder(DATABASE).build(), direct) { result: AppSearchResult<AppSearchSession> ->
                val open = result.getResultValue()
                if (result.isSuccess && open != null) scope.launch {
                    // The session is only adopted once its schema is in place, and asking for a
                    // re-index is what gets the first documents in.
                    runCatching { setSchema(open) }.onSuccess { session = open; pending = true }
                }
            }
        }
    }

    private suspend fun setSchema(open: AppSearchSession) {
        fun searchable(name: String, cardinality: Int) = AppSearchSchema.StringPropertyConfig.Builder(name)
            .setCardinality(cardinality)
            .setIndexingType(AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES)
            .setTokenizerType(AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
            .build()
        fun stored(name: String, cardinality: Int) = AppSearchSchema.StringPropertyConfig.Builder(name)
            .setCardinality(cardinality)
            .setIndexingType(AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_NONE)
            .setTokenizerType(AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_NONE)
            .build()
        val schema = AppSearchSchema.Builder(SCHEMA)
            .addProperty(searchable(PROPERTY_TITLE, PropertyConfig.CARDINALITY_OPTIONAL))
            .addProperty(searchable(PROPERTY_TEXT, PropertyConfig.CARDINALITY_OPTIONAL))
            .addProperty(stored(PROPERTY_SOURCE, PropertyConfig.CARDINALITY_REQUIRED))
            .addProperty(stored(PROPERTY_SUBTITLE, PropertyConfig.CARDINALITY_OPTIONAL))
            .addProperty(stored(PROPERTY_REF, PropertyConfig.CARDINALITY_REQUIRED))
            .addProperty(AppSearchSchema.LongPropertyConfig.Builder(PROPERTY_STAMP)
                .setCardinality(PropertyConfig.CARDINALITY_OPTIONAL)
                .setIndexingType(AppSearchSchema.LongPropertyConfig.INDEXING_TYPE_NONE)
                .build())
            .build()
        val request = SetSchemaRequest.Builder().addSchemas(schema).setVersion(SCHEMA_VERSION).build()
        // Four arguments: the request, the thread the heavy work runs on, the thread the callback
        // runs on, and the callback itself.
        val result = await { done -> open.setSchema(request, direct, direct) { done(it) } }
        check(result.isSuccess) { "setSchema failed: ${result.getResultCode()} ${result.getErrorMessage()}" }
    }

    private suspend fun write(open: AppSearchSession, rows: List<SearchRow>) {
        if (!cleared) {
            // A notification the user dismissed must not stay findable just because the process
            // restarted, and this process has no memory of what the last one indexed.
            clearNamespace(open)
            cleared = true
            indexedIds = emptySet()
        }
        val stale = indexedIds - rows.map { it.id }.toSet()
        if (stale.isNotEmpty()) {
            val request = RemoveByDocumentIdRequest.Builder(NAMESPACE).addIds(stale).build()
            check(awaitBatch { done -> open.remove(request, direct, done) }.isSuccess) { "remove failed" }
        }
        if (rows.isEmpty()) return
        val request = PutDocumentsRequest.Builder().addGenericDocuments(rows.map { document(it) }).build()
        val result = awaitBatch { done -> open.put(request, direct, done) }
        check(result.isSuccess) { "put failed: ${result.getFailures().values.firstOrNull()?.getErrorMessage()}" }
    }

    /** An empty query expression matches every document in the namespace. */
    private suspend fun clearNamespace(open: AppSearchSession) {
        val spec = SearchSpec.Builder().addFilterNamespaces(NAMESPACE).build()
        val result = await { done -> open.remove("", spec, direct) { done(it) } }
        check(result.isSuccess) { "clear failed: ${result.getErrorMessage()}" }
    }

    private suspend fun runQuery(open: AppSearchSession, phrase: String): List<SearchRow> {
        val spec = SearchSpec.Builder()
            .addFilterNamespaces(NAMESPACE)
            .setRankingStrategy(SearchSpec.RANKING_STRATEGY_RELEVANCE_SCORE)
            .setResultCountPerPage(RESULTS_PER_PAGE)
            .build()
        val results = open.search(phrase, spec)
        return try {
            val page = await { done -> results.getNextPage(direct) { done(it) } }
            if (!page.isSuccess) throw IllegalStateException("search failed: ${page.getResultCode()} ${page.getErrorMessage()}")
            page.getResultValue().orEmpty().mapNotNull { row(it.getGenericDocument()) }
        } finally {
            runCatching { results.close() }
        }
    }

    /** A broken index is dropped, not repaired: the drawer keeps working on the plain scan. */
    private fun abandonSession() {
        session = null
        fingerprint = null
        indexedIds = emptySet()
        cleared = false
    }

    private fun document(row: SearchRow): GenericDocument {
        val builder = GenericDocument.Builder<GenericDocument.Builder<*>>(NAMESPACE, documentId(row.id), SCHEMA)
        builder.setPropertyString(PROPERTY_REF, row.id)
        builder.setPropertyString(PROPERTY_SOURCE, row.source)
        builder.setPropertyString(PROPERTY_TITLE, row.title)
        builder.setPropertyString(PROPERTY_SUBTITLE, row.subtitle)
        if (row.text.isNotBlank()) builder.setPropertyString(PROPERTY_TEXT, row.text)
        builder.setPropertyLong(PROPERTY_STAMP, row.stamp)
        return builder.build()
    }

    private fun row(document: GenericDocument): SearchRow? {
        val ref = document.getPropertyString(PROPERTY_REF) ?: return null
        val source = document.getPropertyString(PROPERTY_SOURCE) ?: return null
        return SearchRow(ref, source, document.getPropertyString(PROPERTY_TITLE).orEmpty(),
            document.getPropertyString(PROPERTY_TEXT).orEmpty(), document.getPropertyString(PROPERTY_SUBTITLE).orEmpty(),
            document.getPropertyLong(PROPERTY_STAMP))
    }

    /** Document ids cannot hold a contact lookup key's slashes, so the row keeps the real one. */
    private fun documentId(id: String): String =
        "d" + id.map { if (it.isLetterOrDigit() || it in "._-") it else '_' }.joinToString("").take(80) +
            Integer.toHexString(id.hashCode())

    private suspend fun <T> await(block: ((AppSearchResult<T>) -> Unit) -> Unit) =
        suspendCancellableCoroutine { continuation ->
            block { result -> if (continuation.isActive) continuation.resume(result) }
        }

    private suspend fun awaitBatch(block: (BatchResultCallback<String, Void>) -> Unit) =
        suspendCancellableCoroutine { continuation ->
            block(object : BatchResultCallback<String, Void> {
                override fun onResult(result: AppSearchBatchResult<String, Void>) {
                    if (continuation.isActive) continuation.resume(result)
                }

                override fun onSystemError(throwable: Throwable?) {
                    if (continuation.isActive) continuation.resumeWithException(throwable ?: IllegalStateException("AppSearch is unavailable"))
                }
            })
        }
}
