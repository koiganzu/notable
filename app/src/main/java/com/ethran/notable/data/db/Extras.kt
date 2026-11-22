package com.ethran.notable.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.util.Date
import java.util.UUID

/**
 * What the handwriting on a page says, read by the on-device recognizer, for searching.
 * [indexedAt] is the page's edit time when it was read, so a later edit marks it stale.
 */
@Entity(tableName = "page_text")
class PageText(
    @PrimaryKey val pageId: String,
    val notebookId: String?,
    val text: String,
    /** The recognizer's second guess for each line, searched too: handwriting is ambiguous. */
    val alt: String,
    val indexedAt: Long,
)

/** Handwriting saved for reuse. [strokes] is JSON, positioned from the sticker's top left. */
@Entity(tableName = "sticker")
class Sticker(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val preview: ByteArray,
    val strokes: String,
    val createdAt: Date = Date(),
)

/** A page and when it was last edited, for finding pages whose text needs reading again. */
data class PageStamp(val id: String, val notebookId: String?, val updatedAt: Date)

@Dao
interface ExtrasDao {
    @Query("SELECT id, notebookId, updatedAt FROM page")
    suspend fun pageStamps(): List<PageStamp>

    @Query("SELECT pageId, indexedAt FROM page_text")
    suspend fun indexStamps(): List<IndexStamp>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putPageText(text: PageText)

    @Query(
        "SELECT * FROM page_text WHERE text LIKE '%' || :q || '%' OR alt LIKE '%' || :q || '%' " +
                "ORDER BY indexedAt DESC LIMIT 200"
    )
    suspend fun searchPages(q: String): List<PageText>

    @Query("SELECT COUNT(*) FROM page_text")
    suspend fun indexedCount(): Int

    @Insert
    suspend fun insertSticker(sticker: Sticker)

    @Query("SELECT * FROM sticker ORDER BY createdAt DESC")
    fun stickers(): Flow<List<Sticker>>

    @Query("SELECT * FROM sticker WHERE id = :id")
    suspend fun sticker(id: String): Sticker?

    @Query("DELETE FROM sticker WHERE id = :id")
    suspend fun deleteSticker(id: String)
}

data class IndexStamp(val pageId: String, val indexedAt: Long)
