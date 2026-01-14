package com.project3.todoapp.tasks

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.project3.todoapp.R
import com.project3.todoapp.TodoApplication
import com.project3.todoapp.createtask.CreateTaskActivity
import com.project3.todoapp.databinding.ActivityTasksBinding
import com.project3.todoapp.taskdetail.TaskDetailActivity
import kotlinx.coroutines.launch

class TasksActivity : AppCompatActivity() {
    private lateinit var adapter: TaskAdapter
    private lateinit var binding: ActivityTasksBinding
    private val viewModel: TasksViewModel by viewModels {
        val appContainer = (application as TodoApplication).container
        TasksViewModel.provideFactory(
            repository = appContainer.taskRepository,
            notificationManager = appContainer.notificationManager
        )
    }

    // Bộ xử lý kết quả đăng nhập
    private val signInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                updateAuthButtonUI()
                viewModel.refresh() // Đăng nhập xong thì bắt đầu đồng bộ ngay
                Toast.makeText(this, getString(R.string.notifi_login_success), Toast.LENGTH_SHORT)
                    .show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as TodoApplication).container
        container.permissionManager.checkAndRequestPermissions(this)
        binding = ActivityTasksBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Cập nhật màu nút ngay khi mở app
        updateAuthButtonUI()

        // Setup RecyclerView
        adapter = TaskAdapter(
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
        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = LinearLayoutManager(this)

        // Sync Button
        binding.syncButton.setOnClickListener {
            Toast.makeText(this, getString(R.string.notifi_sync_starting), Toast.LENGTH_SHORT)
                .show()
            viewModel.refresh()
        }

        // Auth Button
        binding.authButton.setOnClickListener {
            showAuthMenu()
        }

        // Filter Button
        binding.filterButton.setOnClickListener {
            showFilterMenu()
        }

        // Add task Button
        binding.addTaskButton.setOnClickListener {
            val intent = Intent(this, CreateTaskActivity::class.java)
            startActivity(intent)
        }

        // Observe data
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.filteredTasks.collect { list ->
                    adapter.submitList(list)
                }
            }
        }
    }

    // Hàm cập nhật màu sắc nút tài khoản
    private fun updateAuthButtonUI() {
        val authManager = (application as TodoApplication).container.authManager
        val color = if (authManager.isUserLoggedIn()) {
            Color.GREEN // Màu xanh lá khi đã đăng nhập
        } else {
            // Lấy màu xanh dương mặc định từ resources
            getColor(R.color.light_blue_600)
        }
        binding.authButton.imageTintList = ColorStateList.valueOf(color)
    }

    // Hiển thị Menu Đăng nhập/Đăng xuất
    private fun showAuthMenu() {
        val popup = PopupMenu(this, binding.authButton)
        val authManager = (application as TodoApplication).container.authManager

        if (authManager.isUserLoggedIn()) {
            val account = authManager.getGoogleAccount()
            popup.menu.add(Menu.NONE, 1, 1, getString(R.string.logout, account?.email))
            popup.setOnMenuItemClickListener {
                authManager.signOut {
                    updateAuthButtonUI()
                    Toast.makeText(this, getString(R.string.notifi_logged_out), Toast.LENGTH_SHORT)
                        .show()
                }
                true
            }
        } else {
            popup.menu.add(Menu.NONE, 2, 2, getString(R.string.login))
            popup.setOnMenuItemClickListener {
                signInLauncher.launch(authManager.getSignInIntent())
                true
            }
        }
        popup.show()
    }

    // Hiển th bộ lọc
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