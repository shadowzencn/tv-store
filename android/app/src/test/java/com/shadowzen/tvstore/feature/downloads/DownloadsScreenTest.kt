package com.shadowzen.tvstore.feature.downloads

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.shadowzen.tvstore.core.database.DownloadStatus
import com.shadowzen.tvstore.core.database.DownloadTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 下载管理页的遥控器操作：主按钮、删除确认和删除后的焦点去向。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class, qualifiers = "w960dp-h540dp-land")
class DownloadsScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun task(appId: String, name: String, status: DownloadStatus, createdAt: Long) = DownloadTask(
        appId = appId, appName = name, packageName = "com.example.$appId", versionName = "1.0", versionCode = 1,
        url = "https://example.com/$appId.apk", sha256 = "a".repeat(64), expectedBytes = 1_000_000,
        totalBytes = null, downloadedBytes = 250_000, bytesPerSecond = 0, status = status,
        errorMessage = null, validator = null, createdAt = createdAt, updatedAt = createdAt,
    )

    @Test
    fun remoteControlDrivesActionsAndDeleteKeepsFocus() {
        var tasks by mutableStateOf(
            listOf(
                task("a", "应用甲", DownloadStatus.DOWNLOADING, 1),
                task("b", "应用乙", DownloadStatus.PAUSED, 2),
            ),
        )
        val actions = mutableListOf<Pair<DownloadAction, String>>()
        var emptied = false
        compose.setContent {
            DownloadsScreen(
                tasks = tasks,
                installStates = emptyMap(),
                notices = emptyMap(),
                freeSpaceText = "可用空间 1.0 GB",
                onAction = { action, task -> actions += action to task.appId },
                onDelete = { task -> tasks = tasks.filterNot { it.appId == task.appId } },
                onListEmptied = { emptied = true },
            )
        }

        compose.onNodeWithText("暂停").requestFocus().assertIsFocused()
        pressOk()
        compose.onNodeWithText("继续").requestFocus()
        pressOk()
        assertEquals(listOf(DownloadAction.PAUSE to "a", DownloadAction.RESUME to "b"), actions)

        // 方向键右移到第一行的删除按钮，第一次确认键只进入确认状态
        compose.onNodeWithText("暂停").requestFocus()
        press(Key.DirectionRight)
        compose.onAllNodesWithText("删除")[0].assertIsFocused()
        pressOk()
        compose.onNodeWithText("确认删除").assertIsFocused()
        assertEquals(2, tasks.size)

        // 焦点离开后取消确认状态
        press(Key.DirectionLeft)
        compose.onNodeWithText("暂停").assertIsFocused()
        assertEquals(0, compose.onAllNodesWithText("确认删除").fetchSemanticsNodes().size)

        // 确认删除后焦点移到剩下任务的主按钮
        press(Key.DirectionRight)
        pressOk()
        pressOk()
        compose.waitForIdle()
        assertEquals(listOf("b"), tasks.map { it.appId })
        compose.onNodeWithText("继续").assertIsFocused()

        press(Key.DirectionRight)
        pressOk()
        pressOk()
        compose.waitForIdle()
        assertTrue(tasks.isEmpty())
        assertTrue(emptied)
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    /** 遥控器确认键。 */
    private fun pressOk() = press(Key.DirectionCenter)
}
