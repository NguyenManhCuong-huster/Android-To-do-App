package com.project.hustassistant.ui.tags

import android.annotation.SuppressLint
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView
import com.project.hustassistant.data.tag.Tag
import com.project.hustassistant.databinding.TagLayoutBinding

class TagsAdapter(
    private val onDeleteClick: (Tag) -> Unit
) : RecyclerView.Adapter<TagsAdapter.TagViewHolder>() {

    private var tags: List<Tag> = emptyList()

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(newTags: List<Tag>) {
        tags = newTags
        notifyDataSetChanged()
    }

    inner class TagViewHolder(val binding: TagLayoutBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(tag: Tag) {
            binding.tvTagName.text = tag.tagName

            try {
                val color = tag.colorHex.toColorInt()
                val background = binding.root.background as? GradientDrawable
                background?.mutate()

                // Set border màu theo tag
                val strokeWidth =
                    (2 * binding.root.context.resources.displayMetrics.density).toInt()
                background?.setStroke(strokeWidth, color)
            } catch (e: Exception) {
            }

            binding.btnDelete.setOnClickListener {
                onDeleteClick(tag)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TagViewHolder {
        val binding = TagLayoutBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TagViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TagViewHolder, position: Int) {
        holder.bind(tags[position])
    }

    override fun getItemCount() = tags.size
}