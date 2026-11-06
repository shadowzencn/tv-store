package com.shadowzen.tvstore.feature.installtest

import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.shadowzen.tvstore.core.designsystem.StoreColors
import com.shadowzen.tvstore.core.designsystem.StoreDimens
import com.shadowzen.tvstore.core.installer.InstallEvents
import kotlinx.coroutines.launch

/** internal 版专用的安装验证页面，按顺序执行 4 个测试场景。 */
@Composable
fun InstallTestScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val controller = remember { InstallTestController(context.applicationContext) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        controller.refreshInstalled()
        InstallEvents.results.collect(controller::onSessionResult)
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = StoreDimens.SafeHorizontal, vertical = 24.dp),
    ) {
        Column(modifier = Modifier.width(300.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(text = "安装测试（内测版）", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = "测试包：${INSTALL_TEST_PACKAGE.substringAfterLast('.')}\n当前：${controller.installedSummary}",
                fontSize = 14.sp,
                color = StoreColors.TextSecondary,
                lineHeight = 20.sp,
            )
            InstallTestCase.entries.forEachIndexed { index, case ->
                Button(
                    onClick = { scope.launch { controller.run(case) } },
                    enabled = !controller.busy,
                ) {
                    Text(text = "${index + 1}. ${case.title}", fontSize = 17.sp)
                }
            }
            OutlinedButton(
                onClick = {
                    val pm = context.packageManager
                    val launch = pm.getLeanbackLaunchIntentForPackage(INSTALL_TEST_PACKAGE)
                        ?: pm.getLaunchIntentForPackage(INSTALL_TEST_PACKAGE)
                    controller.refreshInstalled()
                    if (launch == null) {
                        controller.note("测试应用未安装，或找不到它的启动入口")
                    } else {
                        try {
                            context.startActivity(launch)
                        } catch (e: ActivityNotFoundException) {
                            controller.note("无法打开测试应用：${e.message}")
                        }
                    }
                },
            ) {
                Text(text = "打开测试应用", fontSize = 17.sp)
            }
        }
        Spacer(Modifier.width(40.dp))
        Column(modifier = Modifier.fillMaxSize()) {
            Text(text = "执行记录（最新在上）", fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
            if (controller.log.isEmpty()) {
                Text(
                    text = "按左侧顺序执行：1 首次安装 → 2 升级 → 3 降级应被拒绝 → 4 异签应被拒绝。\n" +
                        "在第 1 步的系统确认框中选择取消，可验证“用户取消”。",
                    fontSize = 15.sp,
                    color = StoreColors.TextSecondary,
                    lineHeight = 22.sp,
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(controller.log) { line ->
                    Text(text = line, fontSize = 15.sp, lineHeight = 21.sp)
                }
            }
        }
    }
}
