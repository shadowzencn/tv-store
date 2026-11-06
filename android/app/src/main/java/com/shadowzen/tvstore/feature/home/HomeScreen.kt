package com.shadowzen.tvstore.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.shadowzen.tvstore.core.designsystem.StoreColors
import com.shadowzen.tvstore.core.designsystem.StoreDimens
import com.shadowzen.tvstore.core.model.StoreApp

const val FEATURED_SHELF_ID = "featured"

fun homeFocusKey(shelfId: String, appId: String) = "$shelfId/$appId"

/**
 * 首页离开组合（进入详情页）后仍需保留的滚动位置和待恢复焦点，
 * 因此由上层持有，而不是在 HomeScreen 内部 remember。
 */
@Stable
class HomeFocusState(initialFocusKey: String? = null) {
    val columnState = LazyListState()
    private val shelfStates = mutableMapOf<String, LazyListState>()

    var pendingFocusKey by mutableStateOf(initialFocusKey)

    /** 首次拿到目录内容时是否已把焦点交给第一张卡片。 */
    var initialFocusAssigned = initialFocusKey != null

    fun shelfState(shelfId: String): LazyListState = shelfStates.getOrPut(shelfId) { LazyListState() }
}

@Composable
fun HomeScreen(
    content: HomeContent,
    categoryName: (String) -> String,
    focusState: HomeFocusState,
    onAppClick: (shelfId: String, app: StoreApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = focusState.columnState,
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        if (content.featured.isNotEmpty()) {
            item(key = FEATURED_SHELF_ID) {
                Shelf(
                    shelfId = FEATURED_SHELF_ID,
                    title = "精选推荐",
                    apps = content.featured,
                    focusState = focusState,
                    onAppClick = onAppClick,
                ) { app, requestFocus, onFocusRequested, onClick ->
                    FeaturedAppCard(app, categoryName(app.categoryId), requestFocus, onFocusRequested, onClick)
                }
            }
        }
        items(content.shelves, key = { it.id }) { shelf ->
            Shelf(
                shelfId = shelf.id,
                title = shelf.title,
                apps = shelf.apps,
                focusState = focusState,
                onAppClick = onAppClick,
            ) { app, requestFocus, onFocusRequested, onClick ->
                AppCard(app, requestFocus, onFocusRequested, onClick)
            }
        }
    }
}

@Composable
private fun Shelf(
    shelfId: String,
    title: String,
    apps: List<StoreApp>,
    focusState: HomeFocusState,
    onAppClick: (shelfId: String, app: StoreApp) -> Unit,
    card: @Composable (app: StoreApp, requestFocus: Boolean, onFocusRequested: () -> Unit, onClick: () -> Unit) -> Unit,
) {
    Column {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = StoreDimens.SafeHorizontal, top = 12.dp),
        )
        if (apps.isEmpty()) {
            Text(
                text = "该分类暂无应用",
                fontSize = 15.sp,
                color = StoreColors.TextSecondary,
                modifier = Modifier.padding(horizontal = StoreDimens.SafeHorizontal, vertical = 24.dp),
            )
            return@Column
        }
        LazyRow(
            state = focusState.shelfState(shelfId),
            contentPadding = StoreDimens.ShelfContentPadding,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.focusRestorer(),
        ) {
            items(apps, key = { it.id }) { app ->
                val key = homeFocusKey(shelfId, app.id)
                card(
                    app,
                    focusState.pendingFocusKey == key,
                    { focusState.pendingFocusKey = null },
                    { onAppClick(shelfId, app) },
                )
            }
        }
    }
}
