package com.shadowzen.tvstore.core.catalog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

sealed interface CatalogResponse {
    data class Updated(val body: String, val etag: String?) : CatalogResponse

    /** 服务器返回 304：目录与本地缓存相同。 */
    data object NotModified : CatalogResponse
}

/** 服务器返回了 2xx 和 304 以外的状态码。[serverMessage] 来自错误响应 `{"error":{"code","message"}}`。 */
class CatalogHttpException(
    val statusCode: Int,
    val errorCode: String?,
    val serverMessage: String?,
) : Exception("目录接口返回 HTTP $statusCode${errorCode?.let { "（$it）" }.orEmpty()}")

/** 只负责请求 `GET /api/v1/catalog`，不解析目录内容，也不读写缓存。 */
class CatalogApi(
    private val client: OkHttpClient,
    baseUrl: HttpUrl,
) {
    // 相对路径解析，服务端部署在子路径（如 https://host/store/）时也能正确拼出地址
    private val catalogUrl: HttpUrl = requireNotNull(baseUrl.resolve("api/v1/catalog"))

    /** @throws IOException 网络失败；@throws CatalogHttpException 服务器返回错误状态码。 */
    suspend fun fetch(etag: String?): CatalogResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(catalogUrl)
            .header("Accept", "application/json")
            .apply { if (etag != null) header("If-None-Match", etag) }
            .build()
        client.newCall(request).execute().use { response ->
            when {
                response.code == 304 -> CatalogResponse.NotModified
                response.isSuccessful -> CatalogResponse.Updated(response.body.string(), response.header("ETag"))
                else -> {
                    val error = parseError(response.body.string())
                    throw CatalogHttpException(response.code, error?.first, error?.second)
                }
            }
        }
    }

    private fun parseError(body: String): Pair<String?, String?>? = try {
        val error = JSONObject(body).optJSONObject("error") ?: return null
        (error.opt("code") as? String) to (error.opt("message") as? String)?.takeIf { it.isNotBlank() }
    } catch (e: JSONException) {
        // 错误响应不是 JSON（例如反向代理的 HTML 错误页），只按状态码报告
        null
    }
}
