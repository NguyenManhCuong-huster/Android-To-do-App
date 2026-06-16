package com.project.hustassistant.data.news

/**
 * News — model dùng trong UI / ViewModel.
 *
 * Bảng `news` ở server chứa CẢ TIN TỨC và KẾ HOẠCH từ HUST CTT, phân biệt qua [kind].
 * Client chỉ ĐỌC (read-only); mọi WRITE đều ở server qua web scraper.
 *
 * @property kind        NEWS = thông báo / sự kiện (panel TIN TỨC ở trang chủ HUST)
 *                       PLAN = lịch học / lịch thi / kế hoạch đào tạo (panel KẾ HOẠCH)
 * @property tag         Phân loại nguồn nhỏ: 'CTSV' | 'ĐTĐH' | 'ĐTSĐH' | 'VLVH' | ...
 *                       Có thể null nếu trang nguồn không có prefix [TAG].
 * @property publishedAt Ngày publish trên trang nguồn (ms epoch UTC). 0L nếu không có.
 * @property modTime     Lần cuối record được cập nhật ở server (ms epoch UTC).
 * @property summary     Plain text full body của trang chi tiết. KHÔNG phải HTML.
 * @property imageUrl    Thumbnail (chỉ NEWS có; PLAN luôn null).
 *
 * @property recommendScore            Score do server tính (cao = match user_info tốt). Null nếu không phải đề xuất.
 * @property recommendReason           "Khớp: K66, CNTT" — server build sẵn, UI render thẳng.
 * @property recommendMatchedKeywords  List keyword đã match (debug / future use).
 */
data class News(
    val id: String,
    val kind: NewsKind,
    val title: String,
    val summary: String?,
    val articleUrl: String?,
    val imageUrl: String?,
    val tag: String?,
    val publishedAt: Long,
    val modTime: Long,
    val sourceName: String?,

    // ─── Recommendation metadata (chỉ có khi news này nằm trong list đề xuất) ───
    val recommendScore: Float? = null,
    val recommendReason: String? = null,
    val recommendMatchedKeywords: List<String> = emptyList(),
) {
    /** True nếu instance này là 1 recommendation (có score). */
    val isRecommended: Boolean get() = recommendScore != null
}

enum class NewsKind {
    NEWS,
    PLAN;

    companion object {
        /** Parse từ string server trả về. Default NEWS nếu không hợp lệ. */
        fun fromServer(value: String?): NewsKind = when (value?.uppercase()) {
            "PLAN" -> PLAN
            else   -> NEWS
        }
    }
}
