package com.project3.todoapp.ui.profile

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.project3.todoapp.ui.common.applyWindowInsets
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.project3.todoapp.TodoApplication
import com.project3.todoapp.data.userinfo.UserInfo
import com.project3.todoapp.databinding.ActivityProfileBinding
import kotlinx.coroutines.launch

/**
 * ProfileActivity — observe 2 stream:
 *   - viewModel.userInfo : UserInfo? (data, từ Room)
 *   - viewModel.state    : UiState   (loading flags, toast, error)
 *
 * THAY ĐỔI:
 *  - populateFields nhận UserInfo (external model) thay vì UserInfoDto.
 *  - Email lấy từ userInfo.email (đã merge từ /me ở repository).
 *  - cancelEdit() không cần restore field thủ công nữa — Flow tự đẩy data
 *    cũ về (vì local chưa thay đổi cho tới khi user bấm Save).
 */
class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding

    private val viewModel: ProfileViewModel by viewModels {
        val container = (application as TodoApplication).container
        ProfileViewModel.provideFactory(container.userInfoRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets(binding.root, binding.appBar)

        setupListeners()
        observeData()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.swipeRefresh.setOnRefreshListener { viewModel.sync() }

        binding.btnLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Đăng xuất")
                .setMessage("Bạn có chắc muốn đăng xuất không?")
                .setPositiveButton("Đăng xuất") { _, _ ->
                    val container = (application as TodoApplication).container
                    container.authManager.signOut {
                        // Wipe local userinfo cache khi logout
                        lifecycleScope.launch {
                            container.userInfoRepository.clearLocal()
                        }
                        Toast.makeText(this, "Đã đăng xuất", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
                .setNegativeButton("Huỷ", null)
                .show()
        }

        binding.btnEdit.setOnClickListener { viewModel.startEdit() }

        binding.btnCancel.setOnClickListener {
            viewModel.cancelEdit()
            // Restore từ UserInfo đang trong StateFlow (= bản local hiện tại,
            // chưa bị thay đổi vì user chưa Save).
            viewModel.userInfo.value?.let { populateFields(it) }
        }

        binding.btnSave.setOnClickListener {
            viewModel.save(
                studentId = binding.etStudentId.text.toString(),
                fullName = binding.etFullName.text.toString(),
                dateOfBirth = binding.etDateOfBirth.text.toString(),
                phone = binding.etPhone.text.toString(),
                school = binding.etSchool.text.toString(),
                major = binding.etMajor.text.toString(),
                className = binding.etClassName.text.toString(),
                course = binding.etCourse.text.toString(),
            )
        }
    }

    private fun observeData() {
        // Stream 1: data từ Room
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.userInfo.collect { info ->
                    if (info != null) {
                        binding.tvEmail.text = info.email ?: ""
                        // Chỉ populate khi không đang edit — tránh ghi đè lên field user đang gõ
                        if (!viewModel.state.value.isEditing) {
                            populateFields(info)
                        }
                        binding.tvNoProfile.isVisible = false
                    } else {
                        // chưa từng load
                        binding.tvNoProfile.isVisible = !viewModel.state.value.isSyncing
                    }
                }
            }
        }

        // Stream 2: UI state (loading, error, edit mode, ...)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { s ->
                    binding.swipeRefresh.isRefreshing = s.isSyncing
                    binding.progressBar.isVisible = s.isSyncing && viewModel.userInfo.value == null

                    setEditMode(s.isEditing, s.isSaving)

                    s.toastMessage?.let {
                        Toast.makeText(this@ProfileActivity, it, Toast.LENGTH_SHORT).show()
                        viewModel.clearToast()
                    }
                    s.errorMessage?.let {
                        Toast.makeText(this@ProfileActivity, it, Toast.LENGTH_LONG).show()
                        viewModel.clearError()
                    }
                }
            }
        }
    }

    /** Type đổi: UserInfoDto → UserInfo (external model). */
    private fun populateFields(info: UserInfo) {
        binding.etStudentId.setText(info.studentId ?: "")
        binding.etFullName.setText(info.fullName ?: "")
        binding.etDateOfBirth.setText(info.dateOfBirth ?: "")
        binding.etPhone.setText(info.phone ?: "")
        binding.etSchool.setText(info.school ?: "")
        binding.etMajor.setText(info.major ?: "")
        binding.etClassName.setText(info.className ?: "")
        binding.etCourse.setText(info.course ?: "")
    }

    private fun setEditMode(editing: Boolean, saving: Boolean = false) {
        val fields = listOf(
            binding.etStudentId, binding.etFullName, binding.etDateOfBirth,
            binding.etPhone, binding.etSchool, binding.etMajor,
            binding.etClassName, binding.etCourse,
        )
        fields.forEach { et ->
            et.isEnabled = editing
            et.alpha = if (editing) 1f else 0.75f
        }

        binding.btnEdit.isVisible = !editing
        binding.btnCancel.isVisible = editing
        binding.layoutSave.isVisible = editing

        binding.btnSave.isEnabled = !saving
        binding.progressSave.isVisible = saving
        binding.btnSave.text = if (saving) "" else "Lưu"
    }
}
