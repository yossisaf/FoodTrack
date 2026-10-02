package com.foodtrack.app.data

import java.util.concurrent.ConcurrentHashMap

/** In-memory cache only; no category data is persisted to app_foods.db. */
class FoodCategoryCache(
    private val classifier: FoodCategoryClassifier = FoodCategoryClassifier()
) {
    private val cache = ConcurrentHashMap<String, CategoryResult>()

    fun resultFor(source: String, foodId: String, foodName: String): CategoryResult =
        cache.getOrPut("$source:$foodId") { classifier.classify(foodName) }

    fun categoryFor(source: String, foodId: String, foodName: String): FoodCategory =
        resultFor(source, foodId, foodName).category

    fun size(): Int = cache.size
}
