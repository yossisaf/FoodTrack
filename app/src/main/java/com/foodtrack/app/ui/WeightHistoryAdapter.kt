package com.foodtrack.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.foodtrack.app.data.WeightEntryEntity
import com.foodtrack.app.databinding.ItemWeightHistoryBinding
import com.foodtrack.app.util.HebrewDateUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ListAdapter + DiffUtil (matching LogEntryAdapter / ActivityLogAdapter)
 * instead of a plain Adapter with notifyDataSetChanged(): this is what gives
 * a deleted row its own remove animation — including the one driven by
 * SwipeToDeleteCallback — rather than the whole list flashing and redrawing.
 */
class WeightHistoryAdapter(
    private val onDelete: (WeightEntryEntity) -> Unit
) : ListAdapter<WeightEntryEntity, WeightHistoryAdapter.Holder>(DIFF_CALLBACK) {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy • HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemWeightHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemWeightHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: WeightEntryEntity) {
            binding.textWeightValue.text = String.format(Locale.getDefault(), "%.1f ק״ג", item.weightKg)
            binding.textWeightDate.text = "${dateFormat.format(Date(item.timestamp))}  ·  ${HebrewDateUtil.format(item.timestamp)}"
            binding.buttonDeleteWeight.setOnClickListener { onDelete(item) }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<WeightEntryEntity>() {
            override fun areItemsTheSame(oldItem: WeightEntryEntity, newItem: WeightEntryEntity) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: WeightEntryEntity, newItem: WeightEntryEntity) = oldItem == newItem
        }
    }
}
