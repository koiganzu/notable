package com.ethran.notable.ui.components

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A short trail of what the app was doing, written straight to disk so it survives a crash that
 * leaves no Java stack (a native crash inside the pen driver, or the system closing the app).
 */
object Breadcrumbs {
    private const val FILE = "breadcrumbs.txt"
    private const val KEEP_LINES = 250
    private val stamp = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile
    private var file: File? = null
    private var written = 0

    fun init(context: Context) {
        file = File(context.filesDir, FILE)
        add("---- app started ----")
    }

    @Synchronized
    fun add(text: String) {
        val target = file ?: return
        runCatching {
            target.appendText("${stamp.format(Date())}  $text\n")
            // Trim now and then so the file never grows without bound.
            if (++written % 100 == 0) {
                val lines = target.readLines()
                if (lines.size > KEEP_LINES) {
                    target.writeText(lines.takeLast(KEEP_LINES).joinToString("\n") + "\n")
                }
            }
        }
    }

    fun read(context: Context): String = runCatching {
        File(context.filesDir, FILE).readLines().takeLast(KEEP_LINES).joinToString("\n")
    }.getOrDefault("")

    /** Why Android says the app's last few runs ended, with whatever trace it kept. */
    fun exitReasons(context: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return "(not available on this Android)"
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val exits = runCatching {
            manager.getHistoricalProcessExitReasons(context.packageName, 0, 5)
        }.getOrDefault(emptyList())
        if (exits.isEmpty()) return "(none recorded)"
        return exits.joinToString("\n\n") { exit ->
            val head = "${stamp.format(Date(exit.timestamp))}  ${reasonName(exit.reason)}" +
                    "  status=${exit.status}  ${exit.description ?: ""}"
            val trace = runCatching {
                exit.traceInputStream?.use { stream -> readableStrings(stream.readBytes()) }
            }.getOrNull()
            if (trace.isNullOrBlank()) head else "$head\n$trace"
        }
    }

    private fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_CRASH -> "CRASH (Java)"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH (native)"
        ApplicationExitInfo.REASON_ANR -> "FROZE (not responding)"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW MEMORY"
        ApplicationExitInfo.REASON_SIGNALED -> "KILLED BY SIGNAL"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE RESOURCE USE"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "CLOSED BY USER"
        ApplicationExitInfo.REASON_USER_STOPPED -> "STOPPED BY USER"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY DIED"
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXITED ITSELF"
        ApplicationExitInfo.REASON_OTHER -> "KILLED BY SYSTEM (other)"
        else -> "reason $reason"
    }

    /** A native trace is binary; pull out the readable parts (messages, function names). */
    private fun readableStrings(bytes: ByteArray): String {
        val out = StringBuilder()
        val run = StringBuilder()
        var kept = 0
        fun flush() {
            if (run.length >= 6 && kept < 120) {
                out.append(run).append('\n')
                kept++
            }
            run.setLength(0)
        }
        for (b in bytes) {
            val c = b.toInt()
            if (c in 32..126) run.append(c.toChar()) else flush()
        }
        flush()
        return out.toString().take(9000)
    }
}
