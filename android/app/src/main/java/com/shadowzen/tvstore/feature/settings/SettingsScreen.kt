package com.shadowzen.tvstore.feature.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.shadowzen.tvstore.BuildConfig
import com.shadowzen.tvstore.core.database.DownloadStatus
import com.shadowzen.tvstore.core.database.DownloadTask
import com.shadowzen.tvstore.core.designsystem.StoreColors
import com.shadowzen.tvstore.core.designsystem.StoreDimens
import com.shadowzen.tvstore.core.designsystem.formatFileSize
import com.shadowzen.tvstore.core.designsystem.pointerClick
import com.shadowzen.tvstore.core.download.DownloadRequest
import com.shadowzen.tvstore.core.model.StoreApp
import com.shadowzen.tvstore.core.network.ClientRelease
import com.shadowzen.tvstore.core.network.ClientVersionHttpException
import com.shadowzen.tvstore.core.network.ClientVersionResult
import kotlinx.coroutines.launch
import java.io.IOException

const val CLIENT_SELF_UPDATE_APP_ID = "tv-store"

/**
 * 设置页（PRD 5.7、5.9、UI-DESIGN 4）：
 * 简洁的纵向设置列表，涵盖：
 * 1. 本商店应用更新检查与一键更新
 * 2. 商店客户端自身更新检查（`GET /api/v1/client/version`）与下载升级
 * 3. 下载空间查看与清理残留缓存
 * 4. 清除搜索历史
 * 5. 问题反馈入口
 * 6. 关于页面信息
 * 7. internal 构建专用：安装验证测试入口
 */
@Composable
fun SettingsScreen(
    catalogApps: List<StoreApp>,
    tasks: List<DownloadTask>,
    freeSpaceText: String,
    searchHistoryCount: Int,
    installTestAvailable: Boolean,
    onUpdateApps: (List<StoreApp>) -> Unit,
    onCheckClientVersion: suspend () -> ClientVersionResult,
    onEnqueueClientUpdate: (DownloadRequest) -> Unit,
    onCleanDownloadCache: suspend () -> Int,
    onClearSearchHistory: () -> Unit,
    onOpenFeedback: () -> Unit,
    onOpenInstallTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    // 计算本商店已安装且目录中有更高版本的应用
    val updatableApps = remember(catalogApps, tasks) {
        val installedByAppId = tasks
            .filter { it.status == DownloadStatus.INSTALLED }
            .associateBy { it.appId }
        catalogApps.filter { app ->
            val installed = installedByAppId[app.id] ?: return@filter false
            val latestCode = app.download?.versionCode ?: return@filter false
            latestCode > installed.versionCode
        }
    }
    var appUpdateStatus by remember { mutableStateOf<String?>(null) }

    var checkingClient by remember { mutableStateOf(false) }
    var availableClientRelease by remember { mutableStateOf<ClientRelease?>(null) }
    var clientStatusText by remember {
        mutableStateOf("当前版本 v${BuildConfig.VERSION_NAME}（versionCode ${BuildConfig.VERSION_CODE}）")
    }
    var clientStatusColor by remember { mutableStateOf(StoreColors.TextSecondary) }

    var cacheMessage by remember { mutableStateOf<String?>(null) }
    var historyMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { firstFocus.requestFocus() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = StoreDimens.SafeHorizontal, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(text = "设置", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)

        // 1. 本商店应用更新
        SettingRow(
            title = "本商店应用更新",
            subtitle = appUpdateStatus ?: if (updatableApps.isEmpty()) {
                "仅管理通过本商店安装的应用。当前没有待更新的应用。"
            } else {
                "发现 ${updatableApps.size} 个应用可更新：" +
                    updatableApps.joinToString("、") { "${it.name} (v${it.versionName})" }
            },
            subtitleColor = if (updatableApps.isNotEmpty()) StoreColors.AccentSecondary else StoreColors.TextSecondary,
            actionLabel = if (updatableApps.isEmpty()) "刷新检查" else "全部更新 (${updatableApps.size})",
            buttonModifier = Modifier.focusRequester(firstFocus),
            onAction = {
                if (updatableApps.isEmpty()) {
                    appUpdateStatus = "已检查：本商店已安装的应用均为最新版本。"
                } else {
                    onUpdateApps(updatableApps)
                    appUpdateStatus = "已将 ${updatableApps.size} 个应用加入下载更新队列，可在“下载”页查看进度。"
                }
            },
        )

        // 2. 检查商店客户端更新
        val releaseToInstall = availableClientRelease
        SettingRow(
            title = "检查商店客户端更新",
            subtitle = clientStatusText,
            subtitleColor = clientStatusColor,
            actionLabel = when {
                checkingClient -> "检查中…"
                releaseToInstall != null -> "下载更新 v${releaseToInstall.versionName}"
                else -> "检查更新"
            },
            onAction = {
                if (checkingClient) return@SettingRow
                if (releaseToInstall != null) {
                    onEnqueueClientUpdate(
                        DownloadRequest(
                            appId = CLIENT_SELF_UPDATE_APP_ID,
                            appName = "TV Store 客户端",
                            packageName = releaseToInstall.packageName,
                            versionName = releaseToInstall.versionName,
                            versionCode = releaseToInstall.versionCode,
                            url = releaseToInstall.downloadUrl,
                            sha256 = releaseToInstall.sha256,
                            sizeBytes = releaseToInstall.sizeBytes,
                        ),
                    )
                    availableClientRelease = null
                    clientStatusColor = StoreColors.Success
                    clientStatusText = "已加入下载队列，下载校验完成后将自动调用系统安装程序升级。"
                    return@SettingRow
                }

                checkingClient = true
                scope.launch {
                    try {
                        when (val res = onCheckClientVersion()) {
                            ClientVersionResult.NotPublished -> {
                                clientStatusColor = StoreColors.TextSecondary
                                clientStatusText = "当前版本 v${BuildConfig.VERSION_NAME}，服务端暂无已发布的客户端更新。"
                            }
                            is ClientVersionResult.Published -> {
                                val remote = res.release
                                if (remote.packageName != BuildConfig.APPLICATION_ID) {
                                    clientStatusColor = StoreColors.Error
                                    clientStatusText = "服务端返回的客户端包名（${remote.packageName}）与本应用不一致。"
                                } else if (remote.versionCode > BuildConfig.VERSION_CODE) {
                                    availableClientRelease = remote
                                    clientStatusColor = StoreColors.AccentSecondary
                                    val notes = remote.releaseNotes?.let { " · $it" }.orEmpty()
                                    clientStatusText =
                                        "发现新版本 v${remote.versionName}（${formatFileSize(remote.sizeBytes)}）$notes"
                                } else {
                                    clientStatusColor = StoreColors.Success
                                    clientStatusText = "当前已是最新版本（v${BuildConfig.VERSION_NAME}）。"
                                }
                            }
                        }
                    } catch (e: ClientVersionHttpException) {
                        clientStatusColor = StoreColors.Error
                        clientStatusText = e.message ?: "检查更新失败"
                    } catch (e: IOException) {
                        clientStatusColor = StoreColors.Error
                        clientStatusText = "网络错误，无法连接更新服务器（${e.message ?: "网络异常"}）"
                    } finally {
                        checkingClient = false
                    }
                }
            },
        )

        // 3. 下载管理设置
        SettingRow(
            title = "下载存储与临时文件清理",
            subtitle = cacheMessage ?: "$freeSpaceText · 清理已结束或未关联任务的残留安装包文件",
            subtitleColor = if (cacheMessage != null) StoreColors.Success else StoreColors.TextSecondary,
            actionLabel = "清理残留包",
            onAction = {
                scope.launch {
                    val count = onCleanDownloadCache()
                    cacheMessage = if (count > 0) {
                        "已清理 $count 个残留文件（$freeSpaceText）"
                    } else {
                        "无需清理，当前没有残留临时文件（$freeSpaceText）"
                    }
                }
            },
        )

        // 4. 清除搜索历史
        SettingRow(
            title = "清除搜索历史",
            subtitle = historyMessage ?: if (searchHistoryCount > 0) {
                "当前保存了 $searchHistoryCount 条本地搜索历史记录"
            } else {
                "当前没有保存的搜索历史记录"
            },
            subtitleColor = if (historyMessage != null) StoreColors.Success else StoreColors.TextSecondary,
            actionLabel = "清除历史",
            onAction = {
                onClearSearchHistory()
                historyMessage = "本地搜索历史已清空"
            },
        )

        // 5. 问题反馈
        SettingRow(
            title = "问题反馈",
            subtitle = "遇到无法安装、无法启动、运行异常或下载问题时，向管理员提交反馈（无需登录）",
            actionLabel = "填写反馈",
            onAction = onOpenFeedback,
        )

        // 6. internal 版专用：安装验证入口
        if (installTestAvailable) {
            SettingRow(
                title = "安装流程验证（Internal 测试）",
                subtitle = "验证首次安装、同签名升级、版本降级拦截与异签冲突拦截",
                actionLabel = "打开安装测试",
                onAction = onOpenInstallTest,
            )
        }

        // 7. 关于页面信息
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(StoreDimens.CardShape)
                .background(StoreColors.CardBackground)
                .padding(horizontal = 24.dp, vertical = 18.dp),
        ) {
            Text(text = "关于 TV Store", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "客户端版本：v${BuildConfig.VERSION_NAME} (versionCode ${BuildConfig.VERSION_CODE}) · 包名 ${BuildConfig.APPLICATION_ID}",
                fontSize = 15.sp,
                color = StoreColors.TextSecondary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "本机环境：${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · 支持架构 ${Build.SUPPORTED_ABIS.joinToString()}",
                fontSize = 15.sp,
                color = StoreColors.TextSecondary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "应用原则：免费无广告、全部安装包经 SHA-256 与签名校验、不静默安装、不扫描其他渠道应用。",
                fontSize = 15.sp,
                color = StoreColors.TextSecondary,
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit,
    subtitleColor: Color = StoreColors.TextSecondary,
    buttonModifier: Modifier = Modifier,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(StoreDimens.CardShape)
            .background(StoreColors.CardBackground)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(text = subtitle, fontSize = 15.sp, color = subtitleColor)
        }
        Spacer(Modifier.width(24.dp))
        OutlinedButton(
            onClick = onAction,
            modifier = buttonModifier.pointerClick(onClick = onAction),
        ) {
            Text(text = actionLabel, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 8.dp))
        }
    }
}
