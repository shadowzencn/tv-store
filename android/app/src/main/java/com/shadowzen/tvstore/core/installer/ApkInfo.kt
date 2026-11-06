package com.shadowzen.tvstore.core.installer

/** 从安装包或已安装应用读出的、安装判断需要的最少信息。签名证书以 SHA-256 十六进制表示。 */
data class ApkInfo(
    val packageName: String,
    val versionCode: Long,
    val versionName: String?,
    val signers: Set<String>,
    val signerHistory: Set<String>,
    val hasMultipleSigners: Boolean,
)

class InvalidApkException(message: String) : Exception(message)
