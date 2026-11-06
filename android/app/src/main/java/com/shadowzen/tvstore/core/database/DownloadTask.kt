package com.shadowzen.tvstore.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    VERIFYING,
    PAUSED,
    FAILED,
    COMPLETED,
    INSTALLED,
}

/** 每个应用最多一个下载任务。[expectedBytes] 来自商店数据，[totalBytes] 来自服务器响应。 */
@Entity(tableName = "download_tasks")
data class DownloadTask(
    @PrimaryKey val appId: String,
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val url: String,
    val sha256: String,
    val expectedBytes: Long?,
    val totalBytes: Long?,
    val downloadedBytes: Long,
    val bytesPerSecond: Long,
    val status: DownloadStatus,
    val errorMessage: String?,
    val validator: String?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val sizeBytes: Long?
        get() = totalBytes ?: expectedBytes

    /** 0 到 1 之间；大小未知时返回 null。 */
    val progress: Float?
        get() {
            val size = sizeBytes ?: return null
            if (size <= 0) return null
            return (downloadedBytes.toFloat() / size).coerceIn(0f, 1f)
        }
}
