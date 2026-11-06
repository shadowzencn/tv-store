package com.shadowzen.tvstore.core.installer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

sealed interface InstallState {
    val message: String

    data object Checking : InstallState {
        override val message = "正在检查安装包…"
    }

    data object Submitted : InstallState {
        override val message = "已提交系统安装…"
    }

    data object WaitingForUser : InstallState {
        override val message = "请在系统安装界面中确认"
    }

    data object Installed : InstallState {
        override val message = "安装成功"
    }

    data object Cancelled : InstallState {
        override val message = "已取消安装"
    }

    data class Failed(override val message: String) : InstallState
}

/**
 * 商店应用的安装流程：检查包名、签名和版本后交给系统安装程序。
 * 安装状态只保存在内存中，按应用 ID 区分。
 */
class StoreInstaller(
    private val context: Context,
    scope: CoroutineScope,
    private val onInstalled: (appId: String) -> Unit,
) {
    private val inspector = ApkInspector(context.packageManager)
    private val installer = ApkInstaller(context)
    private val sessions = ConcurrentHashMap<Int, String>()
    private val mutableStates = MutableStateFlow<Map<String, InstallState>>(emptyMap())
    val states: StateFlow<Map<String, InstallState>> = mutableStates.asStateFlow()

    init {
        scope.launch { InstallEvents.results.collect(::onSessionResult) }
    }

    fun isBusy(appId: String): Boolean = when (states.value[appId]) {
        InstallState.Checking, InstallState.Submitted, InstallState.WaitingForUser -> true
        else -> false
    }

    suspend fun install(appId: String, apk: File, expectedPackageName: String) {
        if (isBusy(appId)) return
        setState(appId, InstallState.Checking)
        try {
            val candidate = withContext(Dispatchers.IO) { inspector.inspectArchive(apk) }
            val installed = inspector.findInstalled(candidate.packageName)
            val decision = InstallPolicy.decide(candidate, installed, expectedPackageName)
            if (!decision.allowed) {
                setState(appId, InstallState.Failed(decision.rejectionMessage()))
                return
            }
            if (!context.packageManager.canRequestPackageInstalls() && openUnknownSourcesSettings()) {
                setState(appId, InstallState.Failed("请在系统设置中允许 TV Store 安装未知应用，然后返回重试"))
                return
            }
            val sessionId = withContext(Dispatchers.IO) { installer.install(apk) }
            sessions[sessionId] = appId
            setState(appId, InstallState.Submitted)
        } catch (e: InvalidApkException) {
            setState(appId, InstallState.Failed(e.message ?: "安装包无效"))
        } catch (e: IOException) {
            setState(appId, InstallState.Failed("读取安装包失败：${e.message}"))
        } catch (e: SecurityException) {
            setState(appId, InstallState.Failed("系统拒绝了安装请求：${e.message}"))
        }
    }

    fun clear(appId: String) {
        mutableStates.update { it - appId }
    }

    private fun onSessionResult(result: InstallSessionResult) {
        val appId = sessions[result.sessionId] ?: return
        val state = when (val outcome = result.outcome) {
            InstallOutcome.WaitingForUser -> InstallState.WaitingForUser
            InstallOutcome.Success -> InstallState.Installed
            InstallOutcome.Cancelled -> InstallState.Cancelled
            is InstallOutcome.Failed -> InstallState.Failed(
                "安装失败：${outcome.reason}" + (outcome.systemMessage?.let { "（$it）" } ?: ""),
            )
        }
        setState(appId, state)
        if (result.outcome != InstallOutcome.WaitingForUser) sessions.remove(result.sessionId)
        if (result.outcome == InstallOutcome.Success) onInstalled(appId)
    }

    private fun setState(appId: String, state: InstallState) {
        mutableStates.update { it + (appId to state) }
    }

    /** 返回 false 表示本机没有该设置页面，此时直接提交安装，由系统决定是否提示。 */
    private fun openUnknownSourcesSettings(): Boolean = try {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

private fun InstallDecision.rejectionMessage(): String = when (this) {
    is InstallDecision.RejectedDowngrade ->
        "不能安装：已安装的版本更新（versionCode $installedVersionCode），商店不会自动降级"
    InstallDecision.RejectedSignatureMismatch -> "不能安装：签名与已安装的版本不一致，不能覆盖安装"
    is InstallDecision.RejectedPackageMismatch -> "不能安装：安装包包名（$actual）与商店记录（$expected）不一致"
    else -> "不能安装"
}

/** 打开已安装的应用；优先使用电视桌面入口。返回 false 表示找不到启动入口。 */
fun launchInstalledApp(context: Context, packageName: String): Boolean {
    val pm = context.packageManager
    val intent = pm.getLeanbackLaunchIntentForPackage(packageName)
        ?: pm.getLaunchIntentForPackage(packageName)
        ?: return false
    return try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
