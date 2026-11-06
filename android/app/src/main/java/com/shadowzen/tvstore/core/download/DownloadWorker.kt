package com.shadowzen.tvstore.core.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.shadowzen.tvstore.TvStoreApplication
import java.io.IOException
import java.util.concurrent.TimeUnit

/** WorkManager 只负责在合适的时候启动队列处理，下载本身由 [DownloadQueueRunner] 完成。 */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as TvStoreApplication).container
        try {
            // 长时间下载需要前台服务，否则 WorkManager 会在约 10 分钟后停止任务
            setForeground(getForegroundInfo())
        } catch (e: IllegalStateException) {
            // Android 12 起应用在后台时不允许启动前台服务；继续下载，被停止后会自动重新调度并续传
            Log.w(TAG, "无法进入前台下载，改为普通后台任务", e)
        }
        return try {
            container.downloadQueueRunner.run()
            Result.success()
        } catch (e: IOException) {
            // 删除文件失败等存储错误；中断的任务会在下次调度时放回队列
            Log.e(TAG, "下载队列处理失败", e)
            Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = buildNotification(applicationContext)
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(context: Context): Notification {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "应用下载", NotificationManager.IMPORTANCE_LOW),
            )
        }
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("TV Store")
            .setContentText("正在下载应用")
            .setOngoing(true)
            .build()
    }

    private companion object {
        const val TAG = "DownloadWorker"
        const val CHANNEL_ID = "downloads"
        const val NOTIFICATION_ID = 1001
    }
}

class WorkManagerDownloadScheduler(private val context: Context) : DownloadScheduler {
    override fun schedule() {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // 追加到正在运行的队列处理之后，保证新任务不会因为当前处理恰好结束而被漏掉
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    private companion object {
        const val UNIQUE_WORK = "download-queue"
    }
}
