package com.ethran.notable.daybook

import android.graphics.Rect
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.StructuredText
import io.shipbook.shipbooksdk.ShipBook
import java.io.File

/**
 * Reads the printed text of a PDF page that lies under a marked area: the passage a reader
 * bracketed, underlined, highlighted or circled with the pen.
 */
object PdfText {
    private val log = ShipBook.getLogger("PdfText")

    /**
     * The text of page [pageIndex] of the PDF at [path] under [area].
     *
     * [area] is in notebook page coordinates, where the PDF page is drawn [pageWidthPx] wide
     * with its top-left corner at the origin. How the area is read depends on its shape:
     *  - tall and narrow (a bracket in the margin): every full line beside it;
     *  - anything else: from the area's left edge on its first line to its right edge on its
     *    last line, the way dragging a text selection works. A flat mark just under a line
     *    (an underline) picks up the line above it.
     *
     * Returns null when the file can't be read, and an empty string when there is no text
     * there (a scanned PDF has none).
     */
    fun under(path: String, pageIndex: Int, area: Rect, pageWidthPx: Int): String? {
        if (pageWidthPx <= 0 || area.isEmpty) return ""
        if (!File(path).exists()) return null

        var document: Document? = null
        try {
            document = Document.openDocument(path)
            if (pageIndex !in 0 until document.countPages()) return ""
            val page = document.loadPage(pageIndex)
            try {
                val bounds = page.bounds
                val pageWidthPt = bounds.x1 - bounds.x0
                if (pageWidthPt <= 0f) return ""
                val toPdf = pageWidthPt / pageWidthPx

                val left = bounds.x0 + area.left * toPdf
                val right = bounds.x0 + area.right * toPdf
                val top = bounds.y0 + area.top * toPdf
                val bottom = bounds.y0 + area.bottom * toPdf
                val width = right - left
                val height = bottom - top
                val isBracket = height > width * 2f && width < pageWidthPt * 0.2f

                val text = page.toStructuredText()
                try {
                    return collect(text, left, right, top, bottom, isBracket)
                } finally {
                    text.destroy()
                }
            } finally {
                page.destroy()
            }
        } catch (e: Exception) {
            log.e("Couldn't read text from $path page $pageIndex", e)
            return null
        } catch (e: UnsatisfiedLinkError) {
            log.e("PDF text library missing", e)
            return null
        } catch (e: NoClassDefFoundError) {
            log.e("PDF text library missing", e)
            return null
        } finally {
            document?.destroy()
        }
    }

    private fun collect(
        text: StructuredText,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
        isBracket: Boolean,
    ): String {
        val lines = text.blocks.orEmpty()
            .flatMap { block -> block?.lines?.toList().orEmpty() }
            .filterNotNull()
            .filter { it.bbox != null && it.chars != null && it.bbox.y1 > it.bbox.y0 }
            .sortedWith(compareBy({ it.bbox.y0 }, { it.bbox.x0 }))

        // Lines the area covers: at least 40% of the line's height is inside it, and (unless
        // it is a margin bracket) the line is at least partly beneath it left to right.
        fun besideHorizontally(line: StructuredText.TextLine) =
            isBracket || (line.bbox.x1 > left && line.bbox.x0 < right)

        var chosen = lines.filter { line ->
            val overlap = minOf(bottom, line.bbox.y1) - maxOf(top, line.bbox.y0)
            overlap >= (line.bbox.y1 - line.bbox.y0) * 0.4f && besideHorizontally(line)
        }
        // An underline covers no line: take the nearest one sitting just above it.
        if (chosen.isEmpty()) {
            chosen = listOfNotNull(
                lines.filter { line ->
                    val lineHeight = line.bbox.y1 - line.bbox.y0
                    besideHorizontally(line) &&
                            line.bbox.y1 <= bottom && top - line.bbox.y1 <= lineHeight * 1.2f
                }.maxByOrNull { it.bbox.y1 }
            )
        }
        if (chosen.isEmpty()) return ""

        val result = StringBuilder()
        chosen.forEachIndexed { index, line ->
            val isFirst = index == 0
            val isLast = index == chosen.lastIndex
            val piece = StringBuilder()
            for (char in line.chars) {
                if (char == null) continue
                val quad = char.quad
                if (!isBracket && quad != null) {
                    val middle = (quad.ul_x + quad.ur_x) / 2f
                    if (isFirst && middle < left) continue
                    if (isLast && middle > right) continue
                }
                piece.appendCodePoint(char.c)
            }
            val cleaned = piece.toString().trim()
            if (cleaned.isEmpty()) return@forEachIndexed
            // A word split across lines with a hyphen is rejoined.
            if (result.endsWith("-") && result.length > 1 && result[result.length - 2].isLetter()) {
                result.setLength(result.length - 1)
            } else if (result.isNotEmpty()) {
                result.append(' ')
            }
            result.append(cleaned)
        }
        return result.toString().replace(Regex("\\s+"), " ").trim()
    }
}
