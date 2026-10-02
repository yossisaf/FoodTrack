package com.foodtrack.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FoodCategoryClassifierTest {
    private val classifier = FoodCategoryClassifier()

    @Test
    fun required_examples_are_classified() {
        val expected = mapOf(
            "לחם" to FoodCategory.BREAD_BAKERY,
            "לחם אחיד" to FoodCategory.BREAD_BAKERY,
            "לחמניה" to FoodCategory.BREAD_BAKERY,
            "פיתה" to FoodCategory.BREAD_BAKERY,
            "גבינה" to FoodCategory.DAIRY,
            "גבינה לבנה, תנובה" to FoodCategory.DAIRY,
            "חלב" to FoodCategory.DAIRY,
            "יוגורט" to FoodCategory.DAIRY,
            "עגבניה" to FoodCategory.VEGETABLES,
            "מלפפון" to FoodCategory.VEGETABLES,
            "תפוח" to FoodCategory.FRUITS,
            "בננה" to FoodCategory.FRUITS,
            "עדשים" to FoodCategory.LEGUMES,
            "חומוס" to FoodCategory.LEGUMES,
            "אורז" to FoodCategory.GRAINS,
            "פסטה" to FoodCategory.GRAINS,
            "ביצה" to FoodCategory.EGGS,
            "חזה עוף" to FoodCategory.MEAT,
            "טונה" to FoodCategory.FISH,
            "שוקולד" to FoodCategory.SWEETS,
            "במבה" to FoodCategory.SNACKS,
            "קולה" to FoodCategory.DRINKS,
            "טחינה" to FoodCategory.SAUCES_SPREADS
        )

        expected.forEach { (name, category) ->
            assertEquals(name, category, classifier.classify(name).category)
        }
    }

    @Test
    fun punctuation_normalization_does_not_change_category() {
        assertEquals(
            classifier.classify("גבינה לבנה תנובה").category,
            classifier.classify("גבינה לבנה, תנובה").category
        )
    }

    @Test
    fun empty_or_unknown_name_falls_back_to_other() {
        assertEquals(FoodCategory.OTHER, classifier.classify("").category)
        assertEquals(FoodCategory.OTHER, classifier.classify("מאכל ללא סיווג").category)
    }
}
