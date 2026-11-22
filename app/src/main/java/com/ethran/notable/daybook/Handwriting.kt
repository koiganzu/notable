package com.ethran.notable.daybook

import com.ethran.notable.data.db.Stroke
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink
import io.shipbook.shipbooksdk.ShipBook
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min

/** What a piece of handwriting says: the best reading and the recognizer's second guess. */
data class Reading(val text: String, val alternative: String)

/**
 * Reads handwriting with Google's on-device recognizer. The English model (about 20 MB) is
 * downloaded the first time and works offline after that.
 */
object HandwritingReader {
    private val log = ShipBook.getLogger("Handwriting")
    private val setup = Mutex()

    @Volatile
    private var recognizer: DigitalInkRecognizer? = null

    @Volatile
    var lastProblem: String? = null
        private set

    private val model: DigitalInkRecognitionModel? by lazy {
        runCatching { DigitalInkRecognitionModelIdentifier.fromLanguageTag("en-US") }
            .getOrNull()
            ?.let { DigitalInkRecognitionModel.builder(it).build() }
    }

    /** Gets the recognizer ready, downloading its model if needed. False if that failed. */
    suspend fun prepare(): Boolean = setup.withLock {
        if (recognizer != null) return true
        val m = model ?: run {
            lastProblem = "This tablet has no handwriting model for English"
            return false
        }
        try {
            val manager = RemoteModelManager.getInstance()
            if (await(manager.isModelDownloaded(m)) != true) {
                lastProblem = "Downloading the handwriting reader (needs Wi-Fi, once)"
                await(manager.download(m, DownloadConditions.Builder().build()))
            }
            recognizer = DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(m).build())
            lastProblem = null
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.e("Handwriting reader unavailable", e)
            lastProblem = "The handwriting reader couldn't be set up. Connect to Wi-Fi and try again."
            false
        }
    }

    /** Reads [strokes] line by line, top to bottom. Null when the reader is unavailable. */
    suspend fun read(strokes: List<Stroke>): Reading? {
        if (strokes.isEmpty()) return Reading("", "")
        if (!prepare()) return null
        val reader = recognizer ?: return null
        val best = StringBuilder()
        val second = StringBuilder()
        for (line in layoutLines(strokes)) {
            val lineBest = ArrayList<String>()
            val lineSecond = ArrayList<String>()
            for (segment in line) {
                val candidates = runCatching { recognize(reader, segment) }
                    .onFailure { if (it is CancellationException) throw it }
                    .getOrNull() ?: continue
                candidates.getOrNull(0)?.let { lineBest += it }
                candidates.getOrNull(1)?.let { lineSecond += it }
            }
            if (lineBest.isNotEmpty()) best.append(lineBest.joinToString("   ")).append('\n')
            if (lineSecond.isNotEmpty()) second.append(lineSecond.joinToString("   ")).append('\n')
        }
        return Reading(best.toString().trim(), second.toString().trim())
    }

    private suspend fun recognize(reader: DigitalInkRecognizer, strokes: List<Stroke>): List<String> {
        val ink = Ink.builder()
        for (stroke in strokes) {
            if (stroke.points.isEmpty()) continue
            val builder = Ink.Stroke.builder()
            val base = stroke.createdAt.time
            stroke.points.forEachIndexed { i, p ->
                val t = base + (p.dt?.toLong() ?: (i * 8L))
                builder.addPoint(Ink.Point.create(p.x, p.y, t))
            }
            ink.addStroke(builder.build())
        }
        val result = await(reader.recognize(ink.build())) ?: return emptyList()
        return result.candidates.map { it.text }
    }

    /**
     * Groups strokes into lines of writing, and splits each line where there is a wide gap
     * (two columns of a template), since the recognizer reads one line at a time. Strokes much
     * taller than the writing around them (boxes, circles, drawings) are left out.
     */
    internal fun layoutLines(strokes: List<Stroke>): List<List<List<Stroke>>> {
        val usable = strokes.filter { it.points.size >= 2 }
        if (usable.isEmpty()) return emptyList()
        val heights = usable.map { max(it.bottom - it.top, 1f) }.sorted()
        val typical = max(heights[heights.size / 2], 12f)
        val text = usable.filter { it.bottom - it.top <= typical * 4f }

        class Line(var top: Float, var bottom: Float, val strokes: MutableList<Stroke>)

        val lines = ArrayList<Line>()
        for (s in text.sortedBy { (it.top + it.bottom) / 2 }) {
            val line = lines.lastOrNull()
            val h = max(s.bottom - s.top, 1f)
            val overlap = if (line == null) 0f
            else min(line.bottom, s.bottom) - max(line.top, s.top)
            val centreInside = line != null && (s.top + s.bottom) / 2 in line.top..line.bottom
            if (line != null && (centreInside || overlap >= 0.4f * min(h, line.bottom - line.top))) {
                line.strokes += s
                line.top = min(line.top, s.top)
                line.bottom = max(line.bottom, s.bottom)
            } else {
                lines += Line(s.top, s.bottom, mutableListOf(s))
            }
        }

        val gap = typical * 5f
        return lines.map { line ->
            val segments = ArrayList<MutableList<Stroke>>()
            var right = Float.NEGATIVE_INFINITY
            for (s in line.strokes.sortedBy { it.left }) {
                if (segments.isEmpty() || s.left - right > gap) segments += mutableListOf(s)
                else segments.last() += s
                right = max(right, s.right)
            }
            // The recognizer works best with strokes in the order they were written.
            segments.map { seg -> seg.sortedBy { it.createdAt.time } }
        }
    }

    private suspend fun <T> await(task: Task<T>): T? = suspendCancellableCoroutine { cont ->
        task.addOnSuccessListener { if (cont.isActive) cont.resume(it) }
        task.addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        task.addOnCanceledListener { cont.cancel() }
    }
}
