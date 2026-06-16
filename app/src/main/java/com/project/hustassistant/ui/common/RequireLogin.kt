package com.project.hustassistant.ui.common

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.project.hustassistant.TodoApplication

/**
 * Gọi [requireLogin] trước khi mở màn hình yêu cầu đăng nhập.
 * Nếu đã đăng nhập → chạy [onLoggedIn].
 * Nếu chưa → hiện dialog hỏi có muốn đăng nhập không.
 *
 * Dùng:
 *   requireLogin(this) { startActivity(Intent(this, EmailActivity::class.java)) }
 */
fun requireLogin(
    context: Context,
    onRequestSignIn: (() -> Unit)? = null,   // callback để launch Google Sign-In
    onLoggedIn: () -> Unit
) {
    val auth = (context.applicationContext as TodoApplication).container.authManager

    if (auth.isUserLoggedIn()) {
        onLoggedIn()
        return
    }

    AlertDialog.Builder(context)
        .setTitle("Đăng nhập yêu cầu")
        .setMessage("Bạn cần đăng nhập để sử dụng tính năng này.")
        .setPositiveButton("Đăng nhập") { _, _ ->
            onRequestSignIn?.invoke()
        }
        .setNegativeButton("Huỷ", null)
        .show()
}
