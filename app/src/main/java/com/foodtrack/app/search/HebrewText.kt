package com.foodtrack.app.search

import java.util.Locale

/**
 * Text helpers for searching Hebrew food names. Pure Kotlin/JVM (no Android
 * classes) so it can be unit-tested on the JVM and stays API 19 friendly.
 */
object HebrewText {

    private val niqqud = Regex("[\\u0591-\\u05BD\\u05BF-\\u05C7]")
    private val quoteMarks = Regex("[\\u05F3\\u05F4'\"`\\u2019\\u2018\\u201C\\u201D]")
    private val separators = Regex("[^\\p{L}\\p{N}\u02BC]+")
    private val decimalPoint = Regex("(?<=\\d)[.,](?=\\d)")
    // A "word" for display/highlight purposes. Niqqud (\p{M}) and quote marks
    // belong to the word so צ'יפס / תמ"ל / קוטג' are one word, not several.
    private val wordRegex = Regex("[\\p{L}\\p{M}\\p{N}%'\"`\\u05F3\\u05F4\\u2019\\u2018\\u201C\\u201D]+")

    private fun finalToRegular(c: Char): Char = when (c) {
        '\u05DA' -> '\u05DB' // ך -> כ
        '\u05DD' -> '\u05DE' // ם -> מ
        '\u05DF' -> '\u05E0' // ן -> נ
        '\u05E3' -> '\u05E4' // ף -> פ
        '\u05E5' -> '\u05E6' // ץ -> צ
        else -> c
    }

    /**
     * Canonical form used for all comparisons: lower-case, no niqqud, no
     * geresh/quotes ("קוטג'" == "קוטג"), final letters folded, punctuation
     * (comma, maqaf, slash, hyphen...) turned into single spaces.
     */
    fun normalize(value: String): String {
        if (value.isEmpty()) return ""
        val lowered = value.lowercase(Locale.ROOT)
            .replace('\u05BE', ' ') // maqaf is a word separator, not a mark
        val noMarks = niqqud.replace(lowered, "")
        val noQuotes = quoteMarks.replace(noMarks, "")
        val folded = buildString(noQuotes.length) { noQuotes.forEach { append(finalToRegular(it)) } }
        // 3.7 stays one number (so a search for "3" does not match "3.7% שומן"); % is dropped.
        return separators.replace(decimalPoint.replace(folded, "\u02BC"), " ").trim()
    }

    /**
     * (normalized, surface) pairs for every word of [value]. One-letter words
     * are kept only when they are digits. The surface form keeps the user's
     * original spelling so a "did you mean" suggestion can be shown properly.
     */
    fun wordMatches(value: String): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        wordRegex.findAll(value).forEach { m ->
            val n = normalizeWord(m.value)
            if (n.length >= 2 || (n.length == 1 && n[0].isDigit())) {
                out.add(n to m.value.trim('\'', '"', '`', '\u05F3', '\u05F4', '\u2019', '\u2018', '\u201C', '\u201D'))
            }
        }
        return out
    }

    /** Normalized words; one-letter words are kept only when they are digits. */
    fun words(value: String): List<String> = wordMatches(value).map { it.first }

    /** Single word normalization (no splitting). */
    fun normalizeWord(word: String): String = normalize(word).replace(" ", "")

    /**
     * "Loose" spelling: drops matres lectionis (ו / י) after the first letter so
     * full/defective spellings compare equal (עגבנייה == עגבניה, חלווה == חלוה).
     * Only used as a fallback — שמן and שומן collide here, so never for ranking.
     */
    fun loose(word: String): String {
        if (word.length <= 2) return word
        val sb = StringBuilder(word.length)
        sb.append(word[0])
        for (i in 1 until word.length) {
            val c = word[i]
            if (c != '\u05D5' && c != '\u05D9') sb.append(c)
        }
        return sb.toString()
    }

    private val latinToHebrew: Map<Char, Char> = mapOf(
        'q' to '/', 'w' to '\'', 'e' to '\u05E7', 'r' to '\u05E8', 't' to '\u05D0',
        'y' to '\u05D8', 'u' to '\u05D5', 'i' to '\u05DF', 'o' to '\u05DD', 'p' to '\u05E4',
        'a' to '\u05E9', 's' to '\u05D3', 'd' to '\u05D2', 'f' to '\u05DB', 'g' to '\u05E2',
        'h' to '\u05D9', 'j' to '\u05D7', 'k' to '\u05DC', 'l' to '\u05DA', ';' to '\u05E3',
        'z' to '\u05D6', 'x' to '\u05E1', 'c' to '\u05D1', 'v' to '\u05D4', 'b' to '\u05E0',
        'n' to '\u05DE', 'm' to '\u05E6', ',' to '\u05EA', '.' to '\u05E5'
    )

    fun hasLatinLetters(value: String): Boolean = value.any { it in 'a'..'z' || it in 'A'..'Z' }

    /** What a Hebrew word looks like when typed on an English keyboard layout by mistake. */
    fun fromEnglishKeyboard(value: String): String = buildString(value.length) {
        value.lowercase(Locale.ROOT).forEach { append(latinToHebrew[it] ?: it) }
    }

    /**
     * Optimal-string-alignment distance (Levenshtein plus adjacent swaps),
     * giving up (returns max + 1) as soon as it is clear the distance exceeds
     * [max]. Cheap enough to run over the whole vocabulary.
     */
    fun editDistance(a: String, b: String, max: Int): Int {
        if (a == b) return 0
        if (kotlin.math.abs(a.length - b.length) > max) return max + 1
        var prev2 = IntArray(b.length + 1)
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in a.indices) {
            curr[0] = i + 1
            var rowMin = curr[0]
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                var v = minOf(curr[j] + 1, prev[j + 1] + 1, prev[j] + cost)
                if (i > 0 && j > 0 && a[i] == b[j - 1] && a[i - 1] == b[j]) {
                    v = minOf(v, prev2[j - 1] + 1)
                }
                curr[j + 1] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > max) return max + 1
            val t = prev2; prev2 = prev; prev = curr; curr = t
        }
        return prev[b.length]
    }

    /**
     * Character ranges of [text] that should be highlighted for [terms]
     * (already normalized). Whole words are highlighted: a word counts when it
     * equals a term, starts with it, or (terms of 3+ letters) contains it.
     */
    fun highlightRanges(text: String, terms: Collection<String>): List<IntRange> {
        if (terms.isEmpty() || text.isEmpty()) return emptyList()
        val out = ArrayList<IntRange>()
        wordRegex.findAll(text).forEach { m ->
            val w = normalizeWord(m.value)
            if (w.isEmpty()) return@forEach
            val hit = terms.any { t ->
                t.isNotEmpty() && (w == t || w.startsWith(t) || (t.length >= 3 && w.contains(t)))
            }
            if (hit) out.add(m.range)
        }
        return out
    }

    /** Title = first comma-separated segment; details = everything after it. */
    fun splitName(name: String): Pair<String, String> {
        val idx = name.indexOf(',')
        if (idx < 0) return name.trim() to ""
        val title = name.substring(0, idx).trim()
        val rest = name.substring(idx + 1).trim().trimStart(',', ' ')
            .replace(Regex("\\s*,\\s*"), ", ").replace(Regex("\\s{2,}"), " ")
        return (if (title.isEmpty()) name.trim() else title) to rest
    }
}
