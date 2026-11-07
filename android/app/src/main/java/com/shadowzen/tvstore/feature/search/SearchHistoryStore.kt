package com.shadowzen.tvstore.feature.search

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 本地搜索历史存储（PRD 5.3、5.9）：
 * - 保存最近搜索的关键词（不区分大小写去重，最新排最前，最多保留 [maxEntries] 条）。
 * - 支持在搜索页或设置页一键清除。
 */
class SearchHistoryStore(
    private val file: File,
    private val maxEntries: Int = 10,
) {
    private val mutableHistory = MutableStateFlow(readFromDisk())

    val history: StateFlow<List<String>> = mutableHistory.asStateFlow()

    @Synchronized
    fun record(query: String) {
        val normalized = query.trim().replace('\n', ' ').replace('\r', ' ')
        if (normalized.isEmpty()) return
        val updated = (listOf(normalized) + mutableHistory.value.filterNot { it.equals(normalized, ignoreCase = true) })
            .take(maxEntries)
        writeToDisk(updated)
        mutableHistory.value = updated
    }

    @Synchronized
    fun clear() {
        if (file.exists()) file.delete()
        mutableHistory.value = emptyList()
    }

    private fun readFromDisk(): List<String> = try {
        if (!file.exists()) {
            emptyList()
        } else {
            file.readLines(Charsets.UTF_8)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .take(maxEntries)
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun writeToDisk(queries: List<String>) {
        val dir = file.parentFile
        if (dir != null && !dir.isDirectory) dir.mkdirs()
        val temp = File(file.path + ".tmp")
        temp.writeText(queries.joinToString("\n"), Charsets.UTF_8)
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
