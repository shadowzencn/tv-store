package com.shadowzen.tvstore.feature.favorites

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 本地收藏存储（PRD 5.8）：
 * - 以应用稳定标识（appId / slug）按收藏时间倒序保存，无需登录。
 * - 取消收藏不卸载应用；卸载应用不自动删除收藏记录。
 * - 采用“写临时文件再原子替换”的方式持久化，避免断电留下半截文件。
 */
class FavoritesStore(private val file: File) {

    private val mutableIds = MutableStateFlow(readFromDisk())

    /** 已收藏的 appId 列表（最新收藏的排在前面）。 */
    val favoriteIds: StateFlow<List<String>> = mutableIds.asStateFlow()

    fun isFavorite(appId: String): Boolean = appId in mutableIds.value

    @Synchronized
    fun toggle(appId: String): Boolean {
        val current = mutableIds.value
        val nowFavorite = appId !in current
        val updated = if (nowFavorite) {
            listOf(appId) + current
        } else {
            current.filterNot { it == appId }
        }
        writeToDisk(updated)
        mutableIds.value = updated
        return nowFavorite
    }

    @Synchronized
    fun remove(appId: String) {
        val current = mutableIds.value
        if (appId !in current) return
        val updated = current.filterNot { it == appId }
        writeToDisk(updated)
        mutableIds.value = updated
    }

    private fun readFromDisk(): List<String> = try {
        if (!file.exists()) {
            emptyList()
        } else {
            file.readLines(Charsets.UTF_8)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun writeToDisk(ids: List<String>) {
        val dir = file.parentFile
        if (dir != null && !dir.isDirectory) dir.mkdirs()
        val temp = File(file.path + ".tmp")
        temp.writeText(ids.joinToString("\n"), Charsets.UTF_8)
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
