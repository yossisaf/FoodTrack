package com.foodtrack.app.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.foodtrack.app.R
import com.foodtrack.app.data.LogEntryEntity
import com.foodtrack.app.data.UserDatabase
import com.foodtrack.app.data.WaterEventEntity
import com.foodtrack.app.data.WeightEntryEntity
import com.foodtrack.app.databinding.ActivityTodayBinding
import com.foodtrack.app.util.DateUtil
import com.foodtrack.app.util.Prefs
import java.util.Locale

/**
 * The app home: a single "Today" dashboard with quick actions.
 * Detailed food, weight, templates and activity screens remain available
 * from the dashboard, but are no longer the primary navigation model.
 */
class TodayActivity : BaseActivity() {
    private lateinit var binding: ActivityTodayBinding
    private lateinit var logAdapter: LogEntryAdapter

    override val navItemId: Int? get() = R.id.nav_today

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTodayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        logAdapter = LogEntryAdapter { entry -> deleteEntry(entry) }
        binding.recyclerLog.layoutManager = LinearLayoutManager(this)
        binding.recyclerLog.adapter = logAdapter
        // Swipe either direction to delete, in addition to the row's own delete button.
        ItemTouchHelper(SwipeToDeleteCallback(binding.recyclerLog) { position ->
            logAdapter.currentList.getOrNull(position)?.let { deleteEntry(it) }
        }).attachToRecyclerView(binding.recyclerLog)

        binding.buttonAddFood.setOnClickListener { openFood() }
        binding.buttonAddActivity.setOnClickListener { openActivity() }
        binding.buttonAddWater.setOnClickListener { addWaterCup() }
        binding.buttonOpenWeight.setOnClickListener { openWeight() }
        binding.textDate.text = "${DateUtil.todayDisplay()}  ·  ${DateUtil.todayHebrew()}"
        binding.buttonOpenTemplates.setOnClickListener {
            startActivity(Intent(this, MealTemplatesActivity::class.java))
        }
        // Goals are editable both by tapping the card and from the title-bar menu.
        binding.cardCalories.setOnClickListener { editGoalDialog() }
        binding.cardWater.setOnClickListener { editWaterGoalDialog() }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.today_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_share -> { shareTodaySummary(); true }
        R.id.action_calorie_goal -> { editGoalDialog(); true }
        R.id.action_water_goal -> { editWaterGoalDialog(); true }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val today = DateUtil.today()
        runSafely("TodayActivity.refresh") {
            val db = UserDatabase.getInstance(applicationContext)
            val entries = db.logDao().forDate(today)
            logAdapter.submitList(entries)
            binding.textEmptyLog.visibility = if (entries.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE

            val calories = db.logDao().totalCaloriesForDate(today)
            val protein = db.logDao().totalProteinForDate(today)
            val activityMinutes = db.activityLogDao().totalDurationMinutesForDate(today)
            val water = db.waterDao().countForDate(today)
            val waterGoal = Prefs.getWaterGoal(applicationContext)
            val calorieGoal = Prefs.getDailyGoal(applicationContext)

            binding.textCalorieGoal.text = String.format(Locale.getDefault(), "%,.0f / %,d קק״ל", calories, calorieGoal)
            binding.progressCalories.max = calorieGoal
            binding.progressCalories.progress = calories.toInt().coerceAtLeast(0).coerceAtMost(calorieGoal)
            val remaining = calorieGoal - calories
            binding.textCalorieRemaining.text = if (remaining >= 0) {
                String.format(Locale.getDefault(), "נותרו %,.0f קק״ל להיום", remaining)
            } else {
                String.format(Locale.getDefault(), "חרגתם ב־%,.0f קק״ל", -remaining)
            }
            binding.textActivitySummary.text = String.format(Locale.getDefault(), "%.0f דקות", activityMinutes)
            binding.textWaterCount.text = String.format(Locale.getDefault(), "%d / %d", water, waterGoal)
            binding.textMacroProtein.text = String.format(Locale.getDefault(), "%.0f ג׳", protein)

            val latestWeight = db.weightDao().latest()
            binding.textLastWeight.text = latestWeight?.let {
                String.format(Locale.getDefault(), "%.1f ק״ג", it.weightKg)
            } ?: "—"

            val height = Prefs.getHeightCm(this@TodayActivity)
            binding.textBmi.text = if (latestWeight != null && height != null && height > 0) {
                val bmi = latestWeight.weightKg / ((height / 100.0) * (height / 100.0))
                String.format(Locale.getDefault(), "%.1f", bmi)
            } else "—"

            refreshWeeklyTrend(db, calorieGoal)
        }
    }

    /** Populates the "weekly trend" bar chart + the 7-day / 30-day average summary line. */
    private suspend fun refreshWeeklyTrend(db: UserDatabase, calorieGoal: Int) {
        // Oldest to newest, today last — matches WeeklyCaloriesView's bar order.
        val weekDates = (6 downTo 0).map { DateUtil.daysAgo(it) }
        val weekCalories = weekDates.map { db.logDao().totalCaloriesForDate(it) }
        val weekLabels = weekDates.map { DateUtil.weekdayLetter(it) }
        binding.weeklyCalories.setData(weekCalories.map { it.toFloat() }, calorieGoal.toFloat(), weekLabels)

        val weeklyAvg = weekCalories.average()
        val monthStart = DateUtil.daysAgo(29)
        val monthlyTotal = db.logDao().totalCaloriesSince(monthStart)
        val monthlyLoggedDays = db.logDao().distinctLoggedDaysSince(monthStart)
        val monthlyAvg = if (monthlyLoggedDays > 0) monthlyTotal / monthlyLoggedDays else 0.0

        binding.textWeeklySummary.text = if (monthlyLoggedDays > 0) {
            String.format(
                Locale.getDefault(),
                "ממוצע 7 ימים: %,.0f קק״ל  ·  ממוצע 30 יום (לפי ימים שתועדו): %,.0f קק״ל",
                weeklyAvg, monthlyAvg
            )
        } else {
            String.format(Locale.getDefault(), "ממוצע 7 ימים: %,.0f קק״ל", weeklyAvg)
        }
    }

    private fun openFood() = startActivity(
        Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_FOCUS_SEARCH, true)
    )
    private fun openActivity() = startActivity(Intent(this, PhysicalActivityActivity::class.java))
    private fun openWeight() = startActivity(Intent(this, WeightStatsActivity::class.java))

    private fun addWaterCup() {
        runSafely("TodayActivity.addWaterCup") {
            UserDatabase.getInstance(applicationContext).waterDao().insertEvent(
                WaterEventEntity(date = DateUtil.today(), timestamp = System.currentTimeMillis())
            )
            refresh()
            showUndoSnackbar("נוספה כוס מים") { undoWaterCup() }
        }
    }

    private fun undoWaterCup() {
        runSafely("TodayActivity.undoWaterCup") {
            val dao = UserDatabase.getInstance(applicationContext).waterDao()
            dao.mostRecentForDate(DateUtil.today())?.let { dao.delete(it) }
            refresh()
        }
    }

    /** Deletes immediately and offers undo — faster and safer than a confirm dialog for a single row. */
    private fun deleteEntry(entry: LogEntryEntity) {
        runSafely("TodayActivity.deleteEntry") {
            UserDatabase.getInstance(applicationContext).logDao().delete(entry)
            refresh()
            showUndoSnackbar("״${entry.foodNameHe}״ נמחק מהיומן") { restoreEntry(entry) }
        }
    }

    private fun restoreEntry(entry: LogEntryEntity) {
        runSafely("TodayActivity.restoreEntry") {
            UserDatabase.getInstance(applicationContext).logDao().insert(entry)
            refresh()
        }
    }

    private fun editGoalDialog() {
        val input = android.widget.EditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        input.setText(Prefs.getDailyGoal(this).toString())
        input.selectAll()
        AlertDialog.Builder(this)
            .setTitle("יעד קלוריות יומי")
            .setView(paddedForDialog(input))
            .setPositiveButton("שמור") { _, _ ->
                val value = input.text.toString().toIntOrNull()
                if (value != null && value in 1..10000) {
                    Prefs.setDailyGoal(this, value)
                    refresh()
                } else {
                    Toast.makeText(this, "יש להזין יעד בין 1 ל־10,000 קק״ל", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("ביטול", null)
            .show()
    }

    private fun editWaterGoalDialog() {
        val input = android.widget.EditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        input.setText(Prefs.getWaterGoal(this).toString())
        input.selectAll()
        AlertDialog.Builder(this)
            .setTitle("יעד כוסות מים")
            .setView(paddedForDialog(input))
            .setPositiveButton("שמור") { _, _ ->
                val value = input.text.toString().toIntOrNull()
                if (value != null && value in 1..50) {
                    Prefs.setWaterGoal(this, value)
                    refresh()
                } else {
                    Toast.makeText(this, "יש להזין יעד בין 1 ל־50 כוסות", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("ביטול", null)
            .show()
    }

    private fun shareTodaySummary() {
        runSafely("TodayActivity.shareTodaySummary") {
            val db = UserDatabase.getInstance(applicationContext)
            val date = DateUtil.today()
            val calories = db.logDao().totalCaloriesForDate(date)
            val protein = db.logDao().totalProteinForDate(date)
            val activity = db.activityLogDao().totalDurationMinutesForDate(date)
            val water = db.waterDao().countForDate(date)
            val weight = db.weightDao().latest()?.weightKg
            val text = String.format(
                Locale.getDefault(),
                "FoodTrack — סיכום היום (%s)\n\nקלוריות: %.0f / %d קק״ל\nפעילות: %.0f דקות\nמשקל: %s\nמים: %d / %d\nחלבון: %.0f / %d ג׳",
                DateUtil.todayHebrew(),
                calories, Prefs.getDailyGoal(this@TodayActivity), activity,
                weight?.let { String.format(Locale.getDefault(), "%.1f ק״ג", it) } ?: "—",
                water, Prefs.getWaterGoal(this@TodayActivity), protein
            )
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }, "שיתוף סיכום היום"))
        }
    }
}
