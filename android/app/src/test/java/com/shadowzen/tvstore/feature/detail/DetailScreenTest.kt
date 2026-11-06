package com.shadowzen.tvstore.feature.detail

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.shadowzen.tvstore.core.database.DownloadStatus
import com.shadowzen.tvstore.core.database.DownloadTask
import com.shadowzen.tvstore.core.model.AppDownload
import com.shadowzen.tvstore.core.sample.SampleCatalog
import com.shadowzen.tvstore.feature.downloads.DownloadAction
import com.shadowzen.tvstore.feature.downloads.statusLineFor
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 详情页主按钮随下载状态换文字，焦点始终停在按钮上。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class, qualifiers = "w960dp-h540dp-land")
class DetailScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun primaryButtonFollowsDownloadStateAndKeepsFocus() {
        val app = SampleCatalog.apps.first().copy(
            download = AppDownload("com.example.player", 1, "https://example.com/p.apk", "a".repeat(64)),
        )
        var task by mutableStateOf<DownloadTask?>(null)
        val actions = mutableListOf<DownloadAction>()
        compose.setContent {
            DetailScreen(
                app = app,
                categoryName = "影音娱乐",
                task = task,
                statusLine = task?.let { statusLineFor(it, null, null) },
                onPrimaryAction = { actions += it },
            )
        }

        compose.onNodeWithText("下载").assertIsFocused()
        pressOk()

        task = DownloadTask(
            appId = app.id, appName = app.name, packageName = "com.example.player", versionName = app.versionName,
            versionCode = 1, url = "https://example.com/p.apk", sha256 = "a".repeat(64), expectedBytes = 1_000_000,
            totalBytes = 1_000_000, downloadedBytes = 400_000, bytesPerSecond = 0, status = DownloadStatus.DOWNLOADING,
            errorMessage = null, validator = null, createdAt = 1, updatedAt = 1,
        )
        compose.waitForIdle()
        compose.onNodeWithText("暂停").assertIsFocused()
        compose.onNodeWithText("下载中 40%").assertExists()
        pressOk()

        task = task!!.copy(status = DownloadStatus.COMPLETED, downloadedBytes = 1_000_000)
        compose.waitForIdle()
        compose.onNodeWithText("安装").assertIsFocused()
        pressOk()

        assertEquals(listOf(DownloadAction.START, DownloadAction.PAUSE, DownloadAction.INSTALL), actions)
    }

    @Test
    fun sampleAppWithoutPackageReportsUnavailable() {
        val actions = mutableListOf<DownloadAction>()
        compose.setContent {
            DetailScreen(
                app = SampleCatalog.apps.first(),
                categoryName = "影音娱乐",
                task = null,
                statusLine = null,
                onPrimaryAction = { actions += it },
            )
        }
        compose.onNodeWithText("下载").assertIsFocused()
        pressOk()
        assertEquals(listOf(DownloadAction.UNAVAILABLE), actions)
    }

    private fun pressOk() {
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
    }
}
