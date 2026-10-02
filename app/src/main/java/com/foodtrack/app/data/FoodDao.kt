package com.foodtrack.app.data

import androidx.room.Dao
import androidx.room.Query

@Dao
interface FoodDao {

    @Query(
        """
        SELECT * FROM foods
        WHERE name_he LIKE '%' || :query || '%'
        ORDER BY name_he
        LIMIT :limit
        """
    )
    suspend fun search(query: String, limit: Int = 120): List<FoodEntity>

    @Query("SELECT * FROM foods WHERE name_he LIKE '%' || :token || '%' LIMIT :limit")
    suspend fun searchToken(token: String, limit: Int = 160): List<FoodEntity>

    @Query("SELECT * FROM foods ORDER BY name_he")
    suspend fun allFoods(): List<FoodEntity>

    @Query("SELECT * FROM foods WHERE food_id = :foodId")
    suspend fun byId(foodId: String): FoodEntity?

    @Query("SELECT * FROM food_portions WHERE food_id = :foodId ORDER BY grams")
    suspend fun portionsFor(foodId: String): List<FoodPortionEntity>
}
