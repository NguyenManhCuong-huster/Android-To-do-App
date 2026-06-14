package com.project3.todoapp.ui.common

import android.app.Activity
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding

/**
 * Kích hoạt edge-to-edge và xử lý system bar + keyboard insets.
 * - [appBar]     : header view — nhận paddingTop = status bar height (nền đỏ lấp sau status bar)
 * - [bottomView] : view nhận paddingBottom = max(nav bar, keyboard). Mặc định = root.
 */
fun Activity.applyWindowInsets(root: View, appBar: View, bottomView: View = root) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowInsetsControllerCompat(window, root).apply {
        isAppearanceLightStatusBars = false   // icon trắng trên nền đỏ
        isAppearanceLightNavigationBars = true // icon tối trên nền sáng phía dưới
    }
    ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
        val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
        val navBars    = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
        val ime        = insets.getInsets(WindowInsetsCompat.Type.ime())
        appBar.updatePadding(top = statusBars.top)
        bottomView.updatePadding(bottom = maxOf(navBars.bottom, ime.bottom))
        WindowInsetsCompat.CONSUMED
    }
    ViewCompat.requestApplyInsets(root)
}
