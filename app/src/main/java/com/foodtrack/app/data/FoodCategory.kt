package com.foodtrack.app.data

/**
 * UI-only food categories. They are deliberately independent from the
 * bundled app_foods.db schema; category_he remains untouched and unused.
 */
enum class FoodCategory(
    val id: String,
    val title: String,
    val icon: String
) {
    ALL("all", "כל המאכלים", "🍽"),
    BREAD_BAKERY("bread_bakery", "לחמים ומאפים", "🍞"),
    DAIRY("dairy", "מוצרי חלב", "🥛"),
    MEAT("meat", "בשר", "🥩"),
    FISH("fish", "דגים", "🐟"),
    EGGS("eggs", "ביצים", "🥚"),
    VEGETABLES("vegetables", "ירקות", "🥦"),
    FRUITS("fruits", "פירות", "🍎"),
    LEGUMES("legumes", "קטניות", "🫘"),
    GRAINS("grains", "דגנים", "🌾"),
    NUTS_SEEDS("nuts_seeds", "אגוזים וזרעים", "🥜"),
    SWEETS("sweets", "ממתקים וקינוחים", "🍫"),
    SNACKS("snacks", "חטיפים", "🍿"),
    DRINKS("drinks", "משקאות", "🥤"),
    SAUCES_SPREADS("sauces_spreads", "רטבים וממרחים", "🫙"),
    PREPARED_FOOD("prepared_food", "מאכלים מוכנים", "🍲"),
    OTHER("other", "אחר", "🍴")
}

data class CategoryResult(
    val category: FoodCategory,
    val confidence: Float
)
