package com.shadowzen.tvstore.core.installer

import android.content.pm.PackageInstaller

sealed interface InstallOutcome {
    data object WaitingForUser : InstallOutcome
    data object Success : InstallOutcome
    data object Cancelled : InstallOutcome
    data class Failed(val reason: String, val systemMessage: String?) : InstallOutcome
}

data class InstallSessionResult(val sessionId: Int, val outcome: InstallOutcome)

/** 把 PackageInstaller 的最终状态转换为界面可展示的结果（不含 STATUS_PENDING_USER_ACTION）。 */
fun installOutcomeOf(status: Int, systemMessage: String?): InstallOutcome = when (status) {
    PackageInstaller.STATUS_SUCCESS -> InstallOutcome.Success
    PackageInstaller.STATUS_FAILURE_ABORTED -> InstallOutcome.Cancelled
    PackageInstaller.STATUS_FAILURE_BLOCKED -> InstallOutcome.Failed("安装被系统或设备策略阻止", systemMessage)
    PackageInstaller.STATUS_FAILURE_CONFLICT ->
        InstallOutcome.Failed("与已安装的应用冲突（可能签名不一致或版本冲突）", systemMessage)
    PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> InstallOutcome.Failed("安装包与本设备不兼容", systemMessage)
    PackageInstaller.STATUS_FAILURE_INVALID -> InstallOutcome.Failed("安装包无效或已损坏", systemMessage)
    PackageInstaller.STATUS_FAILURE_STORAGE -> InstallOutcome.Failed("存储空间不足或存储不可用", systemMessage)
    else -> InstallOutcome.Failed("安装失败（状态码 $status）", systemMessage)
}
