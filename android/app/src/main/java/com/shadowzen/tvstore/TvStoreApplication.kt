package com.shadowzen.tvstore

import android.app.Application
import android.util.Log
import kotlinx.coroutines.launch

class TvStoreApplication : Application() {

    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // 先显示本地缓存的目录，同时在后台向服务器刷新
        container.refreshCatalog()
        // 进程重启或设备重启后，继续处理上次没有完成的下载
        container.appScope.launch {
            try {
                container.downloadManager.resumePendingWork()
            } catch (e: RuntimeException) {
                Log.e("TvStoreApplication", "恢复下载任务失败", e)
            }
        }
    }
}
