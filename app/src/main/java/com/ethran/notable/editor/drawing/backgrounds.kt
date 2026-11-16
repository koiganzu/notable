package com.ethran.notable.editor.drawing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import com.ethran.notable.SCREEN_HEIGHT
import com.ethran.notable.SCREEN_WIDTH
import com.ethran.notable.data.datastore.GlobalAppSettings
import com.ethran.notable.data.model.BackgroundType
import com.ethran.notable.editor.utils.scaleRect
import com.onyx.android.sdk.extension.copy
import io.shipbook.shipbooksdk.ShipBook
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private val log = ShipBook.getLogger("BackgroundsLog")

const val padding = 0
const val lineHeight = 80
const val dotSize = 6f
const val hexVerticalCount = 26


// Default paint for lines, dots, etc
private val defaultPaint = Paint().apply {
    this.color = Color.GRAY
    this.strokeWidth = 1f
}

// For drawing Hexagons
private val defaultPaintStroke = defaultPaint.copy().apply { this.style = Paint.Style.STROKE }
private val marginPaint = Paint().apply {
    this.color = Color.MAGENTA
    this.strokeWidth = 2f
}
private val paginationLinePaint = Paint().apply {
    color = Color.RED
    strokeWidth = 4f
    pathEffect = DashPathEffect(floatArrayOf(10f, 5f), 0f)
}

fun drawLinedBg(canvas: Canvas, scroll: Offset, scale: Float) {
    val height = (canvas.height / scale).toInt()
    val width = (canvas.width / scale).toInt()

    // white bg
    canvas.drawColor(Color.WHITE)


    val offset = IntOffset(lineHeight, lineHeight) - IntOffset(
        scroll.x.toInt() % lineHeight, scroll.y.toInt() % lineHeight
    )

    for (y in 0..height step lineHeight) {
        canvas.drawLine(
            padding.toFloat(),
            y.toFloat() + offset.y,
            (width - padding).toFloat(),
            y.toFloat() + offset.y,
            defaultPaint
        )
    }
}

fun drawDottedBg(canvas: Canvas, scroll: Offset, scale: Float) {
    val height = (canvas.height / scale).toInt()
    val width = (canvas.width / scale).toInt()
    // white bg
    canvas.drawColor(Color.WHITE)


    // dots
    val offset = IntOffset(lineHeight, lineHeight) - IntOffset(
        scroll.x.toInt() % lineHeight, scroll.y.toInt() % lineHeight
    )

    for (y in 0..height step lineHeight) {
        for (x in padding..width - padding step lineHeight) {
            canvas.drawOval(
                x.toFloat() + offset.x - dotSize / 2,
                y.toFloat() + offset.y - dotSize / 2,
                x.toFloat() + offset.x + dotSize / 2,
                y.toFloat() + offset.y + dotSize / 2,
                defaultPaint
            )
        }
    }

}

fun drawSquaredBg(canvas: Canvas, scroll: Offset, scale: Float) {
    val height = (canvas.height / scale).toInt()
    val width = (canvas.width / scale).toInt()

    // white bg
    canvas.drawColor(Color.WHITE)

    // paint

    val offset = IntOffset(lineHeight, lineHeight) - IntOffset(
        scroll.x.toInt() % lineHeight, scroll.y.toInt() % lineHeight
    )

    for (y in 0..height step lineHeight) {
        canvas.drawLine(
            padding.toFloat(),
            y.toFloat() + offset.y,
            (width - padding).toFloat(),
            y.toFloat() + offset.y,
            defaultPaint
        )
    }

    for (x in padding..width - padding step lineHeight) {
        canvas.drawLine(
            x.toFloat() + offset.x,
            padding.toFloat(),
            x.toFloat() + offset.x,
            height.toFloat(),
            defaultPaint
        )
    }
}

fun drawHexedBg(canvas: Canvas, scroll: Offset, scale: Float) {
    val height = (canvas.height / scale)
    val width = (canvas.width / scale)

    // background
    canvas.drawColor(Color.WHITE)


    // https://www.redblobgames.com/grids/hexagons/#spacing
    val r = max(width, height) / (hexVerticalCount * 1.5f) * scale
    val hexHeight = r * 2
    val hexWidth = r * sqrt(3f)

    val rows = (height / hexVerticalCount).toInt()
    val cols = (width / hexWidth).toInt() + 1

    for (row in 0..rows) {
        val offsetX = if (row % 2 == 0) 0f else hexWidth / 2
        for (col in 0..cols) {
            val x = col * hexWidth + offsetX - scroll.x.mod(hexWidth) - hexWidth
            val y = row * hexHeight * 0.75f - scroll.y.mod(hexHeight * 1.5f)
            drawHexagon(canvas, x, y, r)
        }
    }
}

private val tplLine = Paint().apply {
    color = Color.GRAY
    strokeWidth = 2f
}
private val tplHeavy = Paint().apply {
    color = Color.BLACK
    strokeWidth = 5f
}
private val tplText = Paint().apply {
    color = Color.DKGRAY
    textSize = 30f
    isAntiAlias = true
}
private val tplBox = Paint().apply {
    color = Color.DKGRAY
    strokeWidth = 3f
    style = Paint.Style.STROKE
}

/** Sales-oriented page layouts: meeting, call log, Cornell notes and a checklist. */
fun drawTemplateBg(canvas: Canvas, scroll: Offset, scale: Float, kind: String) {
    val w = canvas.width / scale
    val h = canvas.height / scale
    canvas.drawColor(Color.WHITE)
    val sx = scroll.x
    val sy = scroll.y

    fun line(y: Float, x1: Float, x2: Float, paint: Paint = tplLine) =
        canvas.drawLine(x1 - sx, y - sy, x2 - sx, y - sy, paint)

    fun field(label: String, y: Float, x1: Float, x2: Float) {
        line(y, x1, x2)
        canvas.drawText(label, x1 + 6f - sx, y - 12f - sy, tplText)
    }

    fun band(label: String, y: Float, x1: Float, x2: Float) {
        line(y, x1, x2, tplHeavy)
        canvas.drawText(label, x1 + 6f - sx, y - 14f - sy, tplText)
    }

    fun ruled(startY: Float, x1: Float, x2: Float, boxes: Boolean = false) {
        val first = kotlin.math.max(0, kotlin.math.floor((sy - startY) / lineHeight).toInt())
        var y = startY + first * lineHeight
        while (y - sy <= h) {
            line(y, x1, x2)
            if (boxes) {
                val b = 36f
                canvas.drawRect(
                    x1 + 8f - sx, y - b - 10f - sy, x1 + 8f + b - sx, y - 10f - sy, tplBox
                )
            }
            y += lineHeight
        }
    }

    fun vertical(x: Float, y1: Float) =
        canvas.drawLine(x - sx, y1 - sy, x - sx, h + sy + 10f - sy, tplLine)

    when (kind) {
        "meeting" -> {
            val split = w * 0.64f
            field("Customer", 110f, 0f, w * 0.6f)
            field("Date", 110f, w * 0.62f, w)
            field("Attendees", 200f, 0f, w)
            field("Goal / topic", 290f, 0f, w)
            band("NOTES", 370f, 0f, split)
            band("ACTION ITEMS", 370f, split + 12f, w)
            vertical(split + 6f, 370f)
            ruled(370f + lineHeight, 0f, split)
            ruled(370f + lineHeight, split + 12f, w, boxes = true)
        }

        "call" -> {
            field("Contact", 110f, 0f, w * 0.5f)
            field("Company", 110f, w * 0.52f, w)
            field("Phone", 200f, 0f, w * 0.5f)
            field("Date / time", 200f, w * 0.52f, w)
            field("Machine / interest", 290f, 0f, w * 0.5f)
            field("Budget / timeline", 290f, w * 0.52f, w)
            field("Next step", 380f, 0f, w * 0.64f)
            field("Follow-up date", 380f, w * 0.66f, w)
            band("NOTES", 450f, 0f, w)
            ruled(450f + lineHeight, 0f, w)
        }

        "cornell" -> {
            val cue = w * 0.3f
            field("Topic", 110f, 0f, w * 0.65f)
            field("Date", 110f, w * 0.67f, w)
            line(170f, 0f, w, tplHeavy)
            canvas.drawText("Cues / questions", 6f - sx, 210f - sy, tplText)
            canvas.drawText("Notes", cue + 18f - sx, 210f - sy, tplText)
            vertical(cue, 170f)
            ruled(170f + lineHeight, cue + 12f, w)
        }

        "todo" -> {
            field("To do", 110f, 0f, w * 0.65f)
            field("Date", 110f, w * 0.67f, w)
            ruled(110f + lineHeight, 0f, w, boxes = true)
        }
    }
}

fun drawHexagon(canvas: Canvas, centerX: Float, centerY: Float, r: Float) {
    val path = Path()
    for (i in 0..5) {
        val angle = Math.toRadians((30 + 60 * i).toDouble())
        val x = (centerX + r * cos(angle)).toFloat()
        val y = (centerY + r * sin(angle)).toFloat()
        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    path.close()
    canvas.drawPath(path, defaultPaintStroke)
}


fun drawTitleBox(canvas: Canvas) {

    // Draw label-like area in center
    val paint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    val borderPaint = Paint().apply {
        color = Color.DKGRAY
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    // This might not be actual width in some situations
    // investigate it, in case of problems
    val canvasHeight = max(SCREEN_WIDTH, SCREEN_HEIGHT)
    val canvasWidth = min(SCREEN_WIDTH, SCREEN_HEIGHT)

    // Dimensions for the label box
    val labelWidth = canvasWidth * 0.8f
    val labelHeight = canvasHeight * 0.25f
    val left = (canvasWidth - labelWidth) / 2
    val top = (canvasHeight - labelHeight) / 2
    val right = left + labelWidth
    val bottom = top + labelHeight

    val rectF = RectF(left, top, right, bottom)
    val cornerRadius = 64f

    canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint)
    canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, borderPaint)
}


// Backgrounds are rendered oversampled (see loadBackgroundBitmap's resolutionModifier) and drawn
// downscaled onto the screen canvas. Bilinear filtering + dithering makes that downscale crisp
// instead of aliased, and the dither maps grey edges cleanly onto e-ink. Costs no extra memory.
private val bgBitmapPaint = Paint().apply {
    isFilterBitmap = true
    isAntiAlias = true
    isDither = true
}

fun drawBitmapToCanvas(
    canvas: Canvas, imageBitmap: Bitmap, scroll: Offset, scale: Float, repeat: Boolean
) {
    canvas.drawColor(Color.WHITE)
    val imageWidth = imageBitmap.width
    val imageHeight = imageBitmap.height


//    val canvasWidth = canvas.width
    val canvasHeight = canvas.height
    val widthOnCanvas = min(SCREEN_WIDTH, SCREEN_HEIGHT)

    val scaleFactor = widthOnCanvas.toFloat() / imageWidth
    val scaledHeight = (imageHeight * scaleFactor).toInt()

    // TODO: It's working, but its not nice -- do it in better style.
    // Draw the first image, considering the scroll offset
    val srcTop = Offset(
        (scroll.x / scaleFactor).coerceAtLeast(0f),
        ((scroll.y / scaleFactor) % imageHeight).coerceAtLeast(0f)
    )
    val rectOnImage = Rect(0, srcTop.y.toInt(), imageWidth, imageHeight)
    val rectOnCanvas = Rect(
        -scroll.x.toInt(),
        0,
        widthOnCanvas - scroll.x.toInt(),
        ((imageHeight - srcTop.y) * scaleFactor).toInt()
    )

    var filledHeight = 0
    if (repeat || scroll.y < canvasHeight) {
        canvas.drawBitmap(imageBitmap, rectOnImage, rectOnCanvas, bgBitmapPaint)
        filledHeight = rectOnCanvas.bottom
    }
    // TODO: Should we also repeat horizontally?

    if (repeat) {
        var currentTop = filledHeight
        val srcRect = Rect(0, 0, imageWidth, imageHeight)
        while (currentTop < canvasHeight / scale) {

            val dstRect = Rect(
                -scroll.x.toInt(),
                currentTop,
                widthOnCanvas - scroll.x.toInt(),
                currentTop + scaledHeight
            )
            canvas.drawBitmap(imageBitmap, srcRect, dstRect, bgBitmapPaint)
            currentTop += scaledHeight
        }
    }
}

fun drawBg(
    canvas: Canvas,
    backgroundType: BackgroundType,
    background: String,
    scroll: Offset = Offset.Zero,
    resourceBitmap: Bitmap?,
    scale: Float = 1f,          // When exporting, we change scale of canvas. therefore canvas.width/height is scaled
    repeat: Boolean = false,    // for repeating image
    clipRect: Rect? = null,     // before the scaling
) {

    log.v("Loading the background")
    clipRect?.let {
        canvas.save()
        canvas.clipRect(scaleRect(it, scale))
    }
    when (backgroundType) {
        // draw native background for it we don't need a resource
        is BackgroundType.Native -> {
            when (background) {
                "blank" -> canvas.drawColor(Color.WHITE)
                "dotted" -> drawDottedBg(canvas, scroll, scale)
                "lined" -> drawLinedBg(canvas, scroll, scale)
                "squared" -> drawSquaredBg(canvas, scroll, scale)
                "hexed" -> drawHexedBg(canvas, scroll, scale)
                "meeting", "call", "cornell", "todo" ->
                    drawTemplateBg(canvas, scroll, scale, background)

                else -> {
                    // Reaching the Native branch with an unknown value usually means the *type*
                    // detection misclassified a non-native background (e.g. a .pdf) as Native, not
                    // that the string is merely unknown — surface the type so it's obvious.
                    throw IllegalArgumentException(
                        "Unknown native background '$background' (type=$backgroundType)"
                    )
                }
            }
        }

        else -> {
            if (resourceBitmap != null) {
                drawBitmapToCanvas(canvas, resourceBitmap, scroll, scale, repeat)
                if (backgroundType is BackgroundType.CoverImage) {
                    drawTitleBox(canvas)
                }
            } else {
                log.i("No resource provided to draw, maybe out of pages in pdf?")
                canvas.drawColor(Color.WHITE)
            }
        }
    }
    drawMargin(canvas, scroll, scale)

    if (GlobalAppSettings.current.visualizePdfPagination) {
        drawPaginationLine(canvas, scroll, scale)
    }
    if (clipRect != null) {
        canvas.restore()
    }
}

// TODO: make sure it respects horizontal scroll
fun drawMargin(canvas: Canvas, scroll: Offset, scale: Float) {
    // in landscape orientation add margin to indicate what will be visible in vertical orientation.
    if (SCREEN_WIDTH > SCREEN_HEIGHT || scale < 1.0f || scroll.x > 1) {
        val margin = min(SCREEN_HEIGHT, SCREEN_WIDTH) - scroll.x
        // Draw vertical line with x= SCREEN_HEIGHT
        canvas.drawLine(
            margin, padding.toFloat(), margin, (SCREEN_HEIGHT / scale - padding), marginPaint
        )
    }
}

fun drawPaginationLine(canvas: Canvas, scroll: Offset, scale: Float) {
    val textPaint = Paint().apply {
        color = Color.BLACK
        textSize = 24f
        isAntiAlias = true
    }

    // A4 paper ratio (height/width in portrait)
    val a4Ratio = 297f / 210f
    val screenWidth = min(SCREEN_HEIGHT, SCREEN_WIDTH)
    val pageHeight = screenWidth * a4Ratio

    // Convert scroll position to canvas coordinates
    // Calculate current page number (1-based)
    val currentPage = floor(scroll.y / pageHeight).toInt() + 1

    // Calculate position of first page break
    var yPos = (currentPage * pageHeight) - scroll.y

    var pageNum = currentPage
    while (yPos < canvas.height / scale) {
        if (yPos >= 0) { // Only draw visible lines
            val yPosScaled = yPos
            canvas.drawLine(
                0f, yPosScaled, screenWidth.toFloat(), yPosScaled, paginationLinePaint
            )

            // Draw page number label (offset slightly below the line)
            canvas.drawText(
                "Subpage ${pageNum + 1}", 20f - scroll.x, yPosScaled + 30f, textPaint
            )
        } else {
            log.d("Skipping line at $yPos (above visible area)")
        }
        yPos += pageHeight
        pageNum++
    }
}