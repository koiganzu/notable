package com.ethran.notable.daybook

import com.ethran.notable.tips.TipPlace
import com.ethran.notable.tips.TipHint
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ethran.notable.navigation.NavigationDestination
import com.ethran.notable.ui.components.PagePreview
import com.ethran.notable.ui.noRippleClickable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

object DaybookDestination : NavigationDestination {
    override val route = "daybook"
}

/**
 * The Today screen: a month calendar, and for the selected day its schedule (from the
 * calendars synced to the tablet), its handwritten planner page and the digests saved on it.
 */
@Composable
fun DaybookView(
    goToLibrary: () -> Unit,
    goToDigest: () -> Unit,
    openPage: (PageRef) -> Unit,
    viewModel: DaybookViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val digests by viewModel.digests.collectAsStateWithLifecycle()
    val noteDays by viewModel.noteDays.collectAsStateWithLifecycle()
    val linkedNotes by viewModel.linkedNotes.collectAsStateWithLifecycle()
    val plannerPageId by viewModel.plannerPageId.collectAsStateWithLifecycle()

    // Re-read the calendar every time the screen comes back into view.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    val askForCalendar = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.onCalendarPermissionResult(granted) }

    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        ScreenHeader(title = if (state.selected == state.today) "Today" else "Calendar") {
            InkButton("Library") { goToLibrary() }
            ButtonGap()
            InkButton("Digest") { goToDigest() }
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            MonthHeader(
                month = state.month,
                onPrevious = { viewModel.showMonth(state.month.minusMonths(1)) },
                onNext = { viewModel.showMonth(state.month.plusMonths(1)) },
                onToday = { viewModel.goToToday() },
            )
            Spacer(Modifier.height(8.dp))
            MonthGrid(
                month = state.month,
                today = state.today,
                selected = state.selected,
                eventDays = state.eventDays,
                noteDays = noteDays,
                onSelect = viewModel::selectDay,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                DayMark(round = true, color = Color.Black)
                Text("  events      ", fontSize = 13.sp, color = Color.DarkGray)
                DayMark(round = false, color = Color.Black)
                Text("  planner page, digest or linked note", fontSize = 13.sp, color = Color.DarkGray)
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = state.selected.format(
                    DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault())
                ),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
            )
            Spacer(Modifier.height(10.dp))
            InkButton(
                text = when {
                    state.isOpeningPage -> "Opening…"
                    plannerPageId != null -> "Open planner page"
                    else -> "Start planner page"
                },
                filled = true,
                modifier = Modifier.fillMaxWidth(),
            ) { viewModel.openPlannerPage(openPage) }

            // What's already written on the day's planner page.
            plannerPageId?.let { pageId ->
                Spacer(Modifier.height(10.dp))
                PagePreview(
                    pageId = pageId,
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .aspectRatio(3f / 4f)
                        .border(2.dp, Color.Black, RectangleShape)
                        .noRippleClickable { viewModel.openPlannerPage(openPage) },
                )
            }

            state.message?.let { message ->
                Text(message, fontSize = 15.sp, color = Color.Black, modifier = Modifier.padding(top = 8.dp))
            }

            SectionTitle("Schedule")
            when {
                !state.hasCalendarAccess -> {
                    Text(
                        "Allow calendar access to see the events synced to this tablet.",
                        fontSize = 16.sp, color = Color.Black,
                    )
                    Spacer(Modifier.height(8.dp))
                    InkButton("Allow calendar access") {
                        askForCalendar.launch(Manifest.permission.READ_CALENDAR)
                    }
                }

                state.events.isEmpty() ->
                    Text("Nothing scheduled.", fontSize = 16.sp, color = Color.DarkGray)

                else -> state.events.forEach { event -> EventRow(event) }
            }

            if (linkedNotes.isNotEmpty()) {
                SectionTitle("Linked notes (${linkedNotes.size})")
                linkedNotes.forEach { note ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .noRippleClickable { openPage(note.page) }
                    ) {
                        val picture = note.preview
                        if (picture != null) {
                            Image(
                                bitmap = picture,
                                contentDescription = "Linked handwriting",
                                contentScale = ContentScale.Fit,
                                alignment = Alignment.CenterStart,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 72.dp)
                                    .padding(vertical = 8.dp)
                            )
                        } else {
                            Text("(handwriting)", fontSize = 16.sp, color = Color.Black,
                                modifier = Modifier.padding(vertical = 8.dp))
                        }
                        Hairline()
                    }
                }
            }

            SectionTitle("Digest (${digests.size})")
            if (digests.isEmpty()) {
                Text("Nothing saved on this day.", fontSize = 16.sp, color = Color.DarkGray)
            }
            digests.forEach { digest ->
                DigestCard(
                    digest = digest,
                    caption = viewModel.caption(digest),
                    maxLines = 4,
                    onClick = { viewModel.openDigestNote(digest.id, openPage) },
                ) {
                    InkButton(if (digest.notePageId != null) "Open note" else "Write note") {
                        viewModel.openDigestNote(digest.id, openPage)
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
    TipHint(TipPlace.Today)
    }
}

@Composable
internal fun MonthHeader(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        InkButton("<") { onPrevious() }
        Text(
            text = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        InkButton("Today") { onToday() }
        ButtonGap()
        InkButton(">") { onNext() }
    }
}

@Composable
internal fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate,
    eventDays: Set<LocalDate>,
    noteDays: Set<LocalDate>,
    onSelect: (LocalDate) -> Unit,
) {
    val firstDayOfWeek: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val weekdays = (0L..6L).map { firstDayOfWeek.plus(it) }
    // Blank cells before the 1st so it lands under its weekday.
    val leading = (month.atDay(1).dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val cells: List<LocalDate?> =
        List(leading) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    val weeks = cells.chunked(7).map { week -> week + List(7 - week.size) { null } }

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            weekdays.forEach { day ->
                Text(
                    text = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp)
                )
            }
        }
        weeks.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(58.dp)
                            .padding(2.dp)
                    ) {
                        if (day != null) {
                            DayCell(
                                day = day,
                                isToday = day == today,
                                isSelected = day == selected,
                                hasEvents = day in eventDays,
                                hasNotes = day in noteDays,
                                onClick = { onSelect(day) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A small filled dot or square marking a calendar day. */
@Composable
private fun DayMark(round: Boolean, color: Color) {
    Box(
        Modifier
            .size(9.dp)
            .background(color, if (round) CircleShape else RectangleShape)
    )
}

@Composable
private fun DayCell(
    day: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    hasEvents: Boolean,
    hasNotes: Boolean,
    onClick: () -> Unit,
) {
    val ink = if (isSelected) Color.White else Color.Black
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .background(if (isSelected) Color.Black else Color.White)
            .border(if (isToday) 3.dp else 1.dp, Color.Black, RectangleShape)
            .noRippleClickable { onClick() }
            .padding(top = 4.dp)
    ) {
        Text(
            text = day.dayOfMonth.toString(),
            fontSize = 18.sp,
            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
            color = ink,
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
            if (hasEvents) DayMark(round = true, color = ink)
            if (hasEvents && hasNotes) Spacer(Modifier.width(5.dp))
            if (hasNotes) DayMark(round = false, color = ink)
        }
    }
}

@Composable
private fun EventRow(event: CalendarEvent) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(vertical = 8.dp)) {
            Text(
                text = if (event.allDay) "All day"
                else "${DaybookRepository.formatTime(event.begin)}\n${DaybookRepository.formatTime(event.end)}",
                fontSize = 15.sp,
                color = Color.Black,
                modifier = Modifier.weight(0.28f)
            )
            Column(Modifier.weight(0.72f)) {
                Text(event.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                event.location?.let { Text(it, fontSize = 14.sp, color = Color.DarkGray) }
            }
        }
        Hairline()
    }
}
