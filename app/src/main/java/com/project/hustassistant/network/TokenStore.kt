package com.project.hustassistant.network

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TokenStore(context: Context) {

    private val prefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            "auth_prefs",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun save(jwt: String) = prefs.edit().putString(KEY, jwt).apply()
    fun get(): String?    = prefs.getString(KEY, null)
    fun clear()           = prefs.edit().remove(KEY).apply()
    fun hasToken(): Boolean = !get().isNullOrEmpty()

    companion object { private const val KEY = "jwt_token" }
}
