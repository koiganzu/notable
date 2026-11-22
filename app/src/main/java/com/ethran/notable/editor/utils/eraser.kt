package com.ethran.notable.editor.utils

import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import com.ethran.notable.data.datastore.GlobalAppSettings
import com.ethran.notable.data.db.MAX_PRESSURE_NORMALIZED
import com.ethran.notable.data.db.Stroke
import com.ethran.notable.data.db.StrokePoint
import com.ethran.notable.data.model.SimplePointF
import com.ethran.notable.editor.PageView
import com.ethran.notable.editor.state.History
import com.ethran.notable.editor.state.Operation
import io.shipbook.shipbooksdk.Log

enum class Eraser(val _name: String) {
    PEN("PEN"), SELECT("SELECT"),
}

const val SCRIBBLE_TO_ERASE_GRACE_PERIOD_MS = 150L
const val SCRIBBLE_INTERSECTION_THRESHOLD = 0.20f

/**
 * Width (px) of the pen-eraser swath: the diameter of the region [handleErase] actually deletes.
 * Shared so the native side-button eraser indicator (see einkHelper.enableNativeEraser) is drawn
 * at exactly the size it erases.
 */
const val ERASER_SWATH_WIDTH = 30f

const val MINIMUM_SCRIBBLE_POINTS = 15


// Calculates total stroke length using Manhattan distance
private fun calculateStrokeLength(points: List<StrokePoint>): Float {
    var totalDistance = 0.0f
    for (i in 1 until points.size) {
        val dx = points[i].x - points[i - 1].x
        val dy = points[i].y - points[i - 1].y
        totalDistance += kotlin.math.abs(dx) + kotlin.math.abs(dy)
    }
    return totalDistance
}

// Counts the number of direction changes (sharp reversals) in a stroke
private fun calculateNumReversals(
    points: List<StrokePoint>, stepSize: Int = 10
): Int {
    var numReversals = 0
    for (i in 0 until points.size - 2 * stepSize step stepSize) {
        val p1 = points[i]
        val p2 = points[i + stepSize]
        val p3 = points[i + 2 * stepSize]
        val segment1 = SimplePointF(p2.x - p1.x, p2.y - p1.y)
        val segment2 = SimplePointF(p3.x - p2.x, p3.y - p2.y)
        val dotProduct = segment1.x * segment2.x + segment1.y * segment2.y
        // Reversal is detected when angle between segments > 90 degrees
        if (dotProduct < 0) {
            numReversals++
        }
    }
    return numReversals
}


// Filters strokes that significantly intersect with a given bounding box
private fun filterStrokesByIntersection(
    candidateStrokes: List<Stroke>,
    boundingBox: RectF,
    threshold: Float = SCRIBBLE_INTERSECTION_THRESHOLD
): List<Stroke> {
    return candidateStrokes.filter { stroke ->
        val strokeRect = strokeBounds(stroke)
        val intersection = RectF()

        if (intersection.setIntersect(strokeRect, boundingBox)) {
            val strokeArea = strokeRect.width() * strokeRect.height()
            val intersectionArea = intersection.width() * intersection.height()
            val intersectionRatio = if (strokeArea > 0) intersectionArea / strokeArea else 0f

            intersectionRatio >= threshold
        } else {
            false
        }
    }
}

// Erases strokes if touchPoints are "scribble", returns true if erased.
// returns null if not erased, dirty rectangle otherwise
fun handleScribbleToErase(
    page: PageView,
    touchPoints: List<StrokePoint>,
    history: History,
    pen: Pen,
    strokeSize: Float,
    color: Int,
    currentLastStrokeEndTime: Long,
    firstPointTime: Long
): Rect? {
    if (pen == Pen.MARKER) return null // do not erase with highlighter
    if (!GlobalAppSettings.current.scribbleToEraseEnabled) return null // scribble to erase is disabled
    if (touchPoints.size < MINIMUM_SCRIBBLE_POINTS) return null
    if (firstPointTime < currentLastStrokeEndTime + SCRIBBLE_TO_ERASE_GRACE_PERIOD_MS) return null // not enough time has passed since last stroke
    if (calculateNumReversals(touchPoints) < 2) return null

    val strokeLength = calculateStrokeLength(touchPoints)
    val boundingBox = calculateBoundingBox(touchPoints) { Pair(it.x, it.y) }
    val width = boundingBox.width()
    val height = boundingBox.height()
    if (width == 0f || height == 0f) return null

    // Require scribble to be long enough relative to bounding box
    val minLengthForScribble = (width + height) * 3
    if (strokeLength < minLengthForScribble) {
        Log.d("ScribbleToErase", "Stroke is too short, $strokeLength < $minLengthForScribble")
        return null
    }

    // calculate stroke width based on bounding box
    // bigger swinging in scribble = bigger bounding box => larger stroke size
    val minDim = kotlin.math.min(boundingBox.width(), boundingBox.height())
    val maxDim = kotlin.math.max(boundingBox.width(), boundingBox.height())
    val aspectRatio = if (minDim > 0) maxDim / minDim else 1f
    val scaleFactor = kotlin.math.min(1f + (aspectRatio - 1f) / 2f, 2f)
    val strokeSizeForDetection = minDim * 0.15f * scaleFactor


    // Get strokes that might intersect with the scribble path
    val path = pointsToPath(touchPoints.map { SimplePointF(it.x, it.y) })
    val outPath = Path()
    Paint().apply { this.strokeWidth = strokeSizeForDetection }.getFillPath(path, outPath)
    val candidateStrokes = selectStrokesFromPath(page.strokes, outPath)


    // Filter intersecting strokes based on intersection ratio
    val expandedBoundingBox = boundingBox.expandBy(strokeSizeForDetection / 2)
    val deletedStrokes = filterStrokesByIntersection(candidateStrokes, expandedBoundingBox)

    // If strokes were found, remove them and update history
    if (deletedStrokes.isNotEmpty()) {
        val deletedStrokeIds = deletedStrokes.map { it.id }
        page.removeStrokes(deletedStrokeIds)

        // Build the scribble as a real stroke (not added to the page — the net visible result is
        // still "everything gone") so undo can bring it back. Two history blocks make undo staged:
        //   undo #1 (top block)    → re-add the erased strokes AND the scribble
        //   undo #2 (older block)  → remove the scribble again (leaving just the erased strokes)
        // Pushed older-first because undo pops the most-recent block first.
        val scribbleBox = calculateBoundingBox(touchPoints) { Pair(it.x, it.y) }
        scribbleBox.inset(-strokeSize, -strokeSize)
        val scribbleStroke = Stroke(
            size = strokeSize,
            pen = pen,
            pageId = page.currentPageId,
            top = scribbleBox.top,
            bottom = scribbleBox.bottom,
            left = scribbleBox.left,
            right = scribbleBox.right,
            points = touchPoints,
            color = color,
            // Pressure is normalized to [0,1] at capture (TouchPoint.toStrokePoint).
            maxPressure = MAX_PRESSURE_NORMALIZED
        )
        history.addOperationsToHistory(page.currentPageId, listOf(Operation.DeleteStroke(listOf(scribbleStroke.id))))
        history.addOperationsToHistory(page.currentPageId, listOf(Operation.AddStroke(deletedStrokes + scribbleStroke)))
        // Return the erased region in SCREEN coordinates (mirrors handleErase). The caller
        // pushes this rect to the SurfaceView/EPD via commitErase, and the surface bitmap
        // (windowedBitmap) is in screen space — returning page coords here pushed the wrong
        // region whenever scrolled/zoomed. The caller unions this with the scribble track so
        // the firmware ink is cleared in the same post. See docs/onyx-sdk/onyx-scribble-to-erase.md.
        val effectedArea = page.toScreenCoordinates(strokeBounds(deletedStrokes))
        page.drawAreaScreenCoordinates(screenArea = effectedArea)
        return effectedArea
    }
    return null
}


// points is in page coordinates, returns effected area.
fun handleErase(
    page: PageView, history: History, points: List<SimplePointF>, eraser: Eraser
): Rect? {
    val paint = Paint().apply {
        this.strokeWidth = ERASER_SWATH_WIDTH
        this.style = Paint.Style.STROKE
        this.strokeCap = Paint.Cap.ROUND
        this.strokeJoin = Paint.Join.ROUND
        this.isAntiAlias = true
    }
    val path = pointsToPath(points)
    var outPath = Path()

    if (eraser == Eraser.SELECT) {
        path.close()
        outPath = path
    }


    if (eraser == Eraser.PEN) {
        paint.getFillPath(path, outPath)
    }

    val deletedStrokes = selectStrokesFromPath(page.strokes, outPath)

    val deletedStrokeIds = deletedStrokes.map { it.id }

    if (deletedStrokes.isEmpty()) return null
    page.removeStrokes(deletedStrokeIds)

    history.addOperationsToHistory(page.currentPageId, listOf(Operation.AddStroke(deletedStrokes)))

    val effectedArea = page.toScreenCoordinates(strokeBounds(deletedStrokes))
    page.drawAreaScreenCoordinates(screenArea = effectedArea)
    return effectedArea
}


// points is in page coordinates, returns effected area.
fun cleanAllStrokes(
    page: PageView, history: History
): Rect? {
    val deletedStrokes = page.strokes
    val deletedStrokeIds = deletedStrokes.map { it.id }
    if (deletedStrokes.isEmpty()) return null

    page.removeStrokes(deletedStrokeIds)
    history.addOperationsToHistory(page.currentPageId, listOf(Operation.AddStroke(deletedStrokes)))

    val effectedArea = page.toScreenCoordinates(strokeBounds(deletedStrokes))
    page.drawAreaScreenCoordinates(screenArea = effectedArea)
    return effectedArea
}

/**
 * True when [touchPoints] is one closed loop drawn around handwriting already on the page: the
 * pen comes back to where it started and the loop holds at least one stroke. A loop around
 * nothing (a letter O, a zero, an empty box) is not one, so it is drawn as ink.
 */
fun isCircleAroundStrokes(page: PageView, touchPoints: List<StrokePoint>, pen: Pen): Boolean {
    if (!GlobalAppSettings.current.circleToSelect) return false
    if (pen == Pen.MARKER) return false // highlighting over words must stay highlighting
    if (touchPoints.size < 20) return false
    val box = calculateBoundingBox(touchPoints) { Pair(it.x, it.y) }
    val width = box.width()
    val height = box.height()
    if (width < 70f || height < 50f) return false

    // Closed: somewhere in its last stretch the pen passes close to where it began. Hand-drawn
    // loops rarely end exactly on their start; they stop short or run on past it.
    val headEnd = (touchPoints.size * 0.15f).toInt().coerceAtLeast(1)
    val tailStart = (touchPoints.size * 0.6f).toInt()
    var gap = Float.MAX_VALUE
    for (i in 0 until headEnd) {
        val a = touchPoints[i]
        for (j in tailStart until touchPoints.size) {
            val b = touchPoints[j]
            val d = kotlin.math.hypot(a.x - b.x, a.y - b.y)
            if (d < gap) gap = d
        }
    }
    if (gap > kotlin.math.max(width, height) * 0.35f) return false

    // About once round, not a scribble or a spiral: an oval's edge is about 1.6 x (width +
    // height), and an overlapped join adds some.
    val length = calculateStrokeLength(touchPoints)
    if (length < (width + height) * 1.1f || length > (width + height) * 3.2f) return false

    val loop = pointsToPath(touchPoints.map { SimplePointF(it.x, it.y) }).apply { close() }
    return selectStrokesFromPath(page.strokes, loop).isNotEmpty()
}


/**
 * Smart straight line: when the pen is held still at the end of a stroke for a moment, the
 * stroke becomes a straight line from where it began to where the pen rested. Returns the new
 * points, or null to keep the stroke as drawn. [times] are the input's timestamps (ms).
 */
fun straightenIfHeld(points: List<StrokePoint>, times: List<Long>): List<StrokePoint>? {
    if (!GlobalAppSettings.current.smartStraightLine) return null
    if (points.size < 8 || times.size != points.size) return null
    val last = points.last()
    val stillRadius = 9f
    // Walk back from the end while the pen stays within a few pixels of where it stopped.
    var k = points.size - 1
    while (k > 0 && kotlin.math.hypot(points[k - 1].x - last.x, points[k - 1].y - last.y) <= stillRadius) k--
    val held = times.last() - times[k]
    if (held < 550 || k < 3) return null

    val start = points.first()
    val end = points[k]
    val length = kotlin.math.hypot(end.x - start.x, end.y - start.y)
    if (length < 90f) return null
    // Only lines that were roughly straight already: a held curve or a word stays as written.
    val dx = (end.x - start.x) / length
    val dy = (end.y - start.y) / length
    var worst = 0f
    for (i in 0..k) {
        val p = points[i]
        val off = kotlin.math.abs((p.x - start.x) * dy - (p.y - start.y) * dx)
        if (off > worst) worst = off
    }
    if (worst > length * 0.12f + 6f) return null

    val pressures = points.subList(0, k + 1).mapNotNull { it.pressure }.sorted()
    val pressure = pressures.getOrNull(pressures.size / 2)
    val steps = (length / 3f).toInt().coerceAtLeast(2)
    return (0..steps).map { i ->
        val t = i.toFloat() / steps
        StrokePoint(
            x = start.x + (end.x - start.x) * t,
            y = start.y + (end.y - start.y) * t,
            pressure = pressure,
            tiltX = start.tiltX,
            tiltY = start.tiltY,
        )
    }
}
