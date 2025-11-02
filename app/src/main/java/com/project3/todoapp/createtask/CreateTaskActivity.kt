package com.project3.todoapp.createtask

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.project3.todoapp.Repository
import com.project3.todoapp.databinding.ActivityCreateTaskBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CreateTaskActivity : AppCompatActivity() {

    private lateinit var viewModel: CreateTaskViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityCreateTaskBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get repository
        val repository = Repository.provideRepository(this)
        viewModel = CreateTaskViewModel(repository)

        // Bind to activity_create_task.xml
        val titleInput = binding.etTitle
        val descriptionInput = binding.etDescription
        val startInput = binding.etStart
        var startTimeInMillis = 0L
        val endInput = binding.etEnd
        var endTimeInMillis = 0L

        lifecycleScope.launch {
            startTimeInMillis = System.currentTimeMillis()
            endTimeInMillis = startTimeInMillis + 1
            val sdf = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault())
            startInput.setText(sdf.format(Date(startTimeInMillis)))
            endInput.setText(sdf.format(Date(endTimeInMillis)))
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

        // Create task
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

            viewModel.createTask(title, description, start, end)
            viewModel.errorMessage.observe(this) { message ->
                if (message != null) {
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                }
            }

            viewModel.taskCreated.observe(this) { success ->
                if (success == true) finish() // go TasksActivity
            }
        }

        // Cancel create task
        binding.btnCancel.setOnClickListener {
            finish()
        }
    }
}