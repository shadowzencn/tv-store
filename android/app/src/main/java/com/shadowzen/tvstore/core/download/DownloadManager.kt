package com.shadowzen.tvstore.core.download

import com.shadowzen.tvstore.core.database.DownloadStatus
import com.shadowzen.tvstore.core.database.DownloadTask
import com.shadowzen.tvstore.core.database.DownloadTaskDao
import kotlinx.coroutines.flow.Flow

data class DownloadRequest(
    val appId: String,
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val url: String,
    val sha256: String,
    val sizeBytes: Long?,
) {
    init {
        require(SHA256_PATTERN.matches(sha256)) { "SHA-256 必须是 64 位十六进制字符串" }
        require(url.startsWith("https://") || url.startsWith("http://")) { "下载地址必须是 HTTP(S)" }
    }

    private companion object {
        val SHA256_PATTERN = Regex("[0-9a-fA-F]{64}")
    }
}

fun interface DownloadScheduler {
    /** 确保下载队列会被处理。可以重复调用。 */
    fun schedule()
}

/** 下载任务的增删改入口。只改数据库状态并触发调度，实际下载由 [DownloadQueueRunner] 完成。 */
class DownloadManager(
    private val dao: DownloadTaskDao,
    private val files: DownloadFiles,
    private val scheduler: DownloadScheduler,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val tasks: Flow<List<DownloadTask>> = dao.observeAll()

    fun task(appId: String): Flow<DownloadTask?> = dao.observe(appId)

    suspend fun get(appId: String): DownloadTask? = dao.get(appId)

    suspend fun enqueue(request: DownloadRequest) {
        val existing = dao.get(request.appId)
        if (existing != null && existing.versionCode == request.versionCode &&
            existing.sha256.equals(request.sha256, ignoreCase = true)
        ) {
            if (existing.status == DownloadStatus.PAUSED || existing.status == DownloadStatus.FAILED) resume(request.appId)
            return
        }
        // 同一应用的旧版本任务直接替换
        if (existing != null) {
            dao.delete(existing.appId)
            files.delete(existing)
        }
        val now = clock()
        dao.upsert(
            DownloadTask(
                appId = request.appId,
                appName = request.appName,
                packageName = request.packageName,
                versionName = request.versionName,
                versionCode = request.versionCode,
                url = request.url,
                sha256 = request.sha256.lowercase(),
                expectedBytes = request.sizeBytes,
                totalBytes = null,
                downloadedBytes = 0,
                bytesPerSecond = 0,
                status = DownloadStatus.QUEUED,
                errorMessage = null,
                validator = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
        scheduler.schedule()
    }

    suspend fun pause(appId: String): Boolean =
        dao.transition(appId, ACTIVE, DownloadStatus.PAUSED, null, clock()) > 0

    /** 继续已暂停的任务，或重试失败的任务。 */
    suspend fun resume(appId: String): Boolean {
        val changed = dao.transition(
            appId,
            listOf(DownloadStatus.PAUSED, DownloadStatus.FAILED),
            DownloadStatus.QUEUED,
            null,
            clock(),
        ) > 0
        if (changed) scheduler.schedule()
        return changed
    }

    /**
     * 删除任务和文件。正在下载的任务由下载器在发现记录消失、停止写入后删除文件，
     * 避免与仍在写入的文件冲突。
     */
    suspend fun delete(appId: String) {
        val task = dao.get(appId) ?: return
        dao.delete(appId)
        if (task.status != DownloadStatus.DOWNLOADING && task.status != DownloadStatus.VERIFYING) files.delete(task)
    }

    /** 安装成功后删除安装包，保留“已安装”记录。 */
    suspend fun markInstalled(appId: String) {
        val task = dao.get(appId) ?: return
        if (dao.transition(appId, listOf(DownloadStatus.COMPLETED), DownloadStatus.INSTALLED, null, clock()) > 0) {
            files.delete(task)
        }
    }

    /** 下载完成的安装包文件不见了（例如被清理），标记失败，重试时重新下载。 */
    suspend fun markFileMissing(appId: String) {
        dao.transition(
            appId,
            listOf(DownloadStatus.COMPLETED),
            DownloadStatus.FAILED,
            "安装包文件已丢失，请重试下载",
            clock(),
        )
    }

    /** 进程启动时调用：有未完成的任务就重新调度。 */
    suspend fun resumePendingWork() {
        if (dao.countPending() > 0) scheduler.schedule()
    }

    companion object {
        val ACTIVE = listOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING, DownloadStatus.VERIFYING)
    }
}
