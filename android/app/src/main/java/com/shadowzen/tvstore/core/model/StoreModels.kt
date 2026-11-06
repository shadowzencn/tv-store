package com.shadowzen.tvstore.core.model

const val ABI_ARMEABI_V7A = "armeabi-v7a"
const val ABI_ARM64_V8A = "arm64-v8a"

enum class CompatibilityStatus(val label: String) {
    TESTED("已实测"),
    PARAMS_MATCH("参数匹配"),
    UNVERIFIED("尚未验证"),
}

data class StoreCategory(
    val id: String,
    val name: String,
)

data class StoreApp(
    val id: String,
    val name: String,
    val summary: String,
    val description: String,
    val categoryId: String,
    val versionName: String,
    val sizeBytes: Long,
    val minSdk: Int,
    val abis: List<String>,
    val compatibility: CompatibilityStatus,
    val knownIssues: String?,
) {
    val supportsArmeabiV7a: Boolean
        get() = ABI_ARMEABI_V7A in abis
}

data class AppShelf(
    val id: String,
    val title: String,
    val apps: List<StoreApp>,
)
