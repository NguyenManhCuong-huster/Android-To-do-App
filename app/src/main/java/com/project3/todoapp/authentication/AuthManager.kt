package com.project3.todoapp.authentication

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.project3.todoapp.network.AuthApi
import com.project3.todoapp.network.GoogleMobileLoginBody
import com.project3.todoapp.network.TokenStore
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
                Scope("https://www.googleapis.com/auth/gmail.readonly")
            )
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    fun getGoogleAccount(): GoogleSignInAccount? = GoogleSignIn.getLastSignedInAccount(context)
    fun isUserLoggedIn(): Boolean = tokenStore.hasToken()
    fun getSignInIntent(): Intent = googleSignInClient.signInIntent

    /**
     * Gọi sau khi GoogleSignIn trả về account.
     * Exchange idToken → JWT server, lưu vào TokenStore.
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
                    onDone(true, null)
                } else {
                    onDone(false, res.message ?: "Đăng nhập thất bại")
                }
            } catch (e: Exception) {
                onDone(false, e.message ?: "Lỗi kết nối server")
            }
        }
    }

    fun signOut(onComplete: () -> Unit) {
        tokenStore.clear()
        googleSignInClient.signOut().addOnCompleteListener { onComplete() }
    }
}
