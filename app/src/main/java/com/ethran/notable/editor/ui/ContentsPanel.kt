package com.ethran.notable.editor.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ethran.notable.editor.BookmarkItem
import com.ethran.notable.editor.ContentsUiState
import com.ethran.notable.editor.EditorViewModel
import com.ethran.notable.editor.HeadingItem
import com.ethran.notable.editor.ToolbarAction
import com.ethran.notable.ui.noRippleClickable

private enum class ContentsTab { Headings, Keywords, Bookmarks }

/**
 * Side panel listing the open notebook's headings and bookmarked pages. Tapping an entry
 * calls [onJump] with the page to open and, for a heading, where on that page it sits
 * (page coordinates).
 */
@Composable
fun ContentsPanel(
    viewModel: EditorViewModel,
    onJump: (pageId: String, top: Int?) -> Unit,
) {
    val toolbarState by viewModel.toolbarState.collectAsStateWithLifecycle()
    if (!toolbarState.isContentsOpen) return
    val contents by viewModel.contentsState.collectAsStateWithLifecycle()

    val close = { viewModel.onToolbarAction(ToolbarAction.SetContentsOpen(false)) }

    // Full-screen layer: a tap anywhere outside the panel closes it.
    Box(
        Modifier
            .fillMaxSize()
            .noRippleClickable { close() }
    ) {
        ContentsPanelBody(
            contents = contents,
            currentPageId = toolbarState.pageId,
            onClose = close,
            onToggleBookmark = { viewModel.onToolbarAction(ToolbarAction.ToggleBookmark) },
            onDeleteHeading = viewModel::deleteHeading,
            onJump = onJump,
            modifier = Modifier.align(Alignment.CenterStart),
        )
    }
}

@Composable
private fun ContentsPanelBody(
    contents: ContentsUiState,
    currentPageId: String?,
    onClose: () -> Unit,
    onToggleBookmark: () -> Unit,
    onDeleteHeading: (String) -> Unit,
    onJump: (pageId: String, top: Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableStateOf(ContentsTab.Headings) }

    Column(
        modifier
            .fillMaxHeight()
            .width(440.dp)
            .background(Color.White)
            .border(2.dp, Color.Black, RectangleShape)
            // Swallow taps on the panel itself so they don't close it.
            .noRippleClickable { }
            // Swipe the panel left to push it back off screen.
            .pointerInput(Unit) {
                var dragged = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = { if (dragged < -80f) onClose() },
                ) { _, amount -> dragged += amount }
            }
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Contents", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Spacer(Modifier.weight(1f))
            Text(
                "Close",
                fontSize = 16.sp,
                color = Color.Black,
                modifier = Modifier
                    .border(1.dp, Color.Black, RectangleShape)
                    .noRippleClickable { onClose() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth()) {
            TabLabel(
                text = "Headings (${contents.headings.size})",
                selected = tab == ContentsTab.Headings,
                modifier = Modifier.weight(1f),
            ) { tab = ContentsTab.Headings }
            TabLabel(
                text = "Keywords (${contents.keywords.size})",
                selected = tab == ContentsTab.Keywords,
                modifier = Modifier.weight(1f),
            ) { tab = ContentsTab.Keywords }
            TabLabel(
                text = "Bookmarks (${contents.bookmarks.size})",
                selected = tab == ContentsTab.Bookmarks,
                modifier = Modifier.weight(1f),
            ) { tab = ContentsTab.Bookmarks }
        }
        Spacer(Modifier.height(8.dp))

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            when (tab) {
                ContentsTab.Headings -> {
                    if (contents.headings.isEmpty()) {
                        EmptyNote("No headings yet. Lasso some handwriting, then tap the # button.")
                    }
                    contents.headings.forEach { heading ->
                        HeadingRow(
                            heading = heading,
                            isCurrentPage = heading.pageId == currentPageId,
                            onOpen = { onJump(heading.pageId, heading.top) },
                            onDelete = { onDeleteHeading(heading.id) },
                        )
                    }
                }

                ContentsTab.Keywords -> {
                    if (contents.keywords.isEmpty()) {
                        EmptyNote("No keywords yet. Lasso a word, then tap the key button.")
                    }
                    contents.keywords.forEach { keyword ->
                        HeadingRow(
                            heading = keyword,
                            isCurrentPage = keyword.pageId == currentPageId,
                            onOpen = { onJump(keyword.pageId, keyword.top) },
                            onDelete = { onDeleteHeading(keyword.id) },
                        )
                    }
                }

                ContentsTab.Bookmarks -> {
                    if (contents.bookmarks.isEmpty()) {
                        EmptyNote("No bookmarks yet. Use the button below to bookmark this page.")
                    }
                    contents.bookmarks.forEach { bookmark ->
                        BookmarkRow(
                            bookmark = bookmark,
                            isCurrentPage = bookmark.pageId == currentPageId,
                            onOpen = { onJump(bookmark.pageId, null) },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = if (contents.isCurrentPageBookmarked) "Remove bookmark from this page"
            else "Bookmark this page",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, Color.Black, RectangleShape)
                .noRippleClickable { onToggleBookmark() }
                .padding(12.dp)
        )
    }
}

@Composable
private fun TabLabel(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        fontSize = 14.sp,
        maxLines = 1,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) Color.White else Color.Black,
        modifier = modifier
            .background(if (selected) Color.Black else Color.White)
            .border(1.dp, Color.Black, RectangleShape)
            .noRippleClickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 8.dp)
    )
}

@Composable
private fun EmptyNote(text: String) {
    Text(text, fontSize = 15.sp, color = Color.DarkGray, modifier = Modifier.padding(vertical = 16.dp))
}

private fun pageLabel(pageNumber: Int): String = if (pageNumber > 0) "p. $pageNumber" else ""

@Composable
private fun HeadingRow(
    heading: HeadingItem,
    isCurrentPage: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .noRippleClickable { onOpen() }
                .padding(vertical = 8.dp)
        ) {
            Box(Modifier.weight(1f)) {
                if (heading.preview != null) {
                    Image(
                        bitmap = heading.preview,
                        contentDescription = "Heading on page ${heading.pageNumber}",
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.CenterStart,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 64.dp)
                    )
                } else {
                    Text("(heading)", fontSize = 16.sp, color = Color.Black)
                }
            }
            Text(
                pageLabel(heading.pageNumber),
                fontSize = 15.sp,
                fontWeight = if (isCurrentPage) FontWeight.Bold else FontWeight.Normal,
                color = Color.Black,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Text(
                "✕",
                fontSize = 18.sp,
                color = Color.Black,
                modifier = Modifier
                    .noRippleClickable { onDelete() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
        RowDivider()
    }
}

@Composable
private fun BookmarkRow(
    bookmark: BookmarkItem,
    isCurrentPage: Boolean,
    onOpen: () -> Unit,
) {
    Column {
        Text(
            text = if (bookmark.pageNumber > 0) "Page ${bookmark.pageNumber}" else "This page",
            fontSize = 18.sp,
            fontWeight = if (isCurrentPage) FontWeight.Bold else FontWeight.Normal,
            color = Color.Black,
            modifier = Modifier
                .fillMaxWidth()
                .noRippleClickable { onOpen() }
                .padding(vertical = 14.dp)
        )
        RowDivider()
    }
}

@Composable
private fun RowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0xFF999999))
    )
}
