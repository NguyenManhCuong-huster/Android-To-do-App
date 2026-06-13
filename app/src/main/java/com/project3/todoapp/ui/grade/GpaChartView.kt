package com.project3.todoapp.ui.grade

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.project3.todoapp.data.grade.GpaCalculator
import com.project3.todoapp.data.grade.GpaCalculator.SemesterPoint

/**
 * GpaChartView — biểu đồ đường GPA & CPA theo từng học kỳ, vẽ thủ công bằng Canvas
 * (không cần thư viện ngoài như MPAndroidChart).
 *
 * Trục y: 0.0 → 4.0 (thang điểm hệ 4). Trục x: các học kỳ theo thứ tự tăng dần.
 *  - Đường GPA: điểm trung bình riêng từng kỳ.
 *  - Đường CPA: điểm trung bình tích luỹ.
 *
 * Dùng: gọi [setData] với list [SemesterPoint] (lấy từ GpaCalculator.computeSeries).
 */
class GpaChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    private var points: List<SemesterPoint> = emptyList()

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val maxY = 4.0f

    // ─── Paints ────────────────────────────────────────
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CFD8DC")
        strokeWidth = dp(1f)
        style = Paint.Style.STROKE
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#ECEFF1")
        strokeWidth = dp(1f)
        style = Paint.Style.STROKE
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#607D8B")
        textSize = dp(11f)
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#90A4AE")
        textSize = dp(14f)
        textAlign = Paint.Align.CENTER
    }

    private val gpaColor = Color.parseColor("#0DC7D0") // cyan (giống Tasks)
    private val cpaColor = Color.parseColor("#E65100") // cam (giống Tags)

    private fun linePaint(c: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = c
        strokeWidth = dp(2.5f)
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private fun dotPaint(c: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = c
        style = Paint.Style.FILL
    }

    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(10f)
        textAlign = Paint.Align.CENTER
    }

    fun setData(data: List<SemesterPoint>) {
        points = data
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val padLeft = dp(34f)
        val padRight = dp(12f)
        val padTop = dp(16f)
        val padBottom = dp(34f)

        val chartW = width - padLeft - padRight
        val chartH = height - padTop - padBottom
        if (chartW <= 0 || chartH <= 0) return

        if (points.isEmpty()) {
            canvas.drawText(
                "Chưa có dữ liệu điểm để vẽ biểu đồ",
                width / 2f, height / 2f, emptyPaint,
            )
            return
        }

        // ─── Lưới ngang + nhãn trục y (0..4) ───
        for (i in 0..4) {
            val v = i.toFloat()
            val y = padTop + chartH * (1f - v / maxY)
            canvas.drawLine(padLeft, y, padLeft + chartW, y, gridPaint)
            canvas.drawText(String.format("%.1f", v), dp(6f), y + dp(4f), labelPaint)
        }

        // Trục
        canvas.drawLine(padLeft, padTop, padLeft, padTop + chartH, axisPaint)
        canvas.drawLine(padLeft, padTop + chartH, padLeft + chartW, padTop + chartH, axisPaint)

        // ─── Toạ độ x của từng kỳ ───
        val n = points.size
        fun xAt(idx: Int): Float =
            if (n == 1) padLeft + chartW / 2f
            else padLeft + chartW * idx / (n - 1)

        fun yAt(value: Double): Float =
            padTop + chartH * (1f - (value.toFloat().coerceIn(0f, maxY)) / maxY)

        // Nhãn trục x (học kỳ)
        labelPaint.textAlign = Paint.Align.CENTER
        points.forEachIndexed { idx, p ->
            canvas.drawText(
                GpaCalculator.semesterLabel(p.semester),
                xAt(idx), padTop + chartH + dp(16f), labelPaint,
            )
        }
        labelPaint.textAlign = Paint.Align.LEFT

        // ─── Vẽ 1 đường (GPA hoặc CPA) ───
        fun drawSeries(color: Int, selector: (SemesterPoint) -> Double?) {
            val path = Path()
            var started = false
            val lp = linePaint(color)
            val dp2 = dotPaint(color)
            valuePaint.color = color

            points.forEachIndexed { idx, p ->
                val v = selector(p) ?: return@forEachIndexed
                val x = xAt(idx)
                val y = yAt(v)
                if (!started) {
                    path.moveTo(x, y); started = true
                } else {
                    path.lineTo(x, y)
                }
            }
            canvas.drawPath(path, lp)

            // Chấm + giá trị
            points.forEachIndexed { idx, p ->
                val v = selector(p) ?: return@forEachIndexed
                val x = xAt(idx)
                val y = yAt(v)
                canvas.drawCircle(x, y, dp(3.5f), dp2)
                canvas.drawText(String.format("%.2f", v), x, y - dp(7f), valuePaint)
            }
        }

        // CPA vẽ trước (nền), GPA vẽ sau (nổi hơn)
        drawSeries(cpaColor) { it.cpa }
        drawSeries(gpaColor) { it.gpa }
    }
}
