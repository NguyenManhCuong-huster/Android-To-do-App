package com.project.hustassistant.authentication

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.project.hustassistant.network.AuthApi
import com.project.hustassistant.network.GoogleMobileLoginBody
import com.project.hustassistant.network.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Luồng: Google Sign-In → lấy idToken → gửi lên server
 * → server verify + trả JWT → lưu vào TokenStore.
 *
 * ⚠️ serverClientId = Web Client ID (không phải Android Client ID).
 *    Lấy từ Google Cloud Console → APIs & Services → Credentials.
 *    Phải CÙNG với GOOGLE_CLIENT_ID trong server .env.
 */
class AuthManager(
    private val context: Context,
    private val tokenStore: TokenStore,
    private val authApi: AuthApi,
    private val scope: CoroutineScope
) {
    // ⚠️ THAY GIÁ TRỊ NÀY
    private val serverClientId =
        "253879489424-q088ml6fjhv3ibn9bl0le49rp2qjf6vc.apps.googleusercontent.com"

    private val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(serverClientId)
            .requestServerAuthCode(serverClientId)
            .requestScopes(
                Scope("https://www.googleapis.com/auth/gmail.modify")
            )
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    // Cooldown 30s giữa các lần verify session để tránh spam network
    private var lastSessionCheckMs = 0L
    private val SESSION_CHECK_COOLDOWN_MS = 30_000L

    fun getGoogleAccount(): GoogleSignInAccount? = GoogleSignIn.getLastSignedInAccount(context)
    fun isUserLoggedIn(): Boolean = tokenStore.hasToken()
    fun getSignInIntent(): Intent = googleSignInClient.signInIntent

    /**
     * Gọi sau khi GoogleSignIn trả về account.
     * Exchange idToken → JWT server, lưu vào TokenStore.
     * Nếu server từ chối → sign out khỏi Google luôn để lần sau hiện lại picker.
     */
    fun handleSignInResult(account: GoogleSignInAccount, onDone: (Boolean, String?) -> Unit) {
        val idToken = account.idToken
        if (idToken.isNullOrEmpty()) {
            onDone(false, "Không lấy được ID token từ Google")
            return
        }
        val serverAuthCode = account.serverAuthCode  // có thể null nếu user đã grant trước

        scope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    authApi.googleLogin(
                        GoogleMobileLoginBody(
                            id_token = idToken,
                            server_auth_code = serverAuthCode
                        )
                    )
                }
                val jwt = res.data?.token
                if (res.success && !jwt.isNullOrEmpty()) {
                    tokenStore.save(jwt)
                    lastSessionCheckMs = System.currentTimeMillis()
                    onDone(true, null)
                } else {
                    // Server từ chối → sign out Google để lần sau hiện picker chọn tài khoản
                    googleSignInClient.signOut()
                    onDone(false, res.message ?: "Đăng nhập thất bại")
                }
            } catch (e: Exception) {
                // Lỗi mạng/server → sign out Google để lần sau hiện picker
                googleSignInClient.signOut()
                onDone(false, e.message ?: "Lỗi kết nối server")
            }
        }
    }

    /**
     * Xác minh session vẫn còn hợp lệ bằng cách gọi /api/auth/me.
     * Nếu thất bại (server tắt hoặc token hết hạn) → xóa token, trả về false.
     * Có cooldown 30s để tránh gọi network liên tục khi onResume.
     */
    fun verifySession(onResult: (Boolean) -> Unit) {
        if (!tokenStore.hasToken()) {
            onResult(false)
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastSessionCheckMs < SESSION_CHECK_COOLDOWN_MS) {
            // Còn trong cooldown → dùng trạng thái đã cached
            onResult(true)
            return
        }
        scope.launch {
            val isValid = try {
                val res = withContext(Dispatchers.IO) { authApi.me() }
                res.success
            } catch (_: Exception) {
                false
            }
            if (isValid) {
                lastSessionCheckMs = System.currentTimeMillis()
            } else {
                tokenStore.clear()
            }
            // scope là Dispatchers.Main nên callback gọi trên Main thread
            onResult(isValid)
        }
    }

    fun signOut(onComplete: () -> Unit) {
        tokenStore.clear()
        lastSessionCheckMs = 0L  // reset để lần verify tiếp không dùng cache cũ
        googleSignInClient.signOut().addOnCompleteListener { onComplete() }
    }
}
