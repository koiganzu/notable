package com.ethran.notable.daybook

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ethran.notable.ui.noRippleClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A notebook in the tablet's built-in BOOX Notes app. Only its name and id are read. */
data class BooxNote(val id: String, val title: String, val folder: String?, val updated: Long)

/**
 * Bridge to the built-in BOOX Notes app. BOOX firmware lists its notebooks through a content
 * provider and can open one by id; the handwriting itself stays inside BOOX Notes.
 */
object BooxNotes {
    private const val PACKAGE = "com.onyx.android.note"
    private val NOTES: Uri =
        Uri.parse("content://com.onyx.android.sdk.note.ContentProvider/NoteModel")
    private const val OPEN_ACTIVITY = "com.onyx.android.note.note.ui.OpenNoteByDocIdActivity"
    private const val NEW_ACTIVITY = "com.onyx.android.note.note.ui.CreateQuickNoteActivity"

    private class Row(
        val id: String, val title: String, val parent: String?,
        val pages: Int, val status: Int, val updated: Long,
    )

    /** Notebooks most recently edited first; null when this firmware does not share the list. */
    fun recent(context: Context, limit: Int = 40): List<BooxNote>? {
        val rows = runCatching { readRows(context) }.getOrNull() ?: return null
        val byId = rows.associateBy { it.id }
        val parents = rows.mapNotNull { it.parent }.toSet()
        // Which status value means "not deleted" differs by firmware: trust the newest note's.
        val live = rows.filter { it.pages > 0 && it.updated > 0 }.maxByOrNull { it.updated }?.status
        return rows.asSequence()
            .filter { it.pages > 0 && it.id !in parents }
            .filter { live == null || it.status == live }
            .sortedByDescending { it.updated }
            .take(limit)
            .map { BooxNote(it.id, it.title, it.parent?.let { p -> byId[p]?.title }, it.updated) }
            .toList()
    }

    private fun readRows(context: Context): List<Row>? {
        val wanted =
            arrayOf("uniqueId", "title", "parentUniqueId", "pageCount", "status", "updatedAt")
        val cursor = context.contentResolver.query(NOTES, wanted, null, null, null) ?: return null
        val rows = ArrayList<Row>()
        cursor.use { c ->
            val id = c.getColumnIndex("uniqueId")
            val title = c.getColumnIndex("title")
            val parent = c.getColumnIndex("parentUniqueId")
            val pages = c.getColumnIndex("pageCount")
            val status = c.getColumnIndex("status")
            val updated = c.getColumnIndex("updatedAt")
            if (id < 0 || title < 0) return null
            while (c.moveToNext()) {
                val noteId = c.getString(id) ?: continue
                val noteTitle = c.getString(title) ?: continue
                rows += Row(
                    id = noteId,
                    title = noteTitle,
                    parent = if (parent >= 0) c.getString(parent)?.takeIf { it.isNotEmpty() } else null,
                    pages = if (pages >= 0) c.getInt(pages) else 1,
                    status = if (status >= 0) c.getInt(status) else 0,
                    updated = if (updated >= 0) runCatching { c.getLong(updated) }.getOrDefault(0L) else 0L,
                )
            }
        }
        return rows
    }

    private fun start(context: Context, intent: Intent): Boolean =
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess

    fun open(context: Context, note: BooxNote): Boolean = start(
        context,
        Intent(Intent.ACTION_VIEW)
            .setComponent(ComponentName(PACKAGE, OPEN_ACTIVITY))
            .putExtra("note_document_id", note.id)
    ) || openApp(context)

    fun createNew(context: Context): Boolean = start(
        context, Intent(Intent.ACTION_MAIN).setComponent(ComponentName(PACKAGE, NEW_ACTIVITY))
    ) || openApp(context)

    private fun openApp(context: Context): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(PACKAGE) ?: return false
        return start(context, launch)
    }

    /** Runs [onChange] whenever BOOX Notes changes its list. Returns the way to stop. */
    fun watch(context: Context, onChange: () -> Unit): () -> Unit {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = onChange()
        }
        val registered = runCatching {
            context.contentResolver.registerContentObserver(NOTES, true, observer)
        }.isSuccess
        return { if (registered) context.contentResolver.unregisterContentObserver(observer) }
    }
}

/**
 * Library row of the tablet's own BOOX notebooks. Refreshes when BOOX Notes changes its list
 * and whenever this screen comes back to the front. Hidden if the firmware shares nothing.
 */
@Composable
fun BooxNotesRow() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var notes by remember { mutableStateOf<List<BooxNote>?>(null) }
    var tick by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val onResume = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        lifecycleOwner.lifecycle.addObserver(onResume)
        val stop = BooxNotes.watch(context) { tick++ }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(onResume)
            stop()
        }
    }
    LaunchedEffect(tick) {
        notes = withContext(Dispatchers.IO) { BooxNotes.recent(context) }
    }

    val list = notes ?: return
    val card = RoundedCornerShape(12.dp)

    Text("BOOX notebooks")
    Spacer(Modifier.height(10.dp))
    LazyRow {
        item {
            Text(
                "+ New",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .clip(card)
                    .background(Color.Black)
                    .noRippleClickable { BooxNotes.createNew(context) }
                    .padding(horizontal = 18.dp, vertical = 21.dp)
            )
            Spacer(Modifier.width(10.dp))
        }
        items(list, key = { it.id }) { note ->
            Column(
                Modifier
                    .padding(end = 10.dp)
                    .width(170.dp)
                    .clip(card)
                    .border(2.dp, Color.Black, card)
                    .noRippleClickable { BooxNotes.open(context, note) }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    note.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    note.folder ?: "Notes",
                    fontSize = 13.sp,
                    color = Color.DarkGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    Spacer(Modifier.height(10.dp))
}
