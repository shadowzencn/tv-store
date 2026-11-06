package com.shadowzen.tvstore

import android.content.Context
import android.util.Log
import com.shadowzen.tvstore.core.catalog.CatalogApi
import com.shadowzen.tvstore.core.catalog.CatalogCache
import com.shadowzen.tvstore.core.catalog.CatalogParser
import com.shadowzen.tvstore.core.catalog.CatalogRepository
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
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File
import java.io.IOException

/** 手动依赖注入：整个进程共享的单例。 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database by lazy { StoreDatabase.create(appContext) }
    private val httpClient by lazy { StoreHttpClient.create() }

    /** 服务端基础地址，构建时由 build.gradle.kts 写入并校验格式。 */
    private val serverBaseUrl by lazy { BuildConfig.STORE_SERVER_URL.toHttpUrl() }

    val catalogRepository by lazy {
        CatalogRepository(
            api = CatalogApi(httpClient, serverBaseUrl),
            cache = CatalogCache(File(appContext.filesDir, "catalog/catalog.cache")),
            parse = { body ->
                CatalogParser.parse(body, serverBaseUrl) { skipped -> Log.w("CatalogParser", skipped) }
            },
            onError = { message, error -> Log.e("CatalogRepository", message, error) },
        )
    }

    /** 在进程级作用域刷新目录，离开页面不会中断。 */
    fun refreshCatalog() {
        appScope.launch { catalogRepository.refresh() }
    }

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
