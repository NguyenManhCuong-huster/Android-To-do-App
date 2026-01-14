package com.project3.todoapp.taskdetail

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.project3.todoapp.R
import com.project3.todoapp.TodoApplication
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
            repository = appContainer.taskRepository,
            taskNotificationManager = appContainer.notificationManager
        )
    }
    private lateinit var binding: ActivityTaskDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTaskDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val taskId = intent.getStringExtra("TASK_ID") ?: return

        val titleInput = binding.etTitle
        val descriptionInput = binding.etDescription
        val startInput = binding.etStart
        var startTimeInMillis = 0L
        val endInput = binding.etEnd
        var endTimeInMillis = 0L

        lifecycleScope.launch {
            val task = viewModel.getTask(taskId)
            if (task != null) {
                titleInput.setText(task.title)
                descriptionInput.setText(task.description)

                startTimeInMillis = task.start
                endTimeInMillis = task.end
                val sdf = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault())
                startInput.setText(sdf.format(Date(startTimeInMillis)))
                endInput.setText(sdf.format(Date(endTimeInMillis)))
            } else {
                Toast.makeText(applicationContext, "message", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        // Pick tart time
        startInput.setOnClickListener {
            pickDateTime(startInput) { startTimeInMillis = it }
        }

        // Pick end time
        endInput.setOnClickListener {
            pickDateTime(endInput) { endTimeInMillis = it }
        }

        // Save edit
        viewModel.errorMessage.observe(this) { message ->
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.taskUpdated.observe(this) { success ->
            if (success == true) finish()
        }

        // Save task
        binding.btnSave.setOnClickListener {
            val title = binding.etTitle.text.toString()
            val description = binding.etDescription.text.toString()
            val start = startTimeInMillis
            val end = endTimeInMillis

            if (validateInput(title, description, start, end)) {
                viewModel.updateTask(taskId, title, description, start, end)
            }
        }

        // Cancel edit task
        binding.btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun pickDateTime(targetInput: EditText, onTimePicked: (Long) -> Unit) {
        val now = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, y, m, d ->
                TimePickerDialog(this, { _, h, min ->
                    val cal = Calendar.getInstance()
                    cal.set(y, m, d, h, min)
                    val millis = cal.timeInMillis
                    onTimePicked(millis)
                    targetInput.setText(
                        SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault()).format(cal.time)
                    )
                }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show()
            },
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH),
            now.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun validateInput(
        title: String,
        description: String,
        start: Long,
        end: Long
    ): Boolean {
        var isValid = true
        if (title.isEmpty()) {
            binding.etTitle.error = getString(R.string.notifi_null_title)
            isValid = false
        }
        if (description.isEmpty()) {
            binding.etDescription.error = getString(R.string.notifi_null_description)
            isValid = false
        }
        if (start == 0L) {
            binding.etStart.error = getString(R.string.notifi_null_start_time)
            isValid = false
        }
        if (end == 0L) {
            binding.etEnd.error = getString(R.string.notifi_null_end_time)
            isValid = false
        }
        return isValid
    }
}