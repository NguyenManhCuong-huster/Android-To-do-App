package com.project.hustassistant.ui.aichat

import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.View
import com.project.hustassistant.data.ai.AiReference

/**
 * AiReferenceSpan — biến token trích dẫn trong reply của AI thành chip bấm được.
 *
 * Server (resolveReferences) chèn token vào reply text:
 *   [[email:<uuid>]]  → email
 *   [[news:<uuid>]]   → tin tức / kế hoạch
 * và trả kèm references = [{ type, id, label }].
 *
 * applyReferences() thay mỗi token bằng 1 đoạn text bấm được hiển thị
 * "📧 <subject>" / "📰 <title>" (label lấy từ references). Tap → onReferenceTap(ref)
 * → AiChatActivity điều hướng tới EmailThreadActivity / NewsDetailActivity.
 *
 * Token nào không có trong references (client cũ / server chưa resolve) sẽ bị
 * loại bỏ để không hiển thị id thô cho user.
 */

private val REFERENCE_TOKEN =
    Regex("""\[\[(email|news):([0-9a-fA-F\-]{36})]]""", RegexOption.IGNORE_CASE)

// Cùng tông xanh với chip attachment (item_ai_message_attachment_chip.xml).
private const val LINK_COLOR = 0xFF0288D1.toInt()

/**
 * Áp reference lên [content] — vốn có thể là Spanned đã render markdown (Markwon).
 * SpannableStringBuilder.append(CharSequence, start, end) giữ nguyên span của
 * markdown trong các đoạn được copy, chỉ chèn thêm chip trích dẫn.
 */
fun applyReferences(
    content: CharSequence,
    references: List<AiReference>,
    onReferenceTap: (AiReference) -> Unit,
): CharSequence {
    // Không có reference → vẫn dọn token rác (nếu lọt) rồi trả nguyên (giữ span).
    if (references.isEmpty()) {
        return if (content.contains("[[")) REFERENCE_TOKEN.replace(content, "").trimEnd()
        else content
    }

    val refByKey = references.associateBy { "${it.type.lowercase()}:${it.id.lowercase()}" }
    val sb = SpannableStringBuilder()
    var last = 0

    for (m in REFERENCE_TOKEN.findAll(content)) {
        // text trước token
        sb.append(content, last, m.range.first)
        last = m.range.last + 1

        val type = m.groupValues[1].lowercase()
        val id = m.groupValues[2]
        val ref = refByKey["$type:${id.lowercase()}"] ?: continue  // bỏ token không resolve

        val icon = if (type == "news") "📰 " else "📧 "
        val label = ref.label.ifBlank { if (type == "news") "Mở tin" else "Mở email" }

        val start = sb.length
        sb.append(icon).append(label)
        val end = sb.length

        sb.setSpan(
            object : ClickableSpan() {
                override fun onClick(widget: View) = onReferenceTap(ref)
                override fun updateDrawState(ds: TextPaint) {
                    ds.color = LINK_COLOR
                    ds.isUnderlineText = true
                    ds.isFakeBoldText = true
                }
            },
            start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }

    // phần còn lại sau token cuối
    sb.append(content, last, content.length)
    return sb
}
