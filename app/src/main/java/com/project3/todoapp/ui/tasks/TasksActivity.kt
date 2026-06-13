package com.project3.todoapp.ui.tasks

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.widget.PopupMenu
import android.widget.SearchView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.project3.todoapp.R
import com.project3.todoapp.TodoApplication
import com.project3.todoapp.data.tag.Tag
import com.project3.todoapp.databinding.ActivityTasksBinding
import com.project3.todoapp.ui.createtask.CreateTaskActivity
import com.project3.todoapp.ui.tags.TagsActivity
import com.project3.todoapp.ui.taskdetail.TaskDetailActivity
import kotlinx.coroutines.launch

class TasksActivity : AppCompatActivity() {
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var tagFilterAdapter: TagFilterAdapter
    private lateinit var binding: ActivityTasksBinding

    private val viewModel: TasksViewModel by viewModels {
        val appContainer = (application as TodoApplication).container
        TasksViewModel.provideFactory(
            taskRepository = appContainer.taskRepository,
            tagRepository = appContainer.tagRepository, // Truyền thêm TagRepository
            notificationManager = appContainer.notificationManager
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as TodoApplication).container
        container.permissionManager.checkAndRequestPermissions(this)
        binding = ActivityTasksBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupAdapters()
        setupListeners()
        observeData()
    }

    private fun setupAdapters() {
        // 1. Task Adapter
        taskAdapter = TaskAdapter(
            onDeleteClick = { task -> viewModel.deleteTask(task.id) },
            onDetailClick = { task ->
                val intent = Intent(this, TaskDetailActivity::class.java)
                intent.putExtra("TASK_ID", task.id)
                startActivity(intent)
            },
            onCheckedChange = { task, isChecked ->
                viewModel.updateTaskCompletion(task.id, isChecked)
            }
        )
        binding.recyclerView.adapter = taskAdapter
        binding.recyclerView.layoutManager = LinearLayoutManager(this)

        // 2. Tag Filter Adapter (Thanh ngang)
        tagFilterAdapter = TagFilterAdapter { tagId ->
            viewModel.setSelectedTag(tagId)
            tagFilterAdapter.setSelectedTag(tagId) // Cập nhật UI highlight
        }
        binding.rvTagFilter.adapter = tagFilterAdapter
        // LayoutManager đã set trong XML là Horizontal rồi
    }

    private fun setupListeners() {
        // --- Search View ---
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                binding.searchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.setSearchQuery(newText ?: "")
                return true
            }
        })

        // --- Bottom Buttons ---
        binding.syncButton.setOnClickListener {
            Toast.makeText(this, getString(R.string.notifi_sync_starting), Toast.LENGTH_SHORT)
                .show()
            viewModel.refresh()
        }

        // Filter button bây giờ chứa cả Lọc Trạng Thái và Sắp Xếp
        binding.filterButton.setOnClickListener { showFilterAndSortMenu() }

        binding.addTaskButton.setOnClickListener {
            startActivity(Intent(this, CreateTaskActivity::class.java))
        }

        binding.tagsButton.setOnClickListener {
            startActivity(Intent(this, TagsActivity::class.java))
        }
    }

    private fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Quan sát danh sách Task đã lọc
                launch {
                    viewModel.filteredTasks.collect { list ->
                        taskAdapter.submitList(list)
                    }
                }

                // Quan sát danh sách Tag để đổ vào thanh lọc
                launch {
                    viewModel.tags.collect { tags ->
                        // Thêm 1 item giả "All" vào đầu danh sách
                        val filterList = mutableListOf<Tag>()
                        // Tạo tag giả cho nút "All"
                        filterList.add(Tag("ALL", "All", "#CCCCCC", 0L))
                        filterList.addAll(tags)
                        tagFilterAdapter.submitList(filterList)
                    }
                }
            }
        }
    }

    // Menu cải tiến: Bao gồm Filter Status và Sort Option
    private fun showFilterAndSortMenu() {
        val popup = PopupMenu(this, binding.filterButton)

        // --- Group: Status Filter ---
        val statusGroup = popup.menu.addSubMenu("Status Filter")
        statusGroup.add(Menu.NONE, 101, Menu.NONE, "All Tasks").setOnMenuItemClickListener {
            viewModel.setFilterStatus(Filter.ALL); true
        }
        statusGroup.add(Menu.NONE, 102, Menu.NONE, "Completed").setOnMenuItemClickListener {
            viewModel.setFilterStatus(Filter.COMPLETED); true
        }
        statusGroup.add(Menu.NONE, 103, Menu.NONE, "Pending").setOnMenuItemClickListener {
            viewModel.setFilterStatus(Filter.PENDING); true
        }

        // --- Group: Sort Option ---
        val sortGroup = popup.menu.addSubMenu("Sort By")
        sortGroup.add(Menu.NONE, 201, Menu.NONE, "Start Time (Asc)").setOnMenuItemClickListener {
            viewModel.setSortOption(SortOption.START_TIME_ASC); true
        }
        sortGroup.add(Menu.NONE, 202, Menu.NONE, "End Time (Asc)").setOnMenuItemClickListener {
            viewModel.setSortOption(SortOption.END_TIME_ASC); true
        }
        sortGroup.add(Menu.NONE, 203, Menu.NONE, "Priority (Highest)").setOnMenuItemClickListener {
            viewModel.setSortOption(SortOption.PRIORITY_DESC); true
        }
        sortGroup.add(Menu.NONE, 204, Menu.NONE, "Title (A-Z)").setOnMenuItemClickListener {
            viewModel.setSortOption(SortOption.TITLE_ASC); true
        }

        popup.show()
    }

}