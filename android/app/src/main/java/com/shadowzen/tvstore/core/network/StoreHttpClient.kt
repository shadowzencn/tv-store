package com.shadowzen.tvstore.core.network

import android.os.Build
import com.shadowzen.tvstore.BuildConfig
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object StoreHttpClient {
    fun userAgent(): String {
        val model = "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifEmpty { "AndroidTV" }
        val abi = Build.SUPPORTED_ABIS?.firstOrNull() ?: "unknown"
        return "TVStore/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE}; API ${Build.VERSION.SDK_INT}; $model; $abi)"
    }

    fun create(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            if (request.header("User-Agent") != null) {
                chain.proceed(request)
            } else {
                chain.proceed(request.newBuilder().header("User-Agent", userAgent()).build())
            }
        }
        .build()
}
