package com.ethran.notable.daybook

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.ethran.notable.data.db.AppDatabase
import com.ethran.notable.data.db.Heading
import com.ethran.notable.data.db.PageText
import com.ethran.notable.data.db.Sticker
import com.ethran.notable.data.db.Stroke
import com.ethran.notable.data.db.StrokePoint
import com.ethran.notable.editor.utils.Pen
import io.shipbook.shipbooksdk.ShipBook
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Progress of reading the handwriting on every page, for search. */
data class IndexProgress(val done: Int, val total: Int, val running: Boolean, val problem: String?)

/** One search result: a page, or a heading, keyword or to-do on a page. */
data class SearchHit(
    val pageId: String,
    val bookId: String?,
    val place: String,
    val snippet: String,
    val kind: String,
)

/**
 * Handwriting search. Pages are read in the background, newest edits first, and the text is
 * kept so only pages edited since are read again.
 */
@Singleton
class SearchRepository @Inject constructor(private val db: AppDatabase) {
    private val log = ShipBook.getLogger("Search")
    private val dao get() = db.extrasDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private val _progress = MutableStateFlow(IndexProgress(0, 0, false, null))
    val progress: StateFlow<IndexProgress> = _progress.asStateFlow()

    /** Starts reading pages that are new or changed. Does nothing if already running. */
    fun startIndexing() {
        if (job?.isActive == true) return
        job = scope.launch {
            try {
                indexStale()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e("Indexing failed", e)
                _progress.value = _progress.value.copy(running = false, problem = "Reading stopped: ${e.message}")
            }
        }
    }

    private suspend fun indexStale() {
        val indexed = dao.indexStamps().associate { it.pageId to it.indexedAt }
        val stale = dao.pageStamps()
            .filter { (indexed[it.id] ?: -1L) < it.updatedAt.time }
            .sortedByDescending { it.updatedAt.time }
        _progress.value = IndexProgress(0, stale.size, stale.isNotEmpty(), null)
        if (stale.isEmpty()) return
        if (!HandwritingReader.prepare()) {
            _progress.value = IndexProgress(0, stale.size, false, HandwritingReader.lastProblem)
            return
        }
        stale.forEachIndexed { i, page ->
            val strokes = db.pageDao().getPageWithDataById(page.id)?.strokes.orEmpty()
            val reading = if (strokes.isEmpty()) Reading("", "") else HandwritingReader.read(strokes)
            if (reading == null) {
                _progress.value = IndexProgress(i, stale.size, false, HandwritingReader.lastProblem)
                return
            }
            dao.putPageText(
                PageText(page.id, page.notebookId, reading.text, reading.alternative, page.updatedAt.time)
            )
            _progress.value = IndexProgress(i + 1, stale.size, i + 1 < stale.size, null)
        }
    }

    suspend fun search(query: String): List<SearchHit> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext emptyList()
        val titles = HashMap<String, String>()
        suspend fun place(pageId: String, bookId: String?): String {
            if (bookId == null) return "Quick page"
            val book = db.notebookDao().getById(bookId) ?: return "(deleted notebook)"
            val number = book.pageIds.indexOf(pageId) + 1
            val title = titles.getOrPut(bookId) { book.title }
            return if (number > 0) "$title · page $number" else title
        }

        val marks = db.markDao().searchHeadings(q).map { h ->
            SearchHit(
                h.pageId, h.notebookId, place(h.pageId, h.notebookId),
                snippet(h.text.orEmpty(), q), kindLabel(h.kind)
            )
        }
        val pages = dao.searchPages(q).mapNotNull { t ->
            if (db.pageDao().getById(t.pageId) == null) return@mapNotNull null
            val source = if (t.text.contains(q, ignoreCase = true)) t.text else t.alt
            SearchHit(t.pageId, t.notebookId, place(t.pageId, t.notebookId), snippet(source, q), "Page")
        }
        marks + pages
    }

    private fun kindLabel(kind: String) = when (kind) {
        Heading.KIND_KEYWORD -> "Keyword"
        Heading.KIND_TASK -> "To-do"
        Heading.KIND_LINK -> "Link"
        Heading.KIND_EVENT -> "Dated note"
        else -> "Heading"
    }

    private fun snippet(text: String, q: String): String {
        val flat = text.replace('\n', ' ').replace(Regex(" {2,}"), " ")
        val at = flat.indexOf(q, ignoreCase = true)
        if (at < 0) return flat.take(110)
        val start = (at - 45).coerceAtLeast(0)
        val end = (at + q.length + 65).coerceAtMost(flat.length)
        return (if (start > 0) "…" else "") + flat.substring(start, end) + (if (end < flat.length) "…" else "")
    }
}

@Serializable
private class StickerStroke(
    val size: Float,
    val pen: String,
    val color: Int,
    val maxPressure: Int,
    val points: List<StrokePoint>,
)

/** A sticker as shown in the picker. */
class StickerItem(val id: String, val preview: ImageBitmap?)

/** Saved handwriting to stamp onto pages again. */
@Singleton
class StickerStore @Inject constructor(private val db: AppDatabase) {
    private val dao get() = db.extrasDao()
    private val json = Json { ignoreUnknownKeys = true }

    fun all(): Flow<List<StickerItem>> = dao.stickers().map { list ->
        list.map { s ->
            StickerItem(
                s.id,
                runCatching {
                    BitmapFactory.decodeByteArray(s.preview, 0, s.preview.size)?.asImageBitmap()
                }.getOrNull()
            )
        }
    }.flowOn(Dispatchers.IO)

    /** Saves [strokes] (page coordinates) as a sticker, moved so its corner is at 0,0. */
    suspend fun save(strokes: List<Stroke>, preview: ByteArray) = withContext(Dispatchers.IO) {
        val left = strokes.minOf { it.left }
        val top = strokes.minOf { it.top }
        val encoded = strokes.map { s ->
            StickerStroke(
                size = s.size,
                pen = s.pen.name,
                color = s.color,
                maxPressure = s.maxPressure,
                points = s.points.map { p -> p.copy(x = p.x - left, y = p.y - top) },
            )
        }
        dao.insertSticker(Sticker(preview = preview, strokes = json.encodeToString(encoded)))
    }

    /** The sticker's strokes, from 0,0, ready to place on a page. Null if it is gone. */
    suspend fun load(id: String): List<Stroke>? = withContext(Dispatchers.IO) {
        val sticker = dao.sticker(id) ?: return@withContext null
        val now = Date()
        runCatching { json.decodeFromString<List<StickerStroke>>(sticker.strokes) }.getOrNull()
            ?.mapNotNull { s ->
                if (s.points.isEmpty()) return@mapNotNull null
                Stroke(
                    id = UUID.randomUUID().toString(),
                    size = s.size,
                    pen = runCatching { Pen.valueOf(s.pen) }.getOrDefault(Pen.BALLPEN),
                    color = s.color,
                    maxPressure = s.maxPressure,
                    // Padded by the pen size, as drawn strokes are.
                    top = s.points.minOf { it.y } - s.size,
                    bottom = s.points.maxOf { it.y } + s.size,
                    left = s.points.minOf { it.x } - s.size,
                    right = s.points.maxOf { it.x } + s.size,
                    points = s.points,
                    pageId = "",
                    createdAt = now,
                    updatedAt = now,
                )
            }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) { dao.deleteSticker(id) }
}
