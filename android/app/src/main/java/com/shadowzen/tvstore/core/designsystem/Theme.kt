package com.shadowzen.tvstore.core.designsystem

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/** 电视过扫描安全边距（约为 960×540dp 画面的 5%）。 */
object StoreDimens {
    val SafeHorizontal = 48.dp
    val SafeTop = 24.dp
    val CardShape = RoundedCornerShape(12.dp)
    val ShelfContentPadding = PaddingValues(horizontal = SafeHorizontal, vertical = 14.dp)
}

private val StoreColorScheme = darkColorScheme(
    primary = StoreColors.Accent,
    onPrimary = StoreColors.TextPrimary,
    secondary = StoreColors.AccentSecondary,
    background = StoreColors.PageBackground,
    onBackground = StoreColors.TextPrimary,
    surface = StoreColors.CardBackground,
    onSurface = StoreColors.TextPrimary,
    surfaceVariant = StoreColors.SecondaryBackground,
    onSurfaceVariant = StoreColors.TextSecondary,
    border = StoreColors.Divider,
    error = StoreColors.Error,
)

@Composable
fun TvStoreTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = StoreColorScheme, content = content)
}
