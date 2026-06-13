package com.project3.todoapp.ui.grade

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.project3.todoapp.data.grade.GpaCalculator
import com.project3.todoapp.data.grade.Grade
import com.project3.todoapp.databinding.ItemGradeBinding
import com.project3.todoapp.databinding.ItemGradeHeaderBinding

/** Một dòng trong danh sách: tiêu đề học kỳ hoặc 1 học phần. */
sealed interface GradeRow {
    data class Header(
        val semester: String,
        val gpa: Double?,
        val credits: Int,
    ) : GradeRow

    data class Course(val grade: Grade) : GradeRow
}

/**
 * GradeAdapter — hiển thị điểm gom nhóm theo học kỳ.
 *  - Tap 1 học phần → onClick (mở dialog sửa).
 *  - Nút xoá trên mỗi học phần → onDelete.
 */
class GradeAdapter(
    private val onClick: (Grade) -> Unit,
    private val onDelete: (Grade) -> Unit,
) : ListAdapter<GradeRow, RecyclerView.ViewHolder>(DiffCallback()) {

    override fun getItemViewType(position: Int): Int =
        if (getItem(position) is GradeRow.Header) TYPE_HEADER else TYPE_COURSE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderVH(ItemGradeHeaderBinding.inflate(inflater, parent, false))
        } else {
            CourseVH(ItemGradeBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is GradeRow.Header -> (holder as HeaderVH).bind(row)
            is GradeRow.Course -> (holder as CourseVH).bind(row.grade)
        }
    }

    inner class HeaderVH(private val b: ItemGradeHeaderBinding) :
        RecyclerView.ViewHolder(b.root) {
        fun bind(h: GradeRow.Header) {
            b.tvSemester.text = GpaCalculator.semesterLabel(h.semester)
            b.tvSemesterGpa.text = "GPA: ${GpaCalculator.fmt(h.gpa)} · ${h.credits} TC"
        }
    }

    inner class CourseVH(private val b: ItemGradeBinding) :
        RecyclerView.ViewHolder(b.root) {
        fun bind(g: Grade) {
            b.tvCourseCode.text = g.courseCode
            b.tvCourseName.text = g.courseName
            b.tvCredits.text = "${g.credits} TC"
            b.tvGrade.text = g.letterGrade.ifBlank { "—" }
            b.tvCourseNameEn.text = g.courseNameEn
            b.tvCourseNameEn.visibility =
                if (g.courseNameEn.isBlank()) android.view.View.GONE else android.view.View.VISIBLE

            b.root.setOnClickListener { onClick(g) }
            b.btnDeleteGrade.setOnClickListener { onDelete(g) }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<GradeRow>() {
        override fun areItemsTheSame(a: GradeRow, b: GradeRow): Boolean = when {
            a is GradeRow.Header && b is GradeRow.Header -> a.semester == b.semester
            a is GradeRow.Course && b is GradeRow.Course -> a.grade.id == b.grade.id
            else -> false
        }

        override fun areContentsTheSame(a: GradeRow, b: GradeRow): Boolean = a == b
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_COURSE = 1
    }
}
