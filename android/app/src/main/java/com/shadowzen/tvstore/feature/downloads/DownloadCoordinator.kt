package com.shadowzen.tvstore.feature.downloads

import android.content.Context
import android.util.Log
import com.shadowzen.tvstore.core.database.DownloadStatus
import com.shadowzen.tvstore.core.database.DownloadTask
import com.shadowzen.tvstore.core.download.DownloadFiles
import com.shadowzen.tvstore.core.download.DownloadManager
import com.shadowzen.tvstore.core.installer.StoreInstaller
import com.shadowzen.tvstore.core.installer.launchInstalledApp
import com.shadowzen.tvstore.core.model.StoreApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** 把界面上的下载和安装操作转给下载管理器与安装器。操作在进程级作用域执行，离开页面也不会中断。 */
class DownloadCoordinator(
    private val context: Context,
    private val manager: DownloadManager,
    private val files: DownloadFiles,
    private val installer: StoreInstaller,
    private val scope: CoroutineScope,
) {
    private val mutableNotices = MutableStateFlow<Map<String, String>>(emptyMap())

    /** 操作失败时给用户看的提示，按应用 ID 区分；下一次操作时清除。 */
    val notices: StateFlow<Map<String, String>> = mutableNotices.asStateFlow()

    fun perform(action: DownloadAction, appId: String, task: DownloadTask?, app: StoreApp?) {
        setNotice(appId, null)
        scope.launch {
            try {
                when (action) {
                    DownloadAction.START -> app?.toDownloadRequest()?.let { manager.enqueue(it) }
                    DownloadAction.PAUSE -> manager.pause(appId)
                    DownloadAction.RESUME, DownloadAction.RETRY -> {
                        installer.clear(appId)
                        manager.resume(appId)
                    }
                    DownloadAction.INSTALL -> task?.let { install(it) }
                    DownloadAction.OPEN -> task?.let { open(it.packageName, appId) }
                    DownloadAction.UNAVAILABLE -> setNotice(appId, "示例数据没有安装包，接入服务端后才能下载。")
                }
            } catch (e: IOException) {
                Log.e(TAG, "下载操作失败：$action", e)
                setNotice(appId, "操作失败：${e.message}")
            }
        }
    }

    fun delete(appId: String) {
        setNotice(appId, null)
        installer.clear(appId)
        scope.launch {
            try {
                manager.delete(appId)
            } catch (e: IOException) {
                Log.e(TAG, "删除下载任务失败", e)
                setNotice(appId, "删除文件失败：${e.message}")
            }
        }
    }

    /** 下载完成后自动安装；只在界面处于前台时调用，系统安装界面才能正常弹出。 */
    suspend fun installCompleted(appId: String) {
        val task = manager.get(appId) ?: return
        if (task.status == DownloadStatus.COMPLETED) install(task)
    }

    private suspend fun install(task: DownloadTask) {
        val apk = files.apkFile(task)
        if (!apk.exists()) {
            manager.markFileMissing(task.appId)
            return
        }
        installer.install(task.appId, apk, task.packageName)
    }

    private suspend fun open(packageName: String, appId: String) {
        val opened = withContext(Dispatchers.Main) { launchInstalledApp(context, packageName) }
        if (!opened) setNotice(appId, "找不到该应用的启动入口，可能已被卸载")
    }

    private fun setNotice(appId: String, notice: String?) {
        mutableNotices.update { if (notice == null) it - appId else it + (appId to notice) }
    }

    private companion object {
        const val TAG = "DownloadCoordinator"
    }
}
