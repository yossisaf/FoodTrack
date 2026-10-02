package com.foodtrack.app.data

import android.content.Context
import com.foodtrack.app.search.FoodSearchEngine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Category logic lives outside app_foods.db. Base foods are loaded once and
 * classified once in the background, then served from an in-memory index.
 * The same in-memory list also feeds the text search engine, so typing in the
 * search box never touches SQLite for the reference data.
 *
 * Use [getInstance]: the index is process-wide, so re-opening the food screen
 * is instant instead of reloading and reclassifying ~4,500 foods every time.
 */
class FoodCategoryRepository(context: Context) {
    private val appContext = context.applicationContext
    private val cache = FoodCategoryCache()
    private val loadMutex = Mutex()
    @Volatile private var baseFoods: List<FoodSearchResult>? = null
    @Volatile private var categoryIndex: Map<FoodCategory, List<FoodSearchResult>>? = null
    @Volatile private var engine: FoodSearchEngine? = null

    /** In-memory text search over all base foods (built once, on first use). */
    suspend fun searchEngine(): FoodSearchEngine = loadMutex.withLock {
        ensureIndexLocked()
        engine!!
    }

    suspend fun allBaseFoods(): List<FoodSearchResult> = loadMutex.withLock {
        ensureIndexLocked()
        baseFoods.orEmpty()
    }

    /** Returns a precomputed category slice; no 4,510-row scan on each search. */
    suspend fun foodsForCategory(category: FoodCategory): List<FoodSearchResult> = loadMutex.withLock {
        ensureIndexLocked()
        if (category == FoodCategory.ALL) baseFoods.orEmpty()
        else categoryIndex.orEmpty()[category].orEmpty()
    }

    private suspend fun ensureIndexLocked() {
        if (baseFoods != null && categoryIndex != null) return
        val foods = AppDatabase.getInstance(appContext).foodDao().allFoods()
            .map { it.toSearchResult() }

        // Classification is performed exactly once per food. The caller invokes
        // this from Dispatchers.IO, so the first-load work never blocks the UI.
        val index = LinkedHashMap<FoodCategory, MutableList<FoodSearchResult>>()
        FoodCategory.values().forEach { index[it] = ArrayList() }
        foods.forEach { food ->
            val category = categoryFor(food)
            index.getValue(category).add(food)
        }
        engine = FoodSearchEngine(foods)
        baseFoods = foods
        categoryIndex = index.mapValues { it.value.toList() }
    }

    fun categoryFor(food: FoodSearchResult): FoodCategory =
        cache.categoryFor(food.source, food.refId, food.nameHe)

    fun resultFor(food: FoodSearchResult): CategoryResult =
        cache.resultFor(food.source, food.refId, food.nameHe)

    fun cacheSize(): Int = cache.size()

    companion object {
        @Volatile private var instance: FoodCategoryRepository? = null

        fun getInstance(context: Context): FoodCategoryRepository =
            instance ?: synchronized(this) {
                instance ?: FoodCategoryRepository(context.applicationContext).also { instance = it }
            }
    }
}
