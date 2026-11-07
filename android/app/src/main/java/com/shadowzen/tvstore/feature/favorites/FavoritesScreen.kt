package com.shadowzen.tvstore.feature.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.shadowzen.tvstore.core.designsystem.AppIcon
import com.shadowzen.tvstore.core.designsystem.StoreColors
import com.shadowzen.tvstore.core.designsystem.StoreDimens
import com.shadowzen.tvstore.core.designsystem.pointerClick
import com.shadowzen.tvstore.core.model.StoreApp

/**
 * 收藏页（PRD 5.8、UI-DESIGN 4）：
 * - 展示本地收藏的应用列表；
 * - 支持直接进入详情页和取消收藏（取消收藏后焦点自动移到相邻收藏项，若列表清空则回到顶部导航）。
 */
@Composable
fun FavoritesScreen(
    favoriteApps: List<StoreApp>,
    categoryName: (String) -> String,
    pendingAppId: String?,
    onFocusRestored: () -> Unit,
    onOpenDetail: (StoreApp) -> Unit,
    onRemoveFavorite: (StoreApp) -> Unit,
    onListEmptied: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryFocus = remember { mutableMapOf<String, FocusRequester>() }
    var focusIndexAfterRemove by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(favoriteApps) {
        val index = focusIndexAfterRemove ?: return@LaunchedEffect
        focusIndexAfterRemove = null
        if (favoriteApps.isEmpty()) {
            onListEmptied()
        } else {
            primaryFocus[favoriteApps[index.coerceAtMost(favoriteApps.lastIndex)].id]?.requestFocus()
        }
    }

    LaunchedEffect(pendingAppId, favoriteApps) {
        val target = pendingAppId ?: return@LaunchedEffect
        val requester = primaryFocus[target] ?: return@LaunchedEffect
        requester.requestFocus()
        onFocusRestored()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = StoreDimens.SafeHorizontal, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "我的收藏", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(16.dp))
            Text(text = "${favoriteApps.size} 个应用", fontSize = 16.sp, color = StoreColors.TextSecondary)
        }
        Spacer(Modifier.height(16.dp))

        if (favoriteApps.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "暂无收藏应用。在应用详情页选择“收藏”后，应用会显示在这里。",
                    fontSize = 18.sp,
                    color = StoreColors.TextSecondary,
                )
            }
            return@Column
        }

        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            favoriteApps.forEachIndexed { index, app ->
                key(app.id) {
                    val requester = remember { FocusRequester() }
                    DisposableEffect(requester) {
                        primaryFocus[app.id] = requester
                        onDispose { primaryFocus.remove(app.id) }
                    }
                    FavoriteRow(
                        app = app,
                        categoryTitle = categoryName(app.categoryId),
                        primaryFocusRequester = requester,
                        onOpenDetail = { onOpenDetail(app) },
                        onRemove = {
                            focusIndexAfterRemove = index
                            onRemoveFavorite(app)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoriteRow(
    app: StoreApp,
    categoryTitle: String,
    primaryFocusRequester: FocusRequester,
    onOpenDetail: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(StoreDimens.CardShape)
            .background(StoreColors.CardBackground)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app = app, size = 64.dp)
        Spacer(Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = app.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(12.dp))
                Text(text = "v${app.versionName}", fontSize = 15.sp, color = StoreColors.TextSecondary)
                Spacer(Modifier.width(12.dp))
                Text(text = categoryTitle, fontSize = 14.sp, color = StoreColors.AccentSecondary)
            }
            if (app.summary.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = app.summary,
                    fontSize = 15.sp,
                    color = StoreColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(24.dp))
        Button(
            onClick = onOpenDetail,
            modifier = Modifier
                .focusRequester(primaryFocusRequester)
                .pointerClick(onClick = onOpenDetail),
        ) {
            Text(text = "查看详情", fontSize = 17.sp, modifier = Modifier.padding(horizontal = 12.dp))
        }
        Spacer(Modifier.width(12.dp))
        OutlinedButton(
            onClick = onRemove,
            modifier = Modifier.pointerClick(onClick = onRemove),
        ) {
            Text(text = "取消收藏", fontSize = 17.sp)
        }
    }
}
