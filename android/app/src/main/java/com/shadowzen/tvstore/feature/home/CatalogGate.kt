package com.shadowzen.tvstore.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.shadowzen.tvstore.core.catalog.Catalog
import com.shadowzen.tvstore.core.catalog.CatalogState
import com.shadowzen.tvstore.core.designsystem.StoreColors
import com.shadowzen.tvstore.core.designsystem.pointerClick

/**
 * 根据目录状态显示加载中、错误（无缓存）、空目录或实际内容。
 * 错误和空状态都有一个可聚焦的按钮，遥控器按确认键即可重新获取目录。
 *
 * @param requestFocus 为 true 时按钮出现后自动获得焦点，随后调用 [onFocusRequested]。
 */
@Composable
fun CatalogGate(
    state: CatalogState,
    isEmpty: (Catalog) -> Boolean,
    onRetry: () -> Unit,
    requestFocus: Boolean,
    onFocusRequested: () -> Unit,
    content: @Composable (Catalog) -> Unit,
) {
    val catalog = state.catalog
    when {
        catalog == null && state.isLoading -> CatalogMessage(title = "正在加载应用目录…", detail = null)
        catalog == null -> CatalogMessage(
            title = "应用目录加载失败",
            titleColor = StoreColors.Error,
            detail = state.errorMessage ?: "请稍后重试",
            actionLabel = "重试",
            onAction = onRetry,
            requestFocus = requestFocus,
            onFocusRequested = onFocusRequested,
        )
        isEmpty(catalog) -> CatalogMessage(
            title = "暂时还没有可用的应用",
            detail = when {
                state.isLoading -> "正在刷新…"
                state.errorMessage != null -> state.errorMessage
                else -> "管理员上架应用后会显示在这里"
            },
            actionLabel = "刷新",
            onAction = onRetry,
            requestFocus = requestFocus,
            onFocusRequested = onFocusRequested,
        )
        else -> content(catalog)
    }
}

@Composable
private fun CatalogMessage(
    title: String,
    detail: String?,
    titleColor: Color = StoreColors.TextPrimary,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    requestFocus: Boolean = false,
    onFocusRequested: () -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }
    if (actionLabel != null && requestFocus) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
            onFocusRequested()
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = title, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = titleColor)
        if (detail != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = detail,
                fontSize = 18.sp,
                color = StoreColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 640.dp),
            )
        }
        if (actionLabel != null) {
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onAction,
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .pointerClick(onClick = onAction),
            ) {
                Text(text = actionLabel, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 24.dp))
            }
        }
    }
}
