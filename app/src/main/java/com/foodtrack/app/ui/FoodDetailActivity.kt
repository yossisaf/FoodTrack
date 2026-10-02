package com.foodtrack.app.ui

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.foodtrack.app.R
import com.foodtrack.app.data.FoodSearchResult
import com.foodtrack.app.databinding.ActivityFoodDetailBinding
import java.util.Locale

class FoodDetailActivity : BaseActivity() {
    private lateinit var binding: ActivityFoodDetailBinding

    /**
     * When the entry was saved, close this screen too so the user lands back on the
     * search list (ready to add the next food) instead of on a detail page they
     * have already dealt with.
     */
    private val addLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFoodDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val food = FoodSearchResult(
            source = intent.getStringExtra(EXTRA_SOURCE).orEmpty(),
            refId = intent.getStringExtra(EXTRA_REF_ID).orEmpty(),
            nameHe = intent.getStringExtra(EXTRA_NAME).orEmpty(),
            subtitleHe = intent.getStringExtra(EXTRA_CATEGORY).orEmpty(),
            caloriesKcal = intent.getDoubleExtra(EXTRA_CALORIES, Double.NaN).takeUnless { it.isNaN() },
            proteinG = intent.getDoubleExtra(EXTRA_PROTEIN, Double.NaN).takeUnless { it.isNaN() },
            carbsG = intent.getDoubleExtra(EXTRA_CARBS, Double.NaN).takeUnless { it.isNaN() },
            fatG = intent.getDoubleExtra(EXTRA_FAT, Double.NaN).takeUnless { it.isNaN() },
            fiberG = intent.getDoubleExtra(EXTRA_FIBER, Double.NaN).takeUnless { it.isNaN() },
            sugarG = intent.getDoubleExtra(EXTRA_SUGAR, Double.NaN).takeUnless { it.isNaN() },
            saturatedFatG = intent.getDoubleExtra(EXTRA_SATURATED, Double.NaN).takeUnless { it.isNaN() },
            sodiumMg = intent.getDoubleExtra(EXTRA_SODIUM, Double.NaN).takeUnless { it.isNaN() },
            cholesterolMg = intent.getDoubleExtra(EXTRA_CHOLESTEROL, Double.NaN).takeUnless { it.isNaN() }
        )

        binding.textFoodName.text = food.nameHe
        binding.textCategory.text = food.subtitleHe.ifBlank { "מאכל מהמאגר" }

        val calories = food.caloriesKcal
        if (calories != null) {
            binding.textCalories.text = String.format(Locale.getDefault(), "%.0f קק\"ל", calories)
        } else {
            // No calorie value: say so plainly instead of showing a huge, primary-colored "no data".
            binding.textCalories.text = "אין נתון קלורי"
            binding.textCalories.textSize = 20f
            binding.textCalories.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }

        val macroRows = listOfNotNull(
            food.proteinG?.let { Row("חלבון", gramsText(it), R.color.macro_protein) },
            food.carbsG?.let { Row("פחמימות", gramsText(it), R.color.macro_carbs) },
            food.fatG?.let { Row("שומן", gramsText(it), R.color.macro_fat) }
        )
        val moreRows = listOfNotNull(
            food.fiberG?.let { Row("סיבים", gramsText(it), null) },
            food.sugarG?.let { Row("סוכרים", gramsText(it), null) },
            food.saturatedFatG?.let { Row("שומן רווי", gramsText(it), null) },
            food.sodiumMg?.let { Row("נתרן", mgText(it), null) },
            food.cholesterolMg?.let { Row("כולסטרול", mgText(it), null) }
        )
        macroRows.forEach { binding.containerMacros.addView(buildRow(it)) }
        moreRows.forEach { binding.containerMore.addView(buildRow(it)) }
        binding.dividerMore.visibility = if (macroRows.isNotEmpty() && moreRows.isNotEmpty()) View.VISIBLE else View.GONE
        binding.containerMacros.visibility = if (macroRows.isEmpty()) View.GONE else View.VISIBLE
        binding.containerMore.visibility = if (moreRows.isEmpty()) View.GONE else View.VISIBLE
        binding.textNoData.visibility = if (macroRows.isEmpty() && moreRows.isEmpty()) View.VISIBLE else View.GONE

        binding.buttonAddToLog.setOnClickListener { openAdd(food) }
    }

    private data class Row(val label: String, val value: String, val dotColorRes: Int?)

    private fun gramsText(v: Double) = String.format(Locale.getDefault(), "%.1f ג׳", v)
    private fun mgText(v: Double) = String.format(Locale.getDefault(), "%.0f מ\"ג", v)

    /** One label/value line: label on the reading-start side, value on the end side, 44dp tall. */
    private fun buildRow(row: Row): View {
        val line = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dpToPx(44)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        row.dotColorRes?.let { colorRes ->
            val dot = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(ContextCompat.getColor(this@FoodDetailActivity, colorRes))
                }
                layoutParams = LinearLayout.LayoutParams(dpToPx(10), dpToPx(10)).apply { marginEnd = dpToPx(10) }
            }
            line.addView(dot)
        }
        line.addView(TextView(this).apply {
            text = row.label
            textSize = 16f
            setTextColor(ContextCompat.getColor(this@FoodDetailActivity, R.color.text_primary))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        line.addView(TextView(this).apply {
            text = row.value
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@FoodDetailActivity, R.color.text_primary))
        })
        return line
    }

    private fun openAdd(food: FoodSearchResult) {
        addLauncher.launch(Intent(this, AddLogEntryActivity::class.java).apply {
            putExtra(AddLogEntryActivity.EXTRA_SOURCE, food.source)
            putExtra(AddLogEntryActivity.EXTRA_REF_ID, food.refId)
            putExtra(AddLogEntryActivity.EXTRA_NAME_HE, food.nameHe)
            putExtra(AddLogEntryActivity.EXTRA_CALORIES, food.caloriesKcal ?: 0.0)
            putExtra(AddLogEntryActivity.EXTRA_PROTEIN, food.proteinG ?: 0.0)
            putExtra(AddLogEntryActivity.EXTRA_CARBS, food.carbsG ?: 0.0)
            putExtra(AddLogEntryActivity.EXTRA_FAT, food.fatG ?: 0.0)
            putExtra(AddLogEntryActivity.EXTRA_NO_CALORIE_DATA, food.caloriesKcal == null)
        })
    }

    companion object {
        const val EXTRA_SOURCE = "food_source"
        const val EXTRA_REF_ID = "food_ref_id"
        const val EXTRA_NAME = "food_name"
        const val EXTRA_CATEGORY = "food_category"
        const val EXTRA_CALORIES = "food_calories"
        const val EXTRA_PROTEIN = "food_protein"
        const val EXTRA_CARBS = "food_carbs"
        const val EXTRA_FAT = "food_fat"
        const val EXTRA_FIBER = "food_fiber"
        const val EXTRA_SUGAR = "food_sugar"
        const val EXTRA_SATURATED = "food_saturated"
        const val EXTRA_SODIUM = "food_sodium"
        const val EXTRA_CHOLESTEROL = "food_cholesterol"
    }
}
