package com.shadowzen.tvstore.feature.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.shadowzen.tvstore.core.database.DownloadStatus
import com.shadowzen.tvstore.core.database.DownloadTask
import com.shadowzen.tvstore.core.designsystem.AppIcon
import com.shadowzen.tvstore.core.designsystem.StoreProgressBar
import com.shadowzen.tvstore.core.designsystem.StoreColors
import com.shadowzen.tvstore.core.designsystem.StoreDimens
import com.shadowzen.tvstore.core.designsystem.androidVersionName
import com.shadowzen.tvstore.core.designsystem.formatFileSize
import com.shadowzen.tvstore.core.designsystem.pointerClick
import com.shadowzen.tvstore.core.model.CompatibilityStatus
import com.shadowzen.tvstore.core.model.StoreApp
import com.shadowzen.tvstore.feature.downloads.DownloadAction
import com.shadowzen.tvstore.feature.downloads.StatusLine
import com.shadowzen.tvstore.feature.downloads.primaryActionFor
import com.shadowzen.tvstore.feature.downloads.sizeLineFor

@Composable
fun DetailScreen(
    app: StoreApp,
    categoryName: String,
    task: DownloadTask?,
    statusLine: StatusLine?,
    onPrimaryAction: (DownloadAction) -> Unit,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onOpenFeedback: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
) {
    val downloadFocus = remember { FocusRequester() }
    val action = primaryActionFor(
        task = task,
        hasPackage = app.download != null,
        latestVersionCode = app.download?.versionCode,
    )

    LaunchedEffect(app.id) { downloadFocus.requestFocus() }

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = StoreDimens.SafeHorizontal + 24.dp, vertical = 28.dp),
    ) {
        AppIcon(app = app, size = 140.dp)
        Spacer(Modifier.width(40.dp))
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(text = app.name, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            if (app.summary.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(text = app.summary, fontSize = 17.sp, color = StoreColors.TextSecondary)
            }
            Spacer(Modifier.height(14.dp))

            InfoLine("分类", categoryName)
            InfoLine("版本", app.versionName)
            InfoLine("大小", formatFileSize(app.sizeBytes))
            InfoLine("最低系统", "${androidVersionName(app.minSdk)} 及以上")
            InfoLine("CPU 架构", if (app.abis.isEmpty()) "不限" else app.abis.joinToString(" / "))
            InfoLine("兼容状态", app.compatibility.label, compatibilityColor(app.compatibility))
            app.testDeviceNotes?.let { notes ->
                InfoLine("实测说明", notes, StoreColors.TextSecondary)
            }

            if (!app.supportsArmeabiV7a) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "此版本不含 armeabi-v7a，不兼容 32 位设备（包括 TCL Q10L）。",
                    fontSize = 15.sp,
                    color = StoreColors.Warning,
                )
            }
            app.knownIssues?.let { issues ->
                Spacer(Modifier.height(6.dp))
                Text(text = "已知问题：$issues", fontSize = 15.sp, color = StoreColors.Warning)
            }
            app.releaseNotes?.let { releaseNotes ->
                Spacer(Modifier.height(6.dp))
                Text(text = "更新说明：$releaseNotes", fontSize = 14.sp, color = StoreColors.AccentSecondary)
            }

            if (app.description.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(text = app.description, fontSize = 15.sp, color = StoreColors.TextSecondary, lineHeight = 22.sp)
            }
            Spacer(Modifier.height(18.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = { onPrimaryAction(action) },
                    modifier = Modifier
                        .focusRequester(downloadFocus)
                        .pointerClick { onPrimaryAction(action) },
                ) {
                    Text(text = action.label, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 24.dp))
                }
                if (action == DownloadAction.UPDATE) {
                    OutlinedButton(
                        onClick = { onPrimaryAction(DownloadAction.OPEN) },
                        modifier = Modifier.pointerClick { onPrimaryAction(DownloadAction.OPEN) },
                    ) {
                        Text(text = "打开旧版", fontSize = 17.sp, modifier = Modifier.padding(horizontal = 8.dp))
                    }
                }
                if (onToggleFavorite != null) {
                    OutlinedButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.pointerClick(onClick = onToggleFavorite),
                    ) {
                        Text(
                            text = if (isFavorite) "已收藏 · 取消" else "收藏",
                            fontSize = 17.sp,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                }
                if (onOpenFeedback != null) {
                    OutlinedButton(
                        onClick = onOpenFeedback,
                        modifier = Modifier.pointerClick(onClick = onOpenFeedback),
                    ) {
                        Text(text = "问题反馈", fontSize = 17.sp, modifier = Modifier.padding(horizontal = 8.dp))
                    }
                }
                if (onBack != null) {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.pointerClick(onClick = onBack),
                    ) {
                        Text(text = "返回", fontSize = 17.sp, modifier = Modifier.padding(horizontal = 8.dp))
                    }
                }
            }
            if (statusLine != null) {
                Spacer(Modifier.height(8.dp))
                Text(text = statusLine.text, fontSize = 13.sp, color = statusLine.color)
            }
            if (task != null && task.status != DownloadStatus.INSTALLED) {
                Spacer(Modifier.height(8.dp))
                StoreProgressBar(progress = task.progress ?: 0f, modifier = Modifier.width(420.dp))
                Spacer(Modifier.height(4.dp))
                Text(text = sizeLineFor(task), fontSize = 13.sp, color = StoreColors.TextSecondary)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String, valueColor: Color = StoreColors.TextPrimary) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(text = label, fontSize = 16.sp, color = StoreColors.TextSecondary, modifier = Modifier.width(110.dp))
        Text(text = value, fontSize = 16.sp, color = valueColor)
    }
}

private fun compatibilityColor(status: CompatibilityStatus): Color = when (status) {
    CompatibilityStatus.TESTED -> StoreColors.Success
    CompatibilityStatus.PARAMS_MATCH -> StoreColors.AccentSecondary
    CompatibilityStatus.UNVERIFIED -> StoreColors.Warning
}
