package com.shadowzen.tvstore.feature.settings

import android.app.Application
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.shadowzen.tvstore.BuildConfig
import com.shadowzen.tvstore.core.catalog.Catalog
import com.shadowzen.tvstore.core.database.DownloadStatus
import com.shadowzen.tvstore.core.database.DownloadTask
import com.shadowzen.tvstore.core.model.AppDownload
import com.shadowzen.tvstore.core.model.CompatibilityStatus
import com.shadowzen.tvstore.core.model.StoreApp
import com.shadowzen.tvstore.core.model.StoreCategory
import com.shadowzen.tvstore.core.network.ClientRelease
import com.shadowzen.tvstore.core.network.ClientVersionResult
import com.shadowzen.tvstore.feature.category.CategoryFocusState
import com.shadowzen.tvstore.feature.category.CategoryScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class, qualifiers = "w960dp-h540dp-land")
class Stage5ScreensTest {
    @get:Rule val compose = createComposeRule()

    private val sampleCatalog = Catalog(
        categories = listOf(
            StoreCategory("video", "影音工具"),
            StoreCategory("system", "系统优化"),
        ),
        apps = listOf(
            StoreApp(
                id = "smarttube",
                name = "SmartTube",
                summary = "视频客户端",
                description = "描述",
                categoryId = "video",
                versionName = "32.68",
                sizeBytes = 25_000_000,
                minSdk = 21,
                abis = listOf("armeabi-v7a"),
                compatibility = CompatibilityStatus.TESTED,
                knownIssues = null,
                download = AppDownload("org.smarttube.stable", 2458, "https://example.com/st.apk", "a".repeat(64)),
            ),
        ),
    )

    @Test
    fun categoryScreenFiltersBySelectedCategory() {
        val focusState = CategoryFocusState()
        compose.setContent {
            CategoryScreen(
                catalog = sampleCatalog,
                focusState = focusState,
                onAppClick = {},
            )
        }

        compose.onNodeWithText("SmartTube").assertExists()
        compose.onNodeWithText("系统优化 (0)").requestFocus().assertIsFocused()
        pressOk()
        compose.onNodeWithText("该分类暂无上架应用").assertExists()
    }

    @Test
    fun settingsScreenDetectsStoreAppUpdatesAndClientSelfUpdate() {
        val installedOldTask = DownloadTask(
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
        val updatedApps = mutableListOf<String>()
        val clientUpdateRequests = mutableListOf<String>()

        compose.setContent {
            SettingsScreen(
                catalogApps = sampleCatalog.apps,
                tasks = listOf(installedOldTask),
                freeSpaceText = "可用空间 10.0 GB",
                searchHistoryCount = 2,
                installTestAvailable = false,
                onUpdateApps = { list -> updatedApps += list.map { it.id } },
                onCheckClientVersion = {
                    ClientVersionResult.Published(
                        ClientRelease(
                            packageName = BuildConfig.APPLICATION_ID,
                            versionName = "9.9.9",
                            versionCode = BuildConfig.VERSION_CODE + 10L,
                            minSdk = 28,
                            abis = listOf("armeabi-v7a"),
                            sizeBytes = 2_500_000,
                            sha256 = "f".repeat(64),
                            signerSha256 = null,
                            downloadUrl = "https://example.com/tvstore.apk",
                            releaseNotes = "阶段 5 更新",
                        ),
                    )
                },
                onEnqueueClientUpdate = { req -> clientUpdateRequests += req.versionName },
                onCleanDownloadCache = { 0 },
                onClearSearchHistory = {},
                onOpenFeedback = {},
                onOpenInstallTest = {},
            )
        }

        // 首项默认获焦：发现 1 个应用可更新
        compose.onNodeWithText("全部更新 (1)").assertIsFocused()
        pressOk()
        assertEquals(listOf("smarttube"), updatedApps)

        // 检查商店客户端更新 -> 发现 9.9.9 -> 点击下载更新
        compose.onNodeWithText("检查更新").requestFocus().assertIsFocused()
        pressOk()
        compose.onNodeWithText("下载更新 v9.9.9").assertExists()
        pressOk()
        assertEquals(listOf("9.9.9"), clientUpdateRequests)
    }

    private fun pressOk() {
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
    }
}
