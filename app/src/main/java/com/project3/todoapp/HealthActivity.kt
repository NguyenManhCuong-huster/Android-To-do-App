package com.project3.todoapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.project3.todoapp.databinding.ActivityHealthBinding

class HealthActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHealthBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHealthBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}