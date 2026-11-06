package com.shadowzen.tvstore.core.installer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File

/** 通过系统 PackageInstaller 会话安装普通单 APK，由系统负责向用户确认。 */
class ApkInstaller(private val context: Context) {

    /** 提交安装会话并返回会话 ID，结果通过 [InstallEvents] 异步送达。必须在后台线程调用。 */
    fun install(apk: File): Int {
        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setSize(apk.length())
        }
        val sessionId = packageInstaller.createSession(params)
        try {
            packageInstaller.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("base.apk", 0, apk.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                session.commit(resultIntentSender(sessionId))
            }
        } catch (e: Exception) {
            runCatching { packageInstaller.abandonSession(sessionId) }.exceptionOrNull()?.let(e::addSuppressed)
            throw e
        }
        return sessionId
    }

    private fun resultIntentSender(sessionId: Int): IntentSender {
        val intent = Intent(context, InstallResultReceiver::class.java)
            .setAction(InstallResultReceiver.ACTION_INSTALL_RESULT)
            .setPackage(context.packageName)
        // 系统需要往这个 PendingIntent 里填入安装状态，所以 Android 12 起必须是可变的
        val mutableFlag = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(
            context,
            sessionId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag,
        ).intentSender
    }
}
