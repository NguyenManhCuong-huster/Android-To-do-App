package com.project.hustassistant.data.grade

/**
 * GpaCalculator — quy đổi điểm chữ HUST sang thang 4 và tính GPA/CPA.
 *
 * Thang điểm (Quy chế đào tạo ĐHBK Hà Nội):
 *   A+/A = 4.0 · B+ = 3.5 · B = 3.0 · C+ = 2.5 · C = 2.0 · D+ = 1.5 · D = 1.0 · F = 0.0
 *
 * Quy ước tính GPA/CPA:
 *   - Chỉ tính học phần CÓ tín chỉ (credits > 0) VÀ có điểm chữ quy đổi được.
 *     → Học phần GDTC (0 TC) hoặc chưa có điểm bị loại khỏi mẫu/tử số.
 *   - GPA(kỳ)  = Σ(điểm × TC) / Σ(TC)  trong cùng học kỳ.
 *   - CPA(đến kỳ k) = Σ(điểm × TC) / Σ(TC)  cộng dồn mọi kỳ ≤ k.
 *   - Học kỳ sắp xếp tăng dần theo mã ("20251" < "20252" < "20253" < "20261").
 */
object GpaCalculator {

    /** Quy đổi điểm chữ → điểm hệ 4. Trả null nếu không quy đổi được (chưa có điểm). */
    fun letterToPoint(letter: String?): Double? = when (letter?.trim()?.uppercase()) {
        "A+", "A" -> 4.0
        "B+"      -> 3.5
        "B"       -> 3.0
        "C+"      -> 2.5
        "C"       -> 2.0
        "D+"      -> 1.5
        "D"       -> 1.0
        "F"       -> 0.0
        else      -> null
    }

    /** Học phần này có được tính vào GPA/CPA không? */
    fun countsTowardGpa(grade: Grade): Boolean =
        grade.credits > 0 && letterToPoint(grade.letterGrade) != null

    /** Kết quả tổng hợp cho 1 học kỳ + CPA cộng dồn tới hết kỳ đó. */
    data class SemesterPoint(
        val semester: String,
        val gpa: Double?,            // GPA riêng kỳ này (null nếu không có HP tính điểm)
        val cpa: Double?,            // CPA cộng dồn tới hết kỳ này
        val semesterCredits: Int,    // tổng TC tính GPA trong kỳ
        val cumulativeCredits: Int,  // tổng TC tính GPA cộng dồn
    )

    /**
     * Tính chuỗi điểm theo từng học kỳ (đã sort tăng dần) để vẽ biểu đồ đường.
     * Trả về list rỗng nếu không có học phần nào tính được GPA.
     */
    fun computeSeries(grades: List<Grade>): List<SemesterPoint> {
        val bySemester = grades
            .filter { countsTowardGpa(it) }
            .groupBy { it.semester }
            .toSortedMap()

        var cumWeighted = 0.0
        var cumCredits  = 0
        val out = ArrayList<SemesterPoint>(bySemester.size)

        for ((sem, list) in bySemester) {
            var semWeighted = 0.0
            var semCredits  = 0
            for (g in list) {
                val p = letterToPoint(g.letterGrade) ?: continue
                semWeighted += p * g.credits
                semCredits  += g.credits
            }
            cumWeighted += semWeighted
            cumCredits  += semCredits

            out += SemesterPoint(
                semester          = sem,
                gpa               = if (semCredits > 0) semWeighted / semCredits else null,
                cpa               = if (cumCredits > 0) cumWeighted / cumCredits else null,
                semesterCredits   = semCredits,
                cumulativeCredits = cumCredits,
            )
        }
        return out
    }

    /** CPA tổng kết (toàn bộ) — null nếu chưa có học phần nào tính được. */
    fun overallCpa(grades: List<Grade>): Double? {
        var weighted = 0.0
        var credits  = 0
        for (g in grades) {
            val p = letterToPoint(g.letterGrade) ?: continue
            if (g.credits <= 0) continue
            weighted += p * g.credits
            credits  += g.credits
        }
        return if (credits > 0) weighted / credits else null
    }

    /** Tổng số tín chỉ đã tích luỹ (HP có điểm khác F, credits > 0). */
    fun earnedCredits(grades: List<Grade>): Int =
        grades.filter {
            it.credits > 0 &&
                letterToPoint(it.letterGrade) != null &&
                it.letterGrade.trim().uppercase() != "F"
        }.sumOf { it.credits }

    /** Format hiển thị: 2 chữ số thập phân, hoặc "—" nếu null. */
    fun fmt(v: Double?): String = if (v == null) "—" else String.format("%.2f", v)

    /**
     * Đổi mã học kỳ "20251" → nhãn ngắn "K1 2025-26" để vẽ trục x.
     * Kỳ: 1 = HK1, 2 = HK2, 3 = HK hè.
     */
    fun semesterLabel(code: String): String = code
}
