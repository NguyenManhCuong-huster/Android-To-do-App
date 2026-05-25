package com.project3.todoapp.ui.tasks

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.project3.todoapp.data.tag.Tag
import com.project3.todoapp.databinding.ItemTagFilterBinding

class TagFilterAdapter(
    private val onTagSelected: (String?) -> Unit // Null = Chọn "All"
) : ListAdapter<Tag, TagFilterAdapter.ViewHolder>(DiffCallback()) {

    // Lưu ID của tag đang được chọn (null là chọn All)
    private var selectedTagId: String? = null

    @SuppressLint("NotifyDataSetChanged")
    fun setSelectedTag(tagId: String?) {
        selectedTagId = tagId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTagFilterBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemTagFilterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(tag: Tag) {
            val isSelected = (tag.id == selectedTagId) || (selectedTagId == null && tag.id == "ALL")

            // Nếu là item giả "All"
            if (tag.id == "ALL") {
                binding.tvTagName.text = "All"
                setupAppearance(isSelected, "#CCCCCC") // Màu xám cho nút All
                binding.root.setOnClickListener { onTagSelected(null) }
            } else {
                binding.tvTagName.text = tag.tagName
                setupAppearance(isSelected, tag.colorHex)
                binding.root.setOnClickListener { onTagSelected(tag.id) }
            }
        }

        private fun setupAppearance(isSelected: Boolean, colorHex: String) {
            val background = binding.tvTagName.background as GradientDrawable
            background.mutate()

            try {
                val color = colorHex.toColorInt()
                if (isSelected) {
                    // Nếu chọn: Nền màu đậm, Chữ trắng
                    background.setColor(color)
                    background.setStroke(0, 0)
                    binding.tvTagName.setTextColor(Color.WHITE)
                } else {
                    // Nếu không chọn: Nền trắng, Viền màu, Chữ màu (hoặc đen)
                    background.setColor(Color.WHITE)
                    val strokeWidth =
                        (2 * binding.root.context.resources.displayMetrics.density).toInt()
                    background.setStroke(strokeWidth, color)
                    binding.tvTagName.setTextColor(Color.BLACK)
                }
            } catch (e: Exception) {
                // Fallback safe color
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Tag>() {
        override fun areItemsTheSame(oldItem: Tag, newItem: Tag) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Tag, newItem: Tag) = oldItem == newItem
    }
}