package com.project3.todoapp.network

import com.project3.todoapp.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * ApiClient — Retrofit singleton với 2 interceptors:
 *
 *  1. DynamicUrlInterceptor — đọc ServerConfig mỗi request → ghi đè host/port/scheme.
 *     Hiệu quả: đổi IP trong SettingsActivity → request tiếp theo dùng IP mới ngay.
 *
 *  2. AuthInterceptor — gắn "Authorization: Bearer <jwt>" tự động.
 *
 * Khởi tạo 1 lần trong AppContainer, truyền serverConfig + tokenStore.
 */
class ApiClient(
    private val serverConfig: ServerConfig,
    private val tokenStore: TokenStore
) {
    // Retrofit cần 1 baseUrl tĩnh lúc khởi tạo.
    // DynamicUrlInterceptor sẽ ghi đè nó → giá trị này là placeholder.
    private val _placeholder = "http://placeholder.local/"

    private val retrofit: Retrofit by lazy { buildRetrofit() }

    fun <T> create(service: Class<T>): T = retrofit.create(service)

    // ─── Build ───────────────────────────────────────────
    private fun buildRetrofit(): Retrofit {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
            else HttpLoggingInterceptor.Level.NONE
        }

        val http = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(dynamicUrlInterceptor())
            .addInterceptor(authInterceptor())
            .addInterceptor(logging)
            .build()

        return Retrofit.Builder()
            .baseUrl(_placeholder)
            .client(http)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * Interceptor 1: đọc ServerConfig rồi ghi đè scheme/host/port.
     * Path + query + body không đổi.
     */
    private fun dynamicUrlInterceptor() = Interceptor { chain ->
        val original = chain.request()
        val target = serverConfig.getBaseUrl().toHttpUrl()

        val newUrl = original.url.newBuilder()
            .scheme(target.scheme)
            .host(target.host)
            .port(target.port)
            .build()

        chain.proceed(original.newBuilder().url(newUrl).build())
    }

    /** Interceptor 2: JWT header. */
    private fun authInterceptor() = Interceptor { chain ->
        val req = chain.request()
        val jwt = tokenStore.get()
        val newReq = if (jwt.isNullOrEmpty()) req
        else req.newBuilder().addHeader("Authorization", "Bearer $jwt").build()
        chain.proceed(newReq)
    }
}
