package com.shadowzen.tvstore.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/** 用户反馈类型（与服务端 `App\Enums\FeedbackType` 及 PRD 5.10 严格一致）。 */
enum class FeedbackType(val wireValue: String, val label: String) {
    INSTALL_FAILED("install_failed", "无法安装"),
    LAUNCH_FAILED("launch_failed", "无法启动"),
    RUNTIME_ERROR("runtime_error", "运行异常"),
    DOWNLOAD_FAILED("download_failed", "下载失败"),
    OTHER("other", "其他问题"),
}

data class FeedbackSubmission(
    val type: FeedbackType,
    val appId: String?,
    val description: String?,
    val deviceModel: String,
    val androidVersion: String,
    val clientVersion: String,
)

class FeedbackHttpException(
    val statusCode: Int,
    val errorCode: String?,
    val serverMessage: String?,
) : Exception(
    when {
        statusCode == 429 -> serverMessage ?: "提交过于频繁，请稍后再试。"
        !serverMessage.isNullOrBlank() -> serverMessage
        else -> "提交反馈失败（HTTP $statusCode）"
    },
)

/** 提交用户反馈到 `POST /api/v1/feedback`（无需登录，服务端按 IP 限频）。 */
class FeedbackApi(
    private val client: OkHttpClient,
    baseUrl: HttpUrl,
) {
    private val endpointUrl: HttpUrl = requireNotNull(baseUrl.resolve("api/v1/feedback"))

    /**
     * 成功返回服务端生成的反馈记录 ID。
     * @throws IOException 网络连接失败；
     * @throws FeedbackHttpException 422 参数错误、429 频率受限或 5xx 服务端错误。
     */
    suspend fun submit(submission: FeedbackSubmission): Long = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("type", submission.type.wireValue)
            if (!submission.appId.isNullOrBlank()) put("appId", submission.appId.trim())
            val desc = submission.description?.trim()?.take(2000)
            if (!desc.isNullOrEmpty()) put("description", desc)
            put("deviceModel", submission.deviceModel.trim().ifEmpty { "Unknown TV" }.take(100))
            put("androidVersion", submission.androidVersion.trim().ifEmpty { "Unknown" }.take(20))
            put("clientVersion", submission.clientVersion.trim().ifEmpty { "0.1.0" }.take(50))
        }

        val request = Request.Builder()
            .url(endpointUrl)
            .header("Accept", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (response.isSuccessful) {
                try {
                    JSONObject(body).optLong("id", 0L)
                } catch (_: JSONException) {
                    0L
                }
            } else {
                val error = parseError(body)
                throw FeedbackHttpException(response.code, error?.first, error?.second)
            }
        }
    }

    private fun parseError(body: String): Pair<String?, String?>? = try {
        val error = JSONObject(body).optJSONObject("error") ?: return null
        (error.opt("code") as? String) to (error.opt("message") as? String)?.takeIf { it.isNotBlank() }
    } catch (_: JSONException) {
        null
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
