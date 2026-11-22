package com.ethran.notable.daybook

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ethran.notable.navigation.NavigationDestination
import com.ethran.notable.ui.noRippleClickable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

object SearchDestination : NavigationDestination {
    override val route = "search"
}

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: SearchRepository,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    val progress: StateFlow<IndexProgress> = repository.progress

    /** Results, re-run as more pages are read (every five pages, so the list doesn't flicker). */
    val results: StateFlow<List<SearchHit>> = combine(
        _query.debounce(300),
        repository.progress.map { it.done / 5 }.distinctUntilChanged(),
    ) { q, _ -> q }
        .mapLatest { repository.search(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(q: String) {
        _query.value = q
    }

    fun startIndexing() = repository.startIndexing()
}

/** Search every notebook's handwriting, headings, keywords and to-dos. */
@Composable
fun SearchView(
    goToLibrary: () -> Unit,
    openPage: (PageRef) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.startIndexing() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        ScreenHeader(title = "Search") {
            InkButton("Library") { goToLibrary() }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .border(2.dp, Color.Black, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                if (query.isEmpty()) {
                    Text("Search your handwriting", fontSize = 18.sp, color = Color.Gray)
                }
                BasicTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 18.sp, color = Color.Black),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(8.dp))
            val status = when {
                progress.problem != null -> progress.problem
                progress.running -> "Reading handwriting… ${progress.done} of ${progress.total} pages. " +
                        "Results fill in as it goes."
                else -> null
            }
            status?.let { Text(it, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(6.dp))
            if (query.trim().length >= 2 && results.isEmpty() && !progress.running) {
                Text(
                    "Nothing found. Handwriting is read by a machine, so try part of a word.",
                    fontSize = 16.sp, color = Color.DarkGray
                )
            }
        }
        LazyColumn(Modifier.padding(horizontal = 16.dp)) {
            items(results) { hit ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .noRippleClickable { openPage(PageRef(hit.pageId, hit.bookId)) }
                        .padding(vertical = 10.dp)
                ) {
                    Text(
                        if (hit.kind == "Page") hit.place else "${hit.kind} · ${hit.place}",
                        fontSize = 16.sp, fontWeight = FontWeight.Bold
                    )
                    Text(hit.snippet, fontSize = 16.sp, color = Color.DarkGray, maxLines = 2)
                }
                Hairline()
            }
        }
    }
}
