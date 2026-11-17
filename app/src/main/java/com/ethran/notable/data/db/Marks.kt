package com.ethran.notable.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.util.Date
import java.util.UUID

/**
 * A lassoed piece of handwriting marked for finding again. Each remembers the page it is on,
 * where on that page it sits (page coordinates), and a small picture of the handwriting.
 *
 * [kind] says what it was marked as: a heading (the notebook's table of contents), a keyword
 * (an index of terms), or an event, which ties the handwriting to the calendar date in [day].
 */
@Entity(
    foreignKeys = [ForeignKey(
        entity = Page::class,
        parentColumns = arrayOf("id"),
        childColumns = arrayOf("pageId"),
        onDelete = ForeignKey.CASCADE
    )]
)
class Heading(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(index = true) val pageId: String,
    @ColumnInfo(index = true) val notebookId: String?,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    /** PNG of the handwriting as it looked when it was marked. */
    val preview: ByteArray,
    val createdAt: Date = Date(),
    @ColumnInfo(defaultValue = KIND_HEADING) val kind: String = KIND_HEADING,
    /** ISO date, e.g. 2026-11-16, for an event; null otherwise. */
    @ColumnInfo(index = true) val day: String? = null,
    /** For a link: the id of the notebook it opens; null otherwise. */
    val target: String? = null,
) {
    companion object {
        const val KIND_HEADING = "heading"
        const val KIND_KEYWORD = "keyword"
        const val KIND_EVENT = "event"
        const val KIND_LINK = "link"
    }
}

/** A bookmarked page. At most one per page. */
@Entity(
    foreignKeys = [ForeignKey(
        entity = Page::class,
        parentColumns = arrayOf("id"),
        childColumns = arrayOf("pageId"),
        onDelete = ForeignKey.CASCADE
    )]
)
class Bookmark(
    @PrimaryKey val pageId: String,
    @ColumnInfo(index = true) val notebookId: String?,
    val createdAt: Date = Date(),
)

@Dao
interface MarkDao {
    @Insert
    suspend fun insertHeading(heading: Heading)

    @Query("DELETE FROM heading WHERE id = :id")
    suspend fun deleteHeading(id: String)

    @Query("SELECT * FROM heading WHERE notebookId = :notebookId")
    fun headingsForNotebook(notebookId: String): Flow<List<Heading>>

    @Query("SELECT * FROM heading WHERE pageId = :pageId")
    fun headingsForPage(pageId: String): Flow<List<Heading>>

    @Query("SELECT * FROM heading WHERE day = :day ORDER BY createdAt")
    fun headingsForDay(day: String): Flow<List<Heading>>

    @Query("SELECT DISTINCT day FROM heading WHERE day BETWEEN :from AND :to")
    fun daysWithHeadings(from: String, to: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: Bookmark)

    @Query("DELETE FROM bookmark WHERE pageId = :pageId")
    suspend fun deleteBookmark(pageId: String)

    @Query("SELECT COUNT(*) FROM bookmark WHERE pageId = :pageId")
    suspend fun countBookmarks(pageId: String): Int

    @Query("SELECT * FROM bookmark WHERE notebookId = :notebookId")
    fun bookmarksForNotebook(notebookId: String): Flow<List<Bookmark>>

    @Query("SELECT * FROM bookmark WHERE pageId = :pageId")
    fun bookmarksForPage(pageId: String): Flow<List<Bookmark>>
}
