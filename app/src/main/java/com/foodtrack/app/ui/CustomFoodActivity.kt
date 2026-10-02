package com.foodtrack.app.ui

import android.os.Bundle
import android.widget.Toast
import com.foodtrack.app.data.CustomFoodEntity
import com.foodtrack.app.data.UserDatabase
import com.foodtrack.app.databinding.ActivityCustomFoodBinding

class CustomFoodActivity : BaseActivity() {

    private lateinit var binding: ActivityCustomFoodBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomFoodBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonSave.setOnClickListener { save() }
        // "Done" on the keyboard's last field saves, like the button.
        binding.editFat.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) { save(); true } else false
        }

        // Coming from a search that came up empty: the name is already known, so
        // pre-fill it and send the user straight to the calorie field instead of
        // making them retype what they just searched for.
        val prefillName = intent.getStringExtra(EXTRA_PREFILL_NAME)?.trim()
        if (!prefillName.isNullOrEmpty()) {
            binding.editName.setText(prefillName)
            binding.editCalories.requestFocus()
        }
    }

    private fun textToDoubleOrNull(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()

    // Without this, a fast double-tap on "save" (or tapping save right as the
    // keyboard's "Done" fires) could insert the same custom food twice before
    // the screen finishes.
    private var saving = false

    private fun save() {
        if (saving) return
        val name = binding.editName.text.toString().trim()
        binding.layoutName.error = null
        binding.layoutCalories.error = null
        if (name.isEmpty()) {
            binding.layoutName.error = "יש להזין שם למאכל"
            binding.editName.requestFocus()
            return
        }
        val calories = textToDoubleOrNull(binding.editCalories.text.toString())
        if (calories == null || calories < 0) {
            binding.layoutCalories.error = "יש להזין קלוריות ל־100 גרם"
            binding.editCalories.requestFocus()
            return
        }

        saving = true
        binding.buttonSave.isEnabled = false
        runSafely("CustomFoodActivity.save") {
            try {
                val food = CustomFoodEntity(
                    nameHe = name,
                    caloriesKcal = calories,
                    proteinG = textToDoubleOrNull(binding.editProtein.text.toString()),
                    carbsG = textToDoubleOrNull(binding.editCarbs.text.toString()),
                    fatG = textToDoubleOrNull(binding.editFat.text.toString()),
                    fiberG = null,
                    sugarG = null,
                    saturatedFatG = null,
                    sodiumMg = null,
                    cholesterolMg = null,
                    createdAt = System.currentTimeMillis()
                )
                UserDatabase.getInstance(applicationContext).customFoodDao().insert(food)
                Toast.makeText(this@CustomFoodActivity, "נשמר — אפשר לחפש אותו עכשיו", Toast.LENGTH_SHORT).show()
                finish()
            } finally {
                // Only reached if the screen is still open (i.e. the save failed): allow a retry.
                if (!isFinishing) {
                    saving = false
                    binding.buttonSave.isEnabled = true
                }
            }
        }
    }

    companion object {
        const val EXTRA_PREFILL_NAME = "prefillName"
    }
}
