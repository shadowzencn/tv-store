package com.shadowzen.tvstore.core.designsystem

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Compose for TV (`androidx.tv.material3`) 的 `Button`、`OutlinedButton`、`Surface`、`Tab`
 * 内部使用 `tvClickable`，仅监听遥控器/键盘按键事件（DPAD_CENTER / ENTER），忽略鼠标和触控点击。
 *
 * 附加此修饰符后，组件在保留原有遥控器焦点与确认键交互的同时，也支持模拟器鼠标点击或飞鼠点击。
 */
fun Modifier.pointerClick(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val currentOnClick by rememberUpdatedState(onClick)
    if (!enabled) {
        this
    } else {
        pointerInput(Unit) {
            detectTapGestures(onTap = { currentOnClick() })
        }
    }
}
