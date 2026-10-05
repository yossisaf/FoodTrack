package com.foodtrack.app.ui

import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.LinearLayout
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.foodtrack.app.R
import com.foodtrack.app.data.FavoriteFoodEntity
import com.foodtrack.app.data.FoodCategory
import com.foodtrack.app.data.FoodCategoryRepository
import com.foodtrack.app.data.FoodSearchResult
import com.foodtrack.app.data.UserDatabase
import com.foodtrack.app.data.toSearchResult
import com.foodtrack.app.search.Correction
import com.foodtrack.app.search.HebrewText
import com.foodtrack.app.search.SearchOutcome
import com.foodtrack.app.search.SearchSignals
import com.foodtrack.app.util.Prefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.EnumMap
import java.util.Locale

/**
 * Food database/search screen. Searching runs in memory over the bundled food
 * list (see the search package); categories are an app-layer filter on top, and
 * the bundled app_foods.db stays read-only and unchanged.
 */
class MainActivity : BaseActivity() {

    private lateinit var binding: com.foodtrack.app.databinding.ActivityMainBinding
    private lateinit var adapter: FoodAdapter
    private lateinit var categoryRepository: FoodCategoryRepository
    private var searchJob: Job? = null
    private val favoriteKeys = mutableSetOf<String>()
    private var selectedCategory = FoodCategory.ALL
    private var searchSequence = 0L
    private var currentQuery = ""
    private var suggestionQuery: String? = null
    private var resumedOnce = false
    private var compactInitialCollapseApplied = false
    private val selectForMeal: Boolean by lazy { intent.getBooleanExtra(EXTRA_SELECT_FOR_MEAL, false) }
    private val categoryViews = LinkedHashMap<FoodCategory, TextView>()

    // As a tab it is top-level (bottom bar); as a picker for meal templates it is a
    // secondary screen (up arrow, no bottom bar).
    override val navItemId: Int? get() = if (selectForMeal) null else R.id.nav_food

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = com.foodtrack.app.databinding.ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        adaptForCompactScreens()
        categoryRepository = FoodCategoryRepository.getInstance(applicationContext)

        if (selectForMeal) {
            setTitle(R.string.title_pick_food)
            binding.buttonMealTemplates.visibility = View.GONE
        }

        loadFavorites()
        setupCategoryChips()
        adapter = FoodAdapter(
            onFoodClick = { result ->
                rememberQuery()
                if (selectForMeal) onFoodSelected(result) else openFoodDetails(result)
            },
            onAddClick = { result ->
                rememberQuery()
                onFoodSelected(result)
            },
            isFavorite = { result -> favoriteKeys.contains(keyFor(result)) },
            onFavoriteClick = { result -> toggleFavorite(result) },
            categoryFor = { result -> categoryRepository.categoryFor(result).title }
        )
        binding.recyclerResults.layoutManager = LinearLayoutManager(this)
        binding.recyclerResults.adapter = adapter
        // The keyboard covers half the list; once the user starts scrolling they are browsing, not typing.
        binding.recyclerResults.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) hideKeyboard()
            }
        })

        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                val q = query.orEmpty().trim()
                if (q.isNotBlank()) {
                    Prefs.addRecentSearch(this@MainActivity, q)
                    renderRecentSearches()
                }
                hideKeyboard()
                scheduleSearch(q, immediate = true)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                scheduleSearch(newText.orEmpty())
                return true
            }
        })

        binding.textSuggestion.setOnClickListener {
            suggestionQuery?.let { binding.searchView.setQuery(it, false) }
        }

        binding.buttonAddCustomFood.setOnClickListener {
            startActivity(Intent(this, CustomFoodActivity::class.java))
        }

        binding.buttonMealTemplates.setOnClickListener {
            startActivity(Intent(this, MealTemplatesActivity::class.java))
        }

        renderRecentSearches()
        // Build the in-memory index in the background so the first keystroke is not the one that pays for it.
        lifecycleScope.launch(Dispatchers.IO) {
            try { categoryRepository.searchEngine() } catch (_: Exception) { /* surfaced by the real search */ }
        }
        scheduleSearch("", immediate = true)

        if (intent.getBooleanExtra(EXTRA_FOCUS_SEARCH, false)) focusSearch()
    }

    /** Avoids crowding the status/actions row on very narrow phones. */
    private fun adaptForCompactScreens() {
        if (resources.configuration.screenWidthDp >= 360) return
        binding.headerActions.orientation = LinearLayout.VERTICAL
        binding.textCategoryState.maxLines = 2
        listOf(binding.buttonMealTemplates, binding.buttonAddCustomFood).forEach { button ->
            button.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(40)
            )
        }
    }
    override fun onResume() {
        super.onResume()
        // Coming back from adding a custom food / logging something / starring elsewhere:
        // refresh so new foods and your usual foods show up without restarting the screen.
        if (resumedOnce) {
            loadFavorites()
            renderRecentSearches()
            scheduleSearch(currentQuery, immediate = true)
        }
        resumedOnce = true
    }

    private fun focusSearch() {
        binding.searchView.post {
            binding.searchView.isIconified = false
            val edit = binding.searchView.findViewById<View>(androidx.appcompat.R.id.search_src_text)
            val target = edit ?: binding.searchView
            target.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(target, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.searchView.windowToken, 0)
    }

    private fun setupCategoryChips() {
        binding.categoryChips.removeAllViews()
        FoodCategory.values().forEach { category ->
            val chip = TextView(this).apply {
                text = category.title
                gravity = Gravity.CENTER_VERTICAL
                textSize = 14f
                minHeight = dpToPx(40)
                setPadding(dpToPx(12), dpToPx(6), dpToPx(14), dpToPx(6))
                ContextCompat.getDrawable(this@MainActivity, resources.getIdentifier("ic_category_${category.id}", "drawable", packageName))?.let { icon ->
                    icon.setBounds(0, 0, dpToPx(22), dpToPx(22))
                    setCompoundDrawablesRelative(icon, null, null, null)
                    compoundDrawablePadding = dpToPx(6)
                }
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dpToPx(8) }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    selectedCategory = category
                    updateCategoryChipStyles()
                    scheduleSearch(currentQuery, immediate = true)
                }
            }
            categoryViews[category] = chip
            binding.categoryChips.addView(chip)
        }
        updateCategoryChipStyles()
    }

    private fun updateCategoryChipStyles() {
        categoryViews.forEach { (category, view) ->
            val selected = category == selectedCategory
            view.setTextColor(
                ContextCompat.getColor(this, if (selected) R.color.primary_dark_on_soft else R.color.text_primary)
            )
            view.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
            view.background = GradientDrawable().apply {
                cornerRadius = dpToPx(20).toFloat()
                setColor(ContextCompat.getColor(this@MainActivity, if (selected) R.color.primary_soft else R.color.surface))
                setStroke(
                    dpToPx(if (selected) 2 else 1),
                    ContextCompat.getColor(this@MainActivity, if (selected) R.color.primary else R.color.card_stroke)
                )
            }
            view.isSelected = selected
        }
    }

    /** While searching, each chip shows how many results it would give; empty ones fade. */
    private fun updateChipCounts(counts: Map<FoodCategory, Int>?) {
        categoryViews.forEach { (category, view) ->
            if (counts == null || category == FoodCategory.ALL) {
                view.text = category.title
                view.alpha = 1f
            } else {
                val n = counts[category] ?: 0
                view.text = "${category.title} $n"
                view.alpha = if (n == 0 && category != selectedCategory) 0.4f else 1f
            }
        }
    }

    private fun scheduleSearch(query: String, immediate: Boolean = false) {
        currentQuery = query.trim()
        searchJob?.cancel()
        val sequence = ++searchSequence
        searchJob = lifecycleScope.launch {
            if (!immediate) delay(120L)
            runSearch(query, sequence)
        }
    }

    private fun loadFavorites() {
        runSafely("MainActivity.loadFavorites") {
            favoriteKeys.clear()
            UserDatabase.getInstance(applicationContext).favoriteFoodDao().all().forEach {
                favoriteKeys.add("${it.foodSource}:${it.foodRefId}")
            }
            if (::adapter.isInitialized) adapter.notifyDataSetChanged()
        }
    }

    private fun keyFor(result: FoodSearchResult) = "${result.source}:${result.refId}"

    // Guards toggleFavorite against a rapid double-tap: without it, two taps before
    // the first DB check commits could both see "not favorited" and both insert,
    // leaving a duplicate row that a later un-favorite tap (which only clears one
    // row) can't fully remove.
    private val pendingFavoriteToggles = mutableSetOf<String>()

    private fun toggleFavorite(result: FoodSearchResult) {
        val key = keyFor(result)
        if (!pendingFavoriteToggles.add(key)) return

        // Optimistic UI: flip the star (and refresh only this food's row) the instant it's
        // tapped, instead of waiting on a DB round trip before the icon responds. The
        // actual insert/delete below still runs — this just decouples what the user
        // sees from when the write finishes.
        val willFavorite = !favoriteKeys.contains(key)
        if (willFavorite) favoriteKeys.add(key) else favoriteKeys.remove(key)
        adapter.currentList.forEachIndexed { index, row ->
            if (row is FoodRow.Item && keyFor(row.food) == key) adapter.notifyItemChanged(index)
        }

        runSafely("MainActivity.toggleFavorite") {
            try {
                val dao = UserDatabase.getInstance(applicationContext).favoriteFoodDao()
                if (willFavorite) {
                    dao.insert(
                        FavoriteFoodEntity(
                            foodSource = result.source,
                            foodRefId = result.refId,
                            foodNameHe = result.nameHe,
                            addedAt = System.currentTimeMillis()
                        )
                    )
                } else {
                    dao.deleteFor(result.source, result.refId)
                }
            } finally {
                pendingFavoriteToggles.remove(key)
            }
        }
    }

    /** A search that led to a tap was a good search — keep it for one-tap reuse. */
    private fun rememberQuery() {
        if (currentQuery.length >= 2 && HebrewText.words(currentQuery).isNotEmpty()) {
            Prefs.addRecentSearch(this, currentQuery)
        }
    }

    private fun renderRecentSearches() {
        binding.recentSearches.removeAllViews()
        val recents = Prefs.recentSearches(this).take(6)
        val visible = recents.isNotEmpty() && currentQuery.isBlank()
        binding.recentSearchesScroll.visibility = if (visible) View.VISIBLE else View.GONE
        if (!visible) return
        fun chip(label: String, color: Int, description: String, onClick: () -> Unit) = TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = 14f
            minHeight = dpToPx(40)
            setTextColor(ContextCompat.getColor(this@MainActivity, color))
            setPadding(dpToPx(14), dpToPx(8), dpToPx(14), dpToPx(8))
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_stat)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dpToPx(8) }
            contentDescription = description
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        recents.forEach { query ->
            binding.recentSearches.addView(
                chip(query, R.color.primary, "חיפוש אחרון: $query") { binding.searchView.setQuery(query, true) }
            )
        }
        binding.recentSearches.addView(
            chip("נקה", R.color.text_secondary, "נקה חיפושים אחרונים") {
                Prefs.clearRecentSearches(this)
                renderRecentSearches()
            }
        )
    }

    // ------------------------------------------------------------------ search

    private class Page(
        val rows: List<FoodRow>,
        val total: Int,
        val highlight: List<String>,
        val outcome: SearchOutcome?,
        val chipCounts: Map<FoodCategory, Int>?,
        val allCategoriesCount: Int
    )

    private suspend fun runSearch(query: String, sequence: Long) {
        try {
            val cleanQuery = query.trim()
            val category = selectedCategory
            val favorites = favoriteKeys.toSet() // snapshot: the set is mutated on the main thread

            val page = withContext(Dispatchers.IO) { buildPage(cleanQuery, category, favorites) }

            if (!currentCoroutineContext().isActive || sequence != searchSequence || isFinishing || isDestroyed) return
            applyPage(cleanQuery, category, page)
        } catch (t: CancellationException) {
            // A newer keystroke/category selection superseded this search.
        } catch (t: Exception) {
            if (!isFinishing && !isDestroyed) showCrashDialog("MainActivity.runSearch(query=\"$query\")", t)
        }
    }

    private suspend fun buildPage(query: String, category: FoodCategory, favorites: Set<String>): Page {
        val engine = categoryRepository.searchEngine()
        val userDb = UserDatabase.getInstance(applicationContext)
        val custom = userDb.customFoodDao().all().map { it.toSearchResult() }
        val usage = userDb.logDao().usageStats(400)
        val usageCounts = usage.associate { "${it.foodSource}:${it.foodRefId}" to it.uses }

        if (HebrewText.words(query).isNotEmpty()) {
            val recentKeys = usage.sortedByDescending { it.lastUsed }.take(12)
                .map { "${it.foodSource}:${it.foodRefId}" }.toSet()
            val outcome = engine.search(query, custom, SearchSignals(favorites, usageCounts, recentKeys))
            val all = outcome.results.map { it.food }

            val counts = EnumMap<FoodCategory, Int>(FoodCategory::class.java)
            all.forEach { food ->
                val c = categoryRepository.categoryFor(food)
                counts[c] = (counts[c] ?: 0) + 1
            }
            counts[FoodCategory.ALL] = all.size

            val filtered = if (category == FoodCategory.ALL) all
            else all.filter { categoryRepository.categoryFor(it) == category }
            val shown = filtered.take(MAX_RESULTS)
            return Page(
                rows = shown.map { FoodRow.Item(it) },
                total = filtered.size,
                highlight = outcome.highlightTerms,
                outcome = outcome,
                chipCounts = counts,
                allCategoriesCount = all.size
            )
        }

        // Browsing (empty search box).
        val baseFoods = categoryRepository.foodsForCategory(category)
        val customInCategory = custom.filter { category == FoodCategory.ALL || categoryRepository.categoryFor(it) == category }
        val rows = ArrayList<FoodRow>()

        if (category == FoodCategory.ALL) {
            // Your shortcuts first: what you starred, then what you actually log.
            fun resolve(source: String, refId: String): FoodSearchResult? =
                if (source == "custom") custom.firstOrNull { it.refId == refId } else engine.find(source, refId)

            val favFoods = userDb.favoriteFoodDao().all()
                .mapNotNull { resolve(it.foodSource, it.foodRefId) }.take(MAX_SHORTCUTS)
            val favKeys = favFoods.map { keyFor(it) }.toHashSet()
            val usualFoods = usage.filter { "${it.foodSource}:${it.foodRefId}" !in favKeys }
                .mapNotNull { resolve(it.foodSource, it.foodRefId) }.take(MAX_SHORTCUTS)
            val shortcutKeys = favKeys + usualFoods.map { keyFor(it) }

            if (favFoods.isNotEmpty()) {
                rows.add(FoodRow.Header("מועדפים"))
                favFoods.forEach { rows.add(FoodRow.Item(it, "fav")) }
            }
            if (usualFoods.isNotEmpty()) {
                rows.add(FoodRow.Header("נפוצים ביומן שלך"))
                usualFoods.forEach { rows.add(FoodRow.Item(it, "usual")) }
            }
            val rest = (customInCategory + baseFoods).filter { keyFor(it) !in shortcutKeys }
            if (rows.isNotEmpty() && rest.isNotEmpty()) rows.add(FoodRow.Header("כל המאכלים"))
            rest.forEach { rows.add(FoodRow.Item(it)) }
            return Page(rows, customInCategory.size + baseFoods.size, emptyList(), null, null, 0)
        }

        // A category: your starred and most-logged foods float to the top, the rest is A–Z.
        val sorted = (customInCategory + baseFoods).sortedWith(
            compareByDescending<FoodSearchResult> { keyFor(it) in favorites }
                .thenByDescending { usageCounts[keyFor(it)] ?: 0 }
                .thenBy { it.nameHe }
        )
        sorted.forEach { rows.add(FoodRow.Item(it)) }
        return Page(rows, sorted.size, emptyList(), null, null, 0)
    }

    private fun applyPage(query: String, category: FoodCategory, page: Page) {
        val searching = page.outcome != null
        adapter.submit(page.rows, page.highlight)
        updateChipCounts(page.chipCounts)
        renderRecentSearches()

        val total = fmt(page.total)
        val shownCount = page.rows.count { it is FoodRow.Item }
        binding.textCategoryState.text = when {
            !searching -> "${category.title} • $total מאכלים · ערכים ל־100 גרם"
            page.total > shownCount -> "מוצגות ${fmt(shownCount)} מתוך $total תוצאות · ערכים ל־100 גרם"
            category == FoodCategory.ALL -> "$total תוצאות · ערכים ל־100 גרם"
            else -> "$total תוצאות • ${category.title} · ערכים ל־100 גרם"
        }

        showSuggestion(page.outcome)

        val empty = shownCount == 0
        if (!compactInitialCollapseApplied && resources.configuration.screenWidthDp < 360) {
            binding.foodHeader.setExpanded(false, false)
            compactInitialCollapseApplied = true
        }

        binding.emptyStateContainer.visibility = if (empty) View.VISIBLE else View.GONE
        binding.buttonShowAllCategories.visibility = View.GONE
        binding.buttonAddAsCustom.visibility = View.GONE
        if (!empty) return

        when {
            searching && category != FoodCategory.ALL && page.allCategoriesCount > 0 -> {
                // There are matches — just not in the selected category.
                binding.textEmptyState.text = "אין תוצאות ל\"$query\" בקטגוריה ${category.title}"
                binding.buttonShowAllCategories.text = "הצג בכל המאכלים (${fmt(page.allCategoriesCount)})"
                binding.buttonShowAllCategories.visibility = View.VISIBLE
                binding.buttonShowAllCategories.setOnClickListener {
                    selectedCategory = FoodCategory.ALL
                    updateCategoryChipStyles()
                    scheduleSearch(currentQuery, immediate = true)
                }
            }
            searching -> {
                // Only an actual empty search offers "add as custom food" — a category
                // browse with nothing in it has no query to hand the custom-food screen,
                // and "add הכל" as a custom food wouldn't mean anything.
                binding.textEmptyState.text = getString(R.string.no_results_for_query, query)
                binding.buttonAddAsCustom.visibility = View.VISIBLE
                binding.buttonAddAsCustom.setOnClickListener {
                    startActivity(Intent(this@MainActivity, CustomFoodActivity::class.java).apply {
                        putExtra(CustomFoodActivity.EXTRA_PREFILL_NAME, query)
                    })
                }
            }
            else -> binding.textEmptyState.setText(R.string.no_results)
        }
    }

    private fun showSuggestion(outcome: SearchOutcome?) {
        suggestionQuery = null
        val tv = binding.textSuggestion
        val corrected = outcome?.correctedQuery
        val text: CharSequence? = when {
            outcome == null -> null
            outcome.correction == Correction.SPELLING && corrected != null -> {
                suggestionQuery = corrected
                boldMiddle("האם התכוונת ל־", corrected, "?")
            }
            outcome.correction == Correction.KEYBOARD && corrected != null -> {
                suggestionQuery = corrected
                boldMiddle("מציג תוצאות עבור ", corrected, " (נראה שההקלדה הייתה במקלדת אנגלית)")
            }
            outcome.ignoredWords.isNotEmpty() && outcome.results.isNotEmpty() ->
                boldMiddle("לא נמצאו התאמות ל־", outcome.ignoredWords.joinToString(", "), " — מוצגות תוצאות לשאר החיפוש")
            else -> null
        }
        tv.text = text
        tv.visibility = if (text == null) View.GONE else View.VISIBLE
        tv.isClickable = suggestionQuery != null
    }

    private fun boldMiddle(before: String, middle: String, after: String): CharSequence =
        SpannableStringBuilder().apply {
            append(before)
            val start = length
            append(middle)
            setSpan(StyleSpan(Typeface.BOLD), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            append(after)
        }

    private fun fmt(n: Int) = String.format(Locale.getDefault(), "%,d", n)

    private fun openFoodDetails(result: FoodSearchResult) {
        startActivity(Intent(this, FoodDetailActivity::class.java).apply {
            putExtra(FoodDetailActivity.EXTRA_SOURCE, result.source)
            putExtra(FoodDetailActivity.EXTRA_REF_ID, result.refId)
            putExtra(FoodDetailActivity.EXTRA_NAME, result.nameHe)
            putExtra(FoodDetailActivity.EXTRA_CATEGORY, categoryRepository.categoryFor(result).title)
            putExtra(FoodDetailActivity.EXTRA_CALORIES, result.caloriesKcal ?: Double.NaN)
            putExtra(FoodDetailActivity.EXTRA_PROTEIN, result.proteinG ?: Double.NaN)
            putExtra(FoodDetailActivity.EXTRA_CARBS, result.carbsG ?: Double.NaN)
            putExtra(FoodDetailActivity.EXTRA_FAT, result.fatG ?: Double.NaN)
            putExtra(FoodDetailActivity.EXTRA_FIBER, result.fiberG ?: Double.NaN)
            putExtra(FoodDetailActivity.EXTRA_SUGAR, result.sugarG ?: Double.NaN)
            putExtra(FoodDetailActivity.EXTRA_SATURATED, result.saturatedFatG ?: Double.NaN)
            putExtra(FoodDetailActivity.EXTRA_SODIUM, result.sodiumMg ?: Double.NaN)
            putExtra(FoodDetailActivity.EXTRA_CHOLESTEROL, result.cholesterolMg ?: Double.NaN)
        })
    }

    private fun onFoodSelected(result: FoodSearchResult) {
        if (selectForMeal) {
            setResult(RESULT_OK, Intent().apply {
                putExtra(EXTRA_SELECTED_SOURCE, result.source)
                putExtra(EXTRA_SELECTED_REF_ID, result.refId)
                putExtra(EXTRA_SELECTED_NAME, result.nameHe)
                putExtra(EXTRA_SELECTED_CALORIES, result.caloriesKcal ?: 0.0)
                putExtra(EXTRA_SELECTED_PROTEIN, result.proteinG ?: 0.0)
                putExtra(EXTRA_SELECTED_CARBS, result.carbsG ?: 0.0)
                putExtra(EXTRA_SELECTED_FAT, result.fatG ?: 0.0)
            })
            finish()
            return
        }
        startActivity(Intent(this, AddLogEntryActivity::class.java).apply {
            putExtra(AddLogEntryActivity.EXTRA_SOURCE, result.source)
            putExtra(AddLogEntryActivity.EXTRA_REF_ID, result.refId)
            putExtra(AddLogEntryActivity.EXTRA_NAME_HE, result.nameHe)
            putExtra(AddLogEntryActivity.EXTRA_CALORIES, result.caloriesKcal ?: 0.0)
            putExtra(AddLogEntryActivity.EXTRA_PROTEIN, result.proteinG ?: 0.0)
            putExtra(AddLogEntryActivity.EXTRA_CARBS, result.carbsG ?: 0.0)
            putExtra(AddLogEntryActivity.EXTRA_FAT, result.fatG ?: 0.0)
            putExtra(AddLogEntryActivity.EXTRA_NO_CALORIE_DATA, result.caloriesKcal == null)
        })
    }

    companion object {
        const val EXTRA_SELECT_FOR_MEAL = "selectForMeal"
        const val EXTRA_FOCUS_SEARCH = "focusSearch"
        const val EXTRA_SELECTED_SOURCE = "selectedSource"
        const val EXTRA_SELECTED_REF_ID = "selectedRefId"
        const val EXTRA_SELECTED_NAME = "selectedName"
        const val EXTRA_SELECTED_CALORIES = "selectedCalories"
        const val EXTRA_SELECTED_PROTEIN = "selectedProtein"
        const val EXTRA_SELECTED_CARBS = "selectedCarbs"
        const val EXTRA_SELECTED_FAT = "selectedFat"

        private const val MAX_RESULTS = 250
        private const val MAX_SHORTCUTS = 8
    }
}
