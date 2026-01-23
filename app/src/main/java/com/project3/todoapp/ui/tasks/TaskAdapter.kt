package com.project3.todoapp.ui.tasks

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView
import com.project3.todoapp.R
import com.project3.todoapp.data.task.Priority
import com.project3.todoapp.data.task.Task
import com.project3.todoapp.databinding.TaskLayoutBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TaskAdapter(
    private val onDeleteClick: (Task) -> Unit,
    private val onDetailClick: (Task) -> Unit,
    private val onCheckedChange: (Task, Boolean) -> Unit
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    private var tasks: List<Task> = emptyList()

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(newList: List<Task>) {
        tasks = newList
        notifyDataSetChanged()
    }

    inner class TaskViewHolder(val binding: TaskLayoutBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(task: Task) {
            // 1. Bind các thông tin cơ bản
            binding.tvTitle.text = task.title
            binding.tvDescription.text = task.description

            val sdf = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault())
            val startText = sdf.format(Date(task.start))
            val endText = sdf.format(Date(task.end))
            binding.tvTime.text =
                binding.root.context.getString(R.string.task_time_format, startText, endText)

            binding.checkbox.setOnCheckedChangeListener(null)
            binding.checkbox.isChecked = task.isCompleted
            binding.checkbox.setOnCheckedChangeListener { _, isChecked ->
                task.isCompleted = isChecked
                onCheckedChange(task, isChecked)
            }

            binding.linearTextGroup.setOnClickListener { onDetailClick(task) }
            binding.deleteButton.setOnClickListener { onDeleteClick(task) }

            // 2. Xử lý màu viền (Border) theo Priority
            val borderColor = when (task.priority) {
                Priority.LOW -> ContextCompat.getColor(binding.root.context, R.color.priority_low)
                Priority.MEDIUM -> ContextCompat.getColor(
                    binding.root.context,
                    R.color.priority_medium
                )

                Priority.HIGH -> ContextCompat.getColor(binding.root.context, R.color.priority_high)
                Priority.URGENT -> ContextCompat.getColor(
                    binding.root.context,
                    R.color.priority_urgent
                )
            }

            val background = binding.root.background as? GradientDrawable
            background?.mutate()
            background?.setStroke(6, borderColor)

            // 3. Xử lý hiển thị Tag
            binding.tagsContainer.removeAllViews()

            if (task.tags.isNotEmpty()) {
                binding.tagsContainer.visibility = View.VISIBLE
                val inflater = LayoutInflater.from(binding.root.context)

                for (tag in task.tags) {
                    val tagItemView = inflater.inflate(
                        R.layout.item_tag,
                        binding.tagsContainer,
                        false
                    )

                    val tvTagName = tagItemView.findViewById<TextView>(R.id.tvTagName)
                    tvTagName.text = tag.tagName

                    // --- Xử lý màu sắc từ DB ---
                    try {
                        val color = tag.colorHex.toColorInt()

                        val background = tvTagName.background as? GradientDrawable
                        background?.mutate()
                        background?.setColor(color)

                        if (ColorUtils.calculateLuminance(color) < 0.5) {
                            tvTagName.setTextColor(Color.WHITE)
                        } else {
                            tvTagName.setTextColor(Color.BLACK)
                        }

                    } catch (e: Exception) {
                        tvTagName.setTextColor(Color.BLACK)
                    }

                    binding.tagsContainer.addView(tagItemView)
                }
            } else {
                binding.tagsContainer.visibility = View.GONE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = TaskLayoutBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TaskViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(tasks[position])
    }

    override fun getItemCount(): Int = tasks.size
}