package com.project3.todoapp.ui.tag

import android.annotation.SuppressLint
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView
import com.project3.todoapp.data.tag.Tag
import com.project3.todoapp.databinding.TagLayoutBinding

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
                val background = binding.viewColor.background as? GradientDrawable
                background?.mutate()
                background?.setColor(color)
            } catch (e: Exception) {
                // Default color
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