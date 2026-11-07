package com.shadowzen.tvstore.feature.downloads

import androidx.compose.ui.graphics.Color
import com.shadowzen.tvstore.core.database.DownloadStatus
import com.shadowzen.tvstore.core.database.DownloadTask
import com.shadowzen.tvstore.core.designsystem.StoreColors
import com.shadowzen.tvstore.core.designsystem.formatFileSize
import com.shadowzen.tvstore.core.designsystem.formatSpeed
import com.shadowzen.tvstore.core.download.DownloadRequest
import com.shadowzen.tvstore.core.installer.InstallState
import com.shadowzen.tvstore.core.model.StoreApp

enum class DownloadAction(val label: String) {
    START("下载"),
    PAUSE("暂停"),
    RESUME("继续"),
    RETRY("重试"),
    INSTALL("安装"),
    OPEN("打开"),
    UPDATE("更新"),

    /** 应用没有可下载的安装包（例如本地示例数据）。 */
    UNAVAILABLE("下载"),
}

/** 任务状态对应的主按钮。按钮位置固定只换文字，遥控器焦点不会因状态变化而丢失。 */
fun primaryActionFor(
    task: DownloadTask?,
    hasPackage: Boolean,
    latestVersionCode: Long? = null,
): DownloadAction = when (task?.status) {
    null -> if (hasPackage) DownloadAction.START else DownloadAction.UNAVAILABLE
    DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING, DownloadStatus.VERIFYING -> DownloadAction.PAUSE
    DownloadStatus.PAUSED -> DownloadAction.RESUME
    DownloadStatus.FAILED -> DownloadAction.RETRY
    DownloadStatus.COMPLETED -> DownloadAction.INSTALL
    DownloadStatus.INSTALLED -> if (hasPackage && latestVersionCode != null && latestVersionCode > task.versionCode) {
        DownloadAction.UPDATE
    } else {
        DownloadAction.OPEN
    }
}

data class StatusLine(val text: String, val color: Color)

/** 状态文字优先级：操作提示 > 安装状态（仅下载完成后）> 下载状态。 */
fun statusLineFor(
    task: DownloadTask,
    installState: InstallState?,
    notice: String?,
    latestVersionName: String? = null,
    latestVersionCode: Long? = null,
): StatusLine {
    if (notice != null) return StatusLine(notice, StoreColors.Warning)
    if (task.status == DownloadStatus.INSTALLED &&
        latestVersionCode != null &&
        latestVersionCode > task.versionCode
    ) {
        val ver = latestVersionName?.let { " v$it" }.orEmpty()
        return StatusLine("发现新版本$ver（已安装 v${task.versionName}）", StoreColors.AccentSecondary)
    }
    if (installState != null &&
        (task.status == DownloadStatus.COMPLETED || task.status == DownloadStatus.INSTALLED)
    ) {
        val color = when (installState) {
            InstallState.Installed -> StoreColors.Success
            is InstallState.Failed -> StoreColors.Error
            InstallState.Cancelled -> StoreColors.Warning
            else -> StoreColors.AccentSecondary
        }
        return StatusLine(installState.message, color)
    }
    val percent = task.progress?.let { " ${(it * 100).toInt()}%" }.orEmpty()
    return when (task.status) {
        DownloadStatus.QUEUED -> StatusLine("排队等待中", StoreColors.TextSecondary)
        DownloadStatus.DOWNLOADING ->
            StatusLine(task.errorMessage ?: "下载中$percent", if (task.errorMessage != null) StoreColors.Warning else StoreColors.AccentSecondary)
        DownloadStatus.VERIFYING -> StatusLine("正在校验安装包（SHA-256）…", StoreColors.AccentSecondary)
        DownloadStatus.PAUSED -> StatusLine("已暂停$percent", StoreColors.Warning)
        DownloadStatus.FAILED -> StatusLine("下载失败：${task.errorMessage ?: "未知错误"}", StoreColors.Error)
        DownloadStatus.COMPLETED -> StatusLine("下载完成，已通过 SHA-256 校验", StoreColors.Success)
        DownloadStatus.INSTALLED -> StatusLine("已安装", StoreColors.Success)
    }
}

/** 例如 “12.3 MB / 45.6 MB · 1.2 MB/s”；速度只在下载中显示。 */
fun sizeLineFor(task: DownloadTask): String {
    val size = task.sizeBytes
    val amount = when {
        task.status == DownloadStatus.INSTALLED -> size?.let(::formatFileSize).orEmpty()
        size != null -> "${formatFileSize(task.downloadedBytes)} / ${formatFileSize(size)}"
        else -> formatFileSize(task.downloadedBytes)
    }
    return if (task.status == DownloadStatus.DOWNLOADING && task.bytesPerSecond > 0) {
        "$amount · ${formatSpeed(task.bytesPerSecond)}"
    } else {
        amount
    }
}

fun StoreApp.toDownloadRequest(): DownloadRequest? = download?.let {
    DownloadRequest(
        appId = id,
        appName = name,
        packageName = it.packageName,
        versionName = versionName,
        versionCode = it.versionCode,
        url = it.url,
        sha256 = it.sha256,
        sizeBytes = sizeBytes,
    )
}
