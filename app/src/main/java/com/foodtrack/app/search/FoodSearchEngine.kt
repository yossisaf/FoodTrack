package com.foodtrack.app.search

import com.foodtrack.app.data.FoodSearchResult

/**
 * Personal signals that nudge ranking: what the user starred, what they log
 * often and what they logged lately. Keys are "source:refId".
 */
class SearchSignals(
    val favorites: Set<String> = emptySet(),
    val usageCounts: Map<String, Int> = emptyMap(),
    val recentKeys: Set<String> = emptySet()
) {
    companion object {
        val NONE = SearchSignals()
    }
}

class ScoredFood(val food: FoodSearchResult, val score: Double, val fullMatch: Boolean)

enum class Correction { NONE, SPELLING, KEYBOARD }

class SearchOutcome(
    /** Best first. Not truncated and not filtered by category. */
    val results: List<ScoredFood>,
    /** Normalized words worth highlighting in result names. */
    val highlightTerms: List<String>,
    /** Suggested replacement query (spelling fix or Hebrew keyboard layout), if any. */
    val correctedQuery: String?,
    val correction: Correction,
    /** Query words that matched nothing at all and were ignored. */
    val ignoredWords: List<String>
) {
    companion object {
        val EMPTY = SearchOutcome(emptyList(), emptyList(), null, Correction.NONE, emptyList())
    }
}

/**
 * In-memory search over the food list.
 *
 * Why not SQL LIKE: LIKE cannot do Hebrew prefixes (ה/ב/ל), plurals, defective
 * vs. full spelling, typos or field-aware ranking, and candidates it never
 * returns can never be ranked. The whole reference set is only ~4,500 names,
 * so everything is matched and scored in memory (a few ms per keystroke).
 *
 * Names in the reference DB look like "main food, preparation, brand", so the
 * first comma-separated segment carries most of the meaning and is weighted
 * accordingly; a brand at the end is searchable but weighs less.
 */
class FoodSearchEngine(baseFoods: Collection<FoodSearchResult>) {

    private val baseEntries: List<Entry> = baseFoods.map { Entry(it, false) }
    private val baseVocab: Vocab
    private val byKey: Map<String, FoodSearchResult>
    private val wordFreq: Map<String, Int>
    private val logN: Double

    init {
        val words = buildVocab(baseEntries, emptyList())
        baseVocab = Vocab(words)
        wordFreq = words.associate { it.norm to it.freq }
        logN = Math.log(baseEntries.size.coerceAtLeast(2).toDouble())
        byKey = HashMap<String, FoodSearchResult>(baseEntries.size * 2).also { m ->
            baseEntries.forEach { m[it.key] = it.food }
        }
    }

    val size: Int get() = baseEntries.size

    fun find(source: String, refId: String): FoodSearchResult? = byKey["$source:$refId"]

    fun search(
        query: String,
        custom: List<FoodSearchResult> = emptyList(),
        signals: SearchSignals = SearchSignals.NONE
    ): SearchOutcome {
        val typed = HebrewText.wordMatches(query)
        val tokens = typed.map { it.first }.distinct()
        if (tokens.isEmpty()) return SearchOutcome.EMPTY
        val typedSurface = HashMap<String, String>()
        typed.forEach { if (!typedSurface.containsKey(it.first)) typedSurface[it.first] = it.second }

        val customEntries = custom.map { Entry(it, true) }
        val vocab = if (customEntries.isEmpty()) baseVocab else Vocab(buildVocab(baseEntries, customEntries))
        val entries = if (customEntries.isEmpty()) baseEntries else baseEntries + customEntries

        var correction = Correction.NONE
        var correctedQuery: String? = null
        var activeTokens = tokens
        var resolved = tokens.map { resolve(it, vocab, typedSurface[it]) }

        if (resolved.none { it.terms.isNotEmpty() } && HebrewText.hasLatinLetters(query)) {
            // Typed on an English keyboard layout by mistake (akuo -> שוקו).
            val mapped = HebrewText.fromEnglishKeyboard(query)
            val mappedTyped = HebrewText.wordMatches(mapped)
            val mappedSurface = HashMap<String, String>()
            mappedTyped.forEach { if (!mappedSurface.containsKey(it.first)) mappedSurface[it.first] = it.second }
            val mappedTokens = mappedTyped.map { it.first }.distinct()
            val mappedResolved = mappedTokens.map { resolve(it, vocab, mappedSurface[it]) }
            if (mappedResolved.any { it.terms.isNotEmpty() }) {
                activeTokens = mappedTokens
                resolved = mappedResolved
                correction = Correction.KEYBOARD
                correctedQuery = mappedResolved.joinToString(" ") { it.surfaceOrToken() }
            }
        }

        val active = resolved.filter { it.terms.isNotEmpty() }
        if (active.isEmpty()) return SearchOutcome.EMPTY
        val ignored = resolved.filter { it.terms.isEmpty() }.map { it.originalSurface }

        if (correction == Correction.NONE && active.any { it.fuzzy }) {
            correction = Correction.SPELLING
            correctedQuery = resolved.filter { it.terms.isNotEmpty() }
                .joinToString(" ") { if (it.fuzzy) it.surfaceOrToken() else it.originalSurface }
        }

        val allTerms = active.flatMap { r -> r.terms.map { it.text } }.distinct()
        val scored = ArrayList<ScoredFood>()
        for (entry in entries) {
            if (allTerms.none { entry.flatNorm.contains(it) }) continue // cheap reject
            val s = score(entry, active, signals) ?: continue
            scored.add(s)
        }

        val fullCount = scored.count { it.fullMatch }
        val kept = if (fullCount >= PARTIAL_CUTOFF || active.size == 1) scored.filter { it.fullMatch } else scored
        val sorted = kept.sortedWith(
            compareByDescending<ScoredFood> { it.fullMatch }
                .thenByDescending { it.score }
                .thenBy { it.food.nameHe }
                .thenBy { it.food.refId }
        )

        val terms = LinkedHashSet<String>()
        active.forEach { r -> r.terms.forEach { terms.add(it.text) } }
        return SearchOutcome(sorted, terms.toList(), correctedQuery, correction, ignored)
    }

    // ---------------------------------------------------------------- scoring

    private fun score(entry: Entry, active: List<Resolved>, signals: SearchSignals): ScoredFood? {
        if (entry.words.isEmpty()) return null
        val segCount = entry.segments.size
        var tokenTotal = 0.0
        var matched = 0
        var matchedChars = 0
        var extraBonus = 0.0
        val usedWordIdx = HashSet<Int>()

        for (r in active) {
            var best = 0.0
            var bestIdx = -1
            var bestQuality = 0.0
            var bestLiteral = false
            for (i in entry.words.indices) {
                val w = entry.words[i]
                for (t in r.terms) {
                    val q = matchQuality(w, t.text) * t.weight
                    if (q <= 0.0) continue
                    val value = q * segmentFactor(entry.wordSeg[i], segCount) * positionFactor(entry.wordIdx[i])
                    // The word exactly as the user typed it outranks plural/prefix/synonym/typo forms.
                    val literal = t.literal && matchQuality(w, t.text) >= EXACT
                    val ranked = if (literal) value + 450.0 / 1000.0 * segmentFactor(entry.wordSeg[i], segCount) else value
                    if (ranked > best) {
                        best = ranked
                        bestLiteral = literal
                        bestIdx = i
                        bestQuality = q
                    }
                }
            }
            if (bestIdx >= 0) {
                matched++
                tokenTotal += best * 1000.0
                matchedChars += entry.words[bestIdx].length
                usedWordIdx.add(bestIdx)
                if (entry.wordIdx[bestIdx] == 0 && bestQuality >= 0.99) {
                    val bonus = if (bestQuality >= 0.99) 500.0 else 250.0
                    if (entry.wordSeg[bestIdx] == 0) extraBonus += bonus
                    else if (entry.wordSeg[bestIdx] == 1 && segCount >= 3) extraBonus += bonus * 0.5
                }
            }
        }
        if (matched == 0) return null
        val full = matched == active.size

        var score = tokenTotal + extraBonus
        if (full) score += 2500.0
        score += phraseScore(entry, active)

        // How much of the name the query explains; shorter, plainer names first.
        val totalChars = entry.words.sumOf { it.length }.coerceAtLeast(1)
        score += (matchedChars.toDouble() / totalChars) * 400.0
        score -= entry.words.size * 14.0
        score -= (segCount - 1) * 25.0

        // "Typical" foods first: among otherwise equal names, the one whose extra words are
        // common qualifiers (טרי, מבושל, קשה) beats one with rare words (תרנגול הודו, בופאלו).
        var rarity = 0.0
        for (i in entry.words.indices) {
            if (i in usedWordIdx) continue
            val f = wordFreq[entry.words[i]] ?: 1
            rarity += (1.0 - Math.log(f.toDouble() + 1.0) / logN).coerceIn(0.0, 1.0)
        }
        score -= (rarity * 300.0).coerceAtMost(1200.0)

        // Query word repeated inside the name ("שוקו חלב שוקו") is noise, not relevance.
        var occurrences = 0
        for (w in entry.words) {
            if (active.any { r -> r.terms.any { it.text == w } }) occurrences++
        }
        val extra = (occurrences - matched).coerceIn(0, 3)
        score -= extra * 120.0

        // Powders / mixes / infant formula when the user didn't ask for them.
        var noisePenalty = 0.0
        val seen = HashSet<String>()
        for (w in entry.words) {
            val p = if (w in NOISE_WORDS) 1000.0 else if (w in MILD_NOISE_WORDS) 350.0 else continue
            if (seen.add(w) && active.none { r -> r.terms.any { w.startsWith(it.text) } }) noisePenalty += p
        }
        score -= noisePenalty.coerceAtMost(2000.0)

        // Personal signals.
        if (entry.key in signals.favorites) score += 450.0
        signals.usageCounts[entry.key]?.let { score += (it.coerceAtMost(12)) * 30.0 }
        if (entry.key in signals.recentKeys) score += 150.0
        if (entry.isCustom) score += 200.0

        return ScoredFood(entry.food, score, full)
    }

    /**
     * How the typed words sit together in the name: adjacent, in order, inside
     * one segment, and where. Works on the resolved forms, so "תפוח אדמה" also
     * counts as the phrase in "תפוחי אדמה". Prefix-only matches (שוקו → שוקולד)
     * are discounted so the exact word wins. The best placement wins.
     */
    private fun phraseScore(entry: Entry, active: List<Resolved>): Double {
        val n = active.size
        val words = entry.words
        val segCount = entry.segments.size
        var best = 0.0
        for (start in words.indices) {
            if (start + n > words.size) break
            if (entry.wordSeg[start] != entry.wordSeg[start + n - 1]) continue
            var ok = true
            var penalty = 0.0
            for (k in 0 until n) {
                val w = words[start + k]
                var q = 0.0
                for (t in active[k].terms) {
                    val mq = matchQuality(w, t.text)
                    if (mq > q) q = mq
                }
                if (q <= 0.0) { ok = false; break }
                if (q < 0.99) penalty += if (q >= 0.5) 600.0 else 1000.0
            }
            if (!ok) continue
            val seg = entry.wordSeg[start]
            val startIdx = entry.wordIdx[start]
            val endsSeg = startIdx + n == entry.segments[seg].size
            val tier = when {
                seg == 0 && startIdx == 0 && endsSeg -> if (segCount == 1) 3000.0 else 2150.0
                seg == 0 && startIdx == 0 -> 2000.0
                seg == 1 && segCount >= 3 && startIdx == 0 && endsSeg -> 2000.0
                seg == 1 && segCount >= 3 && startIdx == 0 -> 1700.0
                seg == 0 -> 1100.0
                else -> 800.0
            }
            best = maxOf(best, tier - penalty)
        }
        return best
    }

    /** Whole word beats word-prefix (שוקו vs שוקולד) beats substring. */
    private fun matchQuality(word: String, term: String): Double = when {
        word == term -> EXACT
        word.startsWith(term) -> PREFIX
        term.length >= 3 && word.contains(term) -> CONTAINS
        else -> 0.0
    }

    /**
     * Names are "main food, preparation, brand". With only two segments the
     * second is almost always a qualifier or a brand (קפה, מוכן / שניצל, עוף טוב);
     * with three or more the second often names the actual food (משקה חלב, שוקו, תנובה)
     * while the last is usually the brand.
     */
    private fun segmentFactor(seg: Int, segCount: Int): Double = when {
        seg == 0 -> 1.0
        segCount == 2 -> 0.5
        seg == 1 -> 0.8
        seg == segCount - 1 -> 0.4
        else -> 0.6
    }

    private fun positionFactor(indexInSegment: Int): Double =
        1.0 - 0.05 * indexInSegment.coerceAtMost(6)

    // ------------------------------------------------------------- resolution

    private class Term(val text: String, val weight: Double, val literal: Boolean)

    private class Resolved(
        val token: String,
        val terms: List<Term>,
        val fuzzy: Boolean,
        val originalSurface: String,
        private val fuzzySurface: String?
    ) {
        fun surfaceOrToken(): String = fuzzySurface ?: originalSurface
    }

    /**
     * Turns one typed word into the concrete words/stems it may match:
     * itself, plural/feminine variants (עגבניה ↔ עגבניות), prefix-stripped
     * forms (בשוקו → שוקו), curated synonyms and, only if nothing else hits,
     * spelling-tolerant vocabulary words.
     */
    private fun resolve(token: String, vocab: Vocab, typedSurface: String?): Resolved {
        val terms = LinkedHashMap<String, Double>()
        fun add(text: String, weight: Double) {
            if (text.length < 2 && !(text.length == 1 && text[0].isDigit())) return
            val old = terms[text]
            if (old == null || weight > old) terms[text] = weight
        }

        if (hasHit(token, vocab)) add(token, 1.0)
        for (v in suffixVariants(token)) if (hasHit(v, vocab)) add(v, 0.9)

        if (terms.isEmpty()) {
            for (stripped in prefixStrips(token)) {
                if (hasHit(stripped, vocab)) add(stripped, 0.85)
                for (v in suffixVariants(stripped)) if (hasHit(v, vocab)) add(v, 0.8)
                if (terms.isNotEmpty()) break
            }
        }

        SearchSynonyms.map[token]?.forEach { s -> if (hasHit(s, vocab)) add(s, 0.8) }

        if (terms.isEmpty() && token.length >= 4) {
            // Full vs. defective spelling (עגבנייה / עגבניה) is not a typo.
            val lt = HebrewText.loose(token)
            val sameLoose = vocab.words.filter { it.loose == lt }
            val nearest = sameLoose.minOfOrNull { HebrewText.editDistance(it.norm, token, 8) }
            sameLoose.filter { HebrewText.editDistance(it.norm, token, 8) == nearest }
                .sortedByDescending { it.freq }.take(4).forEach { v ->
                add(v.norm, 0.95)
                for (x in suffixVariants(v.norm)) if (hasHit(x, vocab)) add(x, 0.85)
            }
        }

        var fuzzy = false
        var fuzzySurface: String? = null
        if (terms.isEmpty()) {
            val best = fuzzyCandidates(token, vocab)
            if (best.isNotEmpty()) {
                fuzzy = true
                best.forEach { add(it.norm, 0.7) }
                fuzzySurface = best.first().surface
            }
        }

        val surface = typedSurface ?: token
        return Resolved(token, terms.map { Term(it.key, it.value, it.key == token && !fuzzy) }, fuzzy, surface, fuzzySurface)
    }

    private fun hasHit(text: String, vocab: Vocab): Boolean = vocab.hasHit(text)

    /**
     * Singular/plural forms worth trying. Stems shorter than 4 letters are never
     * used as prefixes (קולה must not become "קול…", חלבה must not become חלב).
     * Note: final letters are folded by normalize(), so plural ים is "ימ".
     */
    private fun suffixVariants(t: String): List<String> {
        val out = ArrayList<String>(4)
        if (t.length >= 4 && (t.endsWith("ות") || t.endsWith("ימ"))) {
            val stem = t.dropLast(2)
            if (stem.length >= 4) out.add(stem)
            if (stem.length + 1 >= 3) out.add(stem + "ה")
        }
        if (t.length >= 4 && t.endsWith("ה")) {
            val base = t.dropLast(1)
            out.add(base + "ות")
            out.add(base + "ימ")
            if (base.length >= 4) out.add(base)
        }
        if (t.length >= 5 && t.endsWith("י")) out.add(t.dropLast(1))
        // Plural and construct forms of a plain noun: תפוח → תפוחי (אדמה), גזר → גזרימ.
        if (t.length >= 3 && !t.endsWith("ה") && !t.endsWith("י") && !t.endsWith("ות") && !t.endsWith("ימ")) {
            out.add(t + "י")
            out.add(t + "ימ")
            out.add(t + "ות")
        }
        return out
    }

    private fun prefixStrips(t: String): List<String> {
        val out = ArrayList<String>(2)
        var cur = t
        repeat(2) {
            if (cur.length >= 4 && cur[0] in PREFIX_LETTERS) {
                cur = cur.substring(1)
                out.add(cur)
            }
        }
        return out
    }

    private fun fuzzyCandidates(token: String, vocab: Vocab): List<VocabWord> {
        val maxD = when {
            token.length <= 3 -> 0
            token.length <= 5 -> 1
            else -> 2
        }
        val found = ArrayList<Pair<Double, VocabWord>>()
        for (v in vocab.words) {
            if (v.norm == token) continue
            if (maxD == 0) return emptyList()
            val dd = HebrewText.editDistance(v.norm, token, maxD)
            if (dd > maxD) continue
            found.add(dd.toDouble() to v)
        }
        return found
            .sortedWith(compareBy<Pair<Double, VocabWord>> { it.first }.thenByDescending { it.second.freq })
            .take(4)
            .map { it.second }
    }

    // ------------------------------------------------------------------ data

    private class VocabWord(val norm: String, val loose: String, val freq: Int, val surface: String)

    /** Vocabulary plus one big " w1 w2 w3 " string so prefix/substring probes are a single native scan. */
    private class Vocab(val words: List<VocabWord>) {
        private val blob: String = words.joinToString(" ", prefix = " ", postfix = " ") { it.norm }
        fun hasHit(text: String): Boolean = when {
            text.isEmpty() -> false
            text.length >= 3 -> blob.contains(text)
            else -> blob.contains(" $text")
        }
    }

    private class Entry(val food: FoodSearchResult, val isCustom: Boolean) {
        val key: String = "${food.source}:${food.refId}"
        val segments: List<List<String>>
        val words: List<String>
        val wordSeg: IntArray
        val wordIdx: IntArray
        val surfaces: List<Pair<String, String>>
        val flatNorm: String

        init {
            val segs = ArrayList<List<String>>()
            val flat = ArrayList<String>()
            val segOf = ArrayList<Int>()
            val idxOf = ArrayList<Int>()
            val surf = ArrayList<Pair<String, String>>()
            food.nameHe.split(',').forEach { raw ->
                val ws = HebrewText.wordMatches(raw)
                if (ws.isNotEmpty()) {
                    val segIndex = segs.size
                    segs.add(ws.map { it.first })
                    ws.forEachIndexed { i, pair ->
                        flat.add(pair.first); segOf.add(segIndex); idxOf.add(i); surf.add(pair)
                    }
                }
            }
            segments = segs
            words = flat
            wordSeg = segOf.toIntArray()
            wordIdx = idxOf.toIntArray()
            surfaces = surf
            flatNorm = flat.joinToString(" ")
        }
    }

    private fun buildVocab(base: List<Entry>, custom: List<Entry>): List<VocabWord> {
        val counts = HashMap<String, Int>(base.size * 3)
        val surface = HashMap<String, String>(base.size * 3)
        fun addAll(list: List<Entry>) {
            for (e in list) for ((n, s) in e.surfaces) {
                counts[n] = (counts[n] ?: 0) + 1
                if (!surface.containsKey(n)) surface[n] = s
            }
        }
        addAll(base)
        addAll(custom)
        return counts.map { (n, c) -> VocabWord(n, HebrewText.loose(n), c, surface[n] ?: n) }
    }

    companion object {
        private const val PARTIAL_CUTOFF = 6
        private const val EXACT = 1.0
        private const val PREFIX = 0.4
        private const val CONTAINS = 0.35
        private const val PREFIX_LETTERS = "הבלמושכ"

        /** Forms the user rarely means when they type the plain food name. */
        private val NOISE_WORDS: Set<String> = setOf(
            "אבקה", "אבקת", "תערובת", "תערובות", "להכנת", "מרוכז", "מרוכזת", "תרכיז",
            "תמל", "תינוקות", "תינוק"
        )
        private val MILD_NOISE_WORDS: Set<String> = setOf("מיובש", "מיובשת", "מיובשים", "מיובשות", "מיובשימ")
    }
}
