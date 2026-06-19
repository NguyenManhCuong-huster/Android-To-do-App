package com.project.hustassistant.ui.grade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.project.hustassistant.data.grade.GpaCalculator
import com.project.hustassistant.data.grade.GpaCalculator.SemesterPoint
import com.project.hustassistant.data.grade.Grade
import com.project.hustassistant.data.grade.GradeRepository
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

    /** Tìm TẤT CẢ grades trùng mã học phần (bất kể kỳ), trả list rỗng nếu không có. */
    fun findAllDuplicates(courseCode: String, excludeId: String?): List<Grade> =
        grades.value.filter {
            it.id != excludeId &&
                it.courseCode.equals(courseCode.trim(), ignoreCase = true)
        }

    fun clearToast() { _toast.value = null }

    // ─── Cảnh báo học tập ────────────────────────────────────
    data class AcademicWarning(val level: Level, val message: String) {
        enum class Level { WARNING, DANGER }
    }

    val warnings: StateFlow<List<AcademicWarning>> = repository.getGradesStream()
        .map { grades -> computeWarnings(grades, GpaCalculator.computeSeries(grades)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun computeWarnings(
        grades: List<Grade>,
        series: List<GpaCalculator.SemesterPoint>,
    ): List<AcademicWarning> {
        if (grades.isEmpty()) return emptyList()
        val result = mutableListOf<AcademicWarning>()
        val deduped = GpaCalculator.deduplicateByBestGrade(grades)

        // ── Mức cảnh báo học tập chính thức (Quy chế HUST Điều 16) ──────
        val status = GpaCalculator.computeWarningStatus(grades)

        if (status.level > 0) {
            val msg = when (status.level) {
                1    -> "Cảnh báo học tập mức 1"
                2    -> "Cảnh báo học tập mức 2 — bị hạn chế đăng ký (tối đa 14 TC/kỳ chính)"
                else -> "Cảnh báo học tập mức 3 — nguy cơ buộc thôi học"
            }
            result += AcademicWarning(
                if (status.level >= 2) AcademicWarning.Level.DANGER else AcademicWarning.Level.WARNING,
                msg,
            )
        }

        // Buộc thôi học: mức 3 hai kỳ chính liên tiếp
        if (status.consecutiveLevel3 >= 2) {
            result += AcademicWarning(
                AcademicWarning.Level.DANGER,
                "Cảnh báo mức 3 lần ${status.consecutiveLevel3} liên tiếp — có thể bị buộc thôi học",
            )
        }

        // TC nợ đọng
        if (status.overdueCredits > 0) {
            result += AcademicWarning(
                if (status.overdueCredits > 24) AcademicWarning.Level.DANGER else AcademicWarning.Level.WARNING,
                "TC nợ đọng từ đầu khóa: ${status.overdueCredits} TC" +
                    if (status.overdueCredits > 24) " (vượt ngưỡng 24 TC)" else "",
            )
        }

        // ── Môn F cần học lại ─────────────────────────────────────────────
        val failed = deduped.filter { it.letterGrade.trim().uppercase() == "F" }
        if (failed.isNotEmpty()) {
            result += AcademicWarning(
                AcademicWarning.Level.WARNING,
                "${failed.size} môn cần học lại: ${failed.joinToString { it.courseCode }}",
            )
        }

        // ── GPA kỳ gần nhất ───────────────────────────────────────────────
        val last = series.lastOrNull()
        val lastGpa = last?.gpa
        if (lastGpa != null && lastGpa < 2.0) {
            result += AcademicWarning(
                if (lastGpa < 1.0) AcademicWarning.Level.DANGER else AcademicWarning.Level.WARNING,
                "GPA kỳ ${last!!.semester}: ${GpaCalculator.fmt(lastGpa)} — thấp hơn ngưỡng 2.0",
            )
        }

        // ── CPA tổng (tham khảo thêm) ─────────────────────────────────────
        val cpa = GpaCalculator.overallCpa(grades)
        if (cpa != null && cpa < 2.5) {
            result += AcademicWarning(
                if (cpa < 2.0) AcademicWarning.Level.DANGER else AcademicWarning.Level.WARNING,
                "CPA ${GpaCalculator.fmt(cpa)}" + when {
                    cpa < 2.0 -> " — dưới ngưỡng an toàn"
                    else      -> " — tiếp cận ngưỡng cảnh báo"
                },
            )
        }

        return result
    }

    companion object {
        fun provideFactory(repository: GradeRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    GradesViewModel(repository) as T
            }
    }
}
