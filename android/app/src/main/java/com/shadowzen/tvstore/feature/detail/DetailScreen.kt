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
import androidx.tv.material3.Text
import com.shadowzen.tvstore.core.database.DownloadStatus
import com.shadowzen.tvstore.core.database.DownloadTask
import com.shadowzen.tvstore.core.designsystem.AppIcon
import com.shadowzen.tvstore.core.designsystem.StoreProgressBar
import com.shadowzen.tvstore.core.designsystem.StoreColors
import com.shadowzen.tvstore.core.designsystem.StoreDimens
import com.shadowzen.tvstore.core.designsystem.androidVersionName
import com.shadowzen.tvstore.core.designsystem.formatFileSize
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
) {
    val downloadFocus = remember { FocusRequester() }
    val action = primaryActionFor(task, hasPackage = app.download != null)

    LaunchedEffect(app.id) { downloadFocus.requestFocus() }

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = StoreDimens.SafeHorizontal + 24.dp, vertical = 48.dp),
    ) {
        AppIcon(app = app, size = 140.dp)
        Spacer(Modifier.width(40.dp))
        Column(modifier = Modifier.widthIn(max = 640.dp)) {
            Text(text = app.name, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(text = app.summary, fontSize = 18.sp, color = StoreColors.TextSecondary)
            Spacer(Modifier.height(24.dp))

            InfoLine("分类", categoryName)
            InfoLine("版本", app.versionName)
            InfoLine("大小", formatFileSize(app.sizeBytes))
            InfoLine("最低系统", "${androidVersionName(app.minSdk)} 及以上")
            InfoLine("CPU 架构", app.abis.joinToString(" / "))
            InfoLine("兼容状态", app.compatibility.label, compatibilityColor(app.compatibility))

            if (!app.supportsArmeabiV7a) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "此版本不含 armeabi-v7a，不兼容 32 位设备（包括 TCL Q10L）。",
                    fontSize = 16.sp,
                    color = StoreColors.Warning,
                )
            }
            app.knownIssues?.let { issues ->
                Spacer(Modifier.height(8.dp))
                Text(text = "已知问题：$issues", fontSize = 16.sp, color = StoreColors.Warning)
            }

            Spacer(Modifier.height(20.dp))
            Text(text = app.description, fontSize = 16.sp, color = StoreColors.TextSecondary, lineHeight = 24.sp)
            Spacer(Modifier.height(32.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Button(
                    onClick = { onPrimaryAction(action) },
                    modifier = Modifier.focusRequester(downloadFocus),
                ) {
                    Text(text = action.label, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 24.dp))
                }
            }
            if (task != null && task.status != DownloadStatus.INSTALLED) {
                Spacer(Modifier.height(16.dp))
                StoreProgressBar(progress = task.progress ?: 0f, modifier = Modifier.width(420.dp))
                Spacer(Modifier.height(6.dp))
                Text(text = sizeLineFor(task), fontSize = 15.sp, color = StoreColors.TextSecondary)
            }
            if (statusLine != null) {
                Spacer(Modifier.height(12.dp))
                Text(text = statusLine.text, fontSize = 16.sp, color = statusLine.color)
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String, valueColor: Color = StoreColors.TextPrimary) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = label, fontSize = 17.sp, color = StoreColors.TextSecondary, modifier = Modifier.width(110.dp))
        Text(text = value, fontSize = 17.sp, color = valueColor)
    }
}

private fun compatibilityColor(status: CompatibilityStatus): Color = when (status) {
    CompatibilityStatus.TESTED -> StoreColors.Success
    CompatibilityStatus.PARAMS_MATCH -> StoreColors.AccentSecondary
    CompatibilityStatus.UNVERIFIED -> StoreColors.Warning
}
