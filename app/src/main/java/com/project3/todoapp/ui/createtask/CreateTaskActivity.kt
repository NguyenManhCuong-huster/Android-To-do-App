package com.project3.todoapp.ui.createtask

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
import com.project3.todoapp.R
import com.project3.todoapp.TodoApplication
import com.project3.todoapp.data.tag.Tag
import com.project3.todoapp.data.task.Priority
import com.project3.todoapp.databinding.ActivityCreateTaskBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CreateTaskActivity : AppCompatActivity() {

    // Khởi tạo ViewModel với Factory đầy đủ các Repository
    private val viewModel: CreateTaskViewModel by viewModels {
        val appContainer = (application as TodoApplication).container
        CreateTaskViewModel.provideFactory(
            taskRepository = appContainer.taskRepository,
            tagRepository = appContainer.tagRepository,
            taskTagRepository = appContainer.taskTagRepository,
            taskNotificationManager = appContainer.notificationManager
        )
    }

    private lateinit var binding: ActivityCreateTaskBinding

    // Biến lưu thời gian (để gửi lên ViewModel dạng Long)
    private var startTimeInMillis: Long = 0L
    private var endTimeInMillis: Long = 0L

    // Biến quản lý Tag
    private var availableTags: List<Tag> = emptyList() // Danh sách tất cả Tag từ DB
    private val selectedTagIds = mutableListOf<String>() // Danh sách ID các Tag đang chọn

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateTaskBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Cài đặt ban đầu
        setupDateTimePickers()

        // 2. Quan sát dữ liệu từ ViewModel
        // Lấy danh sách Tag để hiển thị vào Dialog chọn
        viewModel.allTags.observe(this) { tags ->
            availableTags = tags
        }

        // Quan sát thông báo lỗi
        viewModel.errorMessage.observe(this) { message ->
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                binding.btnSave.isEnabled = true
            }
        }

        // Quan sát trạng thái tạo thành công -> Đóng Activity
        viewModel.taskCreated.observe(this) { success ->
            if (success == true) finish()
        }

        // 3. Xử lý các sự kiện Click
        binding.btnAddTag.setOnClickListener {
            showTagSelectionDialog()
        }

        binding.btnSave.setOnClickListener {
            saveTask()
        }

        binding.btnCancel.setOnClickListener {
            finish()
        }
    }

    // --- LOGIC NGÀY GIỜ ---
    private fun setupDateTimePickers() {
        val now = Calendar.getInstance()
        startTimeInMillis = now.timeInMillis
        // Mặc định kết thúc sau 1 tiếng
        endTimeInMillis = startTimeInMillis + (60 * 60 * 1000)

        val sdf = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault())
        binding.etStart.setText(sdf.format(Date(startTimeInMillis)))
        binding.etEnd.setText(sdf.format(Date(endTimeInMillis)))

        binding.etStart.setOnClickListener {
            pickDateTime(binding.etStart) { time -> startTimeInMillis = time }
        }

        binding.etEnd.setOnClickListener {
            pickDateTime(binding.etEnd) { time -> endTimeInMillis = time }
        }
    }

    private fun pickDateTime(targetInput: EditText, onTimePicked: (Long) -> Unit) {
        val currentCal = Calendar.getInstance()
        if (targetInput.text.isNotEmpty()) {
            // Cố gắng set lịch về thời gian đang hiển thị (nếu có)
            // Nếu parse lỗi thì dùng thời gian hiện tại
        }

        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                TimePickerDialog(
                    this,
                    { _, hourOfDay, minute ->
                        val pickedCal = Calendar.getInstance()
                        pickedCal.set(year, month, dayOfMonth, hourOfDay, minute)

                        val timeInMillis = pickedCal.timeInMillis
                        onTimePicked(timeInMillis)

                        val sdf = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault())
                        targetInput.setText(sdf.format(pickedCal.time))
                    },
                    currentCal.get(Calendar.HOUR_OF_DAY),
                    currentCal.get(Calendar.MINUTE),
                    true
                ).show()
            },
            currentCal.get(Calendar.YEAR),
            currentCal.get(Calendar.MONTH),
            currentCal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    // --- LOGIC TAG ---
    private fun showTagSelectionDialog() {
        if (availableTags.isEmpty()) {
            Toast.makeText(
                this,
                "No tags available. Please create tags in Home screen.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Mảng tên Tag để hiển thị
        val tagNames = availableTags.map { it.tagName }.toTypedArray()

        // Mảng trạng thái checked tương ứng
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

    private fun updateSelectedTagsUI() {
        binding.selectedTagsContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)

        // Lấy danh sách object Tag từ các ID đã chọn
        val selectedTags = availableTags.filter { selectedTagIds.contains(it.id) }

        for (tag in selectedTags) {
            // Sử dụng layout tag_layout.xml (trước là item_manage_tag.xml)
            val tagView =
                inflater.inflate(R.layout.tag_layout, binding.selectedTagsContainer, false)

            val tvName = tagView.findViewById<TextView>(R.id.tvTagName)
            val btnDelete = tagView.findViewById<View>(R.id.btnDelete)
            val viewColor = tagView.findViewById<View>(R.id.viewColor)

            tvName.text = tag.tagName

            // Xử lý màu sắc (Chấm tròn màu)
            try {
                val color = tag.colorHex.toColorInt()
                val background = viewColor.background as? GradientDrawable
                background?.mutate()
                background?.setColor(color)
            } catch (e: Exception) {
                // Màu lỗi thì giữ nguyên mặc định xml
            }

            // Xử lý nút xóa trực tiếp trên Tag
            btnDelete.setOnClickListener {
                selectedTagIds.remove(tag.id)
                updateSelectedTagsUI() // Render lại UI
            }

            binding.selectedTagsContainer.addView(tagView)
        }
    }

    // --- LOGIC LƯU TASK ---
    private fun saveTask() {
        val title = binding.etTitle.text.toString()
        val description = binding.etDescription.text.toString()
        val location = binding.etLocation.text.toString()

        val priority = when (binding.rgPriority.checkedRadioButtonId) {
            R.id.rbLow -> Priority.LOW
            R.id.rbHigh -> Priority.HIGH
            R.id.rbUrgent -> Priority.URGENT
            else -> Priority.MEDIUM
        }

        if (validateInput(title, description)) {
            binding.btnSave.isEnabled = false // Chặn click liên tục

            viewModel.createTask(
                title = title,
                description = description,
                start = startTimeInMillis,
                end = endTimeInMillis,
                tagIds = selectedTagIds,
                location = location,
                priority = priority // [TRUYỀN PRIORITY]
            )
        }
    }

    private fun validateInput(title: String, description: String): Boolean {
        var isValid = true
        if (title.isEmpty()) {
            binding.etTitle.error = getString(R.string.notifi_null_title)
            isValid = false
        }
        if (description.isEmpty()) {
            binding.etDescription.error = getString(R.string.notifi_null_description)
            isValid = false
        }
        if (startTimeInMillis == 0L || endTimeInMillis == 0L) {
            Toast.makeText(this, "Please select valid time", Toast.LENGTH_SHORT).show()
            isValid = false
        }
        return isValid
    }
}