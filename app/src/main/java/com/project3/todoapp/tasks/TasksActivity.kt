package com.project3.todoapp.tasks

import android.content.Intent
import android.os.Bundle
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.project3.todoapp.R
import com.project3.todoapp.Repository
import com.project3.todoapp.createtask.CreateTaskActivity
import com.project3.todoapp.databinding.ActivityTasksBinding
import com.project3.todoapp.taskdetail.TaskDetailActivity
import kotlinx.coroutines.launch

class TasksActivity : AppCompatActivity() {
    private lateinit var viewModel: TasksViewModel
    private lateinit var adapter: TaskAdapter
    private lateinit var binding: ActivityTasksBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTasksBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Data Source
        val repository = Repository.provideRepository(this)
        viewModel = TasksViewModel(repository)

        // Setup RecyclerView
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

        // Filter Button
        binding.filterButton.setOnClickListener {
            showFilterMenu()
        }

        // Add task Button
        binding.addTaskButton.setOnClickListener {
            val intent = Intent(this, CreateTaskActivity::class.java)
            startActivity(intent)
        }

        // Present data
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.filteredTasks.collect { list ->
                    adapter.submitList(list)
                }
            }
        }
    }

    private fun showFilterMenu() {
        val popup = PopupMenu(this, binding.filterButton)
        popup.menuInflater.inflate(R.menu.filter_menu, popup.menu)
        popup.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.menu_all -> viewModel.setFilter(Filter.ALL)
                R.id.menu_completed -> viewModel.setFilter(Filter.COMPLETED)
                R.id.menu_pending -> viewModel.setFilter(Filter.PENDING)
            }
            true
        }
        popup.show()
    }
}