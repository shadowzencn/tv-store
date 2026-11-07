package com.shadowzen.tvstore.feature.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.shadowzen.tvstore.core.catalog.Catalog
import com.shadowzen.tvstore.core.designsystem.StoreColors
import com.shadowzen.tvstore.core.designsystem.StoreDimens
import com.shadowzen.tvstore.core.designsystem.pointerClick
import com.shadowzen.tvstore.core.model.StoreApp
import com.shadowzen.tvstore.feature.home.AppCard

private val KEYPAD_ROWS = listOf(
    listOf("A", "B", "C", "D", "E", "F"),
    listOf("G", "H", "I", "J", "K", "L"),
    listOf("M", "N", "O", "P", "Q", "R"),
    listOf("S", "T", "U", "V", "W", "X"),
    listOf("Y", "Z", "1", "2", "3", "4"),
    listOf("5", "6", "7", "8", "9", "0"),
)

@Stable
class SearchFocusState {
    var query by mutableStateOf("")
    var pendingAppId by mutableStateOf<String?>(null)
    val resultListState = LazyListState()
}

@Composable
fun SearchScreen(
    catalog: Catalog,
    history: List<String>,
    focusState: SearchFocusState,
    onRecordQuery: (String) -> Unit,
    onClearHistory: () -> Unit,
    onAppClick: (StoreApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val query = focusState.query
    val results = remember(catalog.apps, query) { SearchMatcher.search(catalog.apps, query) }
    val rows = remember(results) { results.chunked(CARDS_PER_ROW) }
    var inputFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = StoreDimens.SafeHorizontal, vertical = 12.dp),
    ) {
        // 左侧：搜索输入框 + 电视屏幕软键盘（支持遥控器方向键点按与鼠标点击）
        Column(
            modifier = Modifier
                .width(276.dp)
                .fillMaxHeight(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(StoreColors.CardBackground)
                    .border(
                        width = if (inputFocused) 2.dp else 1.dp,
                        color = if (inputFocused) StoreColors.Accent else StoreColors.Divider,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = "拼音首字母或关键词（如 HG、ST）",
                        fontSize = 14.sp,
                        color = StoreColors.TextSecondary,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = { focusState.query = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = StoreColors.TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    cursorBrush = SolidColor(StoreColors.Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { if (query.isNotBlank()) onRecordQuery(query) },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { inputFocused = it.isFocused }
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown) {
                                when (event.key) {
                                    Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                                    Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
                                    Key.DirectionRight -> focusManager.moveFocus(FocusDirection.Right)
                                    else -> false
                                }
                            } else {
                                false
                            }
                        },
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val backspaceAction = {
                    if (focusState.query.isNotEmpty()) {
                        focusState.query = focusState.query.dropLast(1)
                    }
                }
                val clearAction = { focusState.query = "" }
                ActionKeyButton(
                    label = "退格",
                    onClick = backspaceAction,
                    modifier = Modifier.weight(1f),
                )
                ActionKeyButton(
                    label = "清空",
                    onClick = clearAction,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                KEYPAD_ROWS.forEach { rowKeys ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        rowKeys.forEach { keyChar ->
                            KeyCap(
                                label = keyChar,
                                onClick = { focusState.query += keyChar },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "支持首字母缩写（如 HG → HGTV / 红果短剧）",
                fontSize = 12.sp,
                color = StoreColors.TextSecondary,
            )
        }

        Spacer(Modifier.width(28.dp))

        // 右侧：搜索历史 + 实时匹配结果网格
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            if (history.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "搜索历史：",
                        fontSize = 14.sp,
                        color = StoreColors.TextSecondary,
                    )
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) {
                        items(history, key = { it }) { item ->
                            HistoryChip(
                                text = item,
                                onClick = {
                                    focusState.query = item
                                    onRecordQuery(item)
                                },
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onClearHistory,
                        modifier = Modifier.pointerClick(onClick = onClearHistory),
                    ) {
                        Text(text = "清除历史", fontSize = 13.sp)
                    }
                }
            }

            when {
                query.isBlank() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "请使用左侧屏幕键盘点选字母/数字，或直接输入关键词搜索应用",
                            fontSize = 17.sp,
                            color = StoreColors.TextSecondary,
                        )
                    }
                }
                results.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "未找到与“${query.trim()}”相关的应用",
                            fontSize = 17.sp,
                            color = StoreColors.TextSecondary,
                        )
                    }
                }
                else -> {
                    Text(
                        text = "找到 ${results.size} 个匹配应用（关键词：${query.trim()}）",
                        fontSize = 15.sp,
                        color = StoreColors.TextSecondary,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = focusState.resultListState,
                        contentPadding = PaddingValues(top = 4.dp, bottom = 36.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(rows, key = { row -> row.first().id }) { rowApps ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                rowApps.forEach { app ->
                                    AppCard(
                                        app = app,
                                        requestFocus = focusState.pendingAppId == app.id,
                                        onFocusRequested = { focusState.pendingAppId = null },
                                        onClick = {
                                            onRecordQuery(query)
                                            focusState.pendingAppId = app.id
                                            onAppClick(app)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyCap(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(34.dp)
            .pointerClick(onClick = onClick),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = StoreColors.CardBackground,
            contentColor = StoreColors.TextPrimary,
            focusedContainerColor = StoreColors.Accent,
            focusedContentColor = StoreColors.TextPrimary,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, StoreColors.AccentSecondary),
                shape = shape,
            ),
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ActionKeyButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(34.dp)
            .pointerClick(onClick = onClick),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = StoreColors.SecondaryBackground,
            contentColor = StoreColors.TextPrimary,
            focusedContainerColor = StoreColors.Accent,
            focusedContentColor = StoreColors.TextPrimary,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, StoreColors.AccentSecondary),
                shape = shape,
            ),
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun HistoryChip(
    text: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        onClick = onClick,
        modifier = Modifier.pointerClick(onClick = onClick),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = StoreColors.CardBackground,
            contentColor = StoreColors.TextPrimary,
            focusedContainerColor = StoreColors.SecondaryBackground,
            focusedContentColor = StoreColors.TextPrimary,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, StoreColors.Accent),
                shape = shape,
            ),
        ),
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

private const val CARDS_PER_ROW = 3
