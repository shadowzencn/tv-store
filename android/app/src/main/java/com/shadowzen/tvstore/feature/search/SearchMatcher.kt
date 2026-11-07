package com.shadowzen.tvstore.feature.search

import com.shadowzen.tvstore.core.model.StoreApp

/**
 * 电视端本地应用搜索（PRD 5.3）：
 * - 支持中英文搜索、多关键词空格分词、大小写不敏感匹配与基础字符子序列模糊匹配（如输入 "stube" 匹配 "SmartTube"、输入 "红剧" 匹配 "红果短剧"）。
 * - 按相关性排序：名称完全/前缀匹配 > 名称包含 > 标识或包名包含 > 简介/说明包含 > 名称模糊子序列匹配；同分保持后台原有顺序。
 */
object SearchMatcher {

    fun search(apps: List<StoreApp>, rawQuery: String): List<StoreApp> {
        val tokens = rawQuery.trim()
            .lowercase()
            .split(WHITESPACE)
            .filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()

        return apps.mapIndexedNotNull { index, app ->
            val score = scoreApp(app, tokens)
            if (score > 0) ScoredApp(app, score, index) else null
        }
            .sortedWith(compareByDescending<ScoredApp> { it.score }.thenBy { it.originalIndex })
            .map { it.app }
    }

    private fun scoreApp(app: StoreApp, tokens: List<String>): Int {
        val name = app.name.lowercase()
        val id = app.id.lowercase()
        val pkg = app.download?.packageName?.lowercase().orEmpty()
        val summary = app.summary.lowercase()
        val description = app.description.lowercase()

        var totalScore = 0
        for (token in tokens) {
            val tokenScore = when {
                name == token -> 100
                name.startsWith(token) -> 80
                token in name -> 60
                id == token || id.startsWith(token) -> 50
                token in id || token in pkg -> 40
                token in summary -> 30
                token in description -> 20
                token.length >= 2 && (isSubsequence(token, name) || isSubsequence(token, id)) -> 10
                else -> 0
            }
            if (tokenScore == 0) return 0
            totalScore += tokenScore
        }
        return totalScore
    }

    /** 判断 [needle] 的每个字符是否按顺序出现在 [haystack] 中（基础模糊匹配）。 */
    private fun isSubsequence(needle: String, haystack: String): Boolean {
        var needleIdx = 0
        for (ch in haystack) {
            if (ch == needle[needleIdx]) {
                needleIdx++
                if (needleIdx == needle.length) return true
            }
        }
        return false
    }

    private data class ScoredApp(val app: StoreApp, val score: Int, val originalIndex: Int)

    private val WHITESPACE = Regex("\\s+")
}
