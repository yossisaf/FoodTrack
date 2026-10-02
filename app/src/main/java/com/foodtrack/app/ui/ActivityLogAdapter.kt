package com.foodtrack.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.foodtrack.app.data.ActivityLogEntity
import com.foodtrack.app.databinding.ItemActivityEntryBinding
import java.util.Locale

class ActivityLogAdapter(
    private val onDeleteClick: (ActivityLogEntity) -> Unit
) : ListAdapter<ActivityLogEntity, ActivityLogAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemActivityEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onDeleteClick)
    }

    class ViewHolder(private val binding: ItemActivityEntryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: ActivityLogEntity, onDeleteClick: (ActivityLogEntity) -> Unit) {
            binding.textActivityName.text = entry.activityName
            binding.textActivityDetails.text = String.format(
                Locale.getDefault(), "%.0f דק׳  •  %.0f קק״ל", entry.durationMinutes, entry.caloriesKcal
            )
            binding.buttonDeleteActivity.setOnClickListener { onDeleteClick(entry) }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ActivityLogEntity>() {
            override fun areItemsTheSame(oldItem: ActivityLogEntity, newItem: ActivityLogEntity) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: ActivityLogEntity, newItem: ActivityLogEntity) = oldItem == newItem
        }
    }
}
