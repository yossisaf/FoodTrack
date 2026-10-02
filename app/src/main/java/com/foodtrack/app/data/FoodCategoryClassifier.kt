package com.foodtrack.app.data

import java.util.Locale

/**
 * Deterministic Hebrew classifier. It never writes to app_foods.db.
 * Rules are scored across all categories instead of returning on the first
 * keyword match, which prevents incidental words such as "לחם" inside a
 * prepared dairy dish from winning automatically.
 */
class FoodCategoryClassifier {

    private data class Rule(
        val phrase: String,
        val kind: Kind = Kind.KEYWORD
    )

    private enum class Kind { PHRASE, KEYWORD }

    private val rules: Map<FoodCategory, List<Rule>> = mapOf(
        FoodCategory.BREAD_BAKERY to rules(
            phrases = listOf("לחם אחיד", "לחם מלא", "לחם לבן", "לחם שיפון", "לחם כוסמין", "לחם מחמצת", "לחמניית", "לחמניה", "פיתה", "באגט", "בגט", "חלה", "קרואסון", "בורקס", "בורקיט", "בצק עלים", "בצק פילו", "עוגת שמרים", "מאפה", "דאבו"),
            words = listOf("לחם", "לחמניה", "פיתה", "חלה", "בייגל", "ביסקוויט", "עוגיה", "עוגת", "עוגה", "מאפה", "בורקס", "טוסט", "קרקר", "וופל")
        ),
        FoodCategory.DAIRY to rules(
            phrases = listOf("גבינה לבנה", "גבינה צהובה", "גבינה בולגרית", "גבינת שמנת", "גבינת קוטג", "גבינת עזים", "גבינת כבשים", "גבינת מוצרלה", "גבינת פרמזן", "גבינת ריקוטה", "חלב עזים", "חלב פרה", "יוגורט טבעי", "שמנת חמוצה", "שמנת מתוקה", "מעדן חלב", "מעדן יוגורט"),
            words = listOf("גבינה", "גבינת", "קוטג", "חלב", "יוגורט", "שמנת", "ריוויון", "לבנה", "מעדן", "מוצרלה", "פרמזן", "ריקוטה", "חמאה")
        ),
        FoodCategory.MEAT to rules(
            phrases = listOf("חזה עוף", "שוק עוף", "כרעיים", "בשר בקר", "בשר טחון", "בשר כבש", "בשר עגל", "בשר הודו", "כבד עוף", "נקניק", "נקניקיה", "פסטרמה", "קבב", "המבורגר", "סטייק"),
            words = listOf("עוף", "בקר", "כבש", "טלה", "עגל", "הודו", "אווז", "ברווז", "חזיר", "נקניק", "נקניקיה", "פסטרמה", "קבב", "המבורגר", "סטייק", "שניצל", "כבד", "בשר")
        ),
        FoodCategory.FISH to rules(
            phrases = listOf("דג טונה", "טונה בשמן", "טונה במים", "סלמון מעושן", "סלמון אפוי", "פילה דג", "פילה סלמון", "דג אמנון", "דג בורי", "דג בקלה", "דג סרדין"),
            words = listOf("דג", "דגים", "טונה", "סלמון", "סרדין", "סרדינים", "אנשובי", "בקלה", "קרפיון", "אמנון", "מושט", "בורי", "מקרל", "פורל", "הרינג", "שרימפס", "קלמרי")
        ),
        FoodCategory.EGGS to rules(
            phrases = listOf("ביצה קשה", "ביצה רכה", "ביצה מטוגנת", "ביצה מבושלת", "ביצים קשות", "חביתה", "חביתה עם", "אומלט"),
            words = listOf("ביצה", "ביצים", "חביתה", "אומלט")
        ),
        FoodCategory.VEGETABLES to rules(
            phrases = listOf("עגבניה טריה", "עגבניות טריות", "מלפפון טרי", "גזר טרי", "פלפל אדום", "פלפל ירוק", "פלפל צהוב", "כרובית", "ברוקולי", "בטטה", "תפוח אדמה", "תפוחי אדמה", "קישוא", "חציל", "בצל ירוק", "בצל יבש", "עלי חסה", "סלט ירקות"),
            words = listOf("עגבניה", "עגבניות", "מלפפון", "גזר", "פלפל", "כרוב", "כרובית", "ברוקולי", "בטטה", "תפוחי", "קישוא", "חציל", "בצל", "חסה", "תרד", "סלק", "צנונית", "צנון", "ארטישוק", "אספרגוס", "פטריה", "פטריות", "תירס", "אפונה")
        ),
        FoodCategory.FRUITS to rules(
            phrases = listOf("תפוח עץ", "תפוחי עץ", "מיץ תפוזים", "פרי הדר", "פירות יער", "פירות יבשים"),
            words = listOf("אבטיח", "מלון", "תפוז", "קלמנטינה", "מנדרינה", "אשכולית", "לימון", "ליים", "תפוח", "בננה", "אגס", "אפרסק", "נקטרינה", "משמש", "שזיף", "דובדבן", "ענבים", "תות", "רימון", "מנגו", "אננס", "קיווי", "פפאיה", "פסיפלורה", "תמר", "תאנה", "צימוק", "חמוציות", "פרי", "פירות")
        ),
        FoodCategory.LEGUMES to rules(
            phrases = listOf("עדשים מבושלות", "עדשים יבשות", "שעועית אדומה", "שעועית לבנה", "שעועית ירוקה", "גרגרי חומוס", "חומוס יבש", "פול מבושל", "אפונה יבשה", "סויה מבושלת"),
            words = listOf("עדשים", "עדשה", "שעועית", "חומוס", "גרגרי", "פול", "סויה", "טופו", "טמפה", "קטניה", "קטניות", "לוביה")
        ),
        FoodCategory.GRAINS to rules(
            phrases = listOf("אורז לבן", "אורז מלא", "אורז בר", "קמח חיטה", "קמח מלא", "שיבולת שועל", "פתיתי שיבולת שועל", "קינואה מבושלת", "בורגול מבושל", "קוסקוס מבושל", "גריסי פנינה"),
            words = listOf("אורז", "פסטה", "אטריות", "נודלס", "קוסקוס", "בורגול", "קינואה", "גריסים", "גריסי", "שיבולת", "שיבולת", "חיטה", "כוסמין", "שיפון", "תירס", "קמח", "פתיתים", "דוחן", "אמרנט")
        ),
        FoodCategory.NUTS_SEEDS to rules(
            phrases = listOf("אגוזי מלך", "אגוזי לוז", "אגוזי קשיו", "אגוזי פקאן", "אגוזי מקדמיה", "אגוזי ברזיל", "גרעיני חמניה", "גרעיני דלעת", "זרעי צ'יה", "זרעי פשתן", "חמאת בוטנים", "חמאת שקדים", "טחינת שומשום"),
            words = listOf("אגוז", "אגוזים", "שקד", "שקדים", "קשיו", "פקאן", "פיסטוק", "בוטנים", "בוטן", "ברזיל", "מקדמיה", "לוז", "גרעין", "גרעינים", "זרע", "זרעים", "צ'יה", "פשתן", "שומשום")
        ),
        FoodCategory.SWEETS to rules(
            phrases = listOf("שוקולד חלב", "שוקולד מריר", "שוקולד לבן", "עוגת שוקולד", "עוגת גבינה", "גלידה", "סורבה", "פודינג", "מוס שוקולד", "ריבה", "מרמלדה", "ממתק גומי"),
            words = listOf("שוקולד", "ממתק", "ממתקים", "סוכריה", "סוכריות", "קרמל", "טופי", "גלידה", "סורבה", "פודינג", "מוס", "ריבה", "חלבה", "נוגט", "קינוח", "עוגת", "עוגה")
        ),
        FoodCategory.SNACKS to rules(
            phrases = listOf("חטיף בוטנים", "חטיף תפוחי אדמה", "חטיף תירס", "חטיף דגנים", "במבה", "ביסלי", "צ'יפס", "פופקורן"),
            words = listOf("חטיף", "חטיפים", "במבה", "ביסלי", "צ'יפס", "פופקורן", "קרנצ'", "פריכית", "פריכיות")
        ),
        FoodCategory.DRINKS to rules(
            phrases = listOf("משקה קל", "משקה מוגז", "משקה אנרגיה", "משקה איזוטוני", "מיץ תפוזים", "מיץ תפוחים", "מיץ טבעי", "קולה", "קפה שחור", "קפה נמס", "תה ירוק", "תה שחור", "תה קר", "מים מינרליים", "שוקו לשתיה"),
            words = listOf("משקה", "משקאות", "קולה", "פפסי", "מיץ", "מיצים", "מים", "קפה", "תה", "שוקו", "לימונדה", "סודה", "אנרגיה", "בירה", "יין")
        ),
        FoodCategory.SAUCES_SPREADS to rules(
            phrases = listOf("רוטב עגבניות", "רוטב סויה", "רוטב אלף האיים", "רוטב שום", "רוטב חרדל", "רוטב צ'ילי", "רוטב ברביקיו", "טחינה גולמית", "טחינה ירוקה", "ממרח שוקולד", "ממרח בוטנים", "ממרח חומוס", "חמאת בוטנים"),
            words = listOf("רוטב", "רטבים", "ממרח", "ממרחים", "טחינה", "קטשופ", "חרדל", "מיונז", "פסטו", "סילאן", "ממרח")
        ),
        FoodCategory.PREPARED_FOOD to rules(
            phrases = listOf("מרק ירקות", "מרק עוף", "מרק עדשים", "סלט ירקות", "סלט טונה", "סלט ביצים", "פיצה", "לזניה", "מג'דרה", "קציצות", "תבשיל", "ארוחה מוכנה", "מנה מוכנה", "מנה חמה"),
            words = listOf("מרק", "סלט", "פיצה", "לזניה", "מג'דרה", "קציצות", "תבשיל", "ממולא", "ממולאים", "מוקפץ", "פשטידה", "פנקייק", "וופל בלגי", "מנה חמה", "מאכל מוכן")
        )
    )

    // Normalize every rule once. The classifier may process thousands of foods
    // during the first index build, so normalizing rule strings inside the hot
    // loop would create a large amount of avoidable work.
    private val normalizedRules: Map<FoodCategory, List<Rule>> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        rules.mapValues { (_, categoryRules) ->
            categoryRules.map { it.copy(phrase = normalize(it.phrase)) }
        }
    }

    fun classify(foodName: String): CategoryResult {
        val text = normalize(foodName)
        if (text.isBlank()) return CategoryResult(FoodCategory.OTHER, 0.0f)

        val words = text.split(' ').filter { it.isNotBlank() }
        val scores = mutableMapOf<FoodCategory, Double>()

        normalizedRules.forEach { (category, categoryRules) ->
            var score = 0.0
            for (rule in categoryRules) {
                val phrase = rule.phrase
                if (phrase.isBlank()) continue
                val exactPhrase = text == phrase
                val phraseMatch = containsPhrase(text, phrase)
                val exactWord = phrase in words
                val keywordMatch = words.any { it == phrase || it.startsWith(phrase) }
                val partialMatch = text.contains(phrase)
                when {
                    exactPhrase -> score += 100.0
                    rule.kind == Kind.PHRASE && phraseMatch -> score += 100.0
                    exactWord -> score += 50.0
                    rule.kind == Kind.KEYWORD && keywordMatch -> score += 30.0
                    partialMatch -> score += 10.0
                }
                if (text.startsWith(phrase)) score += 20.0
            }
            scores[category] = score
        }

        // High-value exclusions prevent incidental ingredients from winning.
        if (containsAny(text, "חלב קוקוס", "חלב שקדים", "חלב סויה", "חלב שיבולת")) {
            scores[FoodCategory.DRINKS] = (scores[FoodCategory.DRINKS] ?: 0.0) + 90.0
            scores[FoodCategory.DAIRY] = (scores[FoodCategory.DAIRY] ?: 0.0) - 60.0
        }
        if (containsAny(text, "פירורי לחם", "פרורי לחם", "ציפוי שוקולד", "עם גבינה", "עם חלב")) {
            scores[FoodCategory.BREAD_BAKERY] = (scores[FoodCategory.BREAD_BAKERY] ?: 0.0) - 25.0
        }
        if (containsAny(text, "עם שוקולד", "בציפוי שוקולד", "מילוי שוקולד")) {
            scores[FoodCategory.SWEETS] = (scores[FoodCategory.SWEETS] ?: 0.0) + 55.0
        }
        // containsAnyWord (not containsAny): these single-syllable words ("תה",
        // "מים"...) are short enough to turn up as a substring inside an unrelated
        // longer word, so this needs the same word-boundary check containsPhrase
        // already does elsewhere — a trailing space in the phrase itself doesn't
        // work here, because normalize() unconditionally trims its result.
        if (containsAnyWord(text, "מיץ", "משקה", "מים", "קולה", "קפה", "תה")) {
            scores[FoodCategory.DRINKS] = (scores[FoodCategory.DRINKS] ?: 0.0) + 40.0
        }

        val ranked = scores.entries.sortedWith(
            compareByDescending<Map.Entry<FoodCategory, Double>> { it.value }
                .thenBy { it.key.ordinal }
        )
        val best = ranked.firstOrNull()
        if (best == null || best.value <= 0.0) return CategoryResult(FoodCategory.OTHER, 0.0f)

        val second = ranked.drop(1).firstOrNull()?.value ?: 0.0
        val confidence = confidence(best.value, second)
        return if (confidence < 0.20f) {
            CategoryResult(FoodCategory.OTHER, confidence)
        } else {
            CategoryResult(best.key, confidence)
        }
    }

    private fun confidence(best: Double, second: Double): Float {
        val absolute = (best / 120.0).coerceIn(0.0, 1.0)
        val separation = ((best - second) / best.coerceAtLeast(1.0)).coerceIn(0.0, 1.0)
        return (absolute * 0.65 + separation * 0.35).toFloat()
    }

    private fun containsPhrase(text: String, phrase: String): Boolean =
        text == phrase || text.contains(" $phrase ") || text.startsWith("$phrase ") || text.endsWith(" $phrase")

    private fun containsAny(text: String, vararg phrases: String): Boolean = phrases.any { text.contains(normalize(it)) }

    /** Same as [containsAny], but matches [phrases] as whole words only (see [containsPhrase]). */
    private fun containsAnyWord(text: String, vararg phrases: String): Boolean =
        phrases.any { containsPhrase(text, normalize(it)) }

    private fun rules(phrases: List<String>, words: List<String>): List<Rule> =
        phrases.map { Rule(it, Kind.PHRASE) } + words.map { Rule(it, Kind.KEYWORD) }

    private fun normalize(value: String): String = value
        .trim()
        .lowercase(Locale.ROOT)
        .replace('־', '-')
        .replace('–', '-')
        .replace('—', '-')
        .replace(Regex("[\\\"'׳״()\\[\\]{}.,;:!?/\\\\]+"), " ")
        .replace(Regex("[-]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
