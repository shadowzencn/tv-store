package com.shadowzen.tvstore.feature.downloads

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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.shadowzen.tvstore.core.database.DownloadTask
import com.shadowzen.tvstore.core.designsystem.AppIcon
import com.shadowzen.tvstore.core.designsystem.StoreColors
import com.shadowzen.tvstore.core.designsystem.StoreDimens
import com.shadowzen.tvstore.core.designsystem.StoreProgressBar
import com.shadowzen.tvstore.core.designsystem.pointerClick
import com.shadowzen.tvstore.core.installer.InstallState
import com.shadowzen.tvstore.core.model.StoreApp

@Composable
fun DownloadsScreen(
    tasks: List<DownloadTask>,
    installStates: Map<String, InstallState>,
    notices: Map<String, String>,
    freeSpaceText: String,
    onAction: (DownloadAction, DownloadTask) -> Unit,
    onDelete: (DownloadTask) -> Unit,
    onListEmptied: () -> Unit,
    modifier: Modifier = Modifier,
    iconUrl: (appId: String) -> String? = { null },
    catalogApp: (appId: String) -> StoreApp? = { null },
) {
    val primaryFocus = remember { mutableMapOf<String, FocusRequester>() }
    // 删除后把焦点移到相邻任务，避免焦点随被删除的按钮一起消失
    var focusIndexAfterDelete by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(tasks) {
        val index = focusIndexAfterDelete ?: return@LaunchedEffect
        focusIndexAfterDelete = null
        if (tasks.isEmpty()) {
            onListEmptied()
        } else {
            primaryFocus[tasks[index.coerceAtMost(tasks.lastIndex)].appId]?.requestFocus()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = StoreDimens.SafeHorizontal, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "下载管理", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(16.dp))
            Text(text = "${tasks.size} 个任务", fontSize = 16.sp, color = StoreColors.TextSecondary)
            Spacer(Modifier.weight(1f))
            Text(text = freeSpaceText, fontSize = 16.sp, color = StoreColors.TextSecondary)
        }
        Spacer(Modifier.height(16.dp))

        if (tasks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "暂无下载任务。在应用详情页选择“下载”后，任务会显示在这里。",
                    fontSize = 18.sp,
                    color = StoreColors.TextSecondary,
                )
            }
            return@Column
        }

        // 每个应用最多一个任务，数量有限，用普通 Column 保证每行的焦点请求都可用
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            tasks.forEachIndexed { index, task ->
                key(task.appId) {
                    val requester = remember { FocusRequester() }
                    DisposableEffect(requester) {
                        primaryFocus[task.appId] = requester
                        onDispose { primaryFocus.remove(task.appId) }
                    }
                    val app = catalogApp(task.appId)
                    val latestCode = app?.download?.versionCode
                    val action = primaryActionFor(task, hasPackage = true, latestVersionCode = latestCode)
                    DownloadRow(
                        task = task,
                        action = action,
                        iconUrl = app?.iconUrl ?: iconUrl(task.appId),
                        statusLine = statusLineFor(
                            task = task,
                            installState = installStates[task.appId],
                            notice = notices[task.appId],
                            latestVersionName = app?.versionName,
                            latestVersionCode = latestCode,
                        ),
                        primaryFocusRequester = requester,
                        onPrimary = { onAction(action, task) },
                        onDelete = {
                            focusIndexAfterDelete = index
                            onDelete(task)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    task: DownloadTask,
    action: DownloadAction,
    iconUrl: String?,
    statusLine: StatusLine,
    primaryFocusRequester: FocusRequester,
    onPrimary: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmingDelete by remember(task.appId) { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(StoreDimens.CardShape)
            .background(StoreColors.CardBackground)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(iconUrl = iconUrl, appId = task.appId, appName = task.appName, size = 64.dp)
        Spacer(Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = task.appName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(12.dp))
                Text(text = "v${task.versionName}", fontSize = 15.sp, color = StoreColors.TextSecondary)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = statusLine.text,
                fontSize = 16.sp,
                color = statusLine.color,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            StoreProgressBar(progress = task.progress ?: 0f, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Text(text = sizeLineFor(task), fontSize = 14.sp, color = StoreColors.TextSecondary)
        }
        Spacer(Modifier.width(24.dp))
        Button(
            onClick = onPrimary,
            modifier = Modifier
                .focusRequester(primaryFocusRequester)
                .pointerClick(onClick = onPrimary),
        ) {
            Text(text = action.label, fontSize = 17.sp, modifier = Modifier.padding(horizontal = 12.dp))
        }
        Spacer(Modifier.width(12.dp))
        val handleDeleteClick = {
            // 遥控器容易误按，第一次按下只要求确认
            if (confirmingDelete) onDelete() else confirmingDelete = true
        }
        OutlinedButton(
            onClick = handleDeleteClick,
            modifier = Modifier
                .onFocusChanged { if (!it.isFocused) confirmingDelete = false }
                .pointerClick(onClick = handleDeleteClick),
        ) {
            Text(
                text = if (confirmingDelete) "确认删除" else "删除",
                fontSize = 17.sp,
                color = if (confirmingDelete) StoreColors.Error else StoreColors.TextPrimary,
            )
        }
    }
}
