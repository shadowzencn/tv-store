package com.shadowzen.tvstore.core.installer

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** 安装结果从广播接收器传到界面。保留少量重放，防止界面短暂未订阅时丢失结果。 */
object InstallEvents {
    private val mutableResults = MutableSharedFlow<InstallSessionResult>(replay = 8, extraBufferCapacity = 8)
    val results: SharedFlow<InstallSessionResult> = mutableResults.asSharedFlow()

    fun publish(result: InstallSessionResult) {
        check(mutableResults.tryEmit(result)) { "安装结果缓冲区已满" }
    }
}
