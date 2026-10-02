package com.foodtrack.app.search

/**
 * Curated alternates for words people type that the reference DB spells
 * differently. Keys and values are in [HebrewText.normalize] form (no quotes,
 * final letters folded). An alternate is only used when it actually exists in
 * the DB vocabulary, so stale entries are harmless.
 *
 * Plural/feminine forms, ה/ב/ל prefixes and full-vs-defective spelling
 * (עגבנייה/עגבניה) are handled generically by the engine; this list is only
 * for genuinely different words.
 */
object SearchSynonyms {
    val map: Map<String, List<String>> = mapOf(
        "חלווה" to listOf("חלבה"),
        "חלוה" to listOf("חלבה"),
        "סנדביצ" to listOf("סנדוויצ"),
        "סנדויצ" to listOf("סנדוויצ"),
        "סופגנייה" to listOf("סופגניה"),
        "פנקק" to listOf("פנקייק"),
        "פנקייקס" to listOf("פנקייק"),
        "מאיונז" to listOf("מיונז"),
        "מיונס" to listOf("מיונז"),
        "קפוצינו" to listOf("קפוצ"),
        "פשטה" to listOf("פסטה"),
        "טורטייה" to listOf("טורטיה")
    )
}
