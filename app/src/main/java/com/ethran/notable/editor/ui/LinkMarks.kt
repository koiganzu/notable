package com.ethran.notable.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ethran.notable.editor.EditorViewModel
import com.ethran.notable.editor.PageView

/**
 * A dashed box round each link on the open page, so it reads as something to tap. Drawn over
 * the page, never into it: it doesn't print or export.
 */
@Composable
fun LinkMarks(viewModel: EditorViewModel, page: PageView) {
    val contents by viewModel.contentsState.collectAsStateWithLifecycle()
    val viewport by page.viewport.collectAsStateWithLifecycle()
    val pageId = page.currentPageId
    val links = contents.links.filter { it.pageId == pageId }
    if (links.isEmpty()) return
    Canvas(Modifier.fillMaxSize()) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
        for (link in links) {
            val left = (link.left - viewport.scroll.x) * viewport.zoom - 6f
            val top = (link.top - viewport.scroll.y) * viewport.zoom - 6f
            val right = (link.right - viewport.scroll.x) * viewport.zoom + 6f
            val bottom = (link.bottom - viewport.scroll.y) * viewport.zoom + 6f
            if (right < 0 || bottom < 0 || left > size.width || top > size.height) continue
            drawRoundRect(
                color = Color.Black,
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
                cornerRadius = CornerRadius(10f, 10f),
                style = Stroke(width = 3f, pathEffect = dash),
            )
            // A small filled corner tab marks it as a link rather than a selection.
            drawCircle(Color.Black, radius = 7f, center = Offset(right, top))
        }
    }
}
