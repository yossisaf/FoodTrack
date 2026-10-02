package com.foodtrack.app.search

import com.foodtrack.app.data.FoodSearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodSearchEngineTest {

    private fun food(id: String, name: String, source: String = "base") = FoodSearchResult(
        source = source, refId = id, nameHe = name, subtitleHe = "",
        caloriesKcal = null, proteinG = null, carbsG = null, fatG = null,
        fiberG = null, sugarG = null, saturatedFatG = null,
        sodiumMg = null, cholesterolMg = null
    )

    // Names copied from the bundled Tzameret-based reference DB.
    private val catalog = listOf(
        food("1", "משקה חלב, שוקו 2% שומן, תנובה"),
        food("2", "שוקו, אבקה ממותקת להכנת משקה שוקולית, עלית"),
        food("3", "שוקולד לבן"),
        food("4", "בשר עוף, חזה, צלוי"),
        food("5", "שניצלונים מבשר הודו, עוף טוב"),
        food("6", "תפוחי אדמה, פירה עם חמאה"),
        food("7", "מרק בטטה עם תפוח אדמה וחלב קוקוס"),
        food("8", "עגבניה, טריה"),
        food("9", "עגבניות, משומרות, מבושלות"),
        food("10", "לחם שום"),
        food("11", "גבינת קוטג' 3% שומן, תנובה"),
        food("12", "ביצה קשה שלמה, ללא קליפה, עם מלח"),
        food("13", "קפה, מוכן קפוצ'ינו"),
        food("14", "תפוח עץ, עם קליפה (ללא ליבה)"),
        food("15", "חומוס, גרגירים משומרים"),
        food("16", "משקה קל, קולה, ללא סוכר, דיאט"),
        food("17", "קולרבי, טרי")
    )

    private val engine = FoodSearchEngine(catalog)

    private fun ids(query: String, signals: SearchSignals = SearchSignals.NONE) =
        engine.search(query, signals = signals).results.map { it.food.refId }

    @Test
    fun wholeWordBeatsWordPrefix() {
        val r = ids("שוקו")
        assertTrue(r.indexOf("1") < r.indexOf("3"))
    }

    @Test
    fun readyDrinkBeatsPowderForPlainQuery() {
        val r = ids("שוקו")
        assertTrue(r.indexOf("1") < r.indexOf("2"))
    }

    @Test
    fun foodNamedInMainSegmentBeatsBrandMention() {
        val r = ids("עוף")
        assertEquals("4", r.first())
    }

    @Test
    fun singularQueryFindsPluralNameAndOutranksIncidentalMention() {
        val r = ids("תפוח אדמה")
        assertTrue(r.contains("6"))
        assertTrue(r.indexOf("6") < r.indexOf("7"))
    }

    @Test
    fun fullAndDefectiveSpellingAreEquivalent() {
        val r = ids("עגבנייה")
        assertTrue(r.contains("8"))
        assertTrue(r.contains("9")) // plural form too
        assertEquals(Correction.NONE, engine.search("עגבנייה").correction)
    }

    @Test
    fun leadingHebrewPrefixLetterIsIgnoredWhenNothingMatchesAsTyped() {
        assertEquals("10", ids("הלחם").first())
    }

    @Test
    fun apostropheAndQuotesDoNotMatter() {
        assertEquals(listOf("11"), ids("קוטג"))
        assertEquals(listOf("11"), ids("קוטג'"))
    }

    @Test
    fun plainQueryDoesNotDragInUnrelatedPrefixes() {
        // "קולה" must not turn into "קול…" and match קולרבי.
        val r = ids("קולה")
        assertTrue(r.contains("16"))
        assertFalse(r.contains("17"))
    }

    @Test
    fun pluralQueryFindsSingularName() {
        assertEquals("12", ids("ביצים קשות").first())
    }

    @Test
    fun typoFallsBackToClosestWordAndSuggestsIt() {
        val outcome = engine.search("חומוז")
        assertEquals(Correction.SPELLING, outcome.correction)
        assertEquals("חומוס", outcome.correctedQuery)
        assertEquals("15", outcome.results.first().food.refId)
    }

    @Test
    fun wrongKeyboardLayoutIsRecovered() {
        val outcome = engine.search("aueu") // שוקו typed on an English layout
        assertEquals(Correction.KEYBOARD, outcome.correction)
        assertEquals("שוקו", outcome.correctedQuery)
        assertTrue(outcome.results.isNotEmpty())
    }

    @Test
    fun unknownWordIsIgnoredWhenOtherWordsMatch() {
        val outcome = engine.search("קפה הפוך")
        assertEquals(listOf("הפוך"), outcome.ignoredWords)
        assertEquals("13", outcome.results.first().food.refId)
    }

    @Test
    fun nothingMatchesReturnsEmptyOutcome() {
        val outcome = engine.search("zzzzqq")
        assertTrue(outcome.results.isEmpty())
        assertNull(outcome.correctedQuery)
    }

    @Test
    fun blankOrTooShortQueryFindsNothing() {
        assertTrue(engine.search("   ").results.isEmpty())
        assertTrue(engine.search("ש").results.isEmpty())
    }

    @Test
    fun allWordsMustMatchBeforePartialResultsAreShown() {
        val r = engine.search("לחם שום").results
        assertEquals("10", r.first().food.refId)
        assertTrue(r.first().fullMatch)
    }

    @Test
    fun favoritesAndFrequentFoodsBreakTies() {
        val twins = FoodSearchEngine(listOf(food("a", "לחם מלא"), food("b", "לחם אחיד")))
        val base = twins.search("לחם").results.map { it.food.refId }
        val boosted = twins.search(
            "לחם",
            signals = SearchSignals(usageCounts = mapOf("base:" + base.last() to 5))
        ).results.map { it.food.refId }
        assertEquals(base.last(), boosted.first())
    }

    @Test
    fun customFoodsAreSearchedAndPreferred() {
        val custom = food("7", "לחם שום של אמא", source = "custom")
        val outcome = engine.search("לחם שום", custom = listOf(custom))
        val ids = outcome.results.map { "${it.food.source}:${it.food.refId}" }
        assertTrue(ids.contains("custom:7"))
        assertTrue(ids.contains("base:10"))
    }

    @Test
    fun highlightTermsCoverResolvedForms() {
        val terms = engine.search("תפוח אדמה").highlightTerms
        assertTrue(terms.contains("תפוח"))
        assertTrue(terms.contains("אדמה"))
    }

    @Test
    fun wholeNumberDoesNotMatchDecimal() {
        val e = FoodSearchEngine(listOf(food("1", "חלב עזים 3.7% שומן"), food("2", "חלב 3% שומן, תנובה")))
        assertEquals("2", e.search("חלב 3").results.first().food.refId)
    }

    @Test
    fun exactTypedWordBeatsItsPluralVariantInSameRole() {
        val e = FoodSearchEngine(listOf(food("1", "עגבניות, טריות"), food("2", "עגבניה, טריה")))
        assertEquals("2", e.search("עגבניה").results.first().food.refId)
    }

    @Test
    fun findLooksUpBaseFoodByKey() {
        assertNotNull(engine.find("base", "4"))
        assertNull(engine.find("custom", "4"))
    }
}
