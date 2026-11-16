package com.ethran.notable.daybook

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import com.ethran.notable.data.ensureImagesFolder
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** A run of text in a [TextImage]: one paragraph in one size and weight. */
data class TextBlock(
    val text: String,
    val sizeSp: Float,
    val bold: Boolean = false,
    val grey: Boolean = false,
)

/** A picture saved in the notebook image folder, with its size in pixels. */
data class SavedImage(val file: File, val width: Int, val height: Int)

/**
 * Draws typed text into a picture that can be placed on a handwritten page: the date and
 * schedule at the top of a planner page, or a saved passage on a digest note page.
 */
object TextImage {
    private const val MAX_HEIGHT_PX = 3200

    /**
     * Renders [blocks] top to bottom, [widthPx] wide, and saves the picture as a PNG.
     * [quoteBar] draws a vertical rule down the left side, marking the text as a quotation.
     */
    fun save(
        context: Context,
        blocks: List<TextBlock>,
        widthPx: Int,
        quoteBar: Boolean = false,
        underline: Boolean = false,
    ): SavedImage {
        val metrics = context.resources.displayMetrics
        fun px(dp: Float) =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, metrics).toInt()

        val padding = px(8f)
        val barWidth = if (quoteBar) px(4f) else 0
        val textLeft = padding + if (quoteBar) barWidth + px(12f) else 0
        val textWidth = (widthPx - textLeft - padding).coerceAtLeast(px(80f))
        val gap = px(6f)

        val layouts = blocks.filter { it.text.isNotBlank() }.map { block ->
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (block.grey) Color.DKGRAY else Color.BLACK
                textSize = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP, block.sizeSp, metrics
                )
                typeface = if (block.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
            StaticLayout.Builder
                .obtain(block.text, 0, block.text.length, paint, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.15f)
                .setIncludePad(true)
                .build()
        }

        val contentHeight = layouts.sumOf { it.height } + gap * (layouts.size - 1).coerceAtLeast(0)
        val ruleSpace = if (underline) px(10f) else 0
        val height = (contentHeight + padding * 2 + ruleSpace).coerceIn(px(24f), MAX_HEIGHT_PX)

        val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }

            var y = padding.toFloat()
            for (layout in layouts) {
                canvas.save()
                canvas.translate(textLeft.toFloat(), y)
                layout.draw(canvas)
                canvas.restore()
                y += layout.height + gap
            }
            if (quoteBar) {
                canvas.drawRect(
                    padding.toFloat(), padding.toFloat(),
                    (padding + barWidth).toFloat(), (height - padding).toFloat(), ink
                )
            }
            if (underline) {
                val lineY = height - px(4f).toFloat()
                canvas.drawRect(
                    padding.toFloat(), lineY - px(2f), (widthPx - padding).toFloat(), lineY, ink
                )
            }

            val file = File(ensureImagesFolder(), "daybook-${UUID.randomUUID()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            return SavedImage(file, widthPx, height)
        } finally {
            bitmap.recycle()
        }
    }
}
