package com.project3.todoapp.ui.grade

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.project3.todoapp.R
import com.project3.todoapp.TodoApplication
import com.project3.todoapp.data.grade.GpaCalculator
import com.project3.todoapp.data.grade.Grade
import com.project3.todoapp.databinding.ActivityGradesBinding
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * GradesActivity — màn Kết quả học tập.
 *
 *  - Biểu đồ đường GPA/CPA theo từng kỳ (GpaChartView).
 *  - Danh sách điểm gom nhóm theo học kỳ (CRUD): tap để sửa, nút xoá, FAB để thêm.
 *  - Pull-to-refresh / nút sync → đồng bộ với server (local-first + LWW).
 */
class GradesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGradesBinding
    private lateinit var adapter: GradeAdapter

    private val viewModel: GradesViewModel by viewModels {
        GradesViewModel.provideFactory(
            (application as TodoApplication).container.gradeRepository,
        )
    }

    // Các điểm chữ hiển thị trong spinner ("(chưa có)" = chưa nhập điểm).
    private val letterOptions = listOf("(chưa có)", "A+", "A", "B+", "B", "C+", "C", "D+", "D", "F")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGradesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        observeData()

        binding.btnBack.setOnClickListener { finish() }
        binding.btnSync.setOnClickListener { viewModel.sync() }
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.sync()
            binding.swipeRefresh.isRefreshing = false
        }
        binding.fabAddGrade.setOnClickListener { showEditDialog(null) }
    }

    private fun setupRecyclerView() {
        adapter = GradeAdapter(
            onClick = { showEditDialog(it) },
            onDelete = { confirmDelete(it) },
        )
        binding.rvGrades.adapter = adapter
        binding.rvGrades.layoutManager = LinearLayoutManager(this)
    }

    private fun observeData() {
        // Danh sách + biểu đồ (combine grades với series).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(viewModel.grades, viewModel.series) { grades, series ->
                        buildRows(grades, series) to series
                    }.collect { (rows, series) ->
                        adapter.submitList(rows)
                        binding.chartGpa.setData(series)
                        binding.tvEmpty.visibility =
                            if (rows.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
                    }
                }

                launch {
                    viewModel.summary.collect { s ->
                        binding.tvCpa.text = GpaCalculator.fmt(s.overallCpa)
                        binding.tvEarnedCredits.text = s.earnedCredits.toString()
                        binding.tvCourseCount.text = s.courseCount.toString()
                    }
                }

                launch {
                    viewModel.toast.collect { msg ->
                        if (msg != null) {
                            Toast.makeText(this@GradesActivity, msg, Toast.LENGTH_SHORT).show()
                            viewModel.clearToast()
                        }
                    }
                }
            }
        }
    }

    /** Gom điểm theo học kỳ (sort tăng dần) → list gồm Header + Course. */
    private fun buildRows(
        grades: List<Grade>,
        series: List<GpaCalculator.SemesterPoint>,
    ): List<GradeRow> {
        val gpaBySem = series.associateBy { it.semester }
        val rows = ArrayList<GradeRow>()
        grades.groupBy { it.semester }.toSortedMap().forEach { (sem, list) ->
            val totalCredits = list.sumOf { it.credits }
            rows += GradeRow.Header(sem, gpaBySem[sem]?.gpa, totalCredits)
            list.sortedBy { it.courseCode }.forEach { rows += GradeRow.Course(it) }
        }
        return rows
    }

    private fun confirmDelete(grade: Grade) {
        AlertDialog.Builder(this)
            .setTitle("Xoá học phần")
            .setMessage("Xoá '${grade.courseCode} - ${grade.courseName}'?")
            .setPositiveButton("Xoá") { _, _ -> viewModel.deleteGrade(grade.id) }
            .setNegativeButton("Huỷ", null)
            .show()
    }

    /** Dialog thêm mới (grade=null) hoặc sửa (grade!=null). */
    private fun showEditDialog(grade: Grade?) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_grade, null)
        val etSemester = view.findViewById<EditText>(R.id.etSemester)
        val etCode = view.findViewById<EditText>(R.id.etCourseCode)
        val etName = view.findViewById<EditText>(R.id.etCourseName)
        val etNameEn = view.findViewById<EditText>(R.id.etCourseNameEn)
        val etCredits = view.findViewById<EditText>(R.id.etCredits)
        val spinner = view.findViewById<Spinner>(R.id.spinnerGrade)

        spinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, letterOptions,
        )

        // Prefill khi sửa
        if (grade != null) {
            etSemester.setText(grade.semester)
            etCode.setText(grade.courseCode)
            etName.setText(grade.courseName)
            etNameEn.setText(grade.courseNameEn)
            etCredits.setText(grade.credits.toString())
            val idx = letterOptions.indexOf(grade.letterGrade.ifBlank { "(chưa có)" })
            spinner.setSelection(if (idx >= 0) idx else 0)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (grade == null) "Thêm học phần" else "Sửa học phần")
            .setView(view)
            // Truyền null: chặn hành vi tự đóng dialog mặc định của AlertDialog.
            // Ta gắn listener riêng sau show() để kiểm tra trước khi quyết định đóng.
            .setPositiveButton(if (grade == null) "Thêm" else "Lưu", null)
            .setNegativeButton("Huỷ", null)
            .create()

        dialog.show()

        // Gắn listener SAU show() → bấm nút mà dữ liệu chưa hợp lệ thì form VẪN MỞ,
        // báo lỗi ngay trên ô đang nhập; chỉ dismiss khi đã lưu thành công.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            // Xoá lỗi cũ trước mỗi lần kiểm tra.
            etSemester.error = null
            etCode.error = null
            etName.error = null
            etCredits.error = null

            val semester = etSemester.text.toString().trim()
            val code = etCode.text.toString().trim().uppercase()
            val name = etName.text.toString().trim()
            val nameEn = etNameEn.text.toString().trim()
            val creditsRaw = etCredits.text.toString().trim()
            val letterRaw = spinner.selectedItem?.toString() ?: "(chưa có)"
            val letter = if (letterRaw == "(chưa có)") "" else letterRaw

            // Kiểm tra từng ô, set lỗi inline + focus vào ô lỗi đầu tiên rồi dừng.
            if (semester.isEmpty()) {
                etSemester.error = "Cần nhập học kỳ"
                etSemester.requestFocus()
                return@setOnClickListener
            }
            if (code.isEmpty()) {
                etCode.error = "Cần nhập mã học phần"
                etCode.requestFocus()
                return@setOnClickListener
            }
            if (name.isEmpty()) {
                etName.error = "Cần nhập tên học phần"
                etName.requestFocus()
                return@setOnClickListener
            }

            // Tín chỉ: để trống = 0; nếu nhập thì phải là số nguyên 0..30 (khớp CHECK ở DB).
            val credits = if (creditsRaw.isEmpty()) 0 else creditsRaw.toIntOrNull() ?: -1
            if (credits !in 0..30) {
                etCredits.error = "Tín chỉ phải là số từ 0 đến 30"
                etCredits.requestFocus()
                return@setOnClickListener
            }

            // Chặn trùng mã HP trong cùng học kỳ.
            if (viewModel.isDuplicate(semester, code, grade?.id)) {
                etCode.error = "Học phần $code đã có trong kỳ $semester"
                etCode.requestFocus()
                return@setOnClickListener
            }

            if (grade == null) {
                viewModel.createGrade(semester, code, name, nameEn, credits, letter)
            } else {
                viewModel.updateGrade(grade.id, semester, code, name, nameEn, credits, letter)
            }
            dialog.dismiss() // Hợp lệ → lưu xong mới đóng form.
        }
    }
}