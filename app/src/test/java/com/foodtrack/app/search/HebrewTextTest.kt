package com.foodtrack.app.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HebrewTextTest {

    @Test
    fun normalizeStripsNiqqudQuotesAndFoldsFinalLetters() {
        assertEquals("קוטג", HebrewText.normalize("קוֹטֶג'"))
        assertEquals("לחמ", HebrewText.normalize("לחם"))
        assertEquals("תמל", HebrewText.normalize("תמ\"ל"))
        assertEquals("ציפס", HebrewText.normalize("צ'יפס"))
    }

    @Test
    fun normalizeTurnsPunctuationIntoSpaces() {
        assertEquals("עופ חזה צלוי", HebrewText.normalize("עוף, חזה/צלוי"))
        assertEquals("חלב 3 שומנ", HebrewText.normalize("חלב  3% שומן"))
        // maqaf (־) separates words instead of gluing them together
        assertEquals("עמ קמח", HebrewText.normalize("עם\u05BEקמח"))
    }

    @Test
    fun wordsKeepDigitsButDropSingleLetters() {
        assertEquals(listOf("חלב", "3", "שומנ"), HebrewText.words("חלב 3 ש שומן"))
    }

    @Test
    fun looseFormIgnoresFullVsDefectiveSpelling() {
        assertEquals(HebrewText.loose("עגבנייה"), HebrewText.loose("עגבניה"))
        assertEquals(HebrewText.loose("חלווה"), HebrewText.loose("חלוה"))
    }

    @Test
    fun englishKeyboardMappingRecoversHebrewWord() {
        assertEquals("שוקו", HebrewText.fromEnglishKeyboard("aueu"))
        assertEquals("שלום", HebrewText.fromEnglishKeyboard("akuo"))
        assertTrue(HebrewText.hasLatinLetters("aueu"))
        assertTrue(!HebrewText.hasLatinLetters("שוקו"))
    }

    @Test
    fun editDistanceCountsSwapsAsOneAndGivesUpEarly() {
        assertEquals(1, HebrewText.editDistance("שוקולד", "שוקולדד", 2))
        assertEquals(1, HebrewText.editDistance("חלב", "חבל", 2))
        assertEquals(3, HebrewText.editDistance("אורז", "פסטה", 2)) // > max => max + 1
    }

    @Test
    fun highlightMarksWholeMatchingWords() {
        val text = "משקה חלב, שוקולד מריר"
        val ranges = HebrewText.highlightRanges(text, listOf("שוקו"))
        assertEquals(1, ranges.size)
        assertEquals("שוקולד", text.substring(ranges[0].first, ranges[0].last + 1))
    }

    @Test
    fun splitNameSeparatesTitleFromDetails() {
        assertEquals("תפוח עץ" to "עם קליפה (ללא ליבה)", HebrewText.splitName("תפוח עץ, עם קליפה (ללא ליבה)"))
        assertEquals("שוקולד לבן" to "", HebrewText.splitName("שוקולד לבן"))
        assertEquals("גבינה" to "לבנה, תנובה", HebrewText.splitName("גבינה,  לבנה ,תנובה"))
    }
}
