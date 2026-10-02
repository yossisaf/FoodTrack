package com.foodtrack.app

import android.app.Application
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * DEBUGGING SCAFFOLDING — see MainActivity's showCrashDialog() for the
 * matching piece. This catches ANY uncaught crash anywhere in the app
 * (not just inside MainActivity's own try/catch blocks) and writes the
 * full stack trace to a plain file in internal storage. On the next
 * launch, MainActivity checks for this file and shows it in a dialog.
 *
 * This exists specifically to debug crash-on-launch without Logcat
 * access. Remove once real crash reporting / logging is set up.
 */
class FoodTrackApplication : Application() {

    companion object {
        const val CRASH_FILE_NAME = "last_crash.txt"
    }

    override fun onCreate() {
        super.onCreate()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                File(filesDir, CRASH_FILE_NAME).writeText(sw.toString())
            } catch (_: Throwable) {
                // If we can't even write the crash file, there's nothing more to do here.
            }
            // Hand off to the system's default handler so normal crash
            // behavior (and Android's own crash dialog, if any) still happens.
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
