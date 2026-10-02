package com.foodtrack.app.ui

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.Filter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.foodtrack.app.R
import com.foodtrack.app.data.ActivityLogEntity
import com.foodtrack.app.data.PhysicalActivity
import com.foodtrack.app.data.PhysicalActivityRepository
import com.foodtrack.app.data.UserDatabase
import com.foodtrack.app.util.DateUtil
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PhysicalActivityActivity : BaseActivity() {
    override val navItemId: Int? get() = R.id.nav_activity

    private lateinit var activityInput: AutoCompleteTextView
    private lateinit var duration: EditText
    private lateinit var weight: EditText
    private lateinit var selectedText: TextView
    private lateinit var caloriesText: TextView
    private lateinit var formulaText: TextView
    private lateinit var todayText: TextView
    private lateinit var saveButton: View
    private lateinit var adapter: DropdownAdapter
    private lateinit var logAdapter: ActivityLogAdapter

    private var selected: PhysicalActivity? = null
    private var allActivities: List<PhysicalActivity> = emptyList()
    private var saving = false

    /** Chip views keyed by activity code / minutes so their highlight always follows the real state. */
    private val quickPickChips = mutableMapOf<String, TextView>()
    private val durationChips = mutableMapOf<Int, TextView>()

    /**
     * An ArrayAdapter whose built-in filter is switched off. We do the "contains"
     * filtering ourselves; the stock filter would re-filter our list by prefix and
     * hide valid matches (typing "פני" would not find "אופניים").
     */
    private class DropdownAdapter(context: Context) :
        ArrayAdapter<String>(context, android.R.layout.simple_dropdown_item_1line, mutableListOf<String>()) {
        private val passThrough = object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults =
                FilterResults().also { it.values = null; it.count = this@DropdownAdapter.count }

            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                if (this@DropdownAdapter.count > 0) notifyDataSetChanged() else notifyDataSetInvalidated()
            }
        }

        override fun getFilter(): Filter = passThrough
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_physical_activity)
        activityInput = findViewById(R.id.editActivity)
        duration = findViewById(R.id.editDuration)
        weight = findViewById(R.id.editActivityWeight)
        selectedText = findViewById(R.id.textSelectedActivity)
        caloriesText = findViewById(R.id.textCalories)
        formulaText = findViewById(R.id.textFormula)
        todayText = findViewById(R.id.textTodayActivity)
        saveButton = findViewById(R.id.buttonSaveActivity)

        logAdapter = ActivityLogAdapter { deleteEntry(it) }
        val activityRecycler = findViewById<RecyclerView>(R.id.recyclerTodayActivities).apply {
            layoutManager = LinearLayoutManager(this@PhysicalActivityActivity)
            adapter = logAdapter
        }
        // Swipe either direction to delete, in addition to the row's own delete button.
        ItemTouchHelper(SwipeToDeleteCallback(activityRecycler) { position ->
            logAdapter.currentList.getOrNull(position)?.let { deleteEntry(it) }
        }).attachToRecyclerView(activityRecycler)

        // Prefill body weight from the latest weigh-in so the user doesn't retype it every time.
        lifecycleScope.launch {
            try {
                val last = withContext(Dispatchers.IO) { UserDatabase.getInstance(applicationContext).weightDao().latest() }
                if (last != null && weight.text.isNullOrBlank()) {
                    weight.setText(String.format(Locale.US, "%.1f", last.weightKg).removeSuffix(".0"))
                }
            } catch (t: Exception) { /* optional convenience only */ }
        }

        allActivities = PhysicalActivityRepository.all(this)
        adapter = DropdownAdapter(this)
        adapter.addAll(allActivities.take(80).map { it.description })
        activityInput.setAdapter(adapter)

        setupQuickPicks()
        setupDurationChips()

        activityInput.setOnItemClickListener { _, _, position, _ ->
            val description = adapter.getItem(position)
            selected = allActivities.firstOrNull { it.description == description }
            onSelectionChanged()
            // Next step of the flow: jump straight to the duration field.
            duration.requestFocus()
        }
        activityInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val text = s?.toString().orEmpty()
                activityInput.error = null
                // onTextChanged callbacks run before AutoCompleteTextView's own afterTextChanged
                // watcher, so the dropdown always sees the refreshed list.
                adapter.clear()
                adapter.addAll(currentMatches(text).map { it.description })
                selected = allActivities.firstOrNull { it.description.equals(text.trim(), true) }
                onSelectionChanged()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                duration.error = null
                weight.error = null
                syncDurationChips()
                updatePreview()
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        duration.addTextChangedListener(watcher)
        weight.addTextChangedListener(watcher)

        saveButton.setOnClickListener { save() }
        onSelectionChanged()
        refreshToday()
    }

    private fun save() {
        if (saving) return
        hideKeyboard(saveButton)

        val act = selected ?: resolveTypedActivity()
        if (act == null) {
            activityInput.error = "בחרו פעילות מהרשימה"
            activityInput.requestFocus()
            return
        }
        if (selected == null) {
            selected = act
            onSelectionChanged()
        }
        val mins = duration.text.toString().replace(',', '.').toDoubleOrNull()
        if (mins == null || mins <= 0 || mins > 1440) {
            duration.error = "יש להזין משך בדקות (עד 1,440)"
            duration.requestFocus()
            return
        }
        val kg = weight.text.toString().replace(',', '.').toDoubleOrNull()
        if (kg == null || kg !in 20.0..400.0) {
            weight.error = "יש להזין משקל בין 20 ל־400 ק״ג"
            weight.requestFocus()
            return
        }

        saving = true
        saveButton.isEnabled = false
        val kcal = estimate(act.met, kg, mins)
        runSafely("PhysicalActivityActivity.save") {
            try {
                withContext(Dispatchers.IO) {
                    UserDatabase.getInstance(applicationContext).activityLogDao().insert(
                        ActivityLogEntity(
                            date = DateUtil.today(), timestamp = System.currentTimeMillis(),
                            activityCode = act.code, activityName = act.description, met = act.met,
                            durationMinutes = mins, weightKg = kg, caloriesKcal = kcal
                        )
                    )
                }
                showMessage(String.format(Locale.getDefault(), "נשמר: %.0f דק׳ · %.0f קק״ל", mins, kcal))
                // Reset the form (the weight stays: it rarely changes between entries).
                duration.setText("")
                activityInput.setText("")
                loadToday()
            } finally {
                saving = false
                saveButton.isEnabled = true
            }
        }
    }

    /** If the user typed instead of tapping a suggestion, accept an unambiguous match. */
    private fun resolveTypedActivity(): PhysicalActivity? {
        val text = activityInput.text.toString().trim()
        if (text.isBlank()) return null
        allActivities.firstOrNull { it.description.equals(text, true) }?.let { return it }
        return currentMatches(text).singleOrNull()
    }

    private fun onSelectionChanged() {
        val sel = selected
        selectedText.text = sel?.let {
            String.format(Locale.getDefault(), "%s · MET %.1f", it.description, it.met)
        } ?: "בחרו פעילות מהרשימה"
        quickPickChips.forEach { (code, chip) -> chip.isActivated = sel?.code == code }
        updatePreview()
    }

    private fun syncDurationChips() {
        val minutes = duration.text.toString().replace(',', '.').toDoubleOrNull()
        durationChips.forEach { (m, chip) -> chip.isActivated = minutes != null && minutes == m.toDouble() }
    }

    /**
     * Popular activities are pinned to exact Compendium codes. Matching by keyword
     * picked wrong rows (e.g. "הליכה" matched a fishing entry and "ריצה" a shuttle drill).
     */
    private fun setupQuickPicks() {
        val group = findViewById<LinearLayout>(R.id.quickPickGroup)
        val popular = listOf(
            Triple("🚶 הליכה", "17190", "הליכה, קצב בינוני"),
            Triple("🏃 ריצה", "12020", "ריצה קלה"),
            Triple("🚴 אופניים", "01014", "רכיבה על אופניים"),
            Triple("🏊 שחייה", "18240", "שחייה"),
            Triple("🏋️ כוח", "02054", "אימון כוח"),
            Triple("🧘 יוגה", "02175", "יוגה")
        )
        popular.forEach { (label, code, spoken) ->
            val match = allActivities.firstOrNull { it.code == code } ?: return@forEach
            val chip = buildChip(label, dpToPx(16), spoken).apply {
                setOnClickListener {
                    hideKeyboard(this)
                    activityInput.setText(match.description, false)
                    // setText fires the text watcher; make sure this exact code stays selected.
                    selected = match
                    onSelectionChanged()
                }
            }
            quickPickChips[code] = chip
            group.addView(chip)
        }
    }

    private fun setupDurationChips() {
        val group = findViewById<LinearLayout>(R.id.durationChipGroup)
        listOf(15, 30, 45, 60).forEach { minutes ->
            val chip = buildChip("$minutes דק׳", dpToPx(14), "$minutes דקות").apply {
                setOnClickListener {
                    hideKeyboard(this)
                    duration.setText(minutes.toString())
                    duration.setSelection(duration.text.length)
                }
            }
            durationChips[minutes] = chip
            group.addView(chip)
        }
    }

    private fun buildChip(label: String, horizontalPadding: Int, spoken: String): TextView = TextView(this).apply {
        text = label
        contentDescription = spoken
        textSize = 14f
        minHeight = dpToPx(44)
        setPadding(horizontalPadding, dpToPx(9), horizontalPadding, dpToPx(9))
        background = ContextCompat.getDrawable(this@PhysicalActivityActivity, R.drawable.bg_chip)
        setTextColor(ContextCompat.getColorStateList(this@PhysicalActivityActivity, R.color.chip_text_color))
        isActivated = false
        isClickable = true
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = dpToPx(8) }
    }

    private fun currentMatches(query: String): List<PhysicalActivity> {
        val q = query.trim().lowercase(Locale.US)
        if (q.isBlank()) return allActivities.take(80)
        return allActivities.filter {
            it.description.lowercase(Locale.US).contains(q) ||
                it.category.lowercase(Locale.US).contains(q) ||
                it.code.contains(q)
        }.take(80)
    }

    private fun estimate(met: Double, kg: Double, minutes: Double): Double = met * 3.5 * kg / 200.0 * minutes

    /** Always leaves the card consistent: a stale number must never stay after an input is cleared. */
    private fun updatePreview() {
        val a = selected
        val mins = duration.text.toString().replace(',', '.').toDoubleOrNull()
        val kg = weight.text.toString().replace(',', '.').toDoubleOrNull()
        if (a == null || mins == null || kg == null || mins <= 0 || kg <= 0) {
            caloriesText.text = "—"
            formulaText.text = when {
                a == null -> "בחרו פעילות כדי לראות הערכה"
                mins == null || mins <= 0 -> "הזינו משך כדי לראות הערכה"
                else -> "הזינו משקל גוף כדי לראות הערכה"
            }
            return
        }
        val kcal = estimate(a.met, kg, mins)
        caloriesText.text = String.format(Locale.getDefault(), "%.0f קק״ל", kcal)
        formulaText.text = String.format(Locale.getDefault(), "MET %.1f  ·  %.1f ק״ג  ·  %.0f דק׳", a.met, kg, mins)
    }

    private fun refreshToday() {
        runSafely("PhysicalActivityActivity.refreshToday") { loadToday() }
    }

    private suspend fun loadToday() {
        val entries = UserDatabase.getInstance(applicationContext).activityLogDao().forDate(DateUtil.today())
        logAdapter.submitList(entries)
        todayText.text = if (entries.isEmpty()) {
            "עדיין לא נרשמה פעילות היום"
        } else {
            String.format(
                Locale.getDefault(),
                "הוצאה משוערת שנרשמה היום (%s): %.0f קק״ל",
                DateUtil.todayHebrew(), entries.sumOf { it.caloriesKcal }
            )
        }
    }

    /** Deletes immediately and offers undo, matching how food-log entries behave. */
    private fun deleteEntry(entry: ActivityLogEntity) {
        runSafely("PhysicalActivityActivity.deleteEntry") {
            UserDatabase.getInstance(applicationContext).activityLogDao().delete(entry)
            loadToday()
            showUndoSnackbar("הפעילות נמחקה") { restoreEntry(entry) }
        }
    }

    private fun restoreEntry(entry: ActivityLogEntity) {
        runSafely("PhysicalActivityActivity.restoreEntry") {
            UserDatabase.getInstance(applicationContext).activityLogDao().insert(entry)
            loadToday()
        }
    }

    private fun hideKeyboard(view: View) {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(view.windowToken, 0)
    }
}
