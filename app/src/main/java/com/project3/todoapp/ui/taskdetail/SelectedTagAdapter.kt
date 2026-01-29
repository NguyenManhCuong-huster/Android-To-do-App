package com.project3.todoapp.ui.taskdetail

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.project3.todoapp.data.tag.Tag
import com.project3.todoapp.databinding.TagLayoutBinding

class SelectedTagsAdapter(
    private val onDeleteClick: (Tag) -> Unit
) : ListAdapter<Tag, SelectedTagsAdapter.TagViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TagViewHolder {
        val binding = TagLayoutBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TagViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TagViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TagViewHolder(private val binding: TagLayoutBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(tag: Tag) {
            binding.tvTagName.text = tag.tagName

            // --- XỬ LÝ MÀU VIỀN (BORDER) ---
            try {
                val color = tag.colorHex.toColorInt()
                // Lấy background shape và đổi màu stroke
                val background = binding.root.background as? GradientDrawable
                background?.mutate()
                // Set viền dày 2dp
                val strokeWidth =
                    (2 * binding.root.context.resources.displayMetrics.density).toInt()
                background?.setStroke(strokeWidth, color)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            binding.btnDelete.setOnClickListener {
                onDeleteClick(tag)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Tag>() {
        override fun areItemsTheSame(oldItem: Tag, newItem: Tag) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Tag, newItem: Tag) = oldItem == newItem
    }
}