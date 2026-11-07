package com.shadowzen.tvstore.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.shadowzen.tvstore.core.designsystem.AppIcon
import com.shadowzen.tvstore.core.designsystem.StoreColors
import com.shadowzen.tvstore.core.designsystem.StoreDimens
import com.shadowzen.tvstore.core.designsystem.pointerClick
import com.shadowzen.tvstore.core.model.StoreApp

@Composable
fun AppCard(
    app: StoreApp,
    requestFocus: Boolean,
    onFocusRequested: () -> Unit,
    onClick: () -> Unit,
) {
    StoreCardSurface(
        width = 150.dp,
        height = 178.dp,
        requestFocus = requestFocus,
        onFocusRequested = onFocusRequested,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppIcon(app = app, size = 68.dp)
            Spacer(Modifier.height(14.dp))
            Text(
                text = app.name,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = app.summary,
                fontSize = 13.sp,
                color = StoreColors.TextSecondary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 17.sp,
            )
        }
    }
}

@Composable
fun FeaturedAppCard(
    app: StoreApp,
    categoryName: String,
    requestFocus: Boolean,
    onFocusRequested: () -> Unit,
    onClick: () -> Unit,
) {
    StoreCardSurface(
        width = 300.dp,
        height = 140.dp,
        requestFocus = requestFocus,
        onFocusRequested = onFocusRequested,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app = app, size = 84.dp)
            Spacer(Modifier.width(18.dp))
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = app.name,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = app.summary,
                    fontSize = 14.sp,
                    color = StoreColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 19.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(text = categoryName, fontSize = 13.sp, color = StoreColors.AccentSecondary)
            }
        }
    }
}

@Composable
private fun StoreCardSurface(
    width: Dp,
    height: Dp,
    requestFocus: Boolean,
    onFocusRequested: () -> Unit,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    if (requestFocus) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
            onFocusRequested()
        }
    }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(width = width, height = height)
            .focusRequester(focusRequester)
            .pointerClick(onClick = onClick),
        shape = ClickableSurfaceDefaults.shape(shape = StoreDimens.CardShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = StoreColors.CardBackground,
            contentColor = StoreColors.TextPrimary,
            focusedContainerColor = StoreColors.SecondaryBackground,
            focusedContentColor = StoreColors.TextPrimary,
            pressedContainerColor = StoreColors.SecondaryBackground,
            pressedContentColor = StoreColors.TextPrimary,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(3.dp, StoreColors.Accent),
                shape = StoreDimens.CardShape,
            ),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        content = content,
    )
}
