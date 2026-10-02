package com.foodtrack.app.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.foodtrack.app.R
import com.foodtrack.app.data.FoodEntity
import com.foodtrack.app.data.LogEntryEntity
import com.foodtrack.app.data.UserDatabase
import com.foodtrack.app.util.DateUtil
import com.foodtrack.app.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MealTemplatesActivity : BaseActivity() {
    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meal_templates)
        container = findViewById(R.id.templatesContainer)
        findViewById<View>(R.id.buttonNewTemplate).setOnClickListener {
            startActivity(Intent(this, MealTemplateBuilderActivity::class.java))
        }
        loadTemplates()
    }

    override fun onResume() { super.onResume(); if (::container.isInitialized) loadTemplates() }

    private fun loadTemplates() {
        lifecycleScope.launch {
            try {
                val templates = withContext(Dispatchers.IO) { UserDatabase.getInstance(applicationContext).mealTemplateDao().allTemplates() }
                container.removeAllViews()
                if (templates.isEmpty()) {
                    container.addView(TextView(this@MealTemplatesActivity).apply {
                        text = "עדיין אין תבניות.\nבנו ארוחה פעם אחת והוסיפו אותה ליומן בלחיצה אחת."
                        textSize = 16f
                        gravity = android.view.Gravity.CENTER
                        setTextColor(resources.getColor(R.color.text_secondary))
                        setBackgroundResource(R.drawable.bg_empty)
                        setPadding(dpToPx(20), dpToPx(28), dpToPx(20), dpToPx(28))
                    })
                }
                templates.forEach { template -> addTemplateView(template.id, template.nameHe) }
            } catch (t: Exception) { if (!isFinishing && !isDestroyed) showCrashDialog("MealTemplatesActivity.loadTemplates", t) }
        }
    }

    private fun addTemplateView(id: Long, name: String) {
        val row = layoutInflater.inflate(R.layout.item_template, container, false)
        row.findViewById<TextView>(R.id.textTemplateName).text = name
        row.findViewById<View>(R.id.buttonApplyTemplate).setOnClickListener { confirmApply(id, name) }
        row.findViewById<View>(R.id.buttonDeleteTemplate).setOnClickListener { confirmDelete(id, name) }
        container.addView(row)
    }

    private fun confirmApply(id: Long, name: String) {
        AlertDialog.Builder(this).setTitle("להוסיף את $name ליומן?").setMessage("כל המאכלים בתבנית יתווספו להיום לפי הכמויות ששמרת.")
            .setNegativeButton("ביטול", null).setPositiveButton("הוסף") { _, _ -> applyTemplate(id) }.show()
    }

    private fun applyTemplate(id: Long) {
        val mealType = when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
            in 5..10 -> "בוקר"
            in 11..15 -> "צהריים"
            in 16..20 -> "ערב"
            else -> "נשנוש"
        }
        lifecycleScope.launch {
            try {
                // addedCount tracks foods actually inserted, not items.size: an item whose
                // food was since removed from the database is silently skipped below, and
                // the message must reflect what actually landed in the log, not what the
                // template listed.
                var addedCount = 0
                var skippedCount = 0
                withContext(Dispatchers.IO) {
                    val db = UserDatabase.getInstance(applicationContext)
                    val items = db.mealTemplateDao().itemsFor(id)
                    val baseDao = AppDatabase.getInstance(applicationContext).foodDao()
                    val customDao = db.customFoodDao()
                    val logDao = db.logDao()
                    items.forEach { item ->
                        val values = if (item.foodSource == "custom") {
                            customDao.byId(item.foodRefId.toLongOrNull() ?: -1L)?.let { f -> doubleArrayOf(f.caloriesKcal ?: 0.0, f.proteinG ?: 0.0, f.carbsG ?: 0.0, f.fatG ?: 0.0) }
                        } else {
                            baseDao.byId(item.foodRefId)?.let { f -> doubleArrayOf(f.caloriesKcal ?: 0.0, f.proteinG ?: 0.0, f.carbsG ?: 0.0, f.fatG ?: 0.0) }
                        }
                        if (values != null) {
                            val factor = item.grams / 100.0
                            logDao.insert(LogEntryEntity(
                                date = DateUtil.today(), timestamp = System.currentTimeMillis(), foodSource = item.foodSource,
                                foodRefId = item.foodRefId, foodNameHe = item.foodNameHe, grams = item.grams,
                                caloriesKcal = values[0] * factor, proteinG = values[1] * factor,
                                carbsG = values[2] * factor, fatG = values[3] * factor, mealType = mealType
                            ))
                            addedCount++
                        } else {
                            skippedCount++
                        }
                    }
                }
                showMessage(
                    if (skippedCount == 0) "נוספו $addedCount מאכלים ליומן"
                    else "נוספו $addedCount מאכלים ליומן · $skippedCount לא נמצאו יותר במאגר"
                )
            } catch (t: Exception) { if (!isFinishing && !isDestroyed) showCrashDialog("MealTemplatesActivity.applyTemplate", t) }
        }
    }

    private fun confirmDelete(id: Long, name: String) {
        AlertDialog.Builder(this).setTitle("למחוק את $name?").setNegativeButton("ביטול", null).setPositiveButton("מחק") { _, _ ->
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val dao = UserDatabase.getInstance(applicationContext).mealTemplateDao()
                        dao.deleteItemsFor(id)
                        dao.deleteTemplateById(id)
                    }
                    loadTemplates()
                } catch (t: Exception) { if (!isFinishing && !isDestroyed) showCrashDialog("MealTemplatesActivity.deleteTemplate", t) }
            }
        }.show()
    }
}
