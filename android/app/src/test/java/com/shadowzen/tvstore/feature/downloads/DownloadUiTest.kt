package com.shadowzen.tvstore.feature.downloads

import com.shadowzen.tvstore.core.database.DownloadStatus
import com.shadowzen.tvstore.core.database.DownloadTask
import com.shadowzen.tvstore.core.designsystem.StoreColors
import com.shadowzen.tvstore.core.installer.InstallState
import com.shadowzen.tvstore.core.model.AppDownload
import com.shadowzen.tvstore.core.sample.SampleCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadUiTest {

    private fun task(status: DownloadStatus, downloaded: Long = 0, speed: Long = 0, error: String? = null) = DownloadTask(
        appId = "app", appName = "应用", packageName = "com.example.app", versionName = "1.0", versionCode = 1,
        url = "https://example.com/a.apk", sha256 = "a".repeat(64), expectedBytes = 4L * 1024 * 1024,
        totalBytes = null, downloadedBytes = downloaded, bytesPerSecond = speed, status = status,
        errorMessage = error, validator = null, createdAt = 0, updatedAt = 0,
    )

    @Test
    fun primaryActionFollowsTaskStatus() {
        assertEquals(DownloadAction.UNAVAILABLE, primaryActionFor(null, hasPackage = false))
        assertEquals(DownloadAction.START, primaryActionFor(null, hasPackage = true))
        assertEquals(DownloadAction.PAUSE, primaryActionFor(task(DownloadStatus.QUEUED), true))
        assertEquals(DownloadAction.PAUSE, primaryActionFor(task(DownloadStatus.DOWNLOADING), true))
        assertEquals(DownloadAction.PAUSE, primaryActionFor(task(DownloadStatus.VERIFYING), true))
        assertEquals(DownloadAction.RESUME, primaryActionFor(task(DownloadStatus.PAUSED), true))
        assertEquals(DownloadAction.RETRY, primaryActionFor(task(DownloadStatus.FAILED), true))
        assertEquals(DownloadAction.INSTALL, primaryActionFor(task(DownloadStatus.COMPLETED), true))
        assertEquals(DownloadAction.OPEN, primaryActionFor(task(DownloadStatus.INSTALLED), true))
    }

    @Test
    fun sizeLineShowsProgressAndSpeedWhileDownloading() {
        val downloading = task(DownloadStatus.DOWNLOADING, downloaded = 1024L * 1024, speed = 512L * 1024)
        assertEquals("1.0 MB / 4.0 MB · 512 KB/s", sizeLineFor(downloading))
        assertEquals("1.0 MB / 4.0 MB", sizeLineFor(downloading.copy(status = DownloadStatus.PAUSED)))
        assertEquals(0.25f, downloading.progress!!, 0.0001f)
    }

    @Test
    fun statusLinePrefersNoticeThenInstallState() {
        val completed = task(DownloadStatus.COMPLETED)
        assertEquals("提示", statusLineFor(completed, InstallState.Installed, "提示").text)
        assertEquals(StatusLine("安装成功", StoreColors.Success), statusLineFor(completed, InstallState.Installed, null))
        // 下载未完成时忽略旧的安装状态
        assertEquals("已暂停 0%", statusLineFor(task(DownloadStatus.PAUSED), InstallState.Installed, null).text)
        assertEquals(
            "下载失败：网络错误",
            statusLineFor(task(DownloadStatus.FAILED, error = "网络错误"), null, null).text,
        )
    }

    @Test
    fun downloadRequestRequiresPackage() {
        val sample = SampleCatalog.apps.first()
        assertNull(sample.toDownloadRequest())
        val withPackage = sample.copy(
            download = AppDownload("com.example.player", 42, "https://example.com/p.apk", "b".repeat(64)),
        )
        val request = withPackage.toDownloadRequest()!!
        assertEquals(42L, request.versionCode)
        assertEquals(sample.sizeBytes, request.sizeBytes)
    }
}
