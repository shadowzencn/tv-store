package com.shadowzen.tvstore.core.download

import android.annotation.SuppressLint
import android.content.Context
import android.os.storage.StorageManager
import android.util.Log
import java.io.File
import java.io.IOException

/** 可用于下载的空间，包含系统可以自动清理的缓存，比 File.usableSpace 更准确。 */
class StorageSpace(context: Context) {
    private val storageManager = context.getSystemService(StorageManager::class.java)

    fun availableBytes(directory: File): Long = try {
        storageManager.getAllocatableBytes(storageManager.getUuidForPath(directory))
    } catch (e: IOException) {
        fallback(directory, e)
    } catch (e: RuntimeException) {
        // 部分定制系统的存储服务实现不完整（如找不到存储卷时内部空指针），不能因此阻止下载
        fallback(directory, e)
    }

    @SuppressLint("UsableSpace")
    private fun fallback(directory: File, cause: Exception): Long {
        Log.w("StorageSpace", "无法查询可分配空间，改用文件系统可用空间：${directory.path}", cause)
        return directory.usableSpace
    }
}
