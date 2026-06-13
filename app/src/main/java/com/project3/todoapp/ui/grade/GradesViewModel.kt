package com.project3.todoapp.ui.grade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.project3.todoapp.data.grade.GpaCalculator
import com.project3.todoapp.data.grade.GpaCalculator.SemesterPoint
import com.project3.todoapp.data.grade.Grade
import com.project3.todoapp.data.grade.GradeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * GradesViewModel — quản lý kết quả học tập + tính GPA/CPA cho biểu đồ.
 *
 * Data đi thẳng từ Room qua repository (offline-first). Mọi giá trị thống kê
 * (chuỗi điểm theo kỳ, CPA tổng, số TC tích luỹ) được derive từ [grades].
 */
class GradesViewModel(
    private val repository: GradeRepository,
) : ViewModel() {

    /** Tổng hợp hiển thị ở phần header. */
    data class Summary(
        val overallCpa: Double?,
        val earnedCredits: Int,
        val courseCount: Int,
    )

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    /** Danh sách điểm real-time từ Room. */
    val grades: StateFlow<List<Grade>> = repository.getGradesStream()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Chuỗi điểm theo từng học kỳ (cho biểu đồ đường). */
    val series: StateFlow<List<SemesterPoint>> = repository.getGradesStream()
        .map { GpaCalculator.computeSeries(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Tổng hợp CPA + tín chỉ. */
    val summary: StateFlow<Summary> = repository.getGradesStream()
        .map {
            Summary(
                overallCpa = GpaCalculator.overallCpa(it),
                earnedCredits = GpaCalculator.earnedCredits(it),
                courseCount = it.size,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Summary(null, 0, 0))

    // ─── CRUD ─────────────────────────────────────────────
    fun createGrade(
        semester: String,
        courseCode: String,
        courseName: String,
        courseNameEn: String,
        credits: Int,
        letterGrade: String,
    ) {
        viewModelScope.launch {
            repository.createGrade(semester, courseCode, courseName, courseNameEn, credits, letterGrade)
            _toast.value = "Đã thêm $courseCode"
        }
    }

    fun updateGrade(
        id: String,
        semester: String,
        courseCode: String,
        courseName: String,
        courseNameEn: String,
        credits: Int,
        letterGrade: String,
    ) {
        viewModelScope.launch {
            repository.updateGrade(id, semester, courseCode, courseName, courseNameEn, credits, letterGrade)
            _toast.value = "Đã cập nhật $courseCode"
        }
    }

    fun deleteGrade(id: String) {
        viewModelScope.launch { repository.deleteGrade(id) }
    }

    fun sync() {
        viewModelScope.launch {
            runCatching { repository.sync() }
                .onFailure { _toast.value = "Đồng bộ thất bại" }
        }
    }

    /** Kiểm tra trùng mã HP trong cùng học kỳ (chặn dup khi thêm offline). */
    fun isDuplicate(semester: String, courseCode: String, excludeId: String?): Boolean =
        grades.value.any {
            it.id != excludeId &&
                it.semester.equals(semester.trim(), ignoreCase = true) &&
                it.courseCode.equals(courseCode.trim(), ignoreCase = true)
        }

    fun clearToast() { _toast.value = null }

    companion object {
        fun provideFactory(repository: GradeRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    GradesViewModel(repository) as T
            }
    }
}
