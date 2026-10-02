package com.foodtrack.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface CustomFoodDao {
    @Insert
    suspend fun insert(food: CustomFoodEntity): Long

    @Query("SELECT * FROM custom_foods WHERE nameHe LIKE '%' || :query || '%' ORDER BY nameHe LIMIT :limit")
    suspend fun search(query: String, limit: Int = 120): List<CustomFoodEntity>

    @Query("SELECT * FROM custom_foods WHERE nameHe LIKE '%' || :token || '%' LIMIT :limit")
    suspend fun searchToken(token: String, limit: Int = 160): List<CustomFoodEntity>

    @Query("SELECT * FROM custom_foods WHERE id = :id")
    suspend fun byId(id: Long): CustomFoodEntity?

    @Query("SELECT * FROM custom_foods ORDER BY nameHe")
    suspend fun all(): List<CustomFoodEntity>
}

/** How often / how recently a food was logged; drives "your usual foods" and ranking. */
data class FoodUsage(
    val foodSource: String,
    val foodRefId: String,
    val uses: Int,
    val lastUsed: Long
)

@Dao
interface LogDao {
    @Insert
    suspend fun insert(entry: LogEntryEntity): Long

    @Delete
    suspend fun delete(entry: LogEntryEntity)

    @Query("SELECT * FROM log_entries WHERE date = :date ORDER BY timestamp DESC")
    suspend fun forDate(date: String): List<LogEntryEntity>

    @Query("SELECT COALESCE(SUM(caloriesKcal),0) FROM log_entries WHERE date = :date")
    suspend fun totalCaloriesForDate(date: String): Double

    @Query("SELECT COALESCE(SUM(proteinG),0) FROM log_entries WHERE date = :date")
    suspend fun totalProteinForDate(date: String): Double

    @Query("SELECT COALESCE(SUM(carbsG),0) FROM log_entries WHERE date = :date")
    suspend fun totalCarbsForDate(date: String): Double

    @Query("SELECT COALESCE(SUM(fatG),0) FROM log_entries WHERE date = :date")
    suspend fun totalFatForDate(date: String): Double

    /** Sum of calories logged on or after [startDate] (inclusive), for period averages. */
    @Query("SELECT COALESCE(SUM(caloriesKcal),0) FROM log_entries WHERE date >= :startDate")
    suspend fun totalCaloriesSince(startDate: String): Double

    /** Number of distinct days with at least one log entry on or after [startDate]. */
    @Query("SELECT COUNT(DISTINCT date) FROM log_entries WHERE date >= :startDate")
    suspend fun distinctLoggedDaysSince(startDate: String): Int

    /**
     * Most recently logged distinct foods (any date), most recent first —
     * powers the "recent foods" quick-add list. Groups by the food
     * reference so the same food logged many times only appears once.
     */
    @Query(
        """
        SELECT * FROM log_entries
        WHERE id IN (
            SELECT MAX(id) FROM log_entries GROUP BY foodSource, foodRefId
        )
        ORDER BY timestamp DESC
        LIMIT :limit
        """
    )
    suspend fun recentDistinctFoods(limit: Int = 10): List<LogEntryEntity>

    /**
     * Per-food log counts, most used first (ties: most recent first). Read-only
     * aggregate over existing rows — no schema change.
     */
    @Query(
        """
        SELECT foodSource AS foodSource, foodRefId AS foodRefId,
               COUNT(*) AS uses, MAX(timestamp) AS lastUsed
        FROM log_entries
        GROUP BY foodSource, foodRefId
        ORDER BY uses DESC, lastUsed DESC
        LIMIT :limit
        """
    )
    suspend fun usageStats(limit: Int = 400): List<FoodUsage>
}

@Dao
interface WeightDao {
    @Insert
    suspend fun insert(entry: WeightEntryEntity): Long

    @Query("SELECT * FROM weight_log ORDER BY timestamp DESC LIMIT 1")
    suspend fun latest(): WeightEntryEntity?

    @Query("SELECT * FROM weight_log ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recent(limit: Int = 30): List<WeightEntryEntity>

    @Query("SELECT * FROM weight_log ORDER BY timestamp ASC")
    suspend fun allAscending(): List<WeightEntryEntity>

    @Query("SELECT AVG(weightKg) FROM weight_log")
    suspend fun average(): Double?

    @Query("SELECT MIN(weightKg) FROM weight_log")
    suspend fun minimum(): Double?

    @Query("SELECT MAX(weightKg) FROM weight_log")
    suspend fun maximum(): Double?

    @Delete
    suspend fun delete(entry: WeightEntryEntity)
}

@Dao
interface CustomFoodPortionDao {
    @Insert
    suspend fun insert(portion: CustomFoodPortionEntity): Long

    @Query("SELECT * FROM custom_food_portions WHERE customFoodId = :customFoodId ORDER BY grams")
    suspend fun portionsFor(customFoodId: Long): List<CustomFoodPortionEntity>
}

@Dao
interface FavoriteFoodDao {
    @Insert
    suspend fun insert(favorite: FavoriteFoodEntity): Long

    /**
     * Deletes every row for this food, not just one. A rapid double-tap on the
     * star can race two "not favorited yet" checks into two insert()s before
     * either commits; deleting by key (instead of @Delete on a single fetched
     * row) means un-favoriting always fully clears it, with no orphan row left
     * behind to make the food silently resurface later.
     */
    @Query("DELETE FROM favorite_foods WHERE foodSource = :source AND foodRefId = :refId")
    suspend fun deleteFor(source: String, refId: String)

    @Query("SELECT * FROM favorite_foods ORDER BY addedAt DESC")
    suspend fun all(): List<FavoriteFoodEntity>

    @Query("SELECT * FROM favorite_foods WHERE foodSource = :source AND foodRefId = :refId LIMIT 1")
    suspend fun find(source: String, refId: String): FavoriteFoodEntity?
}

@Dao
interface MealTemplateDao {
    @Insert
    suspend fun insertTemplate(template: MealTemplateEntity): Long

    @Insert
    suspend fun insertItem(item: MealTemplateItemEntity): Long

    @Query("SELECT * FROM meal_templates ORDER BY nameHe")
    suspend fun allTemplates(): List<MealTemplateEntity>

    @Query("SELECT * FROM meal_template_items WHERE templateId = :templateId")
    suspend fun itemsFor(templateId: Long): List<MealTemplateItemEntity>

    @Query("DELETE FROM meal_template_items WHERE templateId = :templateId")
    suspend fun deleteItemsFor(templateId: Long)

    @Delete
    suspend fun deleteTemplate(template: MealTemplateEntity)

    @Query("DELETE FROM meal_templates WHERE id = :templateId")
    suspend fun deleteTemplateById(templateId: Long)
}

@Dao
interface WaterDao {
    @Insert
    suspend fun insertEvent(event: WaterEventEntity): Long

    @Query("SELECT COUNT(*) FROM water_log WHERE date = :date")
    suspend fun countForDate(date: String): Int

    @Query("SELECT * FROM water_log WHERE date = :date ORDER BY timestamp DESC LIMIT 1")
    suspend fun mostRecentForDate(date: String): WaterEventEntity?

    @Delete
    suspend fun delete(event: WaterEventEntity)
}

@Dao
interface ActivityLogDao {
    @Insert
    suspend fun insert(entry: ActivityLogEntity): Long

    @Query("SELECT * FROM activity_log WHERE date = :date ORDER BY timestamp DESC")
    suspend fun forDate(date: String): List<ActivityLogEntity>

    @Query("SELECT COALESCE(SUM(caloriesKcal),0) FROM activity_log WHERE date = :date")
    suspend fun totalCaloriesForDate(date: String): Double

    @Query("SELECT COALESCE(SUM(durationMinutes),0) FROM activity_log WHERE date = :date")
    suspend fun totalDurationMinutesForDate(date: String): Double

    @Delete
    suspend fun delete(entry: ActivityLogEntity)
}
