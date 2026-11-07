package com.shadowzen.tvstore.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/** 服务端返回的商店客户端当前发布版本（`GET /api/v1/client/version`）。 */
data class ClientRelease(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val abis: List<String>,
    val sizeBytes: Long,
    val sha256: String,
    val signerSha256: String?,
    val downloadUrl: String,
    val releaseNotes: String?,
)

sealed interface ClientVersionResult {
    data class Published(val release: ClientRelease) : ClientVersionResult

    /** 服务端返回 404：后台尚未发布商店客户端版本。 */
    data object NotPublished : ClientVersionResult
}

class ClientVersionHttpException(
    val statusCode: Int,
    val errorCode: String?,
    val serverMessage: String?,
) : Exception(serverMessage ?: "检查更新接口返回 HTTP $statusCode${errorCode?.let { "（$it）" }.orEmpty()}")

/** 请求并解析 `GET /api/v1/client/version`。 */
class ClientVersionApi(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl,
) {
    private val endpointUrl: HttpUrl = requireNotNull(baseUrl.resolve("api/v1/client/version"))

    /** @throws IOException 网络失败；@throws ClientVersionHttpException 服务端错误或数据格式损坏。 */
    suspend fun fetchLatest(): ClientVersionResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(endpointUrl)
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            when {
                response.code == 404 -> ClientVersionResult.NotPublished
                response.isSuccessful -> ClientVersionResult.Published(parseRelease(body))
                else -> {
                    val error = parseError(body)
                    throw ClientVersionHttpException(response.code, error?.first, error?.second)
                }
            }
        }
    }

    internal fun parseRelease(body: String): ClientRelease {
        val obj = try {
            JSONObject(body)
        } catch (e: JSONException) {
            throw ClientVersionHttpException(200, "invalid_json", "客户端版本响应不是有效 JSON")
        }
        val packageName = obj.optString("packageName").trim()
        val versionName = obj.optString("versionName").trim()
        val versionCode = obj.optLong("versionCode", -1L)
        val minSdk = obj.optInt("minSdk", -1)
        val sizeBytes = obj.optLong("sizeBytes", -1L)
        val sha256 = obj.optString("sha256").trim().lowercase()
        val rawUrl = obj.optString("downloadUrl").trim()
        val resolvedUrl = baseUrl.resolve(rawUrl)

        if (packageName.isEmpty() || versionName.isEmpty() || versionCode <= 0 || minSdk <= 0 ||
            sizeBytes <= 0 || !SHA256_PATTERN.matches(sha256) || resolvedUrl == null
        ) {
            throw ClientVersionHttpException(200, "invalid_payload", "客户端版本信息字段不完整或格式错误")
        }

        val abisArray = obj.optJSONArray("abis")
        val abis = if (abisArray != null) {
            (0 until abisArray.length()).mapNotNull { idx ->
                (abisArray.opt(idx) as? String)?.trim()?.takeIf { it.isNotEmpty() }
            }
        } else {
            emptyList()
        }

        return ClientRelease(
            packageName = packageName,
            versionName = versionName,
            versionCode = versionCode,
            minSdk = minSdk,
            abis = abis,
            sizeBytes = sizeBytes,
            sha256 = sha256,
            signerSha256 = (obj.opt("signerSha256") as? String)?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
            downloadUrl = resolvedUrl.toString(),
            releaseNotes = (obj.opt("releaseNotes") as? String)?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    private fun parseError(body: String): Pair<String?, String?>? = try {
        val error = JSONObject(body).optJSONObject("error") ?: return null
        (error.opt("code") as? String) to (error.opt("message") as? String)?.takeIf { it.isNotBlank() }
    } catch (_: JSONException) {
        null
    }

    private companion object {
        val SHA256_PATTERN = Regex("[0-9a-f]{64}")
    }
}
