package com.foodtrack.app.ui

import com.foodtrack.app.data.FoodSearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRankingTest {
    private fun food(id: String, name: String) = FoodSearchResult(
        source = "base", refId = id, nameHe = name, subtitleHe = "",
        caloriesKcal = null, proteinG = null, carbsG = null, fatG = null,
        fiberG = null, sugarG = null, saturatedFatG = null,
        sodiumMg = null, cholesterolMg = null
    )

    @Test
    fun readyToDrinkShokoBeatsPowderForSimpleShokoQuery() {
        val ready = food("1", "משקה חלב, שוקו 2% שומן, תנובה")
        val powder = food("2", "שוקו, אבקה ממותקת להכנת משקה שוקולית, עלית")
        val ranked = SearchRanking.rank(listOf(powder, ready), "שוקו")
        assertEquals("1", ranked.first().refId)
    }

    @Test
    fun repeatedQueryTokenIsPenalized() {
        // Same leading word; the name that repeats the query word is noisier.
        val simple = food("1", "שוקו חלב")
        val repeated = food("2", "שוקו חלב שוקו")
        val ranked = SearchRanking.rank(listOf(repeated, simple), "שוקו")
        assertEquals("1", ranked.first().refId)
    }

    @Test
    fun recentFoodGetsUsefulTieBreakBoost() {
        val first = food("1", "לחם מלא")
        val second = food("2", "לחם אחיד")
        val ranked = SearchRanking.rank(listOf(first, second), "לחם", recentKeys = setOf("base:2"))
        assertEquals("2", ranked.first().refId)
    }

    @Test
    fun nonMatchingFoodsAreKeptAfterMatches() {
        val match = food("1", "לחם מלא")
        val other = food("2", "גבינה לבנה")
        val ranked = SearchRanking.rank(listOf(other, match), "לחם")
        assertEquals(listOf("1", "2"), ranked.map { it.refId })
        assertTrue(ranked.size == 2)
    }
}
