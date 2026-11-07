package com.shadowzen.tvstore.feature.category

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.shadowzen.tvstore.core.catalog.Catalog
import com.shadowzen.tvstore.core.designsystem.StoreColors
import com.shadowzen.tvstore.core.designsystem.StoreDimens
import com.shadowzen.tvstore.core.model.StoreApp
import com.shadowzen.tvstore.core.model.StoreCategory
import com.shadowzen.tvstore.feature.home.AppCard

const val ALL_CATEGORIES_ID = "__all__"

/**
 * 分类页焦点与选中状态（离开进入详情页再返回时保留当前选中的分类与焦点卡片）。
 */
@Stable
class CategoryFocusState {
    var selectedCategoryId by mutableStateOf(ALL_CATEGORIES_ID)
    var pendingAppId by mutableStateOf<String?>(null)
    val gridListState = LazyListState()
}

@Composable
fun CategoryScreen(
    catalog: Catalog,
    focusState: CategoryFocusState,
    onAppClick: (StoreApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val categories = remember(catalog.categories) {
        listOf(StoreCategory(id = ALL_CATEGORIES_ID, name = "全部")) + catalog.categories
    }
    val activeCategoryId = if (categories.any { it.id == focusState.selectedCategoryId }) {
        focusState.selectedCategoryId
    } else {
        ALL_CATEGORIES_ID
    }
    val filteredApps = remember(catalog.apps, activeCategoryId) {
        if (activeCategoryId == ALL_CATEGORIES_ID) {
            catalog.apps
        } else {
            catalog.apps.filter { it.categoryId == activeCategoryId }
        }
    }
    val rows = remember(filteredApps) { filteredApps.chunked(CARDS_PER_ROW) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 12.dp),
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = StoreDimens.SafeHorizontal, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.focusRestorer(),
        ) {
            items(categories, key = { it.id }) { category ->
                val count = if (category.id == ALL_CATEGORIES_ID) {
                    catalog.apps.size
                } else {
                    catalog.apps.count { it.categoryId == category.id }
                }
                CategoryChip(
                    title = "${category.name} ($count)",
                    selected = category.id == activeCategoryId,
                    onClick = { focusState.selectedCategoryId = category.id },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (filteredApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "该分类暂无上架应用",
                    fontSize = 18.sp,
                    color = StoreColors.TextSecondary,
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = focusState.gridListState,
            contentPadding = PaddingValues(
                start = StoreDimens.SafeHorizontal,
                end = StoreDimens.SafeHorizontal,
                top = 8.dp,
                bottom = 48.dp,
            ),
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

@Composable
private fun CategoryChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) StoreColors.Accent else StoreColors.CardBackground,
            contentColor = StoreColors.TextPrimary,
            focusedContainerColor = if (selected) StoreColors.Accent else StoreColors.SecondaryBackground,
            focusedContentColor = StoreColors.TextPrimary,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, StoreColors.AccentSecondary),
                shape = shape,
            ),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )
    }
}

private const val CARDS_PER_ROW = 5
