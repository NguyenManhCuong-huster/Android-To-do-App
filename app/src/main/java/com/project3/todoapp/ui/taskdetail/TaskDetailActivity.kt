package com.project3.todoapp.ui.taskdetail

import com.project3.todoapp.ui.common.applyWindowInsets
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.project3.todoapp.R
import com.project3.todoapp.TodoApplication
import com.project3.todoapp.data.task.Priority
import com.project3.todoapp.databinding.ActivityTaskDetailBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TaskDetailActivity : AppCompatActivity() {
    private val viewModel: TaskDetailViewModel by viewModels {
        val appContainer = (application as TodoApplication).container
        TaskDetailViewModel.provideFactory(
            taskRepository = appContainer.taskRepository,
            tagRepository = appContainer.tagRepository,
            taskTagRepository = appContainer.taskTagRepository,
            taskNotificationManager = appContainer.notificationManager
        )
    }

    private lateinit var binding: ActivityTaskDetailBinding
    private lateinit var tagsAdapter: SelectedTagsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTaskDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets(binding.root, binding.appBar)

        val taskId = intent.getStringExtra("TASK_ID") ?: run {
            Toast.makeText(this, "Task ID missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupUI()
        setupListeners()

        // Gọi loadTask. ViewModel tự quyết định có load lại từ DB hay dùng cache
        viewModel.loadTask(taskId)

        observeViewModel()
    }

    private fun setupUI() {
        // --- Setup Tags RecyclerView ---
        tagsAdapter = SelectedTagsAdapter { tagToDelete ->
            viewModel.removeTag(tagToDelete.id)
        }
        val flexboxLayoutManager = FlexboxLayoutManager(this).apply {
            flexDirection = FlexDirection.ROW
            flexWrap = FlexWrap.WRAP
            justifyContent = JustifyContent.FLEX_START
        }
        binding.rvSelectedTags.apply {
            layoutManager = flexboxLayoutManager
            adapter = tagsAdapter
        }
    }

    private fun setupListeners() {
        // --- TEXT WATCHERS (Cập nhật ViewModel khi gõ phím) ---
        binding.etTitle.doAfterTextChanged {
            viewModel.setTitle(it.toString())
        }
        binding.etDescription.doAfterTextChanged {
            viewModel.setDescription(it.toString())
        }
        binding.etLocation.doAfterTextChanged {
            viewModel.setLocation(it.toString())
        }

        // --- BUTTON CLICKS ---
        binding.btnSave.setOnClickListener {
            viewModel.saveTask()
        }

        binding.btnCancel.setOnClickListener {
            finish()
        }

        binding.btnAddTag.setOnClickListener {
            showTagSelectionDialog()
        }

        // --- TIME PICKERS ---
        binding.etStart.setOnClickListener {
            val currentVal = viewModel.startTime.value
            pickDateTime(currentVal) { newTime ->
                viewModel.setStartTime(newTime)
            }
        }

        binding.etEnd.setOnClickListener {
            val currentVal = viewModel.endTime.value
            pickDateTime(currentVal) { newTime ->
                viewModel.setEndTime(newTime)
            }
        }

        // --- PRIORITY ---
        binding.rgPriority.setOnCheckedChangeListener { _, checkedId ->
            val priority = when (checkedId) {
                R.id.rbLow -> Priority.LOW
                R.id.rbHigh -> Priority.HIGH
                R.id.rbUrgent -> Priority.URGENT
                else -> Priority.MEDIUM
            }
            viewModel.setPriority(priority)
        }
    }

    private fun observeViewModel() {
        // 1. Tags List (Dùng LiveData cho tiện với ListAdapter)
        viewModel.selectedTagsList.observe(this) { tags ->
            tagsAdapter.submitList(tags)
        }

        // 2. Các StateFlow khác (Dùng coroutine)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.title.collect { title ->
                        if (binding.etTitle.text.toString() != title) {
                            binding.etTitle.setText(title)
                        }
                    }
                }
                launch {
                    viewModel.description.collect { desc ->
                        if (binding.etDescription.text.toString() != desc) {
                            binding.etDescription.setText(desc)
                        }
                    }
                }
                launch {
                    viewModel.location.collect { loc ->
                        if (binding.etLocation.text.toString() != loc) {
                            binding.etLocation.setText(loc)
                        }
                    }
                }
                launch {
                    viewModel.startTime.collect { time ->
                        if (time > 0) {
                            val sdf = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault())
                            binding.etStart.setText(sdf.format(Date(time)))
                        }
                    }
                }
                launch {
                    viewModel.endTime.collect { time ->
                        if (time > 0) {
                            val sdf = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault())
                            binding.etEnd.setText(sdf.format(Date(time)))
                        }
                    }
                }
                launch {
                    viewModel.priority.collect { priority ->
                        val rbId = when (priority) {
                            Priority.LOW -> R.id.rbLow
                            Priority.HIGH -> R.id.rbHigh
                            Priority.URGENT -> R.id.rbUrgent
                            else -> R.id.rbMedium
                        }
                        if (binding.rgPriority.checkedRadioButtonId != rbId) {
                            binding.rgPriority.check(rbId)
                        }
                    }
                }
                launch {
                    viewModel.errorMessage.collect { msg ->
                        if (msg != null) {
                            Toast.makeText(this@TaskDetailActivity, msg, Toast.LENGTH_SHORT).show()
                            viewModel.clearError()
                            binding.btnSave.isEnabled = true
                        }
                    }
                }
                launch {
                    viewModel.taskUpdated.collect { success ->
                        if (success) finish()
                    }
                }
            }
        }
    }

    private fun showTagSelectionDialog() {
        // Sử dụng StateFlow value trực tiếp vì nó luôn lưu giữ giá trị mới nhất
        val allTags = viewModel.allTags.value
        if (allTags.isEmpty()) {
            Toast.makeText(this, "No tags available.", Toast.LENGTH_SHORT).show()
            return
        }

        val tagNames = allTags.map { it.tagName }.toTypedArray()
        val currentIds = viewModel.currentTagIds.value
        val checkedItems = allTags.map { currentIds.contains(it.id) }.toBooleanArray()

        AlertDialog.Builder(this)
            .setTitle("Select Tags")
            .setMultiChoiceItems(tagNames, checkedItems) { _, which, isChecked ->
                val tagId = allTags[which].id
                viewModel.toggleTagSelection(tagId, isChecked)
            }
            .setPositiveButton("OK", null)
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickDateTime(initialTime: Long, onTimePicked: (Long) -> Unit) {
        val calendar = Calendar.getInstance()
        if (initialTime > 0) {
            calendar.timeInMillis = initialTime
        }

        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                TimePickerDialog(
                    this,
                    { _, hourOfDay, minute ->
                        val pickedCal = Calendar.getInstance()
                        pickedCal.set(year, month, dayOfMonth, hourOfDay, minute)
                        onTimePicked(pickedCal.timeInMillis)
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
}