package com.shadowzen.tvstore.feature.search

import com.shadowzen.tvstore.core.database.DownloadStatus
import com.shadowzen.tvstore.core.database.DownloadTask
import com.shadowzen.tvstore.core.model.AppDownload
import com.shadowzen.tvstore.core.model.CompatibilityStatus
import com.shadowzen.tvstore.core.model.StoreApp
import com.shadowzen.tvstore.feature.downloads.DownloadAction
import com.shadowzen.tvstore.feature.downloads.primaryActionFor
import com.shadowzen.tvstore.feature.downloads.statusLineFor
import com.shadowzen.tvstore.feature.favorites.FavoritesStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SearchAndPrefsTest {
    @get:Rule val tempFolder = TemporaryFolder()

    private val apps = listOf(
        StoreApp(
            id = "smarttube",
            name = "SmartTube",
            summary = "第三方大屏视频客户端",
            description = "适合电视遥控器操作的无广告播放器",
            categoryId = "video",
            versionName = "32.68",
            sizeBytes = 25_000_000,
            minSdk = 21,
            abis = listOf("armeabi-v7a"),
            compatibility = CompatibilityStatus.TESTED,
            knownIssues = null,
            download = AppDownload("org.smarttube.stable", 2458, "https://example.com/st.apk", "a".repeat(64)),
        ),
        StoreApp(
            id = "hongguotv",
            name = "红果短剧",
            summary = "电视端短剧观看工具",
            description = "支持遥控器选集与全屏播放",
            categoryId = "video",
            versionName = "0.2.150",
            sizeBytes = 19_000_000,
            minSdk = 24,
            abis = listOf("armeabi-v7a"),
            compatibility = CompatibilityStatus.UNVERIFIED,
            knownIssues = null,
            download = AppDownload("rocks.aston.hongguotv", 149, "https://example.com/hg.apk", "b".repeat(64)),
        ),
    )

    @Test
    fun searchSupportsExactAndFuzzyChineseAndEnglishQueries() {
        assertEquals(listOf("smarttube"), SearchMatcher.search(apps, "Smart").map { it.id })
        assertEquals(listOf("smarttube"), SearchMatcher.search(apps, "stube").map { it.id })
        assertEquals(listOf("hongguotv"), SearchMatcher.search(apps, "红剧").map { it.id })
        assertEquals(listOf("hongguotv"), SearchMatcher.search(apps, "aston").map { it.id })
        assertEquals(listOf("smarttube"), SearchMatcher.search(apps, "遥控器 无广告").map { it.id })
        assertTrue(SearchMatcher.search(apps, "不存在的应用").isEmpty())
        assertTrue(SearchMatcher.search(apps, "   ").isEmpty())
    }

    @Test
    fun favoritesStorePersistsAndDeduplicatesInReverseChronologicalOrder() {
        val file = File(tempFolder.root, "favorites.txt")
        val store = FavoritesStore(file)
        assertTrue(store.favoriteIds.value.isEmpty())

        assertTrue(store.toggle("smarttube"))
        assertTrue(store.toggle("hongguotv"))
        assertEquals(listOf("hongguotv", "smarttube"), store.favoriteIds.value)
        assertTrue(store.isFavorite("smarttube"))

        // 重新加载后保持顺序
        val reloaded = FavoritesStore(file)
        assertEquals(listOf("hongguotv", "smarttube"), reloaded.favoriteIds.value)

        // 再次 toggle 取消收藏
        assertFalse(reloaded.toggle("hongguotv"))
        assertEquals(listOf("smarttube"), reloaded.favoriteIds.value)
    }

    @Test
    fun searchHistoryStoreDeduplicatesCapsAndClears() {
        val file = File(tempFolder.root, "history.txt")
        val store = SearchHistoryStore(file, maxEntries = 3)

        store.record("Netflix")
        store.record("SmartTube")
        store.record("netflix")
        assertEquals(listOf("netflix", "SmartTube"), store.history.value)

        store.record("HGTV")
        store.record("Kodi")
        assertEquals(listOf("Kodi", "HGTV", "netflix"), store.history.value)

        val reloaded = SearchHistoryStore(file, maxEntries = 3)
        assertEquals(listOf("Kodi", "HGTV", "netflix"), reloaded.history.value)

        reloaded.clear()
        assertTrue(reloaded.history.value.isEmpty())
        assertFalse(file.exists())
    }

    @Test
    fun installedTaskShowsUpdateWhenCatalogHasNewerVersionCode() {
        val installedTask = DownloadTask(
            appId = "smarttube",
            appName = "SmartTube",
            packageName = "org.smarttube.stable",
            versionName = "32.00",
            versionCode = 2400,
            url = "https://example.com/old.apk",
            sha256 = "a".repeat(64),
            expectedBytes = 1000,
            totalBytes = 1000,
            downloadedBytes = 1000,
            bytesPerSecond = 0,
            status = DownloadStatus.INSTALLED,
            errorMessage = null,
            validator = null,
            createdAt = 1,
            updatedAt = 1,
        )

        assertEquals(
            DownloadAction.OPEN,
            primaryActionFor(installedTask, hasPackage = true, latestVersionCode = 2400),
        )
        assertEquals(
            DownloadAction.UPDATE,
            primaryActionFor(installedTask, hasPackage = true, latestVersionCode = 2458),
        )
        val line = statusLineFor(
            task = installedTask,
            installState = null,
            notice = null,
            latestVersionName = "32.68",
            latestVersionCode = 2458,
        )
        assertEquals("发现新版本 v32.68（已安装 v32.00）", line.text)
    }
}
