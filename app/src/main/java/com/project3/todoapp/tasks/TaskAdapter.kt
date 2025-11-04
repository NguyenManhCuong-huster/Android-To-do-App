package com.project3.todoapp.tasks

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.project3.todoapp.R
import com.project3.todoapp.data.Task
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

    fun submitList(newList: List<Task>) {
        tasks = newList
        notifyDataSetChanged()
    }

    /**
     * ViewHolder dùng để hiển thị thông tin của một task trên layout.
     *
     * @param binding Đối tượng ViewBinding được sinh ra từ file layout `task_layout.xml`.
     *                 Dùng để truy cập trực tiếp các view (như TextView, Button) trong layout.
     */
    inner class TaskViewHolder(val binding: TaskLayoutBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(task: Task) {
            binding.tvTitle.text = task.title
            binding.tvDescription.text = task.description

            //TextView Time
            val sdf = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault())
            val startText = sdf.format(Date(task.start))
            val endText = sdf.format(Date(task.end))
            binding.tvTime.text =
                binding.root.context.getString(R.string.task_time_format, startText, endText)

            // Delete Button
            binding.deleteButton.setOnClickListener {
                onDeleteClick(task)
            }

            // Edit Button
            /*binding.editButton.setOnClickListener {
                onDetailClick(task)
            }*/

            // Task preview
            binding.linearTextGroup.setOnClickListener {
                onDetailClick(task)
            }

            // Checkbox isCompleted
            binding.checkbox.setOnCheckedChangeListener(null)
            binding.checkbox.isChecked = task.isCompleted
            binding.checkbox.setOnCheckedChangeListener { _, isChecked ->
                task.isCompleted = isChecked
                onCheckedChange(task, isChecked)
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

    override fun onBindViewHolder(
        holder: TaskViewHolder,
        position: Int
    ) {
        holder.bind(tasks[position])
    }

    override fun getItemCount(): Int = tasks.size

}