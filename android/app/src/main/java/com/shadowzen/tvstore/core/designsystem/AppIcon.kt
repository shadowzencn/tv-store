package com.shadowzen.tvstore.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.shadowzen.tvstore.core.model.StoreApp

/**
 * 应用图标：有 iconUrl 时用 Coil 加载网络图标，否则显示占位图标。
 * 加载中和加载失败都保持底层占位图标可见，不影响既有焦点放大等卡片修饰。
 */
@Composable
fun AppIcon(app: StoreApp, size: Dp, modifier: Modifier = Modifier) {
    AppIcon(
        iconUrl = app.iconUrl,
        appId = app.id,
        appName = app.name,
        size = size,
        modifier = modifier,
    )
}

/** 下载记录等没有 StoreApp、也拿不到图标的场景：始终显示占位图标。 */
@Composable
fun AppIcon(appId: String, appName: String, size: Dp, modifier: Modifier = Modifier) {
    AppIcon(iconUrl = null, appId = appId, appName = appName, size = size, modifier = modifier)
}

@Composable
fun AppIcon(
    iconUrl: String?,
    appId: String,
    appName: String,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.22f))
            .background(Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        PlaceholderIcon(appId = appId, appName = appName, size = size)
        if (iconUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(iconUrl)
                    // 电视端流畅优先，不做淡入过渡
                    .crossfade(false)
                    .build(),
                contentDescription = contentDescription,
                // 不设置 placeholder/error painter：加载中和失败时本层什么都不画，露出占位图标。
                // 请求尺寸由 AsyncImage 按组件的 size 约束自动确定。
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        }
    }
}

/** 没有真实图标时使用的统一占位图标：彩色圆角方块加应用名首字。 */
@Composable
private fun PlaceholderIcon(appId: String, appName: String, size: Dp) {
    val palette = StoreColors.IconPalette
    val color = palette[(appId.hashCode() and Int.MAX_VALUE) % palette.size]
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.22f))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = appName.take(1),
            color = Color.White,
            fontSize = (size.value * 0.42f).sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
