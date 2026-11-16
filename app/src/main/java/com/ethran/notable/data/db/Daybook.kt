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

/** The handwritten planner page for one calendar day. [day] is an ISO date, e.g. 2026-11-16. */
@Entity(
    foreignKeys = [ForeignKey(
        entity = Page::class,
        parentColumns = arrayOf("id"),
        childColumns = arrayOf("pageId"),
        onDelete = ForeignKey.CASCADE
    )]
)
data class DayPage(
    @PrimaryKey val day: String,
    @ColumnInfo(index = true) val pageId: String,
)

/**
 * A passage saved from a book, article or any other app. [day] is the ISO date it was saved
 * on, so the calendar can list a day's digests. [notePageId] is the handwritten page of
 * notes about it, once one has been started.
 */
@Entity
data class Digest(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val text: String,
    val source: String? = null,
    @ColumnInfo(index = true) val day: String,
    val createdAt: Date = Date(),
    val notePageId: String? = null,
)

@Dao
interface DaybookDao {
    @Query("SELECT * FROM daypage WHERE day = :day")
    suspend fun dayPage(day: String): DayPage?

    @Query("SELECT * FROM daypage WHERE day = :day")
    fun dayPageFlow(day: String): Flow<DayPage?>

    @Query("SELECT * FROM daypage")
    suspend fun allDayPages(): List<DayPage>

    @Query("SELECT day FROM daypage WHERE day BETWEEN :from AND :to")
    fun daysWithPages(from: String, to: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setDayPage(dayPage: DayPage)

    @Insert
    suspend fun insertDigest(digest: Digest)

    @Query("DELETE FROM digest WHERE id = :id")
    suspend fun deleteDigest(id: String)

    @Query("UPDATE digest SET notePageId = :pageId WHERE id = :id")
    suspend fun setDigestNotePage(id: String, pageId: String?)

    @Query("SELECT * FROM digest WHERE id = :id")
    suspend fun digest(id: String): Digest?

    @Query("SELECT * FROM digest ORDER BY createdAt DESC")
    fun allDigests(): Flow<List<Digest>>

    @Query("SELECT * FROM digest ORDER BY createdAt DESC")
    suspend fun allDigestsOnce(): List<Digest>

    @Query("SELECT * FROM digest WHERE day = :day ORDER BY createdAt DESC")
    fun digestsForDay(day: String): Flow<List<Digest>>

    @Query("SELECT DISTINCT day FROM digest WHERE day BETWEEN :from AND :to")
    fun daysWithDigests(from: String, to: String): Flow<List<String>>
}
