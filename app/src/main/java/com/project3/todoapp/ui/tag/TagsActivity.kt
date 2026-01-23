package com.project3.todoapp.ui.tag

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.project3.todoapp.R
import com.project3.todoapp.TodoApplication
import com.project3.todoapp.databinding.ActivityTagsBinding
import kotlinx.coroutines.launch

class TagsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTagsBinding
    private val viewModel: TagsViewModel by viewModels {
        val appContainer = (application as TodoApplication).container
        TagsViewModel.provideFactory(appContainer.tagRepository)
    }
    private lateinit var adapter: TagsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTagsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        observeData()

        binding.fabAddTag.setOnClickListener {
            showAddTagDialog()
        }
    }

    private fun setupRecyclerView() {
        adapter = TagsAdapter { tag ->
            // Xác nhận xóa
            AlertDialog.Builder(this)
                .setTitle("Delete Tag")
                .setMessage("Are you sure you want to delete '${tag.tagName}'?")
                .setPositiveButton("Delete") { _, _ ->
                    viewModel.deleteTag(tag.id)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        binding.rvTags.adapter = adapter
        binding.rvTags.layoutManager = LinearLayoutManager(this)
    }

    private fun observeData() {
        lifecycleScope.launch {
            viewModel.tags.collect { tags ->
                adapter.submitList(tags)
            }
        }
    }

    private fun showAddTagDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_tag, null)
        val etTagName = dialogView.findViewById<EditText>(R.id.etTagName)
        val colorContainer = dialogView.findViewById<LinearLayout>(R.id.colorContainer)

        // 1. Tìm View Preview
        val viewPreviewColor = dialogView.findViewById<android.view.View>(R.id.viewPreviewColor)

        val colors = listOf(
            "#F44336", "#E91E63", "#9C27B0", "#673AB7",
            "#3F51B5", "#2196F3", "#03A9F4", "#00BCD4",
            "#009688", "#4CAF50", "#8BC34A", "#CDDC39",
            "#FFEB3B", "#FFC107", "#FF9800", "#FF5722",
            "#795548", "#9E9E9E", "#607D8B", "#000000"
        )

        var selectedColorHex = "#9E9E9E" // Màu mặc định

        // 2. Hàm cập nhật màu cho ô Preview (Tạo hình bo góc giống Tag thật)
        fun updatePreview(hex: String) {
            val shape = GradientDrawable()
            shape.shape = GradientDrawable.RECTANGLE
            shape.cornerRadius = 16f * resources.displayMetrics.density // Bo góc 16dp
            shape.setColor(hex.toColorInt())
            shape.setStroke(2, Color.LTGRAY) // Viền xám nhạt để nổi bật nếu chọn màu trắng
            viewPreviewColor.background = shape
        }

        // Cập nhật preview lần đầu tiên (màu mặc định)
        updatePreview(selectedColorHex)

        // Tạo danh sách màu
        for (hex in colors) {
            val colorView = android.view.View(this)
            val params = LinearLayout.LayoutParams(64, 64) // Kích thước ô màu chọn
            params.setMargins(8, 8, 8, 8)
            colorView.layoutParams = params

            val shape = GradientDrawable()
            shape.shape = GradientDrawable.OVAL
            shape.setColor(hex.toColorInt())
            shape.setStroke(2, Color.LTGRAY)
            colorView.background = shape

            colorView.setOnClickListener {
                selectedColorHex = hex
                // 3. Gọi hàm cập nhật preview khi bấm chọn
                updatePreview(selectedColorHex)
            }
            colorContainer.addView(colorView)
        }

        AlertDialog.Builder(this)
            .setTitle("New Tag")
            .setView(dialogView)
            .setPositiveButton("Create") { _, _ ->
                val name = etTagName.text.toString().trim()
                if (name.isNotEmpty()) {
                    viewModel.createTag(name, selectedColorHex)
                } else {
                    Toast.makeText(this, "Tag name cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}