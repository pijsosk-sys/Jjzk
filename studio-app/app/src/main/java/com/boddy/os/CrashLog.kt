package com.boddy.os

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Saves crash reports so the next launch can show what went wrong. */
object CrashLog {

    private fun file(context: Context) = File(context.filesDir, "last-crash.txt")

    fun record(context: Context, t: Throwable, fatal: Boolean) {
        try {
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val kind = if (fatal) "Crash" else "Recovered error"
            file(context).appendText(
                "$kind at $time (Android ${Build.VERSION.RELEASE}, ${Build.MODEL})\n" +
                    Log.getStackTraceString(t) + "\n"
            )
        } catch (_: Exception) {
        }
    }

    /** The saved report, left in place (for Copy log). Null if nothing went wrong. */
    fun peek(context: Context): String? = file(context).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }

    /** The saved report, removed once read. Null if nothing went wrong. */
    fun take(context: Context): String? {
        val f = file(context)
        if (!f.exists()) return null
        return f.readText().also { f.delete() }.takeIf { it.isNotBlank() }
    }
}

class BoddyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, t ->
            CrashLog.record(this, t, fatal = true)
            previous?.uncaughtException(thread, t)
        }
    }
}
