package com.ethran.notable.daybook

import kotlinx.coroutines.flow.map
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ethran.notable.data.db.Digest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** State for the Digest screen: every saved passage, filtered by a search box. */
@HiltViewModel
class DigestViewModel @Inject constructor(
    private val repository: DaybookRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Group being shown: null for all, [UNSORTED] for passages not in a group. */
    private val _group = MutableStateFlow<String?>(null)
    val group: StateFlow<String?> = _group.asStateFlow()

    /** Every group in use, by name. */
    val groups: StateFlow<List<String>> = repository.allDigests()
        .map { all -> all.mapNotNull { it.category }.distinct().sortedBy { it.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun showGroup(name: String?) {
        _group.value = name
    }

    fun setGroup(id: String, name: String?) {
        viewModelScope.launch { runCatching { repository.setDigestCategory(id, name) } }
    }

    val digests: StateFlow<List<Digest>> =
        combine(repository.allDigests(), _query, _group) { everything, query, group ->
            val all = when (group) {
                null -> everything
                UNSORTED -> everything.filter { it.category == null }
                else -> everything.filter { it.category == group }
            }
            val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.isEmpty()) all
            else all.filter { digest ->
                val haystack = (digest.text + " " + (digest.source ?: "")).lowercase()
                words.all { haystack.contains(it) }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) {
        _query.value = value
    }

    /** Saves text taken from the clipboard as a new digest. */
    fun addFromClipboard(text: String?) {
        if (text.isNullOrBlank()) {
            _message.value = "The clipboard has no text."
            return
        }
        viewModelScope.launch {
            val saved = runCatching { repository.addDigest(text, "Clipboard") }.getOrNull()
            _message.value = if (saved != null) "Saved to Digest." else "Couldn't save that."
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { runCatching { repository.deleteDigest(id) } }
    }

    fun export() {
        viewModelScope.launch {
            _message.value = runCatching { repository.exportDigests() }
                .fold({ "Exported to Documents/${it.name}" }, { "Export failed." })
        }
    }

    fun openNote(digestId: String, onOpen: (PageRef) -> Unit) {
        viewModelScope.launch {
            val page = runCatching { repository.openDigestNote(digestId) }.getOrNull()
            if (page != null) onOpen(page) else _message.value = "Couldn't open the note page."
        }
    }

    fun caption(digest: Digest): String =
        (digest.category?.let { "$it · " } ?: "") + repository.digestCaption(digest)

    companion object {
        const val UNSORTED = "\u0000unsorted"
    }
}
