package com.shadowzen.tvstore.core.catalog

import com.shadowzen.tvstore.core.model.AppDownload
import com.shadowzen.tvstore.core.model.CompatibilityStatus
import com.shadowzen.tvstore.core.model.StoreApp
import com.shadowzen.tvstore.core.model.StoreCategory
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

class CatalogFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 解析 `GET /api/v1/catalog` 的响应。
 *
 * 顶层结构损坏时抛出 [CatalogFormatException]；单个分类或应用字段不合法时只跳过该条目并通过
 * `onSkipped` 报告，避免一条坏数据让整个商店无法显示。
 */
object CatalogParser {

    fun parse(body: String, baseUrl: HttpUrl, onSkipped: (String) -> Unit = {}): Catalog {
        val root = try {
            JSONObject(body)
        } catch (e: JSONException) {
            throw CatalogFormatException("目录不是有效的 JSON 对象", e)
        }
        val categoryArray = root.optJSONArray("categories") ?: throw CatalogFormatException("缺少 categories 数组")
        val appArray = root.optJSONArray("apps") ?: throw CatalogFormatException("缺少 apps 数组")

        val categories = LinkedHashMap<String, StoreCategory>()
        categoryArray.forEachObject("分类", onSkipped) { item ->
            val category = StoreCategory(id = item.string("id"), name = item.string("name"))
            if (categories.putIfAbsent(category.id, category) != null) {
                throw CatalogFormatException("分类 ${category.id} 重复")
            }
        }

        val apps = LinkedHashMap<String, StoreApp>()
        appArray.forEachObject("应用", onSkipped) { item ->
            val app = parseApp(item, baseUrl)
            if (apps.putIfAbsent(app.id, app) != null) throw CatalogFormatException("应用 ${app.id} 重复")
        }
        return Catalog(categories.values.toList(), apps.values.toList())
    }

    private fun parseApp(item: JSONObject, baseUrl: HttpUrl): StoreApp {
        val id = item.string("id")
        val version = item.optJSONObject("version") ?: throw CatalogFormatException("应用 $id 缺少 version 对象")
        val sha256 = version.string("sha256").lowercase()
        if (!SHA256_PATTERN.matches(sha256)) throw CatalogFormatException("应用 $id 的 sha256 不是 64 位十六进制")
        val downloadUrl = version.string("downloadUrl")
        val url = baseUrl.resolve(downloadUrl) ?: throw CatalogFormatException("应用 $id 的 downloadUrl 无效：$downloadUrl")
        val abis = version.optJSONArray("abis")?.let { array ->
            (0 until array.length()).map { index ->
                (array.opt(index) as? String)?.takeIf { it.isNotBlank() }
                    ?: throw CatalogFormatException("应用 $id 的 abis 含有无效值")
            }
        }
            ?: throw CatalogFormatException("应用 $id 缺少 abis")

        return StoreApp(
            id = id,
            name = item.string("name"),
            summary = item.optionalString("summary").orEmpty(),
            description = item.optionalString("description").orEmpty(),
            categoryId = item.string("categoryId"),
            versionName = version.string("versionName"),
            sizeBytes = version.positiveLong("sizeBytes"),
            minSdk = version.positiveLong("minSdk").toInt(),
            abis = abis,
            compatibility = compatibilityOf(version.optionalString("compatibility")),
            knownIssues = version.optionalString("knownIssues"),
            featured = item.optBoolean("featured", false),
            iconUrl = resolveIconUrl(item.optionalString("iconUrl"), baseUrl),
            publishedAtMillis = version.optionalString("publishedAt")?.let(::parseTimestamp),
            download = AppDownload(
                packageName = version.string("packageName"),
                versionCode = version.positiveLong("versionCode"),
                url = url.toString(),
                sha256 = sha256,
            ),
        )
    }

    /** 未知或缺失的兼容状态按“尚未验证”显示，不夸大兼容性。 */
    private fun compatibilityOf(value: String?): CompatibilityStatus = when (value) {
        "verified" -> CompatibilityStatus.TESTED
        "spec_match" -> CompatibilityStatus.PARAMS_MATCH
        else -> CompatibilityStatus.UNVERIFIED
    }

    /**
     * 图标是装饰性字段：只接受绝对 http(s) 地址或以 / 开头的相对路径，
     * 其他无效值返回 null 让界面显示占位图标，不影响整个应用的展示。
     */
    private fun resolveIconUrl(value: String?, baseUrl: HttpUrl): String? {
        if (value == null) return null
        if (!value.startsWith("/") && "://" !in value) return null
        val resolved = baseUrl.resolve(value) ?: return null
        if (resolved.scheme != "http" && resolved.scheme != "https") return null
        return resolved.toString()
    }

    private fun parseTimestamp(value: String): Long? = try {
        OffsetDateTime.parse(value).toInstant().toEpochMilli()
    } catch (e: DateTimeParseException) {
        null
    }

    private inline fun JSONArray.forEachObject(
        kind: String,
        onSkipped: (String) -> Unit,
        block: (JSONObject) -> Unit,
    ) {
        for (index in 0 until length()) {
            try {
                block(optJSONObject(index) ?: throw CatalogFormatException("不是 JSON 对象"))
            } catch (e: CatalogFormatException) {
                onSkipped("跳过第 ${index + 1} 个$kind：${e.message}")
            }
        }
    }

    private fun JSONObject.string(name: String): String {
        val value = opt(name)
        if (value !is String || value.isBlank()) throw CatalogFormatException("字段 $name 缺失或不是字符串")
        return value
    }

    private fun JSONObject.optionalString(name: String): String? = (opt(name) as? String)?.takeIf { it.isNotBlank() }

    private fun JSONObject.positiveLong(name: String): Long {
        val value = opt(name)
        if (value !is Number) throw CatalogFormatException("字段 $name 缺失或不是数字")
        val long = value.toLong()
        if (long <= 0 || long.toDouble() != value.toDouble()) throw CatalogFormatException("字段 $name 必须是正整数")
        return long
    }

    private val SHA256_PATTERN = Regex("[0-9a-f]{64}")
}
