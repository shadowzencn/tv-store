package com.shadowzen.tvstore.core.network

import android.os.Build
import android.util.Log
import com.shadowzen.tvstore.BuildConfig
import com.shadowzen.tvstore.core.database.DownloadTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

/**
 * 下载完成与安装成功事件上报（`POST /api/v1/events`）：
 * 服务端据此记录用户 IP、电视品牌型号、Android 版本、CPU 架构及对应应用版本。
 * 上报失败仅记录调试日志，绝不阻塞或影响用户的下载与安装流程。
 */
class EventReporter(
    private val httpClient: OkHttpClient,
    baseUrl: HttpUrl,
) {
    private val eventsUrl: HttpUrl = baseUrl.newBuilder()
        .addPathSegments("api/v1/events")
        .build()

    suspend fun reportDownload(task: DownloadTask): Boolean = report("download", task)

    suspend fun reportInstall(task: DownloadTask): Boolean = report("install", task)

    internal suspend fun report(type: String, task: DownloadTask): Boolean = withContext(Dispatchers.IO) {
        val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifEmpty { "Android TV" }
        val androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        val abi = Build.SUPPORTED_ABIS?.joinToString("/")?.ifEmpty { null }
        val clientVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

        val payload = JSONObject().apply {
            put("type", type)
            put("appId", task.appId)
            put("packageName", task.packageName)
            put("versionName", task.versionName)
            put("versionCode", task.versionCode)
            put("deviceModel", deviceModel)
            put("androidVersion", androidVersion)
            if (abi != null) put("abi", abi)
            put("clientVersion", clientVersion)
        }.toString()

        val request = Request.Builder()
            .url(eventsUrl)
            .header("Accept", "application/json")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: IOException) {
            Log.d(TAG, "上报事件($type, ${task.appId})失败: ${e.message}")
            false
        }
    }

    private companion object {
        const val TAG = "EventReporter"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
