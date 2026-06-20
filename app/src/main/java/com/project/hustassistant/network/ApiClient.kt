package com.project.hustassistant.network

import com.project.hustassistant.BuildConfig
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

    private val okHttpClient: OkHttpClient by lazy { buildHttp() }
    private val retrofit: Retrofit by lazy { buildRetrofit() }

    /**
     * Client RIÊNG cho call streaming (SSE) như AI chat.
     *
     * KHÁC [okHttpClient] ở 2 điểm sống còn cho stream:
     *   1. KHÔNG log BODY — HttpLoggingInterceptor mức BODY đọc trọn response để
     *      log trước khi trả về, làm MẤT tính streaming (đó là lý do trước đây
     *      "chưa thấy stream"). Ở đây chỉ log HEADERS.
     *   2. readTimeout = 0 (vô hạn) — giữa các token / lúc chạy tool có thể im
     *      lặng vài chục giây, không được timeout.
     * Vẫn dùng chung dynamicUrl + auth interceptor nên có host động + JWT.
     */
    val streamingHttpClient: OkHttpClient by lazy { buildStreamingHttp() }

    fun <T> create(service: Class<T>): T = retrofit.create(service)

    // ─── Build ───────────────────────────────────────────
    private fun buildHttp(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
            else HttpLoggingInterceptor.Level.NONE
        }

        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .addInterceptor(dynamicUrlInterceptor())
            .addInterceptor(authInterceptor())
            .addInterceptor(logging)
            .build()
    }

    private fun buildStreamingHttp(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.HEADERS
            else HttpLoggingInterceptor.Level.NONE
        }

        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)      // stream: không timeout giữa các chunk
            .writeTimeout(120, TimeUnit.SECONDS)
            .addInterceptor(dynamicUrlInterceptor())
            .addInterceptor(authInterceptor())
            .addInterceptor(logging)
            .build()
    }

    private fun buildRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(_placeholder)
            .client(okHttpClient)
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
