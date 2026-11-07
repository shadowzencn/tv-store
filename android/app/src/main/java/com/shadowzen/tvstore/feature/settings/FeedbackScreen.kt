package com.shadowzen.tvstore.feature.settings

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.shadowzen.tvstore.BuildConfig
import com.shadowzen.tvstore.core.designsystem.StoreColors
import com.shadowzen.tvstore.core.designsystem.StoreDimens
import com.shadowzen.tvstore.core.model.StoreApp
import com.shadowzen.tvstore.core.network.FeedbackHttpException
import com.shadowzen.tvstore.core.network.FeedbackSubmission
import com.shadowzen.tvstore.core.network.FeedbackType
import kotlinx.coroutines.launch
import java.io.IOException

private val QUICK_PHRASES = listOf(
    "打开后闪退或黑屏",
    "遥控器方向键或确认键无法操作",
    "安装提示不兼容或签名冲突",
    "下载中断或校验失败",
    "画面比例或字体显示异常",
)

@Composable
fun FeedbackScreen(
    apps: List<StoreApp>,
    initialAppId: String?,
    onSubmitFeedback: suspend (FeedbackSubmission) -> Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedType by remember { mutableStateOf(FeedbackType.INSTALL_FAILED) }
    var selectedAppId by remember(initialAppId) { mutableStateOf(initialAppId) }
    var description by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }
    var inputFocused by remember { mutableStateOf(false) }

    val firstFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    val deviceModel = remember { "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifEmpty { "Android TV" } }
    val androidVersion = remember { "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})" }
    val clientVersion = remember { "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})" }

    LaunchedEffect(Unit) { firstFocus.requestFocus() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = StoreDimens.SafeHorizontal, vertical = 24.dp),
    ) {
        Text(text = "问题反馈", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "无需登录即可提交。遥控器可直接点选下方问题类型与快捷短语。",
            fontSize = 16.sp,
            color = StoreColors.TextSecondary,
        )

        Spacer(Modifier.height(22.dp))
        Text(text = "1. 问题类型", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            items(FeedbackType.entries, key = { it.name }) { type ->
                SelectableChip(
                    label = type.label,
                    selected = selectedType == type,
                    modifier = if (type == FeedbackType.INSTALL_FAILED) Modifier.focusRequester(firstFocus) else Modifier,
                    onClick = {
                        selectedType = type
                        resultMessage = null
                    },
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(text = "2. 关联应用（可选）", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            item(key = "__none__") {
                SelectableChip(
                    label = "不指定（商店整体问题）",
                    selected = selectedAppId == null,
                    onClick = {
                        selectedAppId = null
                        resultMessage = null
                    },
                )
            }
            items(apps, key = { it.id }) { app ->
                SelectableChip(
                    label = app.name,
                    selected = selectedAppId == app.id,
                    onClick = {
                        selectedAppId = app.id
                        resultMessage = null
                    },
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(text = "3. 补充说明（可选，可点选快捷短语或手动输入）", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            items(QUICK_PHRASES, key = { it }) { phrase ->
                SelectableChip(
                    label = "+ $phrase",
                    selected = false,
                    onClick = {
                        description = if (description.isBlank()) phrase else "$description；$phrase"
                        resultMessage = null
                    },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(StoreDimens.CardShape)
                .background(StoreColors.CardBackground)
                .border(
                    width = if (inputFocused) 2.dp else 1.dp,
                    color = if (inputFocused) StoreColors.Accent else StoreColors.Divider,
                    shape = StoreDimens.CardShape,
                )
                .padding(horizontal = 20.dp, vertical = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (description.isEmpty()) {
                Text(
                    text = "可在此输入具体现象，或直接点选上方快捷短语…",
                    fontSize = 16.sp,
                    color = StoreColors.TextSecondary,
                )
            }
            BasicTextField(
                value = description,
                onValueChange = {
                    if (it.length <= 2000) description = it
                },
                textStyle = TextStyle(color = StoreColors.TextPrimary, fontSize = 17.sp),
                cursorBrush = SolidColor(StoreColors.Accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { inputFocused = it.isFocused },
            )
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = "将自动附带设备信息：$deviceModel · $androidVersion · 客户端 v$clientVersion",
            fontSize = 14.sp,
            color = StoreColors.TextSecondary,
        )

        if (resultMessage != null) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = resultMessage.orEmpty(),
                fontSize = 16.sp,
                color = if (isSuccess) StoreColors.Success else StoreColors.Error,
            )
        }

        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = {
                    if (isSubmitting) return@Button
                    isSubmitting = true
                    resultMessage = null
                    scope.launch {
                        try {
                            onSubmitFeedback(
                                FeedbackSubmission(
                                    type = selectedType,
                                    appId = selectedAppId,
                                    description = description.trim().ifEmpty { null },
                                    deviceModel = deviceModel,
                                    androidVersion = androidVersion,
                                    clientVersion = clientVersion,
                                ),
                            )
                            isSuccess = true
                            description = ""
                            resultMessage = "反馈已提交成功，感谢你的反馈！"
                        } catch (e: FeedbackHttpException) {
                            isSuccess = false
                            resultMessage = e.message ?: "提交反馈失败"
                        } catch (e: IOException) {
                            isSuccess = false
                            resultMessage = "网络连接失败，请稍后重试（${e.message ?: "网络异常"}）"
                        } finally {
                            isSubmitting = false
                        }
                    }
                },
            ) {
                Text(
                    text = if (isSubmitting) "正在提交…" else "提交反馈",
                    fontSize = 17.sp,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }

            if (description.isNotEmpty()) {
                OutlinedButton(onClick = { description = "" }) {
                    Text(text = "清空说明", fontSize = 17.sp)
                }
            }

            OutlinedButton(onClick = onBack) {
                Text(text = "返回", fontSize = 17.sp)
            }
        }
    }
}

@Composable
private fun SelectableChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) StoreColors.Accent else StoreColors.CardBackground,
            contentColor = StoreColors.TextPrimary,
            focusedContainerColor = if (selected) StoreColors.Accent else StoreColors.SecondaryBackground,
            focusedContentColor = StoreColors.TextPrimary,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, StoreColors.AccentSecondary),
                shape = shape,
            ),
        ),
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
