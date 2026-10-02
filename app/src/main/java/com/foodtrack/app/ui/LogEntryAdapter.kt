package com.foodtrack.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.foodtrack.app.data.LogEntryEntity
import com.foodtrack.app.databinding.ItemLogEntryBinding
import java.util.Locale

class LogEntryAdapter(
    private val onDeleteClick: (LogEntryEntity) -> Unit
) : ListAdapter<LogEntryEntity, LogEntryAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLogEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onDeleteClick)
    }

    class ViewHolder(private val binding: ItemLogEntryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: LogEntryEntity, onDeleteClick: (LogEntryEntity) -> Unit) {
            binding.textEntryName.text = entry.foodNameHe
            val meal = entry.mealType?.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""
            binding.textEntryDetails.text = String.format(
                Locale.getDefault(),
                "%.0f גרם  •  %.0f קק\"ל%s",
                entry.grams,
                entry.caloriesKcal,
                meal
            )
            binding.buttonDelete.setOnClickListener { onDeleteClick(entry) }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<LogEntryEntity>() {
            override fun areItemsTheSame(oldItem: LogEntryEntity, newItem: LogEntryEntity) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: LogEntryEntity, newItem: LogEntryEntity) =
                oldItem == newItem
        }
    }
}
