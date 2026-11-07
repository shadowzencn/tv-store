package com.shadowzen.tvstore.core.catalog

import org.json.JSONArray
import org.json.JSONObject

/** 按 TDD 第 5 节约定的字段（camelCase）构造目录响应。 */
object CatalogFixtures {
    fun version(
        sha256: String = "A".repeat(64),
        downloadUrl: String = "/files/apks/${"a".repeat(64)}.apk",
        publishedAt: String? = "2026-03-01T08:00:00Z",
        compatibility: String? = "verified",
        abis: List<String> = listOf("armeabi-v7a", "arm64-v8a"),
    ): JSONObject = JSONObject()
        .put("packageName", "com.example.player")
        .put("versionName", "2.4.1")
        .put("versionCode", 241)
        .put("minSdk", 21)
        .put("abis", JSONArray(abis))
        .put("sizeBytes", 19_500_000)
        .put("sha256", sha256)
        .put("signerSha256", "b".repeat(64))
        .put("downloadUrl", downloadUrl)
        .put("compatibility", compatibility ?: JSONObject.NULL)
        .put("knownIssues", JSONObject.NULL)
        .put("publishedAt", publishedAt ?: JSONObject.NULL)

    fun app(
        id: String,
        categoryId: String = "video",
        featured: Boolean = false,
        iconUrl: String? = null,
        version: JSONObject? = version(),
    ): JSONObject = JSONObject()
        .put("id", id)
        .put("name", "应用 $id")
        .put("summary", "简介 $id")
        .put("description", "说明 $id")
        .put("categoryId", categoryId)
        .put("featured", featured)
        .put("iconUrl", iconUrl ?: JSONObject.NULL)
        .put("version", version ?: JSONObject.NULL)

    fun catalog(vararg apps: JSONObject): String = JSONObject()
        .put(
            "categories",
            JSONArray()
                .put(JSONObject().put("id", "video").put("name", "影音娱乐"))
                .put(JSONObject().put("id", "network").put("name", "网络工具")),
        )
        .put("apps", JSONArray(apps.toList()))
        .toString()
}
