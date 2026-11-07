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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.shadowzen.tvstore.core.catalog.Catalog
import com.shadowzen.tvstore.core.designsystem.StoreColors
import com.shadowzen.tvstore.core.designsystem.StoreDimens
import com.shadowzen.tvstore.core.model.StoreApp
import com.shadowzen.tvstore.feature.home.AppCard

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = StoreDimens.SafeHorizontal, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(StoreDimens.CardShape)
                    .background(StoreColors.CardBackground)
                    .border(
                        width = if (inputFocused) 2.dp else 1.dp,
                        color = if (inputFocused) StoreColors.Accent else StoreColors.Divider,
                        shape = StoreDimens.CardShape,
                    )
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = "输入应用名称、拼音缩写或功能关键词（如 SmartTube、Netflix、播放器）…",
                        fontSize = 17.sp,
                        color = StoreColors.TextSecondary,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = { focusState.query = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = StoreColors.TextPrimary,
                        fontSize = 18.sp,
                    ),
                    cursorBrush = SolidColor(StoreColors.Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { if (query.isNotBlank()) onRecordQuery(query) },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { inputFocused = it.isFocused },
                )
            }

            Spacer(Modifier.width(16.dp))
            Button(
                onClick = { if (query.isNotBlank()) onRecordQuery(query) },
            ) {
                Text(text = "搜索", fontSize = 17.sp, modifier = Modifier.padding(horizontal = 12.dp))
            }

            if (query.isNotEmpty()) {
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = { focusState.query = "" }) {
                    Text(text = "清空", fontSize = 17.sp)
                }
            }
        }

        if (history.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "搜索历史：",
                    fontSize = 15.sp,
                    color = StoreColors.TextSecondary,
                )
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
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
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = onClearHistory) {
                    Text(text = "清除历史", fontSize = 14.sp)
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        when {
            query.isBlank() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "请输入关键词搜索应用，或直接点选上方搜索历史",
                        fontSize = 18.sp,
                        color = StoreColors.TextSecondary,
                    )
                }
            }
            results.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "未找到与“${query.trim()}”相关的应用",
                        fontSize = 18.sp,
                        color = StoreColors.TextSecondary,
                    )
                }
            }
            else -> {
                Text(
                    text = "找到 ${results.size} 个匹配应用",
                    fontSize = 16.sp,
                    color = StoreColors.TextSecondary,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = focusState.resultListState,
                    contentPadding = PaddingValues(top = 6.dp, bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(rows, key = { row -> row.first().id }) { rowApps ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
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

@Composable
private fun HistoryChip(
    text: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        onClick = onClick,
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
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

private const val CARDS_PER_ROW = 5
