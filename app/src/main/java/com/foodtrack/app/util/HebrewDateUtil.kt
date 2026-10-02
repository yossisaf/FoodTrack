package com.foodtrack.app.util

import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import java.util.Date

/**
 * Converts Gregorian dates to a Hebrew (Jewish) calendar date string, e.g.
 * "כ״ג באב תשפ״ו", using the KosherJava Zmanim library
 * (https://github.com/KosherJava/zmanim).
 *
 * A fresh [HebrewDateFormatter] is created per call because, like
 * SimpleDateFormat, it is not guaranteed to be thread-safe and this utility
 * may be called from coroutine threads.
 */
object HebrewDateUtil {
    private fun formatter(): HebrewDateFormatter = HebrewDateFormatter().apply {
        isHebrewFormat = true
        isUseGershGershayim = true
        isUseLongHebrewYears = false
    }

    /** Hebrew date string for the given [Date], e.g. "כ״ג באב תשפ״ו". */
    fun format(date: Date): String {
        val jewishCalendar = JewishCalendar(date)
        val hdf = formatter()
        val dateStr = hdf.format(jewishCalendar)
        // If today is a Yom Tov or a fast day, append it — e.g.
        // "א׳ תשרי תשפ״ז · ראש השנה". Guarded per the library's own usage
        // pattern, since formatYomTov's output is only meaningful then.
        return if (jewishCalendar.isYomTov || jewishCalendar.isTaanis) {
            "$dateStr · ${hdf.formatYomTov(jewishCalendar)}"
        } else {
            dateStr
        }
    }

    /** Hebrew date string for a millisecond epoch [timestamp]. */
    fun format(timestamp: Long): String = format(Date(timestamp))

    /** Hebrew date string for the current moment. */
    fun today(): String = format(Date())
}
