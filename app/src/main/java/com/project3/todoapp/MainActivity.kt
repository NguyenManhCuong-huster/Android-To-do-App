package com.project3.todoapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.project3.todoapp.notification.createNotificationChannel
import com.project3.todoapp.tasks.TasksActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createNotificationChannel(this)
        startActivity(Intent(this, TasksActivity::class.java))
        finish()
    }
}