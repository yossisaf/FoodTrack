package com.foodtrack.app.ui

import android.os.Bundle
import android.widget.Toast
import androidx.recyclerview.widget.ItemTouchHelper
import com.foodtrack.app.R
import com.foodtrack.app.data.UserDatabase
import com.foodtrack.app.data.WeightEntryEntity
import com.foodtrack.app.databinding.ActivityWeightStatsBinding
import com.foodtrack.app.util.DateUtil
import com.foodtrack.app.util.Prefs
import android.content.Intent
import java.util.Locale
import kotlin.math.sqrt

class WeightStatsActivity : BaseActivity() {
    override val navItemId: Int? get() = R.id.nav_weight
    private lateinit var binding: ActivityWeightStatsBinding
    private lateinit var historyAdapter: WeightHistoryAdapter

    // Without this, a fast double-tap on "save" could insert the same weigh-in twice.
    private var savingWeight = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWeightStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        historyAdapter = WeightHistoryAdapter { deleteEntry(it) }
        binding.recyclerWeightHistory.adapter = historyAdapter
        // Swipe either direction to delete, in addition to the row's own delete button —
        // same gesture as the food log and activity log, both with undo.
        ItemTouchHelper(SwipeToDeleteCallback(binding.recyclerWeightHistory) { position ->
            historyAdapter.currentList.getOrNull(position)?.let { deleteEntry(it) }
        }).attachToRecyclerView(binding.recyclerWeightHistory)
        binding.buttonSaveWeight.setOnClickListener { saveWeight() }
        binding.editHeight.setText(Prefs.getHeightCm(this)?.let { String.format(Locale.getDefault(), "%.0f", it) } ?: "")
        binding.buttonCalculateBmi.setOnClickListener { calculateBmi() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        runSafely("WeightStatsActivity.refresh") {
            val dao = UserDatabase.getInstance(applicationContext).weightDao()
            val all = dao.allAscending()
            val recent = all.takeLast(60)
            historyAdapter.submitList(all.asReversed())
            binding.textEmptyWeight.visibility = if (all.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            binding.recyclerWeightHistory.visibility = if (all.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE

            if (all.isNotEmpty()) {
                val latest = all.last()
                binding.textCurrentWeight.text = String.format(Locale.getDefault(), "%.1f ק״ג", latest.weightKg)
                val previous = if (all.size >= 2) all[all.size - 2] else null
                val change = if (previous != null) latest.weightKg - previous.weightKg else 0.0
                binding.textLatestChange.text = formatChange(change, "מהמדידה הקודמת")

                val first = all.first()
                binding.textOverallChange.text = formatChange(latest.weightKg - first.weightKg, "מהמדידה הראשונה")
                binding.textAverage.text = String.format(Locale.getDefault(), "%.1f ק״ג", all.map { it.weightKg }.average())
                binding.textMinMax.text = String.format(Locale.getDefault(), "%.1f – %.1f ק״ג", all.minOf { it.weightKg }, all.maxOf { it.weightKg })

                val now = System.currentTimeMillis()
                val week = all.filter { it.timestamp >= now - 7L * 24 * 60 * 60 * 1000 }
                val month = all.filter { it.timestamp >= now - 30L * 24 * 60 * 60 * 1000 }
                binding.textWeek.text = periodChange(week)
                binding.textMonth.text = periodChange(month)
                binding.textStability.text = String.format(Locale.getDefault(), "סטיית מדידות: %.1f ק״ג", standardDeviation(recent))
                binding.weightChart.setValues(recent.map { it.weightKg.toFloat() })
                val dateFormat = java.text.SimpleDateFormat("d.M", Locale.getDefault())
                binding.weightChart.setDateRange(
                    if (recent.size >= 2) dateFormat.format(java.util.Date(recent.first().timestamp)) else null,
                    dateFormat.format(java.util.Date(recent.last().timestamp))
                )
                val height = Prefs.getHeightCm(this@WeightStatsActivity)
                if (height != null) {
                    val bmi = latest.weightKg / ((height / 100.0) * (height / 100.0))
                    binding.textBmi.text = String.format(Locale.getDefault(), "BMI: %.1f", bmi)
                    binding.textBmiCategory.text = when { bmi < 18.5 -> "טווח BMI: מתחת ל־18.5"; bmi < 25.0 -> "טווח BMI: 18.5–24.9"; bmi < 30.0 -> "טווח BMI: 25.0–29.9"; else -> "טווח BMI: 30 ומעלה" }
                }
            } else {
                binding.textCurrentWeight.text = "—"
                binding.textLatestChange.text = "עדיין אין מדידות"
                binding.textOverallChange.text = "—"
                binding.textAverage.text = "—"
                binding.textMinMax.text = "—"
                binding.textWeek.text = "—"
                binding.textMonth.text = "—"
                binding.textStability.text = "—"
                binding.weightChart.setValues(emptyList())
                binding.weightChart.setDateRange(null, null)
            }
        }
    }

    private fun calculateBmi() {
        val heightCm = binding.editHeight.text.toString().replace(',', '.').toDoubleOrNull()
        if (heightCm == null || heightCm !in 100.0..250.0) {
            Toast.makeText(this, "יש להזין גובה בין 100 ל־250 ס״מ", Toast.LENGTH_SHORT).show()
            return
        }
        Prefs.setHeightCm(this, heightCm)
        runSafely("WeightStatsActivity.calculateBmi") {
            val latest = UserDatabase.getInstance(applicationContext).weightDao().latest()
            if (latest == null) {
                binding.textBmi.text = "BMI: —"
                binding.textBmiCategory.text = "שמרו קודם מדידת משקל"
                return@runSafely
            }
            val bmi = latest.weightKg / ((heightCm / 100.0) * (heightCm / 100.0))
            binding.textBmi.text = String.format(Locale.getDefault(), "BMI: %.1f", bmi)
            binding.textBmiCategory.text = when {
                bmi < 18.5 -> "טווח BMI: מתחת ל־18.5"
                bmi < 25.0 -> "טווח BMI: 18.5–24.9"
                bmi < 30.0 -> "טווח BMI: 25.0–29.9"
                else -> "טווח BMI: 30 ומעלה"
            }
        }
    }

    private fun saveWeight() {
        if (savingWeight) return
        val value = binding.editWeight.text.toString().trim().replace(',', '.').toDoubleOrNull()
        if (value == null || value !in 20.0..400.0) {
            Toast.makeText(this, "יש להזין משקל בין 20 ל־400 ק״ג", Toast.LENGTH_SHORT).show()
            return
        }
        savingWeight = true
        binding.buttonSaveWeight.isEnabled = false
        runSafely("WeightStatsActivity.saveWeight") {
            try {
                UserDatabase.getInstance(applicationContext).weightDao().insert(
                    WeightEntryEntity(
                        date = DateUtil.today(),
                        timestamp = System.currentTimeMillis(),
                        weightKg = value
                    )
                )
                binding.editWeight.setText("")
                showMessage("המדידה נשמרה")
                refresh()
            } finally {
                savingWeight = false
                binding.buttonSaveWeight.isEnabled = true
            }
        }
    }

    /**
     * Deletes immediately with undo, matching the food log and activity log —
     * a confirm dialog was an extra tap that the undo snackbar already makes
     * unnecessary, and it also blocked the swipe gesture from working (a swipe
     * can't answer an "are you sure?" popup).
     */
    private fun deleteEntry(entry: WeightEntryEntity) {
        runSafely("WeightStatsActivity.deleteEntry") {
            UserDatabase.getInstance(applicationContext).weightDao().delete(entry)
            refresh()
            showUndoSnackbar(String.format(Locale.getDefault(), "המדידה %.1f ק״ג נמחקה", entry.weightKg)) {
                restoreEntry(entry)
            }
        }
    }

    private fun restoreEntry(entry: WeightEntryEntity) {
        runSafely("WeightStatsActivity.restoreEntry") {
            UserDatabase.getInstance(applicationContext).weightDao().insert(entry)
            refresh()
        }
    }

    private fun periodChange(items: List<WeightEntryEntity>): String {
        if (items.size < 2) return "אין מספיק נתונים"
        val delta = items.last().weightKg - items.first().weightKg
        return formatChange(delta, "מתחילת התקופה")
    }

    private fun formatChange(delta: Double, suffix: String): String {
        val sign = when {
            delta > 0.049 -> "+"
            delta < -0.049 -> "−"
            else -> ""
        }
        return String.format(Locale.getDefault(), "%s%.1f ק״ג · %s", sign, kotlin.math.abs(delta), suffix)
    }

    private fun standardDeviation(items: List<WeightEntryEntity>): Double {
        if (items.size < 2) return 0.0
        val avg = items.map { it.weightKg }.average()
        return sqrt(items.sumOf { (it.weightKg - avg) * (it.weightKg - avg) } / items.size)
    }
}
