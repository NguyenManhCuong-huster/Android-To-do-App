package com.project3.todoapp.taskdetail

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.project3.todoapp.Repository
import com.project3.todoapp.databinding.ActivityTaskDetailBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TaskDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTaskDetailBinding

    private lateinit var viewModel: TaskDetailViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTaskDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val taskId = intent.getStringExtra("TASK_ID") ?: return

        // --- Lấy repository dùng chung ---
        val repository = Repository.provideRepository(this)
        viewModel = TaskDetailViewModel(repository)

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

        // Pick start time
        startInput.setOnClickListener {
            val now = Calendar.getInstance()
            DatePickerDialog(
                this,
                { _, y, m, d ->
                    TimePickerDialog(this, { _, h, min ->
                        val cal = Calendar.getInstance()
                        cal.set(y, m, d, h, min)
                        startTimeInMillis = cal.timeInMillis
                        startInput.setText(
                            SimpleDateFormat(
                                "HH:mm dd/MM/yyyy",
                                Locale.getDefault()
                            ).format(cal.time)
                        )
                    }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show()
                },
                now.get(Calendar.YEAR),
                now.get(Calendar.MONTH),
                now.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        // Pick end time
        endInput.setOnClickListener {
            val now = Calendar.getInstance()
            DatePickerDialog(
                this,
                { _, y, m, d ->
                    TimePickerDialog(this, { _, h, min ->
                        val cal = Calendar.getInstance()
                        cal.set(y, m, d, h, min)
                        endTimeInMillis = cal.timeInMillis
                        endInput.setText(
                            SimpleDateFormat(
                                "HH:mm dd/MM/yyyy",
                                Locale.getDefault()
                            ).format(cal.time)
                        )
                    }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show()
                },
                now.get(Calendar.YEAR),
                now.get(Calendar.MONTH),
                now.get(Calendar.DAY_OF_MONTH)
            ).show()
        }


        // Save edit
        binding.btnSave.setOnClickListener {
            val title = titleInput.text.toString()
            val description = descriptionInput.text.toString()
            val start = startTimeInMillis
            val end = endTimeInMillis

            if (start == 0L) {
                startInput.error = "Enter start time, please!"
                return@setOnClickListener
            }

            if (end == 0L) {
                endInput.error = "Enter end time, please!"
                return@setOnClickListener
            }

            if (title.isEmpty()) {
                titleInput.error = "Enter title, please!"
                return@setOnClickListener
            }

            if (description.isEmpty()) {
                descriptionInput.error = "Enter Description, please!"
                return@setOnClickListener
            }

            viewModel.updateTask(taskId, title, description, start, end)
            viewModel.errorMessage.observe(this) { message ->
                if (message != null) {
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                }
            }

            viewModel.taskUpdated.observe(this) { success ->
                if (success == true) finish() // go TasksActivity
            }
        }

        // Cancel edit task
        binding.btnCancel.setOnClickListener {
            finish()
        }
    }
}