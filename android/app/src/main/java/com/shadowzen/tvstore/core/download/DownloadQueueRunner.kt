package com.shadowzen.tvstore.core.download

import com.shadowzen.tvstore.core.database.DownloadStatus
import com.shadowzen.tvstore.core.database.DownloadTask
import com.shadowzen.tvstore.core.database.DownloadTaskDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 按创建顺序逐个处理排队的任务。数据库是任务状态的唯一来源：
 * 用户暂停或删除只改数据库，这里监听到后停止当前下载。
 * 同一时间只能有一个实例在运行（由 WorkManager 唯一任务保证）。
 */
class DownloadQueueRunner(
    private val dao: DownloadTaskDao,
    private val engine: DownloadEngine,
    private val files: DownloadFiles,
    private val retryDelaysMillis: List<Long> = listOf(2_000, 5_000, 10_000),
    private val clock: () -> Long = System::currentTimeMillis,
    private val onCompleted: (DownloadTask) -> Unit = {},
    private val onError: (String, Throwable) -> Unit = { _, _ -> },
) {

    suspend fun run() {
        // 只有本类会把任务设为下载中，运行开始时仍是下载中的任务一定是上次被中断的
        dao.requeueInterrupted(clock())
        try {
            while (true) {
                currentCoroutineContext().ensureActive()
                val task = dao.nextQueued() ?: return
                if (dao.transition(task.appId, listOf(DownloadStatus.QUEUED), DownloadStatus.DOWNLOADING, null, clock()) == 0) {
                    continue
                }
                process(task.appId)
            }
        } catch (e: CancellationException) {
            // 被系统停止（如网络断开、进程即将结束），放回队列等待下次调度
            withContext(NonCancellable) { dao.requeueInterrupted(clock()) }
            throw e
        }
    }

    private suspend fun process(appId: String) {
        var retries = 0
        while (true) {
            val task = dao.get(appId) ?: return
            if (task.status !in RUNNING) return
            val result = try {
                downloadUntilStopped(task)
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                // 程序缺陷或系统接口异常：记录并让这个任务失败，队列中的其他任务继续处理
                onError("下载任务 $appId 出现内部错误", e)
                DownloadResult.Failure(DownloadError.INTERNAL, "下载出现内部错误：${e.javaClass.simpleName}")
            }
            when (result) {
                null -> {
                    cleanUpIfDeleted(task)
                    return
                }
                is DownloadResult.Success -> {
                    val completed = dao.transition(appId, RUNNING, DownloadStatus.COMPLETED, null, clock()) > 0
                    if (completed) {
                        onCompleted(task)
                    } else {
                        cleanUpIfDeleted(task)
                    }
                    return
                }
                is DownloadResult.Failure -> {
                    if (!result.error.retryable || retries >= retryDelaysMillis.size) {
                        dao.transition(appId, RUNNING, DownloadStatus.FAILED, result.message, clock())
                        return
                    }
                    val delayMillis = retryDelaysMillis[retries++]
                    val note = "${result.message}，${delayMillis / 1000} 秒后自动重试（$retries/${retryDelaysMillis.size}）"
                    if (dao.transition(appId, RUNNING, DownloadStatus.DOWNLOADING, note, clock()) == 0) {
                        cleanUpIfDeleted(task)
                        return
                    }
                    if (waitUnlessStopped(appId, delayMillis)) {
                        cleanUpIfDeleted(task)
                        return
                    }
                }
            }
        }
    }

    /** 返回 null 表示用户暂停或删除了任务。 */
    private suspend fun downloadUntilStopped(task: DownloadTask): DownloadResult? = coroutineScope {
        val spec = DownloadSpec(
            url = task.url,
            expectedSha256 = task.sha256,
            expectedSize = task.expectedBytes,
            partFile = files.partFile(task),
            targetFile = files.apkFile(task),
            validator = task.validator,
        )
        val download = async { engine.download(spec, TaskListener(task.appId)) }
        val watcher = launch {
            dao.observe(task.appId).first { it == null || it.status !in RUNNING }
            download.cancel()
        }
        try {
            download.await()
        } catch (e: CancellationException) {
            // 外层也被取消时继续抛出；只是 watcher 停止了下载时返回 null
            currentCoroutineContext().ensureActive()
            null
        } finally {
            watcher.cancel()
        }
    }

    /** 等待重试间隔。返回 true 表示等待期间任务被暂停或删除。 */
    private suspend fun waitUnlessStopped(appId: String, delayMillis: Long): Boolean =
        withTimeoutOrNull(delayMillis) {
            dao.observe(appId).first { it == null || it.status !in RUNNING }
        } != null

    private suspend fun cleanUpIfDeleted(task: DownloadTask) {
        if (dao.get(task.appId) == null) files.delete(task)
    }

    private inner class TaskListener(private val appId: String) : DownloadListener {
        override suspend fun onResponse(totalBytes: Long?, validator: String?, offset: Long) {
            dao.updateResponse(appId, totalBytes, validator, offset, clock())
        }

        override suspend fun onProgress(downloadedBytes: Long, totalBytes: Long?, bytesPerSecond: Long) {
            dao.updateProgress(appId, downloadedBytes, bytesPerSecond, clock())
        }

        override suspend fun onVerifying() {
            dao.transition(appId, listOf(DownloadStatus.DOWNLOADING), DownloadStatus.VERIFYING, null, clock())
        }
    }

    private companion object {
        val RUNNING = listOf(DownloadStatus.DOWNLOADING, DownloadStatus.VERIFYING)
    }
}
