package com.foodtrack.app.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.foodtrack.app.R
import com.foodtrack.app.data.MealTemplateEntity
import com.foodtrack.app.data.MealTemplateItemEntity
import com.foodtrack.app.data.UserDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MealTemplateBuilderActivity : BaseActivity() {
    private val selected = mutableListOf<SelectedFood>()
    private lateinit var container: LinearLayout
    private lateinit var empty: TextView
    private lateinit var nameEdit: EditText
    private lateinit var saveButton: View

    // Without this, a fast double-tap on "save" could insert the same template twice.
    private var saving = false

    data class SelectedFood(
        val source: String,
        val refId: String,
        val name: String,
        val calories: Double,
        val protein: Double,
        val carbs: Double,
        val fat: Double,
        var grams: Double
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meal_template_builder)
        nameEdit = findViewById(R.id.editTemplateName)
        container = findViewById(R.id.templateItemsContainer)
        empty = findViewById(R.id.textTemplateEmpty)

        saveButton = findViewById(R.id.buttonSaveTemplate)
        findViewById<View>(R.id.buttonChooseFood).setOnClickListener { chooseFood() }
        saveButton.setOnClickListener { saveTemplate() }
        renderItems()
    }

    private fun chooseFood() {
        startActivityForResult(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_SELECT_FOR_MEAL, true), REQUEST_FOOD)
    }

    @Deprecated("Activity result API kept for minSdk 19 compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        if (requestCode == REQUEST_FOOD) {
            startPortionWizard(data)
            return
        }
        if (requestCode == REQUEST_PORTION) {
            val grams = data.getDoubleExtra(AddLogEntryActivity.EXTRA_SELECTED_GRAMS, 0.0)
            if (grams <= 0) return
            selected.add(SelectedFood(
                data.getStringExtra(AddLogEntryActivity.EXTRA_SOURCE).orEmpty(),
                data.getStringExtra(AddLogEntryActivity.EXTRA_REF_ID).orEmpty(),
                data.getStringExtra(AddLogEntryActivity.EXTRA_NAME_HE).orEmpty(),
                data.getDoubleExtra(AddLogEntryActivity.EXTRA_CALORIES, 0.0),
                data.getDoubleExtra(AddLogEntryActivity.EXTRA_PROTEIN, 0.0),
                data.getDoubleExtra(AddLogEntryActivity.EXTRA_CARBS, 0.0),
                data.getDoubleExtra(AddLogEntryActivity.EXTRA_FAT, 0.0), grams))
            renderItems()
        }
    }

    private fun startPortionWizard(data: Intent) {
        startActivityForResult(Intent(this, AddLogEntryActivity::class.java).apply {
            putExtra(AddLogEntryActivity.EXTRA_SOURCE, data.getStringExtra(MainActivity.EXTRA_SELECTED_SOURCE).orEmpty())
            putExtra(AddLogEntryActivity.EXTRA_REF_ID, data.getStringExtra(MainActivity.EXTRA_SELECTED_REF_ID).orEmpty())
            putExtra(AddLogEntryActivity.EXTRA_NAME_HE, data.getStringExtra(MainActivity.EXTRA_SELECTED_NAME).orEmpty())
            putExtra(AddLogEntryActivity.EXTRA_CALORIES, data.getDoubleExtra(MainActivity.EXTRA_SELECTED_CALORIES, 0.0))
            putExtra(AddLogEntryActivity.EXTRA_PROTEIN, data.getDoubleExtra(MainActivity.EXTRA_SELECTED_PROTEIN, 0.0))
            putExtra(AddLogEntryActivity.EXTRA_CARBS, data.getDoubleExtra(MainActivity.EXTRA_SELECTED_CARBS, 0.0))
            putExtra(AddLogEntryActivity.EXTRA_FAT, data.getDoubleExtra(MainActivity.EXTRA_SELECTED_FAT, 0.0))
            putExtra(AddLogEntryActivity.EXTRA_RETURN_PORTION, true)
        }, REQUEST_PORTION)
    }

    private fun showGramsDialog(default: Double, onDone: (Double?) -> Unit) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "גרם"
            setText(default.toInt().toString())
            selectAll()
        }
        AlertDialog.Builder(this)
            .setTitle("כמות במנה")
            .setMessage("כמה גרם מהמאכל יהיו בתבנית?")
            .setView(paddedForDialog(input))
            .setNegativeButton("ביטול") { _, _ -> onDone(null) }
            .setPositiveButton("הוסף") { _, _ ->
                val value = input.text.toString().toDoubleOrNull()?.takeIf { it > 0.0 && it <= 10000.0 }
                if (value == null) Toast.makeText(this, "יש להזין כמות בין 0 ל־10,000 גרם", Toast.LENGTH_SHORT).show()
                onDone(value)
            }.show()
    }

    private fun renderItems() {
        container.removeAllViews()
        empty.visibility = if (selected.isEmpty()) View.VISIBLE else View.GONE
        selected.forEach { food ->
            val row = layoutInflater.inflate(R.layout.item_template_food, container, false)
            row.findViewById<TextView>(R.id.textTemplateFood).text =
                String.format(Locale.getDefault(), "%s  •  %.0f ג׳", food.name, food.grams)
            row.findViewById<View>(R.id.buttonEditGrams).setOnClickListener {
                showGramsDialog(food.grams) { value -> if (value != null) { food.grams = value; renderItems() } }
            }
            // Remove by identity (not by captured index) so rapid taps can't delete the wrong row.
            row.findViewById<View>(R.id.buttonRemoveFood).setOnClickListener { selected.remove(food); renderItems() }
            container.addView(row)
        }
    }

    private fun saveTemplate() {
        if (saving) return
        val name = nameEdit.text.toString().trim()
        if (name.isBlank()) { nameEdit.error = "יש לתת שם לתבנית"; return }
        if (selected.isEmpty()) { Toast.makeText(this, "הוסף לפחות מאכל אחד", Toast.LENGTH_SHORT).show(); return }
        saving = true
        saveButton.isEnabled = false
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val dao = UserDatabase.getInstance(applicationContext).mealTemplateDao()
                    val id = dao.insertTemplate(MealTemplateEntity(nameHe = name, createdAt = System.currentTimeMillis()))
                    selected.forEach { f ->
                        dao.insertItem(MealTemplateItemEntity(templateId = id, foodSource = f.source, foodRefId = f.refId, foodNameHe = f.name, grams = f.grams))
                    }
                }
                Toast.makeText(this@MealTemplateBuilderActivity, "התבנית נשמרה", Toast.LENGTH_SHORT).show() // Toast (not Snackbar): screen closes right after
                finish()
            } catch (t: Exception) {
                if (!isFinishing && !isDestroyed) showCrashDialog("MealTemplateBuilderActivity.saveTemplate", t)
            } finally {
                // Only reached if the screen is still open (i.e. the save failed): allow a retry.
                if (!isFinishing) {
                    saving = false
                    saveButton.isEnabled = true
                }
            }
        }
    }

    companion object { private const val REQUEST_FOOD = 501; private const val REQUEST_PORTION = 502 }
}
