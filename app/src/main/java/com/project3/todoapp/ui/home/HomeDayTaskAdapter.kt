package com.project3.todoapp.ui.home

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.project3.todoapp.R
import com.project3.todoapp.data.task.Priority
import com.project3.todoapp.data.task.Task
import com.project3.todoapp.databinding.ItemHomeDayTaskBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeDayTaskAdapter(
    private val onTaskClick: (Task) -> Unit
) : ListAdapter<Task, HomeDayTaskAdapter.TaskViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = ItemHomeDayTaskBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TaskViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) =
        holder.bind(getItem(position))

    inner class TaskViewHolder(private val b: ItemHomeDayTaskBinding) :
        RecyclerView.ViewHolder(b.root) {

        private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

        fun bind(task: Task) {
            b.tvTaskTitle.text = task.title
            b.tvTaskTime.text  = timeFmt.format(Date(task.start))

            // Priority color bar
            val color = when (task.priority) {
                Priority.LOW    -> ContextCompat.getColor(b.root.context, R.color.priority_low)
                Priority.MEDIUM -> ContextCompat.getColor(b.root.context, R.color.priority_medium)
                Priority.HIGH   -> ContextCompat.getColor(b.root.context, R.color.priority_high)
                Priority.URGENT -> ContextCompat.getColor(b.root.context, R.color.priority_urgent)
            }
            b.viewPriorityBar.setBackgroundColor(color)

            // Strikethrough if completed
            if (task.isCompleted) {
                b.tvTaskTitle.paintFlags = b.tvTaskTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                b.ivCompleted.visibility = View.VISIBLE
            } else {
                b.tvTaskTitle.paintFlags = b.tvTaskTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                b.ivCompleted.visibility = View.GONE
            }

            b.root.setOnClickListener { onTaskClick(task) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Task>() {
        override fun areItemsTheSame(o: Task, n: Task) = o.id == n.id
        override fun areContentsTheSame(o: Task, n: Task) = o == n
    }
}
