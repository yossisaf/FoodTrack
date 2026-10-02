package com.foodtrack.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Maps onto the "foods" table bundled in assets/databases/app_foods.db —
 * source: Ministry of Health "Tzameret" database (data.gov.il), already
 * natively in Hebrew including Israeli brand products. Replaced the
 * earlier USDA-based pipeline entirely (see project history / chat for
 * why: native Hebrew, richer portion coverage, no translation needed).
 *
 * category_he is nullable — Tzameret has no food-group column, unlike
 * the old USDA-derived data. Category-based browsing is not available
 * until/unless we build our own categorization on top of this.
 *
 * Portions live in a separate table (FoodPortionEntity, one-to-many) —
 * see food_portions. Do NOT re-add single portion_label/portion_grams
 * columns here; that was the old (too-limited) design.
 */
@Entity(
    tableName = "foods",
    indices = [Index(value = ["name_he"], name = "idx_name")]
)
data class FoodEntity(
    @PrimaryKey
    @ColumnInfo(name = "food_id")
    val foodId: String,

    @ColumnInfo(name = "name_he")
    val nameHe: String,

    @ColumnInfo(name = "category_he")
    val categoryHe: String?,

    @ColumnInfo(name = "calories_kcal")
    val caloriesKcal: Double?,

    @ColumnInfo(name = "protein_g")
    val proteinG: Double?,

    @ColumnInfo(name = "carbs_g")
    val carbsG: Double?,

    @ColumnInfo(name = "fat_g")
    val fatG: Double?,

    @ColumnInfo(name = "fiber_g")
    val fiberG: Double?,

    @ColumnInfo(name = "sugar_g")
    val sugarG: Double?,

    @ColumnInfo(name = "saturated_fat_g")
    val saturatedFatG: Double?,

    @ColumnInfo(name = "sodium_mg")
    val sodiumMg: Double?,

    @ColumnInfo(name = "cholesterol_mg")
    val cholesterolMg: Double?
)

/**
 * One row per known household measure for a base food (e.g. "כוס" = 240g).
 * food_id is a logical reference to foods.food_id — not a Room @ForeignKey
 * (this whole table is read-only reference data shipped as one asset, so
 * referential integrity is guaranteed by the data pipeline, not the DB).
 */
@Entity(
    tableName = "food_portions",
    indices = [Index(value = ["food_id"], name = "idx_portions_food_id")]
)
data class FoodPortionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "food_id")
    val foodId: String,

    @ColumnInfo(name = "label_he")
    val labelHe: String,

    val grams: Double
)
