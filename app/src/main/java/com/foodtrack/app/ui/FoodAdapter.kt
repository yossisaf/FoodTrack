package com.foodtrack.app.ui

import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.foodtrack.app.R
import com.foodtrack.app.data.FoodSearchResult
import com.foodtrack.app.databinding.ItemFoodBinding
import com.foodtrack.app.databinding.ItemSectionHeaderBinding
import com.foodtrack.app.search.HebrewText
import java.util.Locale

/** One row of the food list: either a section header or a food. */
sealed class FoodRow {
    data class Header(val title: String) : FoodRow()
    data class Item(val food: FoodSearchResult, val section: String = "") : FoodRow()
}

class FoodAdapter(
    private val onFoodClick: (FoodSearchResult) -> Unit,
    private val onAddClick: (FoodSearchResult) -> Unit,
    private val isFavorite: (FoodSearchResult) -> Boolean,
    private val onFavoriteClick: (FoodSearchResult) -> Unit,
    private val categoryFor: (FoodSearchResult) -> String
) : ListAdapter<FoodRow, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    /** Normalized words to highlight in names (the resolved search terms). */
    private var highlightTerms: List<String> = emptyList()

    /** Replaces the list and the terms to highlight in one go. */
    fun submit(rows: List<FoodRow>, terms: List<String>) {
        val termsChanged = terms != highlightTerms
        highlightTerms = terms
        submitList(rows) {
            // DiffUtil sees identical rows when only the query changed (same food,
            // different highlight), so force a rebind in that case.
            if (termsChanged && itemCount > 0) notifyItemRangeChanged(0, itemCount)
        }
    }

    override fun getItemViewType(position: Int): Int =
        if (getItem(position) is FoodRow.Header) TYPE_HEADER else TYPE_ITEM

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(ItemSectionHeaderBinding.inflate(inflater, parent, false))
        } else {
            FoodViewHolder(ItemFoodBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is FoodRow.Header -> (holder as HeaderViewHolder).binding.textSectionTitle.let {
                it.text = row.title
                ViewCompat.setAccessibilityHeading(it, true)
            }
            is FoodRow.Item -> (holder as FoodViewHolder).bind(
                row.food, highlightTerms, onFoodClick, onAddClick, isFavorite, onFavoriteClick, categoryFor
            )
        }
    }

    class HeaderViewHolder(val binding: ItemSectionHeaderBinding) : RecyclerView.ViewHolder(binding.root)

    class FoodViewHolder(private val binding: ItemFoodBinding) : RecyclerView.ViewHolder(binding.root) {

        init {
            if (binding.root.resources.configuration.screenWidthDp < 360) {
                (binding.root.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                    lp.setMargins(lp.leftMargin / 2, dp(binding.root, 2), lp.rightMargin / 2, dp(binding.root, 2))
                    binding.root.layoutParams = lp
                }
                (binding.root.getChildAt(0) as? ViewGroup)?.setPaddingRelative(
                    dp(binding.root, 10), dp(binding.root, 7), dp(binding.root, 2), dp(binding.root, 7)
                )
                binding.textFoodName.textSize = 16f
                binding.textFoodDetail.textSize = 12f
                binding.textFoodCalories.textSize = 13f
                binding.buttonFavorite.layoutParams.width = dp(binding.root, 44)
                binding.buttonFavorite.layoutParams.height = dp(binding.root, 44)
                binding.buttonFavorite.textSize = 23f
                binding.buttonAddFood.layoutParams.height = dp(binding.root, 44)
                binding.buttonAddFood.minimumWidth = dp(binding.root, 56)
                binding.buttonAddFood.setPadding(dp(binding.root, 6), 0, dp(binding.root, 6), 0)
            }
        }

        fun bind(
            food: FoodSearchResult,
            terms: List<String>,
            onFoodClick: (FoodSearchResult) -> Unit,
            onAddClick: (FoodSearchResult) -> Unit,
            isFavorite: (FoodSearchResult) -> Boolean,
            onFavoriteClick: (FoodSearchResult) -> Unit,
            categoryFor: (FoodSearchResult) -> String
        ) {
            val ctx = binding.root.context
            val (title, details) = HebrewText.splitName(food.nameHe)
            binding.textFoodName.text = highlighted(title, terms)

            // Category is shown as a muted prefix of the detail line ("חלב • 3% שומן, תנובה").
            val category = categoryFor(food).ifBlank { food.subtitleHe }
                .takeUnless { it.isBlank() || it == "אחר" }
            val detailLine = highlighted(details, terms)
            val prefix = when {
                category != null && details.isNotEmpty() -> "$category • "
                category != null -> category
                else -> ""
            }
            binding.textFoodDetail.text =
                if (prefix.isEmpty()) detailLine else android.text.TextUtils.concat(prefix, detailLine)
            binding.textFoodDetail.visibility =
                if (binding.textFoodDetail.text.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE

            binding.textFoodCalories.text = macroLine(ctx, food)

            binding.root.setOnClickListener { onFoodClick(food) }
            binding.buttonAddFood.setOnClickListener { onAddClick(food) }
            binding.buttonAddFood.contentDescription = "הוסף ליומן: ${food.nameHe}"
            val fav = isFavorite(food)
            binding.buttonFavorite.text = if (fav) "★" else "☆"
            binding.buttonFavorite.setTextColor(
                ContextCompat.getColor(ctx, if (fav) R.color.star_on else R.color.text_secondary)
            )
            binding.buttonFavorite.contentDescription =
                if (fav) "הסר ממועדפים: ${food.nameHe}" else "הוסף למועדפים: ${food.nameHe}"
            binding.buttonFavorite.setOnClickListener { onFavoriteClick(food) }
        }

        private fun dp(view: View, value: Int): Int = (value * view.resources.displayMetrics.density).toInt()

        private fun highlighted(text: String, terms: List<String>): CharSequence {
            if (text.isEmpty() || terms.isEmpty()) return text
            val ranges = HebrewText.highlightRanges(text, terms)
            if (ranges.isEmpty()) return text
            val ctx = binding.root.context
            val fg = ContextCompat.getColor(ctx, R.color.primary_dark_on_soft)
            val bg = ContextCompat.getColor(ctx, R.color.primary_soft)
            return SpannableString(text).also { sp ->
                ranges.forEach { r ->
                    val end = r.last + 1
                    sp.setSpan(BackgroundColorSpan(bg), r.first, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sp.setSpan(ForegroundColorSpan(fg), r.first, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sp.setSpan(StyleSpan(Typeface.BOLD), r.first, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }

        /** "165 קק״ל · ח׳ 31 · פ׳ 0 · ש׳ 4" with the macro letters in the dashboard's macro colors. */
        private fun macroLine(ctx: android.content.Context, food: FoodSearchResult): CharSequence {
            val kcal = food.caloriesKcal ?: return "אין נתון קלורי"
            val sb = android.text.SpannableStringBuilder()
            sb.append(String.format(Locale.getDefault(), "%.0f קק״ל", kcal))
            fun macro(label: String, value: Double?, colorRes: Int) {
                if (value == null) return
                sb.append("  ·  ")
                val start = sb.length
                sb.append(label)
                sb.setSpan(ForegroundColorSpan(ContextCompat.getColor(ctx, colorRes)), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.append(String.format(Locale.getDefault(), " %.0f", value))
            }
            macro("ח׳", food.proteinG, R.color.macro_protein)
            macro("פ׳", food.carbsG, R.color.macro_carbs)
            macro("ש׳", food.fatG, R.color.macro_fat)
            return sb
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<FoodRow>() {
            override fun areItemsTheSame(oldItem: FoodRow, newItem: FoodRow): Boolean = when {
                oldItem is FoodRow.Header && newItem is FoodRow.Header -> oldItem.title == newItem.title
                oldItem is FoodRow.Item && newItem is FoodRow.Item ->
                    oldItem.food.source == newItem.food.source && oldItem.food.refId == newItem.food.refId &&
                        oldItem.section == newItem.section
                else -> false
            }

            override fun areContentsTheSame(oldItem: FoodRow, newItem: FoodRow): Boolean = oldItem == newItem
        }
    }
}
