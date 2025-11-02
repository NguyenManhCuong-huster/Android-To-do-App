package com.project3.todoapp.tasks

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.project3.todoapp.Repository
import com.project3.todoapp.createtask.CreateTaskActivity
import com.project3.todoapp.databinding.ActivityTasksBinding
import com.project3.todoapp.taskdetail.TaskDetailActivity
import kotlinx.coroutines.launch

class TasksActivity : AppCompatActivity() {
    private lateinit var viewModel: TasksViewModel
    private lateinit var adapter: TaskAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityTasksBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // --- Tạo database và repository ---
        val repository = Repository.provideRepository(this)
        viewModel = TasksViewModel(repository)

        // --- Setup RecyclerView ---
        val recyclerView = binding.recyclerView
        adapter = TaskAdapter(
            onDeleteClick = { task ->
                viewModel.deleteTask(task.id)
            },
            onDetailClick = { task ->
                val intent = Intent(this, TaskDetailActivity::class.java)
                intent.putExtra("TASK_ID", task.id)
                startActivity(intent)
            },
            onCheckedChange = { task, isChecked ->
                viewModel.updateTaskCompletion(task.id, isChecked)
            }
        )
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Khi bấm nút Add task
        binding.addTaskButton.setOnClickListener {
            val intent = Intent(this, CreateTaskActivity::class.java)
            startActivity(intent)
        }

        // --- Quan sát dữ liệu từ ViewModel ---
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.tasks.collect { list ->
                    adapter.submitList(list)
                }
            }
        }
    }
}