package com.ethran.notable.tips

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ethran.notable.data.datastore.GlobalAppSettings
import com.ethran.notable.ui.noRippleClickable
import kotlinx.coroutines.delay

/** Where the reader is, as far as tips are concerned. */
enum class TipPlace(val title: String) {
    Library("In the Library"),
    Writing("While writing"),
    ToolsHidden("When the tools are hidden"),
    Selection("With something lassoed"),
    Contents("In Contents"),
    Today("On Today"),
    Digest("In Digest"),
}

class Tip(val id: String, val place: TipPlace, val text: String)

/**
 * Short pointers written for someone opening the app cold. Each is shown once, at the moment it
 * is useful, and all of them can be read again from the "?" button.
 */
object Tips {
    val all = listOf(
        Tip("lib_new", TipPlace.Library, "Tap + under Notebooks to start a notebook. Today is your planner and calendar; Digest is where saved snippets go."),
        Tip("lib_boox", TipPlace.Library, "BOOX notebooks are the ones from the tablet's own Notes app. Tapping one opens it there."),
        Tip("lib_template", TipPlace.Library, "Each notebook can have its own paper: dots, lines, or a sales layout such as Meeting notes."),

        Tip("write_tools", TipPlace.Writing, "The tools are down the left side. Tap a pen to pick it up; tap it again to set thickness and opacity."),
        Tip("write_hide", TipPlace.Writing, "Swipe up or down on the tool panel to tuck it away and get the whole page."),
        Tip("write_circle", TipPlace.Writing, "Draw a loop around something you wrote to select it. No need to switch tools."),
        Tip("write_swap", TipPlace.Writing, "Tap with two fingers to swap between pen and eraser."),
        Tip("write_pens", TipPlace.Writing, "The + after the pens adds another: fountain pen, pencil, brush, marker, highlighter."),
        Tip("write_template", TipPlace.Writing, "The layout button near the bottom of the panel changes this page's paper."),
        Tip("write_pages", TipPlace.Writing, "Swipe left or right with one finger to turn the page."),

        Tip("hidden_open", TipPlace.ToolsHidden, "Swipe up or down along the left edge of the screen to bring the tools back."),
        Tip("hidden_contents", TipPlace.ToolsHidden, "Swipe right from the left edge to open Contents: your headings, keywords, links and bookmarks."),

        Tip("sel_heading", TipPlace.Selection, "# turns this into a heading, so you can jump to it from Contents. The key makes it a keyword."),
        Tip("sel_date", TipPlace.Selection, "The calendar button ties this to a date. It then shows up on Today for that day."),
        Tip("sel_link", TipPlace.Selection, "The chain button links this to another notebook or to a day's planner page. Follow it later from Contents, under Links."),
        Tip("sel_digest", TipPlace.Selection, "Over a PDF, the book button copies the printed text underneath into Digest."),
        Tip("sel_move", TipPlace.Selection, "Drag the selection to move it. Double-tap it to make a copy."),

        Tip("con_jump", TipPlace.Contents, "Tap any entry to jump straight to it. Swipe the panel left to close it."),
        Tip("con_bookmark", TipPlace.Contents, "The button at the bottom bookmarks the page you are on."),

        Tip("today_page", TipPlace.Today, "Open planner page gives each day its own page to write on. Tap any date to see that day."),
        Tip("today_calendar", TipPlace.Today, "Meetings come from the calendars synced to this tablet. If none show, add your account in the tablet's settings."),

        Tip("digest_save", TipPlace.Digest, "Save text here from any app: select it, tap Share, then Save to Digest. Or copy it and use Save clipboard."),
        Tip("digest_note", TipPlace.Digest, "Tap a snippet to open a page beside it for your own handwritten notes."),
    )

    private const val STORE = "tips"

    private fun seen(context: Context): Set<String> =
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).getStringSet("seen", emptySet())
            ?: emptySet()

    /** The next tip for [place] the reader has not been shown, or null when they have seen all. */
    fun nextUnseen(context: Context, place: TipPlace): Tip? {
        val done = seen(context)
        return all.firstOrNull { it.place == place && it.id !in done }
    }

    fun markSeen(context: Context, tip: Tip) {
        val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        prefs.edit().putStringSet("seen", seen(context) + tip.id).apply()
    }

    /** Lets every tip show once more. */
    fun reset(context: Context) {
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().clear().apply()
    }
}

/**
 * One line at the foot of the screen: the next unseen tip for [place]. It waits a moment so it
 * never lands mid-stroke, stays a few seconds, and is not shown again. Tap it to dismiss.
 */
@Composable
fun TipHint(place: TipPlace, modifier: Modifier = Modifier) {
    if (!GlobalAppSettings.current.showTips) return
    val context = LocalContext.current
    var tip by remember { mutableStateOf<Tip?>(null) }

    LaunchedEffect(place) {
        tip = null
        delay(1500)
        val next = Tips.nextUnseen(context, place) ?: return@LaunchedEffect
        Tips.markSeen(context, next)
        tip = next
        delay(9000)
        tip = null
    }

    val showing = tip ?: return
    Box(modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 70.dp, end = 70.dp, bottom = 70.dp)
                .widthIn(max = 640.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black)
                .noRippleClickable { tip = null }
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Text("Tip", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.width(12.dp))
            Text(showing.text, fontSize = 16.sp, color = Color.White)
        }
    }
}

/**
 * The "?" page. Tips for where the reader is now come first, then everything else, so it doubles
 * as a short guide to the whole app.
 */
@Composable
fun HelpDialog(current: List<TipPlace>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val panel = RoundedCornerShape(18.dp)
    val rest = TipPlace.entries.filter { it !in current }
    var wasReset by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color.White, panel)
                .border(3.dp, Color.Black, panel)
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Tips",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "Done",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black)
                        .noRippleClickable { onDismiss() }
                        .padding(horizontal = 22.dp, vertical = 8.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier
                    .heightIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                (current + rest).forEachIndexed { index, place ->
                    val here = index < current.size
                    Spacer(Modifier.height(14.dp))
                    Text(
                        if (here) "${place.title} (where you are now)" else place.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        Modifier
                            .padding(top = 4.dp, bottom = 4.dp)
                            .fillMaxWidth()
                            .height(if (here) 3.dp else 1.dp)
                            .background(Color.Black)
                    )
                    Tips.all.filter { it.place == place }.forEach { tip ->
                        Text(
                            tip.text,
                            fontSize = 17.sp,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    if (wasReset) "Tips will show again as you go" else "Show the pop-up tips again",
                    fontSize = 16.sp,
                    modifier = Modifier
                        .border(2.dp, Color.Black, RoundedCornerShape(50))
                        .noRippleClickable {
                            Tips.reset(context)
                            wasReset = true
                        }
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                )
            }
        }
    }
}
