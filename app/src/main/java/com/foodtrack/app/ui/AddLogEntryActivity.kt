package com.foodtrack.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.foodtrack.app.R
import com.foodtrack.app.data.AppDatabase
import com.foodtrack.app.data.LogEntryEntity
import com.foodtrack.app.data.UserDatabase
import com.foodtrack.app.databinding.ActivityAddLogEntryBinding
import com.foodtrack.app.util.DateUtil
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipDrawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class AddLogEntryActivity : BaseActivity() {

    private lateinit var binding: ActivityAddLogEntryBinding

    private lateinit var source: String
    private lateinit var refId: String
    private lateinit var nameHe: String
    private var caloriesPer100 = 0.0
    private var proteinPer100 = 0.0
    private var carbsPer100 = 0.0
    private var fatPer100 = 0.0
    private var noCalorieData = false
    private var returnPortion = false

    private var selectedUnit = "גרם"
    private var gramsPerUnit = 1.0
    private var unitIsGram = true
    private var suppressQuantityWatcher = false

    /** Guards against double-taps creating duplicate log entries. */
    private var saving = false

    /** The single user-defined unit ("מידה אחרת…"), kept so it survives rotation. */
    private var customPortion: PortionOption? = null
    private var customChip: Chip? = null

    private data class PortionOption(val label: String, val grams: Double)

    private val mealNames by lazy {
        mapOf(
            R.id.buttonMealBreakfast to "בוקר",
            R.id.buttonMealLunch to "צהריים",
            R.id.buttonMealDinner to "ערב",
            R.id.buttonMealSnack to "נשנוש"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddLogEntryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        source = intent.getStringExtra(EXTRA_SOURCE).orEmpty()
        refId = intent.getStringExtra(EXTRA_REF_ID).orEmpty()
        nameHe = intent.getStringExtra(EXTRA_NAME_HE).orEmpty()
        caloriesPer100 = intent.getDoubleExtra(EXTRA_CALORIES, 0.0)
        proteinPer100 = intent.getDoubleExtra(EXTRA_PROTEIN, 0.0)
        carbsPer100 = intent.getDoubleExtra(EXTRA_CARBS, 0.0)
        fatPer100 = intent.getDoubleExtra(EXTRA_FAT, 0.0)
        noCalorieData = intent.getBooleanExtra(EXTRA_NO_CALORIE_DATA, false)
        returnPortion = intent.getBooleanExtra(EXTRA_RETURN_PORTION, false)

        binding.textFoodName.text = nameHe
        binding.textCalorieWarning.visibility = if (noCalorieData) View.VISIBLE else View.GONE

        if (returnPortion) {
            // Building a meal template: there is no meal type here and nothing is logged yet.
            binding.textHeading.text = "בחירת כמות לתבנית"
            binding.textPreviewLabel.text = "הערכים לכמות שנבחרה"
            binding.textMealLabel.visibility = View.GONE
            binding.cardMealType.visibility = View.GONE
            binding.buttonAddToLog.text = "בחר כמות והוסף לתבנית"
        } else {
            binding.buttonAddToLog.text = "הוסף ליומן"
        }

        setupMealTypeSelector()

        if (savedInstanceState == null) restoreDefaults() else restoreState(savedInstanceState)

        binding.editQuantity.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!suppressQuantityWatcher) updatePreview()
            }
        })
        // "Done" on the keyboard only dismisses it; saving stays an explicit tap on the button.
        binding.editQuantity.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard(v)
                true
            } else false
        }
        binding.buttonQuantityMinus.setOnClickListener { stepQuantity(-1) }
        binding.buttonQuantityPlus.setOnClickListener { stepQuantity(1) }

        binding.buttonAddToLog.setOnClickListener {
            hideKeyboard(it)
            if (returnPortion) returnPortionToCaller() else saveEntry()
        }
        loadPortions()
        updatePreview()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_QTY, binding.editQuantity.text.toString())
        outState.putString(STATE_UNIT, selectedUnit)
        outState.putDouble(STATE_GRAMS_PER_UNIT, gramsPerUnit)
        outState.putBoolean(STATE_UNIT_IS_GRAM, unitIsGram)
        customPortion?.let {
            outState.putString(STATE_CUSTOM_LABEL, it.label)
            outState.putDouble(STATE_CUSTOM_GRAMS, it.grams)
        }
    }

    private fun restoreDefaults() {
        setGramMode(100.0)
    }

    private fun restoreState(state: Bundle) {
        selectedUnit = state.getString(STATE_UNIT) ?: "גרם"
        gramsPerUnit = state.getDouble(STATE_GRAMS_PER_UNIT, 1.0)
        unitIsGram = state.getBoolean(STATE_UNIT_IS_GRAM, true)
        if (state.containsKey(STATE_CUSTOM_LABEL)) {
            customPortion = PortionOption(
                state.getString(STATE_CUSTOM_LABEL).orEmpty(),
                state.getDouble(STATE_CUSTOM_GRAMS, 0.0)
            )
        }
        binding.textSelectedUnit.text = selectedUnit
        setQuantityText(state.getString(STATE_QTY).orEmpty())
    }

    private fun setGramMode(quantity: Double) {
        selectedUnit = "גרם"
        gramsPerUnit = 1.0
        unitIsGram = true
        binding.textSelectedUnit.text = "גרם"
        setQuantityText(formatQuantity(quantity))
        updatePreview()
    }

    private fun selectPortion(label: String, grams: Double) {
        if (grams <= 0.0) return
        selectedUnit = label
        gramsPerUnit = grams
        unitIsGram = false
        binding.textSelectedUnit.text = label
        setQuantityText("1")
        updatePreview()
    }

    private fun setQuantityText(text: String) {
        suppressQuantityWatcher = true
        binding.editQuantity.setText(text)
        binding.editQuantity.setSelection(binding.editQuantity.text.length)
        suppressQuantityWatcher = false
    }

    /** −/+ steppers: 10 g per tap in gram mode, half a unit per tap for portions. */
    private fun stepQuantity(direction: Int) {
        val step = if (unitIsGram) 10.0 else 0.5
        val minValue = if (unitIsGram) 1.0 else 0.5
        val maxValue = MAX_GRAMS / gramsPerUnit
        val next = (quantityEntered() + direction * step).coerceIn(minValue, maxValue)
        setQuantityText(formatQuantity(next))
        updatePreview()
    }

    private fun newChip(label: String): Chip = Chip(this).apply {
        // Same Choice look as the meal-type chips instead of the default Action-chip look.
        setChipDrawable(
            ChipDrawable.createFromAttributes(
                this@AddLogEntryActivity, null, 0,
                com.google.android.material.R.style.Widget_MaterialComponents_Chip_Choice
            )
        )
        text = label
    }

    private fun portionChip(option: PortionOption): Chip = newChip(option.label).apply {
        isCheckable = true
        tag = option
        // Re-tapping the current unit must not reset the quantity back to 1.
        setOnClickListener {
            if (unitIsGram || selectedUnit != option.label || gramsPerUnit != option.grams) {
                selectPortion(option.label, option.grams)
            }
        }
    }

    private fun loadPortions() {
        runSafely("AddLogEntryActivity.loadPortions") {
            val portions: List<PortionOption> = withContext(Dispatchers.IO) {
                when (source) {
                    "base" -> AppDatabase.getInstance(applicationContext).foodDao().portionsFor(refId)
                        .map { PortionOption(it.labelHe, it.grams) }
                    "custom" -> refId.toLongOrNull()?.let { id ->
                        UserDatabase.getInstance(applicationContext).customFoodPortionDao().portionsFor(id)
                            .map { PortionOption(it.labelHe, it.grams) }
                    } ?: emptyList()
                    else -> emptyList()
                }
            }
            val group = binding.portionChipGroup
            group.removeAllViews()
            customChip = null

            val gramChip = newChip("גרם").apply {
                isCheckable = true
                setOnClickListener {
                    if (!unitIsGram) setGramMode(gramsEntered().takeIf { it > 0 } ?: 100.0)
                }
            }
            group.addView(gramChip)
            gramChip.isChecked = unitIsGram

            val chips = mutableListOf<Chip>()
            portions.distinctBy { it.label.trim() }.take(8).forEach { portion ->
                val chip = portionChip(portion)
                group.addView(chip)
                chips += chip
            }

            val moreChip = newChip("+ מידה אחרת").apply {
                isCheckable = false
                setOnClickListener { showCustomUnitDialog() }
            }
            group.addView(moreChip)

            // Rebuild selection after rotation.
            customPortion?.let { addCustomPortionChip(it, select = false) }
            if (!unitIsGram) {
                val current = PortionOption(selectedUnit, gramsPerUnit)
                val match = (chips + listOfNotNull(customChip)).firstOrNull { it.tag == current }
                match?.isChecked = true
            }
        }
    }

    /** Adds (replacing any previous) the user-defined unit chip just before "+ מידה אחרת". */
    private fun addCustomPortionChip(option: PortionOption, select: Boolean) {
        val group = binding.portionChipGroup
        customChip?.let { group.removeView(it) }
        val chip = portionChip(option)
        group.addView(chip, (group.childCount - 1).coerceAtLeast(0))
        customChip = chip
        if (select) chip.isChecked = true
    }

    private fun showCustomUnitDialog() {
        val nameInput = EditText(this).apply {
            hint = "למשל: כף, מארז, פרוסה"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_NEXT
        }
        val gramsInput = EditText(this).apply {
            hint = "כמה גרם יש במידה אחת?"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(24), dpToPx(8), dpToPx(24), 0)
            addView(nameInput)
            addView(
                gramsInput,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = dpToPx(8) }
            )
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("מידה אישית")
            .setMessage("לדוגמה: מארז = 250 גרם")
            .setView(box)
            .setNegativeButton("ביטול", null)
            .setPositiveButton("בחר", null)
            .create()

        fun submit() {
            val label = nameInput.text.toString().trim()
            val grams = gramsInput.text.toString().replace(',', '.').toDoubleOrNull() ?: 0.0
            if (label.isBlank()) {
                nameInput.error = "יש להזין שם למידה"
                nameInput.requestFocus()
                return
            }
            if (grams <= 0.0 || grams > MAX_GRAMS) {
                gramsInput.error = "יש להזין משקל בין 0 ל־10,000 גרם"
                gramsInput.requestFocus()
                return
            }
            val option = PortionOption(label, grams)
            customPortion = option
            addCustomPortionChip(option, select = true)
            selectPortion(option.label, option.grams)
            dialog.dismiss()
        }

        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener { submit() }
            nameInput.requestFocus()
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        }
        gramsInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { submit(); true } else false
        }
        dialog.show()
    }

    private fun setupMealTypeSelector() {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val suggestedId = when (hour) {
            in 5..10 -> R.id.buttonMealBreakfast
            in 11..15 -> R.id.buttonMealLunch
            in 16..20 -> R.id.buttonMealDinner
            else -> R.id.buttonMealSnack
        }
        // selectionRequired keeps one chip checked; a restored state (rotation) overrides this default.
        binding.mealTypeGroup.check(suggestedId)
    }

    private fun selectedMealType(): String = mealNames[binding.mealTypeGroup.checkedChipId].orEmpty()

    private fun quantityEntered(): Double = binding.editQuantity.text.toString().replace(',', '.').toDoubleOrNull() ?: 0.0

    private fun gramsEntered(): Double = quantityEntered() * gramsPerUnit

    private fun isValidGrams(grams: Double) = grams > 0.0 && grams <= MAX_GRAMS

    /** Updates the live totals, the "= X grams" helper line, and whether saving is currently possible. */
    private fun updatePreview() {
        val grams = gramsEntered()
        val valid = isValidGrams(grams)
        binding.buttonAddToLog.isEnabled = valid && !saving

        if (!valid) {
            val empty = binding.editQuantity.text.isNullOrBlank()
            binding.textUnitHint.text = if (empty) "הזן כמות" else "יש להזין כמות בין 0 ל־10,000 גרם"
            binding.textUnitHint.setTextColor(
                ContextCompat.getColor(this, if (empty) R.color.text_secondary else R.color.danger)
            )
            binding.textPreview.text = "—"
            binding.textPreviewMacros.text = ""
            return
        }

        binding.textUnitHint.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        binding.textUnitHint.text = if (unitIsGram) {
            "הזן משקל מדויק בגרמים"
        } else {
            "מנה אחת = ${formatGrams(gramsPerUnit)} גרם  •  סה״כ ${formatGrams(grams)} גרם"
        }

        val factor = grams / 100.0
        binding.textPreview.text = String.format(Locale.getDefault(), "%.0f קק\"ל", caloriesPer100 * factor)
        binding.textPreviewMacros.text = String.format(
            Locale.getDefault(),
            "חלבון %.1f ג'  •  פחמימות %.1f ג'  •  שומן %.1f ג'",
            proteinPer100 * factor, carbsPer100 * factor, fatPer100 * factor
        )
    }

    private fun returnPortionToCaller() {
        val grams = gramsEntered()
        if (!isValidGrams(grams)) {
            updatePreview()
            return
        }
        setResult(RESULT_OK, Intent().apply {
            putExtra(EXTRA_SELECTED_GRAMS, grams)
            putExtra(EXTRA_SOURCE, source)
            putExtra(EXTRA_REF_ID, refId)
            putExtra(EXTRA_NAME_HE, nameHe)
            putExtra(EXTRA_CALORIES, caloriesPer100)
            putExtra(EXTRA_PROTEIN, proteinPer100)
            putExtra(EXTRA_CARBS, carbsPer100)
            putExtra(EXTRA_FAT, fatPer100)
        })
        finish()
    }

    private fun saveEntry() {
        if (saving) return
        val grams = gramsEntered()
        if (!isValidGrams(grams)) {
            updatePreview()
            return
        }
        saving = true
        binding.buttonAddToLog.isEnabled = false

        val factor = grams / 100.0
        val mealType = selectedMealType()
        val calories = caloriesPer100 * factor

        runSafely("AddLogEntryActivity.saveEntry") {
            try {
                UserDatabase.getInstance(applicationContext).logDao().insert(
                    LogEntryEntity(
                        date = DateUtil.today(),
                        timestamp = System.currentTimeMillis(),
                        foodSource = source,
                        foodRefId = refId,
                        foodNameHe = nameHe,
                        grams = grams,
                        caloriesKcal = calories,
                        proteinG = proteinPer100 * factor,
                        carbsG = carbsPer100 * factor,
                        fatG = fatPer100 * factor,
                        mealType = mealType
                    )
                )
                Toast.makeText(
                    this@AddLogEntryActivity,
                    String.format(Locale.getDefault(), "נוסף ליומן: %s גרם, %.0f קק\"ל", formatGrams(grams), calories),
                    Toast.LENGTH_SHORT
                ).show()
                // Lets the detail screen close itself too, so the user returns to the search list.
                setResult(RESULT_OK)
                finish()
            } finally {
                // Only reached with the screen still open when the save failed: allow a retry.
                if (!isFinishing) {
                    saving = false
                    updatePreview()
                }
            }
        }
    }

    private fun hideKeyboard(view: View) {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun formatQuantity(value: Double): String {
        val rounded = Math.round(value * 100) / 100.0
        return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
    }

    private fun formatGrams(value: Double): String =
        if (value % 1.0 == 0.0) String.format(Locale.getDefault(), "%.0f", value)
        else String.format(Locale.getDefault(), "%.1f", value)

    companion object {
        const val EXTRA_SOURCE = "source"
        const val EXTRA_REF_ID = "refId"
        const val EXTRA_NAME_HE = "nameHe"
        const val EXTRA_CALORIES = "caloriesPer100"
        const val EXTRA_PROTEIN = "proteinPer100"
        const val EXTRA_CARBS = "carbsPer100"
        const val EXTRA_FAT = "fatPer100"
        const val EXTRA_RETURN_PORTION = "returnPortion"
        const val EXTRA_SELECTED_GRAMS = "selectedGrams"
        const val EXTRA_NO_CALORIE_DATA = "noCalorieData"

        private const val MAX_GRAMS = 10000.0
        private const val STATE_QTY = "state_qty"
        private const val STATE_UNIT = "state_unit"
        private const val STATE_GRAMS_PER_UNIT = "state_grams_per_unit"
        private const val STATE_UNIT_IS_GRAM = "state_unit_is_gram"
        private const val STATE_CUSTOM_LABEL = "state_custom_label"
        private const val STATE_CUSTOM_GRAMS = "state_custom_grams"
    }
}
