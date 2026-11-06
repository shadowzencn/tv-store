package com.shadowzen.tvstore.feature.home

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.tv.material3.Text
import com.shadowzen.tvstore.core.catalog.Catalog
import com.shadowzen.tvstore.core.catalog.CatalogState
import com.shadowzen.tvstore.core.sample.SampleCatalog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 首页目录状态：加载中、无缓存时的错误和重试、空目录。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class, qualifiers = "w960dp-h540dp-land")
class CatalogGateTest {
    @get:Rule val compose = createComposeRule()

    private val sampleCatalog = Catalog(SampleCatalog.categories, SampleCatalog.apps)

    @Test
    fun errorStateRetriesWithOkKeyAndShowsContentAfterSuccess() {
        var state by mutableStateOf(CatalogState(isLoading = true))
        var retries = 0
        var focusPending by mutableStateOf(true)
        compose.setContent {
            CatalogGate(
                state = state,
                isEmpty = { it.apps.isEmpty() },
                onRetry = {
                    retries++
                    focusPending = true
                    state = CatalogState(isLoading = true)
                },
                requestFocus = focusPending,
                onFocusRequested = { focusPending = false },
            ) { catalog -> Text("共 ${catalog.apps.size} 个应用") }
        }

        compose.onNodeWithText("正在加载应用目录…").assertExists()

        state = CatalogState(errorMessage = "无法连接服务器，请检查网络后重试")
        compose.waitForIdle()
        compose.onNodeWithText("应用目录加载失败").assertExists()
        compose.onNodeWithText("无法连接服务器，请检查网络后重试").assertExists()
        compose.onNodeWithText("重试").assertIsFocused()
        pressOk()
        assertEquals(1, retries)
        compose.onNodeWithText("正在加载应用目录…").assertExists()

        state = CatalogState(errorMessage = "服务器内部错误（HTTP 500）")
        compose.waitForIdle()
        compose.onNodeWithText("重试").assertIsFocused()

        state = CatalogState(catalog = sampleCatalog)
        compose.waitForIdle()
        compose.onNodeWithText("共 ${SampleCatalog.apps.size} 个应用").assertExists()
    }

    @Test
    fun emptyCatalogShowsFocusableRefreshButton() {
        var retries = 0
        compose.setContent {
            CatalogGate(
                state = CatalogState(catalog = Catalog(emptyList(), emptyList())),
                isEmpty = { it.apps.isEmpty() },
                onRetry = { retries++ },
                requestFocus = true,
                onFocusRequested = {},
            ) { Text("不应显示") }
        }

        compose.onNodeWithText("暂时还没有可用的应用").assertExists()
        compose.onNodeWithText("刷新").assertIsFocused()
        pressOk()
        assertEquals(1, retries)
        compose.onNodeWithText("不应显示").assertDoesNotExist()
    }

    private fun pressOk() {
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
    }
}
