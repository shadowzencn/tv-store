package com.shadowzen.tvstore.core.installer

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.io.File
import java.security.MessageDigest

class ApkInspector(private val packageManager: PackageManager) {

    fun inspectArchive(apk: File): ApkInfo {
        val info = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageArchiveInfo(apk.path, PackageManager.PackageInfoFlags.of(FLAGS.toLong()))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageArchiveInfo(apk.path, FLAGS)
        } ?: throw InvalidApkException("无法解析安装包：${apk.name}")
        return info.toApkInfo()
    }

    /** 返回 null 表示该包未安装，或因软件包可见性限制无法查询。 */
    fun findInstalled(packageName: String): ApkInfo? = try {
        val info = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(FLAGS.toLong()))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, FLAGS)
        }
        info.toApkInfo()
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private fun PackageInfo.toApkInfo(): ApkInfo {
        val signing = signingInfo ?: throw InvalidApkException("无法读取 $packageName 的签名信息")
        val current = signing.apkContentsSigners.orEmpty().map { it.sha256() }.toSet()
        val history = if (signing.hasMultipleSigners()) {
            current
        } else {
            signing.signingCertificateHistory.orEmpty().map { it.sha256() }.toSet() + current
        }
        return ApkInfo(
            packageName = packageName,
            versionCode = longVersionCode,
            versionName = versionName,
            signers = current,
            signerHistory = history,
            hasMultipleSigners = signing.hasMultipleSigners(),
        )
    }

    private fun Signature.sha256(): String =
        MessageDigest.getInstance("SHA-256").digest(toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val FLAGS = PackageManager.GET_SIGNING_CERTIFICATES
    }
}
