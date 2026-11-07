package com.shadowzen.tvstore.core.download

import com.shadowzen.tvstore.core.database.DownloadTask
import java.io.File
import java.io.IOException

/** 下载文件的存放位置。文件名只由应用 ID 和 versionCode 决定，续传时能找回同一个文件。 */
class DownloadFiles(val directory: File) {

    fun partFile(task: DownloadTask): File = File(directory, "${baseName(task)}.apk.part")

    fun apkFile(task: DownloadTask): File = File(directory, "${baseName(task)}.apk")

    fun delete(task: DownloadTask) {
        listOf(partFile(task), apkFile(task)).forEach { file ->
            if (file.exists() && !file.delete()) throw IOException("无法删除下载文件：${file.name}")
        }
    }

    /** 清理已安装完成或已不在活跃任务列表中的残留安装包文件，返回删除的文件数量。 */
    fun cleanOrphans(tasks: List<DownloadTask>): Int {
        val children = directory.listFiles() ?: return 0
        val keepNames = tasks
            .filter { it.status != com.shadowzen.tvstore.core.database.DownloadStatus.INSTALLED }
            .flatMap { listOf(partFile(it).name, apkFile(it).name) }
            .toSet()
        var deleted = 0
        for (file in children) {
            if (file.isFile && file.name !in keepNames && file.delete()) {
                deleted++
            }
        }
        return deleted
    }

    private fun baseName(task: DownloadTask): String =
        task.appId.replace(UNSAFE_CHARS, "_") + "-" + task.versionCode

    private companion object {
        val UNSAFE_CHARS = Regex("[^A-Za-z0-9._-]")
    }
}
