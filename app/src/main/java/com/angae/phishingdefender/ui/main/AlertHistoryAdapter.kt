package com.angae.phishingdefender.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.angae.phishingdefender.databinding.ItemAlertHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AlertHistory(
    val sender: String = "",
    val body: String = "",
    val reason: String = "",
    val createdAt: Long = 0L
)

class AlertHistoryAdapter : RecyclerView.Adapter<AlertHistoryAdapter.ViewHolder>() {

    private var items: List<AlertHistory> = emptyList()

    fun submitList(newList: List<AlertHistory>) {
        items = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAlertHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(private val binding: ItemAlertHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        private val dateFormat = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault())

        fun bind(item: AlertHistory) {
            binding.tvHistorySender.text = item.sender
            binding.tvHistoryReason.text = item.reason
            binding.tvHistoryBody.text = item.body
            binding.tvHistoryDate.text = dateFormat.format(Date(item.createdAt))
        }
    }
}
