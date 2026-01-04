package com.project3.todoapp.authentication


import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes

class AuthManager(private val context: Context) {

    // 1. Cấu hình quyền truy cập: Email + Thư mục ẩn Drive (AppData)
    private val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
        .build()

    // 2. Tạo Client để thực hiện Đăng nhập/Đăng xuất
    private val googleSignInClient: GoogleSignInClient by lazy {
        GoogleSignIn.getClient(context, gso)
    }

    // Lấy tài khoản hiện tại
    fun getGoogleAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    // Kiểm tra trạng thái đăng nhập
    fun isUserLoggedIn(): Boolean {
        return getGoogleAccount() != null
    }

    // Lấy ID người dùng
    fun getUserId(): String? {
        return getGoogleAccount()?.id
    }

    // 3. Lấy "Thư mời đăng nhập" (Intent) để Activity dùng mở màn hình chọn tài khoản
    fun getSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }

    // 4. Hàm đăng xuất
    fun signOut(onComplete: () -> Unit) {
        googleSignInClient.signOut().addOnCompleteListener {
            // Sau khi đăng xuất xong, thực hiện một hành động (ví dụ: cập nhật UI)
            onComplete()
        }
    }
}