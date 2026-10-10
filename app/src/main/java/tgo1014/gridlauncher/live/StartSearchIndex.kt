package tgo1014.gridlauncher.live

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
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.widget.Toast
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import tgo1014.gridlauncher.domain.models.TileSettings
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The drawer's search, and the one place in the launcher where anything leaves memory for disk.
 *
 * Two answers to the same question, kept deliberately unequal:
 *
 *  - **In memory.** Notifications, people and the calendar are collected into [_rows] and scanned
 *    with [StartSearch.filter]. Notification content lives here and only here: read on demand,
 *    drawn on screen, and gone when the process dies.
 *  - **On disk, opt-in.** `android.app.appsearch` is a system service with a private on-device
 *    database, so whether an ordinary sideloaded build gets a working session is not something the
 *    app can assume. It is off unless the user asks for it, it is only ever handed
 *    [SearchPersistence.indexable] output, and every entry point is wrapped: a failure just leaves
 *    the in-memory scan to answer, and the drawer is written against [SearchRow] alone so it
 *    cannot tell the difference.
 *
 * SECURITY: the writer takes a [PersistedRow], not a [SearchRow]. A notification row has no way to
 * become one, so "this launcher does not store notification data" is a property of the types on the
 * writing path rather than of a setting nobody remembers to check.
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

    /** Set once the database has been emptied for good, so the purge is not repeated every launch. */
    private val PURGED = booleanPreferencesKey("searchIndexPurged")

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
    @Volatile private var started = false
    @Volatile private var optedIn: Boolean? = null
    @Volatile private var fingerprint: Int? = null
    @Volatile private var indexedIds: Set<String> = emptySet()
    @Volatile private var cleared = false

    /**
     * One flag, however many notifications arrive, and one wake-up however long the loop has been
     * idle. Conflated, so a burst during a burst collapses into a single re-read.
     */
    private val signal = Channel<Unit>(Channel.CONFLATED)

    /** Call from the application and from the launcher's resume. Idempotent. */
    @Synchronized fun start(context: Context) {
        val app = context.applicationContext
        if (started) { signal.trySend(Unit); return }
        started = true
        scope.launch {
            // The first pass must not wait a quarter of an hour for work nobody has queued yet.
            signal.trySend(Unit)
            while (true) {
                // Idle: look back in a quarter of an hour, or sooner if anything asked to be re-read.
                withTimeoutOrNull(SWEEP_MS) { signal.receive() }
                // Told to work: debounce first, so a burst of notification ticks costs one re-read
                // rather than one each.
                delay(DEBOUNCE_MS)
                runCatching { refreshNow(app) }
            }
        }
    }

    /** Asks for a re-read of the underlying data. */
    fun refresh(context: Context) {
        val app = context.applicationContext
        start(app)
        scope.launch { runCatching { refreshNow(app) } }
    }

    /** The cheap half of a refresh, for the places that only know their own data changed. */
    fun invalidate() {
        signal.trySend(Unit)
    }

    /**
     * Answers a drawer query. Uses the index when the user has asked for one, and the same rows
     * scanned by hand when they have not, so a device without AppSearch searches exactly as well as
     * one with it, and a device with the index switched off still finds its notifications.
     *
     * SECURITY: the two are unioned, never substituted. The index holds people and calendar only,
     * so answering from it alone would quietly stop notifications from being findable the moment
     * the user turned indexing on - the one thing the in-memory scan exists to guarantee.
     */
    suspend fun search(query: String): List<SearchRow> {
        val phrase = StartSearch.phrase(query)
        if (phrase.isEmpty()) return emptyList()
        val memory = StartSearch.filter(rows.value, query)
        val open = session ?: return memory
        return withContext(Dispatchers.IO) {
            val hits = runCatching { runQuery(open, phrase) }.getOrElse { abandonSession(); emptyList() }
            val found = hits.map { it.id }.toSet()
            hits + memory.filterNot { it.id in found }
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

    // ---- collecting ----------------------------------------------------------------------------

    private suspend fun refreshNow(context: Context) = work.withLock {
        val settings = readSettings(context)
        val snapshot = collect(context, settings)
        _rows.value = snapshot
        if (!settings.searchIndexEnabled) {
            standDown(context)
            return@withLock
        }
        optedIn = true
        // The session arrives asynchronously; adopting it is what asks for the first documents.
        val open = session ?: run { openSession(context) { session = setSchema(it); invalidate() }; return@withLock }
        // The fingerprint is taken over what would be written, not over what is in memory, so a
        // burst of notifications moves it not at all and costs no database write.
        val writable = SearchPersistence.indexable(snapshot)
        val digest = SearchPersistence.digest(writable)
        if (digest != fingerprint) {
            runCatching { write(open, writable) }
                .onSuccess { fingerprint = digest; indexedIds = writable.map { it.id }.toSet() }
                .onFailure { abandonSession() }
        }
    }

    /**
     * The rows the drawer searches. Notification content enters here and stops: this list is
     * published to the UI, scanned by `StartSearch.filter`, and handed to `SearchPersistence`, which
     * drops every notification row on the way to the disk.
     */
    private fun collect(context: Context, settings: TileSettings): List<SearchRow> =
        (notificationRows(settings, NotificationTiles.notifications.value)
            + contactRows(ContactTiles.favorites(context))
            + eventRows(upcomingEvents(context)))
            .sortedBy { it.id }

    /** The same week-long Instances query the Calendar tile already runs, permission or no. */
    private fun upcomingEvents(context: Context): List<CalendarEvent> = BuiltInTiles.agendaEvents(context, 30)

    /** The app's single DataStore instance; a second factory over the same file is illegal. */
    private suspend fun readSettings(context: Context): TileSettings = runCatching {
        store(context).data.first()[stringPreferencesKey("settingsKey")]?.let { Json.decodeFromString<TileSettings>(it) } ?: TileSettings()
    }.getOrDefault(TileSettings())

    private fun store(context: Context) =
        EntryPointAccessors.fromApplication(context.applicationContext, SettingsEntryPoint::class.java).dataStore()

    /**
     * The index is off, which is where it starts and where it belongs.
     *
     * Nothing is written, any session this process opened is dropped, and once per install whatever
     * an earlier build put in the database is emptied. A launcher that has already stored a user's
     * notification text does not stop being trusted by deciding not to write more of it: the files
     * have to go. The marker keeps this to once per install rather than once per launch, and a
     * device with no working AppSearch simply has nothing to empty.
     */
    private suspend fun standDown(context: Context) {
        if (optedIn == false) return
        optedIn = false
        val open = session
        session = null
        fingerprint = null
        indexedIds = emptySet()
        cleared = false
        if (open != null) runCatching { open.close() }
        if (runCatching { store(context).data.first()[PURGED] == true }.getOrDefault(false)) return
        val purge = awaitSession(context) ?: return
        runCatching { clearNamespace(purge) }
        runCatching { purge.close() }
        runCatching { store(context).edit { it[PURGED] = true } }
    }

    // ---- AppSearch ------------------------------------------------------------------------------

    /**
     * Opens a session and hands it to [adopt] once its schema is in place, which is also what asks
     * for the first documents to be written. A device without a working AppSearch never gets here,
     * and the drawer's own scan answers instead.
     */
    private fun openSession(context: Context, adopt: suspend (AppSearchSession) -> Unit) {
        requestSession(context) { result ->
            val open = result.getResultValue()
            if (result.isSuccess && open != null) scope.launch { runCatching { adopt(open) } }
        }
    }

    /** The same request, awaited, for the one-off purge that has to know whether it got a session. */
    private suspend fun awaitSession(context: Context): AppSearchSession? {
        val manager = runCatching { context.getSystemService(AppSearchManager::class.java) }.getOrNull() ?: return null
        runCatching { System.loadLibrary("appsearch") }
        val result = runCatching { await<AppSearchSession> { done -> manager.createSearchSession(searchContext(), direct) { done(it) } } }.getOrNull()
        return result?.takeIf { it.isSuccess }?.getResultValue()
    }

    private fun requestSession(context: Context, callback: (AppSearchResult<AppSearchSession>) -> Unit) {
        val manager = runCatching { context.getSystemService(AppSearchManager::class.java) }.getOrNull() ?: return
        // The index is a JNI library the calling process loads itself. A build without it throws
        // here and never gets a session, which is exactly the fallback case.
        runCatching { System.loadLibrary("appsearch") }
        runCatching { manager.createSearchSession(searchContext(), direct, callback) }
    }

    private fun searchContext() = AppSearchManager.SearchContext.Builder(DATABASE).build()

    private suspend fun setSchema(open: AppSearchSession): AppSearchSession {
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
        return open
    }

    /**
     * SECURITY: the only function in the app that writes a document, and it takes a [PersistedRow].
     *
     * A notification row cannot be passed here - there is no overload that would accept one, and
     * `SearchPersistence.indexable` has already refused to make one from the collected rows. That
     * is what makes "this launcher never stores notification content" true by construction rather
     * than by review.
     */
    private suspend fun write(open: AppSearchSession, rows: List<PersistedRow>) {
        if (!cleared) {
            // A row the user deleted must not stay findable just because the process restarted,
            // and this process has no memory of what the last one indexed.
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
            // SECURITY: a database written by an older build could still hold rows from a source
            // that is no longer indexable. They are dropped here, on the way back out to the screen,
            // so nothing unreadable is ever drawn even if a stale file survives on disk.
            SearchPersistence.discardUnindexable(page.getResultValue().orEmpty().mapNotNull { row(it.getGenericDocument()) })
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

    private fun document(row: PersistedRow): GenericDocument {
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
