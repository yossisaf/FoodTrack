package com.foodtrack.app.ui

import com.foodtrack.app.data.FoodSearchResult
import com.foodtrack.app.search.FoodSearchEngine
import com.foodtrack.app.search.HebrewText
import com.foodtrack.app.search.SearchSignals

/**
 * Thin compatibility wrapper around [FoodSearchEngine] for callers that already
 * hold a candidate list. The real work (Hebrew-aware matching, field-aware
 * scoring, typo tolerance) lives in the search package; the search screen uses
 * the engine directly so it can search the full food list in memory.
 */
object SearchRanking {

    fun tokens(query: String): List<String> = HebrewText.words(query).distinct()

    /**
     * Ranks [results] for [query]. Foods that do not match at all are kept, after
     * the matches, in alphabetical order (the old behaviour: nothing is dropped).
     */
    fun rank(
        results: Collection<FoodSearchResult>,
        query: String,
        isFavorite: (FoodSearchResult) -> Boolean = { false },
        recentKeys: Set<String> = emptySet()
    ): List<FoodSearchResult> {
        if (tokens(query).isEmpty()) return results.sortedBy { it.nameHe }
        val favorites = results.filter(isFavorite).map { "${it.source}:${it.refId}" }.toSet()
        val outcome = FoodSearchEngine(results).search(
            query = query,
            signals = SearchSignals(favorites = favorites, recentKeys = recentKeys)
        )
        val matched = outcome.results.map { it.food }
        val matchedKeys = matched.map { "${it.source}:${it.refId}" }.toHashSet()
        val rest = results.filter { "${it.source}:${it.refId}" !in matchedKeys }.sortedBy { it.nameHe }
        return matched + rest
    }
}
