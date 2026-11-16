package com.ethran.notable.daybook

import android.content.Context
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class DaybookUiState(
    val today: LocalDate = LocalDate.now(),
    val selected: LocalDate = LocalDate.now(),
    val month: YearMonth = YearMonth.now(),
    val hasCalendarAccess: Boolean = false,
    /** Events on the selected day. */
    val events: List<CalendarEvent> = emptyList(),
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

    /** Days of the shown month that have a planner page or a digest. */
    val noteDays: StateFlow<Set<LocalDate>> = shownMonth
        .flatMapLatest { month ->
            combine(repository.daysWithPages(month), repository.daysWithDigests(month)) { a, b -> a + b }
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
