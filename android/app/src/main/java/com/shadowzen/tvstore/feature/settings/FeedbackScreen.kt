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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import com.shadowzen.tvstore.core.designsystem.pointerClick
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
    val submitFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    val deviceModel = remember { "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifEmpty { "Android TV" } }
    val androidVersion = remember { "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})" }
    val clientVersion = remember { "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})" }

    LaunchedEffect(Unit) { firstFocus.requestFocus() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = StoreDimens.SafeHorizontal, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(text = "问题反馈", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "无需登录即可提交。遥控器或鼠标可直接点选问题类型与快捷短语。",
                    fontSize = 14.sp,
                    color = StoreColors.TextSecondary,
                )
            }
            Text(
                text = "附带设备信息：$deviceModel · $androidVersion · v$clientVersion",
                fontSize = 12.sp,
                color = StoreColors.TextSecondary,
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(text = "1. 问题类型", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 2.dp),
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

        Spacer(Modifier.height(10.dp))
        Text(text = "2. 关联应用（可选）", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 2.dp),
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

        Spacer(Modifier.height(10.dp))
        Text(text = "3. 补充说明（可选，点选快捷短语或输入后按方向键下移到“提交反馈”）", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 2.dp),
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

        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(StoreColors.CardBackground)
                .border(
                    width = if (inputFocused) 2.dp else 1.dp,
                    color = if (inputFocused) StoreColors.Accent else StoreColors.Divider,
                    shape = RoundedCornerShape(10.dp),
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (description.isEmpty()) {
                Text(
                    text = "可在此输入具体现象，或直接跳过按方向键下移到“提交反馈”…",
                    fontSize = 15.sp,
                    color = StoreColors.TextSecondary,
                )
            }
            BasicTextField(
                value = description,
                onValueChange = {
                    if (it.length <= 2000) description = it
                },
                singleLine = true,
                textStyle = TextStyle(color = StoreColors.TextPrimary, fontSize = 16.sp),
                cursorBrush = SolidColor(StoreColors.Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { submitFocus.requestFocus() },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { inputFocused = it.isFocused }
                    .onPreviewKeyEvent { event ->
                        // 拦截方向键上下，避免焦点被 BasicTextField 光标移动困在输入框内
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.DirectionDown -> {
                                    submitFocus.requestFocus()
                                    true
                                }
                                Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
                                else -> false
                            }
                        } else {
                            false
                        }
                    },
            )
        }

        val submitAction = {
            if (!isSubmitting) {
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
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Button(
                onClick = submitAction,
                modifier = Modifier
                    .focusRequester(submitFocus)
                    .pointerClick(onClick = submitAction),
            ) {
                Text(
                    text = if (isSubmitting) "正在提交…" else "提交反馈",
                    fontSize = 16.sp,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            }

            if (description.isNotEmpty()) {
                OutlinedButton(
                    onClick = { description = "" },
                    modifier = Modifier.pointerClick { description = "" },
                ) {
                    Text(text = "清空说明", fontSize = 16.sp)
                }
            }

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.pointerClick(onClick = onBack),
            ) {
                Text(text = "返回", fontSize = 16.sp)
            }

            if (resultMessage != null) {
                Text(
                    text = resultMessage.orEmpty(),
                    fontSize = 14.sp,
                    color = if (isSuccess) StoreColors.Success else StoreColors.Error,
                )
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
    val shape = RoundedCornerShape(16.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.pointerClick(onClick = onClick),
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
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}
