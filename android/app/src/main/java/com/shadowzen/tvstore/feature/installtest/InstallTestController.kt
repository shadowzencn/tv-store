package com.shadowzen.tvstore.feature.installtest

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.shadowzen.tvstore.core.installer.ApkInfo
import com.shadowzen.tvstore.core.installer.ApkInspector
import com.shadowzen.tvstore.core.installer.ApkInstaller
import com.shadowzen.tvstore.core.installer.InstallDecision
import com.shadowzen.tvstore.core.installer.InstallOutcome
import com.shadowzen.tvstore.core.installer.InstallPolicy
import com.shadowzen.tvstore.core.installer.InstallSessionResult
import com.shadowzen.tvstore.core.installer.InvalidApkException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val INSTALL_TEST_PACKAGE = "com.shadowzen.tvstore.installtest"
private const val ASSET_DIR = "installtest"

enum class InstallTestCase(val assetName: String, val title: String) {
    INSTALL_V1("v1.apk", "安装 v1"),
    UPGRADE_V2("v2.apk", "升级到 v2"),
    DOWNGRADE_V1("v1.apk", "尝试降级到 v1"),
    OTHER_SIGNED_V2("v2other.apk", "安装异签 v2"),
}

fun isInstallTestAvailable(context: Context): Boolean =
    context.assets.list(ASSET_DIR)?.isNotEmpty() == true

class InstallTestController(private val appContext: Context) {
    private val inspector = ApkInspector(appContext.packageManager)
    private val installer = ApkInstaller(appContext)
    private val sessionTitles = mutableMapOf<Int, String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.ROOT)

    val log = mutableStateListOf<String>()
    var installedSummary by mutableStateOf("查询中…")
        private set
    var busy by mutableStateOf(false)
        private set

    fun refreshInstalled() {
        installedSummary = try {
            inspector.findInstalled(INSTALL_TEST_PACKAGE)?.describe() ?: "未安装"
        } catch (e: InvalidApkException) {
            "无法读取：${e.message}"
        }
    }

    suspend fun run(case: InstallTestCase) {
        if (busy) return
        busy = true
        try {
            val apk = withContext(Dispatchers.IO) { copyAsset(case.assetName) }
            val candidate = withContext(Dispatchers.IO) { inspector.inspectArchive(apk) }
            val installed = inspector.findInstalled(candidate.packageName)
            append("${case.title}：安装包 ${candidate.describe()}；已安装 ${installed?.describe() ?: "无"}")

            val decision = InstallPolicy.decide(candidate, installed, INSTALL_TEST_PACKAGE)
            if (!decision.allowed) {
                append("已拒绝，未调用系统安装：${decision.describe()}")
                return
            }
            append("安装前检查通过：${decision.describe()}")

            if (!appContext.packageManager.canRequestPackageInstalls() && openUnknownSourcesSettings()) {
                append("尚未允许 TV Store 安装未知应用，已打开系统设置。开启后返回并重试。")
                return
            }

            val sessionId = withContext(Dispatchers.IO) { installer.install(apk) }
            sessionTitles[sessionId] = case.title
            append("已提交安装会话 #$sessionId，等待系统处理…")
        } catch (e: InvalidApkException) {
            append("失败：${e.message}")
        } catch (e: IOException) {
            append("读写安装包失败：${e.message}")
        } catch (e: SecurityException) {
            append("系统拒绝了安装请求：${e.message}")
        } finally {
            busy = false
        }
    }

    fun onSessionResult(result: InstallSessionResult) {
        val title = sessionTitles[result.sessionId] ?: return
        val text = when (val outcome = result.outcome) {
            InstallOutcome.WaitingForUser -> "已弹出系统安装确认界面"
            InstallOutcome.Success -> "安装成功"
            InstallOutcome.Cancelled -> "用户取消了安装"
            is InstallOutcome.Failed -> "安装失败：${outcome.reason}" +
                (outcome.systemMessage?.let { "（系统信息：$it）" } ?: "")
        }
        append("$title（会话 #${result.sessionId}）：$text")
        if (result.outcome != InstallOutcome.WaitingForUser) {
            sessionTitles.remove(result.sessionId)
            refreshInstalled()
        }
    }

    /** 返回 false 表示本机没有该设置页面，此时直接尝试安装，交给系统决定是否提示。 */
    private fun openUnknownSourcesSettings(): Boolean = try {
        appContext.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.fromParts("package", appContext.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (e: ActivityNotFoundException) {
        append("本机没有“安装未知应用”设置页面（${e.message}），直接尝试安装。")
        false
    }

    private fun copyAsset(name: String): File {
        val target = File(appContext.cacheDir, "$ASSET_DIR/$name")
        target.parentFile?.mkdirs()
        appContext.assets.open("$ASSET_DIR/$name").use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target
    }

    fun note(message: String) = append(message)

    private fun append(message: String) {
        log.add(0, "${timeFormat.format(Date())}  $message")
        while (log.size > 30) log.removeAt(log.lastIndex)
    }
}

private fun ApkInfo.describe(): String =
    "v${versionName ?: "?"}（versionCode $versionCode，签名 ${signers.firstOrNull()?.take(12) ?: "无"}…）"

private fun InstallDecision.describe(): String = when (this) {
    InstallDecision.NewInstall -> "全新安装"
    is InstallDecision.Upgrade -> "升级 versionCode $fromVersionCode → $toVersionCode"
    is InstallDecision.Reinstall -> "同版本重装（versionCode $versionCode）"
    is InstallDecision.RejectedDowngrade ->
        "不允许降级（已安装 versionCode $installedVersionCode，安装包 $candidateVersionCode）"
    InstallDecision.RejectedSignatureMismatch -> "签名与已安装版本不一致，不能覆盖安装"
    is InstallDecision.RejectedPackageMismatch -> "包名不符（预期 $expected，实际 $actual）"
}
