package com.foodtrack.app.data

/**
 * Common shape for "a food that can be searched and logged", whether it
 * comes from the bundled reference DB (source="base") or was entered by
 * the user (source="custom"). Search screens work against this type only.
 *
 * Deliberately does NOT include portion data — with the Tzameret-based
 * schema a food can have many portions (avg ~4), and fetching them for
 * every row in a search results list would mean N+1 queries. Portions
 * are looked up on demand in AddLogEntryActivity, once a specific food
 * is chosen.
 */
data class FoodSearchResult(
    val source: String, // "base" or "custom"
    val refId: String,  // food_id, or custom_foods.id.toString()
    val nameHe: String,
    val subtitleHe: String, // category (base, if known) or "מזון אישי" (custom)
    val caloriesKcal: Double?,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    val fiberG: Double?,
    val sugarG: Double?,
    val saturatedFatG: Double?,
    val sodiumMg: Double?,
    val cholesterolMg: Double?
)

fun FoodEntity.toSearchResult(): FoodSearchResult = FoodSearchResult(
    source = "base",
    refId = foodId,
    nameHe = nameHe,
    subtitleHe = categoryHe ?: "",
    caloriesKcal = caloriesKcal,
    proteinG = proteinG,
    carbsG = carbsG,
    fatG = fatG,
    fiberG = fiberG,
    sugarG = sugarG,
    saturatedFatG = saturatedFatG,
    sodiumMg = sodiumMg,
    cholesterolMg = cholesterolMg
)

fun CustomFoodEntity.toSearchResult(): FoodSearchResult = FoodSearchResult(
    source = "custom",
    refId = id.toString(),
    nameHe = nameHe,
    subtitleHe = "מזון אישי",
    caloriesKcal = caloriesKcal,
    proteinG = proteinG,
    carbsG = carbsG,
    fatG = fatG,
    fiberG = fiberG,
    sugarG = sugarG,
    saturatedFatG = saturatedFatG,
    sodiumMg = sodiumMg,
    cholesterolMg = cholesterolMg
)
