package com.ethran.notable.daybook

import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ethran.notable.MainActivity
import com.ethran.notable.data.db.Digest
import com.ethran.notable.editor.EditorDestination
import com.ethran.notable.ui.noRippleClickable
import com.ethran.notable.ui.theme.InkaTheme
import com.ethran.notable.utils.hasUsableStorage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * A side panel that opens over whatever app is on screen, the way a widget would: today's
 * schedule and planner page, or the latest digests. It is what the Today and Digest launcher
 * icons open, so pinning those in a quick-access panel gives a glance at each without
 * leaving the book or note underneath. A tap outside the panel closes it.
 */
@AndroidEntryPoint
class PeekActivity : ComponentActivity() {

    @Inject
    lateinit var repository: dagger.Lazy<DaybookRepository>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val showDigest = intent?.component?.className?.endsWith(".DigestShortcut") == true
        val ready = hasUsableStorage(this)

        setContent {
            InkaTheme {
                // The see-through area beside the panel: tap it to go back to what was open.
                Box(
                    Modifier
                        .fillMaxSize()
                        .noRippleClickable { finish() }
                ) {
                    Column(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .fillMaxWidth(0.62f)
                            .background(Color.White)
                            .border(2.dp, Color.Black, RectangleShape)
                            .noRippleClickable { }
                            .padding(16.dp)
                    ) {
                        when {
                            !ready -> SetupNeeded()
                            showDigest -> DigestPeek()
                            else -> TodayPeek()
                        }
                    }
                }
            }
        }
    }

    /** Opens [route] in the main app and closes the panel. */
    private fun openInApp(route: String) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .setAction(ACTION_OPEN_SCREEN)
                .putExtra(EXTRA_ROUTE, route)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    private fun openPage(page: PageRef) =
        openInApp(EditorDestination.createRoute(page.pageId, page.bookId))

    @Composable
    private fun SetupNeeded() {
        PeekHeader("SuperBoox Manta")
        Text(
            "Open the app once and allow file access. After that this panel shows your day.",
            fontSize = 17.sp, lineHeight = 24.sp, color = Color.Black,
        )
        Spacer(Modifier.height(12.dp))
        InkButton("Open the app") { openInApp(DaybookDestination.route) }
    }

    @Composable
    private fun ColumnScope.TodayPeek() {
        val scope = rememberCoroutineScope()
        val today = remember { LocalDate.now() }
        var events by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }
        var isOpening by remember { mutableStateOf(false) }
        var message by remember { mutableStateOf<String?>(null) }
        val hasCalendar = remember { hasCalendarPermission(this@PeekActivity) }

        LaunchedEffect(Unit) {
            events = withContext(Dispatchers.IO) { CalendarEvents.forDay(this@PeekActivity, today) }
        }

        PeekHeader(
            today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault()))
        )
        InkButton(
            text = if (isOpening) "Opening…" else "Open planner page",
            filled = true,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (!isOpening) {
                isOpening = true
                scope.launch {
                    val page = runCatching { repository.get().openDayPage(today, events) }.getOrNull()
                    if (page != null) openPage(page)
                    else {
                        isOpening = false
                        message = "Couldn't open the planner page."
                    }
                }
            }
        }
        message?.let { Text(it, fontSize = 15.sp, color = Color.Black, modifier = Modifier.padding(top = 8.dp)) }

        SectionTitle("Schedule")
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            when {
                !hasCalendar -> Text(
                    "Calendar access isn't allowed yet. Open the calendar below and allow it.",
                    fontSize = 16.sp, color = Color.DarkGray,
                )

                events.isEmpty() -> Text("Nothing scheduled.", fontSize = 16.sp, color = Color.DarkGray)
                else -> events.forEach { event ->
                    Text(
                        DaybookRepository.eventLine(event),
                        fontSize = 16.sp, lineHeight = 22.sp, color = Color.Black,
                        modifier = Modifier.padding(vertical = 7.dp)
                    )
                    Hairline()
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row {
            InkButton("Calendar") { openInApp(DaybookDestination.route) }
            ButtonGap()
            InkButton("Digest") { openInApp(DigestDestination.route) }
        }
    }

    @Composable
    private fun ColumnScope.DigestPeek() {
        val scope = rememberCoroutineScope()
        val digests by remember { repository.get().allDigests() }
            .collectAsStateWithLifecycle(initialValue = emptyList<Digest>())
        var message by remember { mutableStateOf<String?>(null) }

        PeekHeader("Digest")
        InkButton("Save clipboard to Digest", filled = true, modifier = Modifier.fillMaxWidth()) {
            val text = runCatching {
                getSystemService(ClipboardManager::class.java)?.primaryClip
                    ?.takeIf { it.itemCount > 0 }?.getItemAt(0)
                    ?.coerceToText(this@PeekActivity)?.toString()
            }.getOrNull()
            if (text.isNullOrBlank()) message = "The clipboard has no text."
            else scope.launch {
                val saved = runCatching { repository.get().addDigest(text, "Clipboard") }.getOrNull()
                message = if (saved != null) "Saved." else "Couldn't save that."
            }
        }
        message?.let { Text(it, fontSize = 15.sp, color = Color.Black, modifier = Modifier.padding(top = 8.dp)) }

        SectionTitle("Latest")
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            if (digests.isEmpty()) {
                Text("Nothing saved yet.", fontSize = 16.sp, color = Color.DarkGray)
            }
            digests.take(8).forEach { digest ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .noRippleClickable {
                            scope.launch {
                                val page = runCatching { repository.get().openDigestNote(digest.id) }
                                    .getOrNull()
                                if (page != null) openPage(page)
                                else message = "Couldn't open the note page."
                            }
                        }
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        digest.text, fontSize = 16.sp, lineHeight = 22.sp, color = Color.Black,
                        maxLines = 3, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        repository.get().digestCaption(digest),
                        fontSize = 13.sp, color = Color.DarkGray,
                    )
                }
                Hairline()
            }
        }
        Spacer(Modifier.height(10.dp))
        Row {
            InkButton("All digests") { openInApp(DigestDestination.route) }
            ButtonGap()
            InkButton("Today") { openInApp(DaybookDestination.route) }
        }
    }

    @Composable
    private fun PeekHeader(title: String) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.Black,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            InkButton("Close") { finish() }
        }
        Spacer(Modifier.height(12.dp))
    }

    companion object {
        /** Asks [MainActivity] to show the screen named by [EXTRA_ROUTE]. */
        const val ACTION_OPEN_SCREEN = "com.ethran.notable.action.OPEN_SCREEN"
        const val EXTRA_ROUTE = "route"
    }
}
