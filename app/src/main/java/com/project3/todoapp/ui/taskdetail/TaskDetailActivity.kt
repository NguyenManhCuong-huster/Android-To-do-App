package com.project3.todoapp.ui.taskdetail

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import com.project3.todoapp.R
import com.project3.todoapp.TodoApplication
import com.project3.todoapp.data.tag.Tag
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

    // Biến lưu thời gian
    private var startTimeInMillis = 0L
    private var endTimeInMillis = 0L

    // Biến quản lý Tag
    private var availableTags: List<Tag> = emptyList()
    private val selectedTagIds = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTaskDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val taskId = intent.getStringExtra("TASK_ID") ?: return

        // 1. Quan sát danh sách Tag (để dùng cho Dialog)
        viewModel.allTags.observe(this) { tags ->
            availableTags = tags
        }

        // 2. Load dữ liệu Task lên UI
        lifecycleScope.launch {
            val task = viewModel.getTask(taskId)
            if (task != null) {
                // Text Fields
                binding.etTitle.setText(task.title)
                binding.etDescription.setText(task.description)
                binding.etLocation.setText(task.addressName ?: "")

                // Time
                startTimeInMillis = task.start
                endTimeInMillis = task.end
                val sdf = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault())
                binding.etStart.setText(sdf.format(Date(startTimeInMillis)))
                binding.etEnd.setText(sdf.format(Date(endTimeInMillis)))

                // Priority RadioButton
                val rbId = when (task.priority) {
                    Priority.LOW -> R.id.rbLow
                    Priority.HIGH -> R.id.rbHigh
                    Priority.URGENT -> R.id.rbUrgent
                    else -> R.id.rbMedium
                }
                binding.rgPriority.check(rbId)

                // Tags
                selectedTagIds.clear()
                selectedTagIds.addAll(task.tags.map { it.id })

                updateSelectedTagsUI(task.tags)
            } else {
                Toast.makeText(applicationContext, "Task not found", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        // 3. Setup sự kiện
        setupDateTimePickers()

        binding.btnAddTag.setOnClickListener {
            showTagSelectionDialog()
        }

        viewModel.errorMessage.observe(this) { message ->
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                binding.btnSave.isEnabled = true // Mở lại nút lưu nếu lỗi
            }
        }

        viewModel.taskUpdated.observe(this) { success ->
            if (success == true) finish()
        }

        binding.btnSave.setOnClickListener {
            saveTask(taskId)
        }

        binding.btnCancel.setOnClickListener {
            finish()
        }
    }

    // --- LOGIC UI & DIALOG ---

    private fun updateSelectedTagsUI(tagsToDisplay: List<Tag>? = null) {
        binding.selectedTagsContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)

        // Nếu truyền list cụ thể (lúc load ban đầu) thì dùng list đó
        // Nếu không (null) thì lọc từ availableTags dựa trên selectedTagIds
        val displayList = tagsToDisplay ?: availableTags.filter { selectedTagIds.contains(it.id) }

        for (tag in displayList) {
            val tagView =
                inflater.inflate(R.layout.tag_layout, binding.selectedTagsContainer, false)

            val tvName = tagView.findViewById<TextView>(R.id.tvTagName)
            val btnDelete = tagView.findViewById<View>(R.id.btnDelete)
            val viewColor = tagView.findViewById<View>(R.id.viewColor)

            tvName.text = tag.tagName

            try {
                val color = tag.colorHex.toColorInt()
                val background = viewColor.background as? GradientDrawable
                background?.mutate()
                background?.setColor(color)
            } catch (e: Exception) {
            }

            // Logic xóa tag khỏi list chọn
            btnDelete.setOnClickListener {
                selectedTagIds.remove(tag.id)
                updateSelectedTagsUI()
            }

            binding.selectedTagsContainer.addView(tagView)
        }
    }

    private fun showTagSelectionDialog() {
        if (availableTags.isEmpty()) {
            Toast.makeText(this, "No tags available.", Toast.LENGTH_SHORT).show()
            return
        }

        val tagNames = availableTags.map { it.tagName }.toTypedArray()
        val checkedItems = availableTags.map { selectedTagIds.contains(it.id) }.toBooleanArray()

        AlertDialog.Builder(this)
            .setTitle("Select Tags")
            .setMultiChoiceItems(tagNames, checkedItems) { _, which, isChecked ->
                val tagId = availableTags[which].id
                if (isChecked) {
                    if (!selectedTagIds.contains(tagId)) selectedTagIds.add(tagId)
                } else {
                    selectedTagIds.remove(tagId)
                }
            }
            .setPositiveButton("OK") { _, _ ->
                updateSelectedTagsUI()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // --- LOGIC TIME ---
    private fun setupDateTimePickers() {
        binding.etStart.setOnClickListener {
            pickDateTime(binding.etStart) { t ->
                startTimeInMillis = t
            }
        }
        binding.etEnd.setOnClickListener {
            pickDateTime(binding.etEnd) { t ->
                endTimeInMillis = t
            }
        }
    }

    private fun pickDateTime(targetInput: EditText, onTimePicked: (Long) -> Unit) {
        val now = Calendar.getInstance()
        // Nếu text đang có giá trị thì parse để set lịch (Optional)
        DatePickerDialog(this, { _, y, m, d ->
            TimePickerDialog(this, { _, h, min ->
                val cal = Calendar.getInstance()
                cal.set(y, m, d, h, min)
                targetInput.setText(
                    SimpleDateFormat(
                        "HH:mm dd/MM/yyyy",
                        Locale.getDefault()
                    ).format(cal.time)
                )
                onTimePicked(cal.timeInMillis)
            }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show()
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show()
    }

    // --- LOGIC SAVE ---
    private fun saveTask(taskId: String) {
        val title = binding.etTitle.text.toString()
        val description = binding.etDescription.text.toString()
        val location = binding.etLocation.text.toString()

        val priority = when (binding.rgPriority.checkedRadioButtonId) {
            R.id.rbLow -> Priority.LOW
            R.id.rbHigh -> Priority.HIGH
            R.id.rbUrgent -> Priority.URGENT
            else -> Priority.MEDIUM
        }

        if (validateInput(title, description, startTimeInMillis, endTimeInMillis)) {
            binding.btnSave.isEnabled = false // Chặn double click
            viewModel.updateTask(
                id = taskId,
                title = title,
                description = description,
                start = startTimeInMillis,
                end = endTimeInMillis,
                priority = priority,
                location = location,
                tagIds = selectedTagIds
            )
        }
    }

    private fun validateInput(title: String, description: String, start: Long, end: Long): Boolean {
        var isValid = true
        if (title.isEmpty()) {
            binding.etTitle.error = getString(R.string.notifi_null_title)
            isValid = false
        }
        if (description.isEmpty()) {
            binding.etDescription.error = getString(R.string.notifi_null_description)
            isValid = false
        }
        if (start == 0L || end == 0L) {
            Toast.makeText(this, "Please check start/end time", Toast.LENGTH_SHORT).show()
            isValid = false
        }
        return isValid
    }
}