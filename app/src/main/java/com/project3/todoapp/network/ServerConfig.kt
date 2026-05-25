package com.project3.todoapp.network

import android.content.Context
import android.net.Uri

/**
 * ServerConfig — lưu địa chỉ server vào SharedPreferences.
 * ApiClient đọc động mỗi request → đổi IP trong app mà không cần restart.
 */
class ServerConfig(context: Context) {

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getBaseUrl(): String {
        val raw = prefs.getString(KEY_URL, DEFAULT_URL) ?: DEFAULT_URL
        return raw.trimEnd('/') + "/"
    }

    fun getHost(): String = Uri.parse(getBaseUrl()).host ?: "192.168.1.100"

    fun getPort(): Int {
        val uri  = Uri.parse(getBaseUrl())
        val port = uri.port
        return if (port > 0) port else if (uri.scheme == "https") 443 else 80
    }

    fun getScheme(): String = Uri.parse(getBaseUrl()).scheme ?: "http"

    /** host + port → lưu URL. VD: setFromHostPort("192.168.1.100", 3000) */
    fun setFromHostPort(host: String, port: Int, scheme: String = "http") {
        prefs.edit().putString(KEY_URL, "$scheme://${host.trim()}:$port/").apply()
    }

    fun setUrl(url: String) {
        prefs.edit().putString(KEY_URL, url.trimEnd('/') + "/").apply()
    }

    fun isValidUrl(url: String): Boolean = try {
        val uri = Uri.parse(url.trim())
        !uri.host.isNullOrEmpty() &&
        uri.scheme in listOf("http", "https") &&
        uri.port != 0
    } catch (_: Exception) { false }

    fun reset() = prefs.edit().remove(KEY_URL).apply()

    companion object {
        private const val PREF_NAME  = "server_config"
        private const val KEY_URL    = "base_url"

        // Emulator → máy host; thiết bị thật → IP LAN thật
        const val DEFAULT_URL = "http://10.0.2.2:3000/"
    }
}
