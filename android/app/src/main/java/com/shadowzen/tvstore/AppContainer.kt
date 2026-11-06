package com.shadowzen.tvstore

import android.content.Context
import android.util.Log
import com.shadowzen.tvstore.core.database.StoreDatabase
import com.shadowzen.tvstore.core.download.DownloadEngine
import com.shadowzen.tvstore.core.download.DownloadFiles
import com.shadowzen.tvstore.core.download.DownloadManager
import com.shadowzen.tvstore.core.download.DownloadQueueRunner
import com.shadowzen.tvstore.core.download.StorageSpace
import com.shadowzen.tvstore.core.download.WorkManagerDownloadScheduler
import com.shadowzen.tvstore.core.installer.StoreInstaller
import com.shadowzen.tvstore.core.network.StoreHttpClient
import com.shadowzen.tvstore.feature.downloads.DownloadCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

/** 手动依赖注入：整个进程共享的单例。 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database by lazy { StoreDatabase.create(appContext) }
    private val httpClient by lazy { StoreHttpClient.create() }

    val storageSpace by lazy { StorageSpace(appContext) }

    val downloadFiles by lazy { DownloadFiles(File(appContext.filesDir, "downloads")) }

    private val completedDownloadsFlow = MutableSharedFlow<String>(extraBufferCapacity = 16)

    /** 刚下载完成的应用 ID，界面在前台时据此自动发起安装。 */
    val completedDownloads: SharedFlow<String> = completedDownloadsFlow.asSharedFlow()

    val downloadManager by lazy {
        DownloadManager(database.downloadTaskDao(), downloadFiles, WorkManagerDownloadScheduler(appContext))
    }

    val downloadQueueRunner by lazy {
        DownloadQueueRunner(
            dao = database.downloadTaskDao(),
            engine = DownloadEngine(httpClient, storageSpace::availableBytes),
            files = downloadFiles,
            onCompleted = { completedDownloadsFlow.tryEmit(it.appId) },
            onError = { message, error -> Log.e("DownloadQueueRunner", message, error) },
        )
    }

    val downloadCoordinator by lazy {
        DownloadCoordinator(appContext, downloadManager, downloadFiles, storeInstaller, appScope)
    }

    val storeInstaller by lazy {
        StoreInstaller(appContext, appScope) { appId ->
            appScope.launch {
                try {
                    downloadManager.markInstalled(appId)
                } catch (e: IOException) {
                    Log.e("AppContainer", "安装成功后删除安装包失败", e)
                }
            }
        }
    }
}
