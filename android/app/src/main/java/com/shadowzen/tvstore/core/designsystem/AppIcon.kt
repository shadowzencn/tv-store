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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.shadowzen.tvstore.core.model.StoreApp

/** 没有真实图标时使用的统一占位图标：彩色圆角方块加应用名首字。 */
@Composable
fun AppIcon(app: StoreApp, size: Dp, modifier: Modifier = Modifier) {
    val palette = StoreColors.IconPalette
    val color = palette[(app.id.hashCode() and Int.MAX_VALUE) % palette.size]
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.22f))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = app.name.take(1),
            color = Color.White,
            fontSize = (size.value * 0.42f).sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
