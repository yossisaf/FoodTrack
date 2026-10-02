package com.foodtrack.app.util

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale

object Prefs {
    private const val PREFS_NAME = "foodtrack_prefs"
    private const val KEY_DAILY_GOAL = "daily_calorie_goal"
    private const val DEFAULT_GOAL = 2000
    private const val KEY_RECENT_SEARCHES = "recent_searches"
    private const val KEY_WATER_GOAL = "water_goal_cups"
    private const val DEFAULT_WATER_GOAL = 8
    private const val KEY_HEIGHT_CM = "height_cm"

    fun getDailyGoal(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_DAILY_GOAL, DEFAULT_GOAL)
    }

    fun setDailyGoal(context: Context, goal: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_DAILY_GOAL, goal).apply()
    }

    fun recentSearches(context: Context): List<String> = context
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_RECENT_SEARCHES, "")
        ?.split("\u001f")
        ?.filter { it.isNotBlank() }
        ?: emptyList()

    fun addRecentSearch(context: Context, query: String) {
        val value = query.trim()
        if (value.isBlank()) return
        val current = recentSearches(context).filterNot { it.equals(value, true) }.toMutableList()
        current.add(0, value)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_RECENT_SEARCHES, current.take(8).joinToString("\u001f")).apply()
    }

    fun clearRecentSearches(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().remove(KEY_RECENT_SEARCHES).apply()
    }

    fun getWaterGoal(context: Context): Int = context
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getInt(KEY_WATER_GOAL, DEFAULT_WATER_GOAL)

    fun setWaterGoal(context: Context, goal: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_WATER_GOAL, goal.coerceIn(1, 30)).apply()
    }

    fun getHeightCm(context: Context): Double? = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_HEIGHT_CM, null)?.replace(',', '.')?.toDoubleOrNull()

    fun setHeightCm(context: Context, heightCm: Double) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_HEIGHT_CM, heightCm.toString()).apply()
    }


}

object DateUtil {
    // SimpleDateFormat is mutable and not thread-safe. Create a fresh formatter
    // per call because these helpers are also used from coroutine threads.
    private fun isoFormat() = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private fun displayFormat() = SimpleDateFormat("EEEE, d MMMM", Locale("he"))

    fun today(): String = isoFormat().format(Date())

    fun todayDisplay(): String = displayFormat().format(Date())

    fun daysAgo(days: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        return isoFormat().format(cal.time)
    }

    /** Hebrew (Jewish) calendar date for today, e.g. "כ״ג באב תשפ״ו". */
    fun todayHebrew(): String = HebrewDateUtil.today()

    /** Hebrew (Jewish) calendar date for an ISO "yyyy-MM-dd" string. */
    fun hebrewDateFor(isoDate: String): String? =
        isoFormat().parse(isoDate)?.let { HebrewDateUtil.format(it) }

    /** Hebrew (Jewish) calendar date for a millisecond epoch timestamp. */
    fun hebrewDateFor(timestamp: Long): String = HebrewDateUtil.format(timestamp)

    /** Single-letter Hebrew weekday label (א׳–ש׳) for an ISO "yyyy-MM-dd" date. */
    fun weekdayLetter(isoDate: String): String {
        val letters = listOf("א", "ב", "ג", "ד", "ה", "ו", "ש") // Calendar.SUNDAY..SATURDAY
        val date = isoFormat().parse(isoDate) ?: return "?"
        val cal = Calendar.getInstance()
        cal.time = date
        val index = cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
        return letters.getOrElse(index) { "?" }
    }
}
