package com.ethran.notable.daybook

import com.ethran.notable.tips.TipPlace
import com.ethran.notable.tips.TipHint
import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ethran.notable.navigation.NavigationDestination

object DigestDestination : NavigationDestination {
    override val route = "digest"
}

/**
 * The Digest screen: every passage saved from other apps, newest first, with a search box.
 * Each passage can be opened into a handwritten note page that starts with the passage on it.
 */
@Composable
fun DigestView(
    goToLibrary: () -> Unit,
    goToToday: () -> Unit,
    openPage: (PageRef) -> Unit,
    viewModel: DigestViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val digests by viewModel.digests.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    // Which passage is shown in full, and which is one tap away from being deleted.
    var expandedId by remember { mutableStateOf<String?>(null) }
    var confirmDeleteId by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        ScreenHeader(title = "Digest") {
            InkButton("Library") { goToLibrary() }
            ButtonGap()
            InkButton("Today") { goToToday() }
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .border(2.dp, Color.Black, RectangleShape)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                if (query.isEmpty()) {
                    Text("Search digests", fontSize = 17.sp, color = Color.Gray)
                }
                BasicTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 17.sp, color = Color.Black),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                InkButton("Add from clipboard") {
                    viewModel.addFromClipboard(readClipboardText(context))
                }
                ButtonGap()
                InkButton("Export all") { viewModel.export() }
                if (query.isNotEmpty()) {
                    ButtonGap()
                    InkButton("Clear search") { viewModel.setQuery("") }
                }
            }
            message?.let {
                Text(it, fontSize = 15.sp, color = Color.Black, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(8.dp))

            if (digests.isEmpty()) {
                Text(
                    text = if (query.isNotEmpty()) "No digests match that search."
                    else "Nothing saved yet. In a book or any app, select text, tap Share and " +
                            "choose \"Save to Digest\". Or copy text and tap \"Add from clipboard\".",
                    fontSize = 16.sp,
                    lineHeight = 23.sp,
                    color = Color.DarkGray,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            LazyColumn(Modifier.fillMaxSize()) {
                items(digests, key = { it.id }) { digest ->
                    DigestCard(
                        digest = digest,
                        caption = viewModel.caption(digest),
                        maxLines = if (expandedId == digest.id) Int.MAX_VALUE else 5,
                        onClick = {
                            expandedId = if (expandedId == digest.id) null else digest.id
                        },
                    ) {
                        InkButton(if (digest.notePageId != null) "Open note" else "Write note") {
                            viewModel.openNote(digest.id, openPage)
                        }
                        ButtonGap()
                        InkButton("Copy") { copyToClipboard(context, digest.text) }
                        ButtonGap()
                        if (confirmDeleteId == digest.id) {
                            InkButton("Confirm delete", filled = true) {
                                confirmDeleteId = null
                                viewModel.delete(digest.id)
                            }
                        } else {
                            InkButton("Delete") { confirmDeleteId = digest.id }
                        }
                    }
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
    TipHint(TipPlace.Digest)
    }
}

private fun readClipboardText(context: Context): String? = runCatching {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
        ?.coerceToText(context)?.toString()
}.getOrNull()

private fun copyToClipboard(context: Context, text: String) {
    runCatching {
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("Digest", text))
    }
}
