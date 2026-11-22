package com.ethran.notable.daybook

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ethran.notable.data.db.Digest
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/** A piece of handwriting tied to a day, and the page it is on. */
/** A to-do on Today: its handwriting, what it says, and why it is listed (overdue, no date). */
data class TaskItem(
    val id: String,
    val page: PageRef,
    val preview: ImageBitmap?,
    val text: String?,
    val done: Boolean,
    val note: String?,
)

data class LinkedNote(val id: String, val page: PageRef, val preview: ImageBitmap?)

data class DaybookUiState(
    val today: LocalDate = LocalDate.now(),
    val selected: LocalDate = LocalDate.now(),
    val month: YearMonth = YearMonth.now(),
    val hasCalendarAccess: Boolean = false,
    /** Events on the selected day. */
    val events: List<CalendarEvent> = emptyList(),
    /**
     * To-dos for the selected day. On today itself, also every open to-do from earlier days
     * and every open one with no date, so nothing slips.
     */
    val tasks: StateFlow<List<TaskItem>> = selectedDay
        .flatMapLatest { day ->
            combine(repository.tasksForDay(day), repository.openTasks()) { forDay, open ->
                val today = LocalDate.now()
                val carried = if (day != today) emptyList() else open.filter {
                    val due = it.day?.let { d -> runCatching { LocalDate.parse(d) }.getOrNull() }
                    due == null || due.isBefore(today)
                }
                (carried + forDay).distinctBy { it.id }.map { task ->
                    val due = task.day?.let { d -> runCatching { LocalDate.parse(d) }.getOrNull() }
                    TaskItem(
                        id = task.id,
                        page = PageRef(task.pageId, task.notebookId),
                        preview = runCatching {
                            BitmapFactory.decodeByteArray(task.preview, 0, task.preview.size)
                                ?.asImageBitmap()
                        }.getOrNull(),
                        text = task.text,
                        done = task.done,
                        note = when {
                            due == null -> "No date"
                            due != day -> "From " + due.format(
                                java.time.format.DateTimeFormatter.ofPattern("EEE d MMM")
                            )
                            else -> null
                        },
                    )
                }.sortedBy { it.done }
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setTaskDone(id: String, done: Boolean) {
        viewModelScope.launch { repository.setTaskDone(id, done) }
    }

    /** Days of the shown month that have calendar events. */
    val eventDays: Set<LocalDate> = emptySet(),
    val isOpeningPage: Boolean = false,
    val message: String? = null,
)

/** State for the Today screen: a month calendar and what belongs to the selected day. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DaybookViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: DaybookRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        DaybookUiState(hasCalendarAccess = hasCalendarPermission(context))
    )
    val state: StateFlow<DaybookUiState> = _state.asStateFlow()

    private val selectedDay = MutableStateFlow(LocalDate.now())
    private val shownMonth = MutableStateFlow(YearMonth.now())

    /** Digests saved on the selected day. */
    val digests: StateFlow<List<Digest>> = selectedDay
        .flatMapLatest { repository.digestsForDay(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The selected day's planner page, if it has been started. */
    val plannerPageId: StateFlow<String?> = selectedDay
        .flatMapLatest { repository.plannerPageId(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Handwriting tied to the selected day, each with its picture decoded for display. */
    val linkedNotes: StateFlow<List<LinkedNote>> = selectedDay
        .flatMapLatest { repository.linkedNotes(it) }
        .map { notes ->
            notes.map { note ->
                LinkedNote(
                    id = note.id,
                    page = PageRef(note.pageId, note.notebookId),
                    preview = runCatching {
                        BitmapFactory.decodeByteArray(note.preview, 0, note.preview.size)
                            ?.asImageBitmap()
                    }.getOrNull(),
                )
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Days of the shown month that have a planner page, a digest or linked handwriting. */
    val noteDays: StateFlow<Set<LocalDate>> = shownMonth
        .flatMapLatest { month ->
            combine(
                repository.daysWithPages(month),
                repository.daysWithDigests(month),
                repository.daysWithLinkedNotes(month),
            ) { pages, digests, linked -> pages + digests + linked }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Call when the screen is shown: the date may have rolled over and events may have changed. */
    fun refresh() {
        _state.update {
            it.copy(today = LocalDate.now(), hasCalendarAccess = hasCalendarPermission(context))
        }
        loadEvents()
    }

    fun selectDay(day: LocalDate) {
        selectedDay.value = day
        shownMonth.value = YearMonth.from(day)
        _state.update { it.copy(selected = day, month = YearMonth.from(day), message = null) }
        loadEvents()
    }

    fun goToToday() = selectDay(LocalDate.now())

    fun showMonth(month: YearMonth) {
        shownMonth.value = month
        _state.update { it.copy(month = month) }
        loadEvents()
    }

    fun onCalendarPermissionResult(granted: Boolean) {
        _state.update {
            it.copy(
                hasCalendarAccess = granted,
                message = if (granted) null else "Calendar access was not allowed.",
            )
        }
        if (granted) loadEvents()
    }

    private fun loadEvents() {
        val day = _state.value.selected
        val month = _state.value.month
        viewModelScope.launch {
            val (events, eventDays) = withContext(Dispatchers.IO) {
                CalendarEvents.forDay(context, day) to
                        CalendarEvents.daysWithEvents(context, month.atDay(1), month.atEndOfMonth())
            }
            _state.update { current ->
                // Drop the result if the selection moved on while we were reading.
                if (current.selected != day || current.month != month) current
                else current.copy(events = events, eventDays = eventDays)
            }
        }
    }

    /** Opens the selected day's planner page, creating it on first use. */
    fun openPlannerPage(onOpen: (PageRef) -> Unit) {
        if (_state.value.isOpeningPage) return
        val day = _state.value.selected
        _state.update { it.copy(isOpeningPage = true, message = null) }
        viewModelScope.launch {
            val events = withContext(Dispatchers.IO) { CalendarEvents.forDay(context, day) }
            val page = runCatching { repository.openDayPage(day, events) }.getOrNull()
            _state.update {
                it.copy(
                    isOpeningPage = false,
                    message = if (page == null) "Couldn't open the planner page." else null,
                )
            }
            if (page != null) onOpen(page)
        }
    }

    fun openDigestNote(digestId: String, onOpen: (PageRef) -> Unit) {
        viewModelScope.launch {
            val page = runCatching { repository.openDigestNote(digestId) }.getOrNull()
            if (page != null) onOpen(page)
            else _state.update { it.copy(message = "Couldn't open the note page.") }
        }
    }

    fun caption(digest: Digest): String = repository.digestCaption(digest)
}
