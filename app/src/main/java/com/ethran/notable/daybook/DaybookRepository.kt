package com.ethran.notable.daybook

import android.content.Context
import androidx.core.net.toUri
import com.ethran.notable.data.datastore.GlobalAppSettings
import com.ethran.notable.data.db.AppDatabase
import com.ethran.notable.data.db.DayPage
import com.ethran.notable.data.db.Digest
import com.ethran.notable.data.db.Image
import com.ethran.notable.data.db.Kv
import com.ethran.notable.data.db.Notebook
import com.ethran.notable.data.db.Page
import com.ethran.notable.data.model.BackgroundType
import dagger.hilt.android.qualifiers.ApplicationContext
import io.shipbook.shipbooksdk.ShipBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** A page to open in the editor, with the notebook it belongs to. */
data class PageRef(val pageId: String, val bookId: String?)

/**
 * Everything behind the Today and Digest screens: the handwritten planner page for each day,
 * saved passages ("digests") and the note page that goes with each.
 *
 * Planner pages live in a "Daily Planner" notebook and digest notes in a "Digest Notes"
 * notebook, so both are ordinary notebooks that can be browsed, exported and given headings.
 */
@Singleton
class DaybookRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val db: AppDatabase,
) {
    private val log = ShipBook.getLogger("DaybookRepository")
    private val dao get() = db.daybookDao()

    // One page is created at a time, so a double tap can't make two pages for the same day.
    private val createLock = Mutex()

    // ---------------------------------------------------------------- planner pages

    /** Opens [day]'s planner page, creating it (with the date and [events] at the top) if needed. */
    suspend fun openDayPage(day: LocalDate, events: List<CalendarEvent>): PageRef =
        withContext(Dispatchers.IO) {
            createLock.withLock {
                val key = day.toString()
                val book = notebookFor(KEY_PLANNER_NOTEBOOK, "Daily Planner")

                val existing = dao.dayPage(key)?.let { db.pageDao().getById(it.pageId) }
                if (existing != null) {
                    existing.notebookId?.let { db.notebookDao().setOpenPageId(it, existing.id) }
                    return@withLock PageRef(existing.id, existing.notebookId)
                }

                val page = Page(
                    notebookId = book.id,
                    background = book.defaultBackground,
                    backgroundType = book.defaultBackgroundType,
                )
                db.pageDao().create(page)

                // Keep the notebook in date order: insert before the first later day.
                val dayOfPage = dao.allDayPages().associate { it.pageId to it.day }
                val pageIds = (db.notebookDao().getById(book.id)?.pageIds ?: emptyList())
                    .toMutableList()
                val insertAt = pageIds.indexOfFirst { id -> dayOfPage[id]?.let { it > key } == true }
                if (insertAt >= 0) pageIds.add(insertAt, page.id) else pageIds.add(page.id)
                db.notebookDao().setPageIds(book.id, pageIds, Date())
                db.notebookDao().setOpenPageId(book.id, page.id)

                placeTextOnPage(page.id, plannerHeader(day, events), underline = true)
                dao.setDayPage(DayPage(day = key, pageId = page.id))
                PageRef(page.id, book.id)
            }
        }

    /** Days in [month] that already have a planner page. */
    fun daysWithPages(month: YearMonth): Flow<Set<LocalDate>> =
        dao.daysWithPages(month.atDay(1).toString(), month.atEndOfMonth().toString())
            .map(::toDays)

    private fun plannerHeader(day: LocalDate, events: List<CalendarEvent>): List<TextBlock> {
        val title = day.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault()))
        val blocks = mutableListOf(TextBlock(title, sizeSp = 22f, bold = true))
        events.take(MAX_EVENTS_ON_PAGE).forEach { event ->
            blocks += TextBlock(eventLine(event), sizeSp = 15f)
        }
        if (events.size > MAX_EVENTS_ON_PAGE) {
            blocks += TextBlock("+ ${events.size - MAX_EVENTS_ON_PAGE} more", sizeSp = 15f, grey = true)
        }
        return blocks
    }

    // ---------------------------------------------------------------- digests

    fun allDigests(): Flow<List<Digest>> = dao.allDigests()

    fun digestsForDay(day: LocalDate): Flow<List<Digest>> = dao.digestsForDay(day.toString())

    /** Days in [month] on which at least one digest was saved. */
    fun daysWithDigests(month: YearMonth): Flow<Set<LocalDate>> =
        dao.daysWithDigests(month.atDay(1).toString(), month.atEndOfMonth().toString())
            .map(::toDays)

    /** Saves [text] as a digest dated today. Returns null if there is nothing to save. */
    suspend fun addDigest(text: String, source: String?): Digest? = withContext(Dispatchers.IO) {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return@withContext null
        val digest = Digest(
            text = cleaned.take(MAX_DIGEST_CHARS),
            source = source?.trim()?.takeIf { it.isNotEmpty() }?.take(200),
            day = LocalDate.now().toString(),
        )
        dao.insertDigest(digest)
        digest
    }

    suspend fun deleteDigest(id: String) = withContext(Dispatchers.IO) { dao.deleteDigest(id) }

    /**
     * Opens the handwritten note page for a digest, creating it if needed. A new page starts
     * with the passage printed at the top, so notes can be written right beside it.
     */
    suspend fun openDigestNote(digestId: String): PageRef? = withContext(Dispatchers.IO) {
        createLock.withLock {
            val digest = dao.digest(digestId) ?: return@withLock null

            val existing = digest.notePageId?.let { db.pageDao().getById(it) }
            if (existing != null) {
                existing.notebookId?.let { db.notebookDao().setOpenPageId(it, existing.id) }
                return@withLock PageRef(existing.id, existing.notebookId)
            }

            val book = notebookFor(KEY_DIGEST_NOTEBOOK, "Digest Notes")
            val page = Page(
                notebookId = book.id,
                background = book.defaultBackground,
                backgroundType = book.defaultBackgroundType,
            )
            db.pageDao().create(page)
            val pageIds = (db.notebookDao().getById(book.id)?.pageIds ?: emptyList()) + page.id
            db.notebookDao().setPageIds(book.id, pageIds, Date())
            db.notebookDao().setOpenPageId(book.id, page.id)

            val shown = if (digest.text.length > MAX_CHARS_ON_PAGE)
                digest.text.take(MAX_CHARS_ON_PAGE).trimEnd() + " …" else digest.text
            val blocks = listOf(
                TextBlock(shown, sizeSp = 16f),
                TextBlock(digestCaption(digest), sizeSp = 13f, grey = true),
            )
            placeTextOnPage(page.id, blocks, quoteBar = true)
            dao.setDigestNotePage(digest.id, page.id)
            PageRef(page.id, book.id)
        }
    }

    /** Writes every digest to Documents/Digest.md, newest first. Returns the file. */
    suspend fun exportDigests(): File = withContext(Dispatchers.IO) {
        val digests = dao.allDigestsOnce()
        val text = buildString {
            append("# Digest\n\n")
            digests.forEach { digest ->
                digest.text.lines().forEach { line -> append("> ").append(line).append('\n') }
                append('\n').append("— ").append(digestCaption(digest)).append("\n\n---\n\n")
            }
        }
        val documents = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOCUMENTS
        )
        if (!documents.exists()) documents.mkdirs()
        File(documents, "Digest.md").also { it.writeText(text) }
    }

    /** "Meditations · Nov 16, 2026" — where and when a digest was saved. */
    fun digestCaption(digest: Digest): String {
        val date = Instant.ofEpochMilli(digest.createdAt.time).atZone(ZoneId.systemDefault())
            .toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        return listOfNotNull(digest.source, date).joinToString(" · ")
    }

    // ---------------------------------------------------------------- helpers

    /** The notebook whose id is stored under [key], created with [title] if it is gone. */
    private suspend fun notebookFor(key: String, title: String): Notebook {
        db.kvDao().get(key)?.value
            ?.let { db.notebookDao().getById(it) }
            ?.let { return it }
        val notebook = Notebook(
            title = title,
            defaultBackground = GlobalAppSettings.current.defaultNativeTemplate,
            defaultBackgroundType = BackgroundType.Native.key,
        )
        db.notebookDao().create(notebook)
        db.kvDao().set(Kv(key, notebook.id))
        return notebook
    }

    /** Puts [blocks] at the top of a page as a picture. A failure leaves the page blank. */
    private suspend fun placeTextOnPage(
        pageId: String,
        blocks: List<TextBlock>,
        quoteBar: Boolean = false,
        underline: Boolean = false,
    ) {
        try {
            val metrics = context.resources.displayMetrics
            // Pages are as wide as the screen held upright.
            val pageWidth = minOf(metrics.widthPixels, metrics.heightPixels)
            val margin = (pageWidth * 0.04f).toInt()
            val top = (64 * metrics.density).toInt() // below the toolbar
            val saved = TextImage.save(
                context, blocks, pageWidth - margin * 2, quoteBar = quoteBar, underline = underline
            )
            db.ImageDao().create(
                Image(
                    x = margin,
                    y = top,
                    width = saved.width,
                    height = saved.height,
                    uri = saved.file.toUri().toString(),
                    pageId = pageId,
                )
            )
        } catch (e: Exception) {
            log.e("Couldn't place text on page $pageId", e)
        } catch (e: OutOfMemoryError) {
            log.e("Out of memory placing text on page $pageId", e)
        }
    }

    private fun toDays(values: List<String>): Set<LocalDate> =
        values.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()

    companion object {
        private const val KEY_PLANNER_NOTEBOOK = "daybook_planner_notebook_id"
        private const val KEY_DIGEST_NOTEBOOK = "daybook_digest_notebook_id"
        private const val MAX_EVENTS_ON_PAGE = 12
        private const val MAX_CHARS_ON_PAGE = 1500
        private const val MAX_DIGEST_CHARS = 20_000

        private val timeFormat: DateTimeFormatter =
            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

        fun formatTime(millis: Long): String =
            Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime()
                .let { time: LocalTime -> time.format(timeFormat) }

        /** "9:00 AM – 10:00 AM  Team sync (Room 2)" or "All day  Trade show". */
        fun eventLine(event: CalendarEvent): String {
            val time = if (event.allDay) "All day"
            else "${formatTime(event.begin)} – ${formatTime(event.end)}"
            val place = event.location?.let { " ($it)" } ?: ""
            return "$time   ${event.title}$place"
        }
    }
}
