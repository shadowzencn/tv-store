package com.shadowzen.tvstore.core.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object StoreHttpClient {
    fun create(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
}
