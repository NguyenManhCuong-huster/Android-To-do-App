package com.project3.todoapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.project3.todoapp.tasks.TasksActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Gọi sang TasksActivity luôn
        val intent = Intent(this, TasksActivity::class.java)
        startActivity(intent)

        // Kết thúc MainActivity để không quay lại bằng nút Back
        finish()
    }
}

