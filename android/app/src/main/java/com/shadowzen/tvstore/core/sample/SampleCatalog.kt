package com.shadowzen.tvstore.core.sample

import com.shadowzen.tvstore.core.model.ABI_ARM64_V8A
import com.shadowzen.tvstore.core.model.ABI_ARMEABI_V7A
import com.shadowzen.tvstore.core.model.AppShelf
import com.shadowzen.tvstore.core.model.CompatibilityStatus
import com.shadowzen.tvstore.core.model.CompatibilityStatus.PARAMS_MATCH
import com.shadowzen.tvstore.core.model.CompatibilityStatus.TESTED
import com.shadowzen.tvstore.core.model.CompatibilityStatus.UNVERIFIED
import com.shadowzen.tvstore.core.model.StoreApp
import com.shadowzen.tvstore.core.model.StoreCategory

/**
 * 界面原型使用的本地示例数据，不对应任何真实应用或安装包。
 * 接入服务端 API 后由真实数据替换。
 */
object SampleCatalog {

    val categories: List<StoreCategory> = listOf(
        StoreCategory("video", "影音娱乐"),
        StoreCategory("network", "网络工具"),
        StoreCategory("system", "系统工具"),
        StoreCategory("file", "文件管理"),
        StoreCategory("utility", "实用工具"),
        StoreCategory("game", "游戏应用"),
    )

    private val v7AndV8 = listOf(ABI_ARMEABI_V7A, ABI_ARM64_V8A)
    private val v7Only = listOf(ABI_ARMEABI_V7A)
    private val v8Only = listOf(ABI_ARM64_V8A)

    val apps: List<StoreApp> = listOf(
        sample("local-player", "本地播放器", "播放 U 盘和局域网中的视频", "video", "2.4.1", 18.6, 21, v7AndV8, TESTED),
        sample("music-player", "音乐播放器", "适合大屏的本地音乐播放", "video", "1.8.0", 9.2, 23, v7AndV8, PARAMS_MATCH),
        sample("cast-receiver", "投屏接收", "接收手机投屏的画面和声音", "video", "3.0.2", 24.3, 24, v7Only, TESTED, "部分手机投屏时可能出现短暂延迟。"),
        sample("photo-viewer", "相册浏览", "在电视上浏览照片和幻灯片", "video", "1.2.5", 6.4, 21, v7AndV8, UNVERIFIED),
        sample("net-speed", "网速测试", "测试电视当前的网络速度", "network", "4.1.0", 12.1, 21, v7AndV8, TESTED),
        sample("lan-transfer", "局域网传输", "手机和电视之间互传文件", "network", "2.0.7", 15.8, 23, v7AndV8, PARAMS_MATCH),
        sample("net-diagnose", "网络诊断", "检查 DNS、网关和连接状态", "network", "1.3.0", 4.9, 21, v7Only, UNVERIFIED),
        sample("dns-helper", "DNS 设置助手", "帮助查看和切换 DNS 配置", "network", "0.9.4", 3.2, 26, v7AndV8, UNVERIFIED),
        sample("launcher", "桌面启动器", "简洁的电视桌面替代方案", "system", "5.2.0", 21.4, 24, v7AndV8, PARAMS_MATCH, "部分品牌电视不允许替换默认桌面。"),
        sample("app-manager", "应用管理器", "查看、启动和卸载已装应用", "system", "2.6.3", 7.7, 21, v7AndV8, TESTED),
        sample("storage-clean", "存储清理", "清理缓存，释放存储空间", "system", "3.1.1", 10.5, 23, v7Only, PARAMS_MATCH),
        sample("device-info", "设备信息", "查看系统版本、CPU 和内存", "system", "1.5.0", 5.1, 21, v7AndV8, TESTED),
        sample("file-manager", "文件管理器", "浏览本地存储和 U 盘文件", "file", "6.0.2", 13.9, 21, v7AndV8, TESTED),
        sample("unzip", "压缩解压", "解压 ZIP、7Z 等常见格式", "file", "2.2.0", 8.3, 23, v7AndV8, PARAMS_MATCH),
        sample("usb-helper", "U 盘助手", "自动识别并打开 U 盘内容", "file", "1.0.8", 3.6, 21, v7Only, UNVERIFIED),
        sample("smb-client", "网络共享", "访问局域网 SMB 共享文件夹", "file", "3.4.0", 11.2, 24, v7AndV8, PARAMS_MATCH),
        sample("weather", "天气", "大字体显示天气预报", "utility", "2.1.0", 9.8, 23, v7AndV8, PARAMS_MATCH),
        sample("clock", "时钟屏保", "全屏时钟和日期显示", "utility", "1.4.2", 2.7, 21, v7AndV8, TESTED),
        sample("calculator", "计算器", "支持遥控器操作的计算器", "utility", "1.1.0", 1.9, 21, v7AndV8, UNVERIFIED),
        sample("key-mapper", "按键映射", "自定义遥控器按键功能", "utility", "0.8.1", 4.4, 26, v7Only, UNVERIFIED, "需要系统无障碍权限，部分电视不支持。"),
        sample("snake", "贪吃蛇", "用方向键玩的经典小游戏", "game", "1.0.3", 5.5, 21, v7AndV8, TESTED),
        sample("blocks", "方块消除", "简单好上手的消除游戏", "game", "2.3.0", 32.6, 24, v7AndV8, PARAMS_MATCH),
        sample("gomoku", "五子棋", "支持人机对战的五子棋", "game", "1.6.1", 7.0, 21, v7AndV8, UNVERIFIED),
        sample(
            "chess", "中国象棋", "多难度人机对战象棋", "game", "3.2.0", 41.7, 28, v8Only, UNVERIFIED,
            "该示例版本只含 arm64-v8a，用于演示不兼容提示。",
        ),
    )

    private val appsById: Map<String, StoreApp> = apps.associateBy { it.id }

    val featured: List<StoreApp> =
        listOf("local-player", "file-manager", "net-speed", "cast-receiver").map(appsById::getValue)

    val shelves: List<AppShelf> = buildList {
        add(
            AppShelf(
                id = "common",
                title = "常用应用",
                apps = listOf("file-manager", "local-player", "app-manager", "lan-transfer", "clock", "device-info")
                    .map(appsById::getValue),
            ),
        )
        categories.forEach { category ->
            add(AppShelf(category.id, category.name, apps.filter { it.categoryId == category.id }))
        }
    }

    fun findApp(id: String): StoreApp? = appsById[id]

    fun categoryName(id: String): String = categories.firstOrNull { it.id == id }?.name ?: "未分类"

    private fun sample(
        id: String,
        name: String,
        summary: String,
        categoryId: String,
        versionName: String,
        sizeMb: Double,
        minSdk: Int,
        abis: List<String>,
        compatibility: CompatibilityStatus,
        knownIssues: String? = null,
    ) = StoreApp(
        id = id,
        name = name,
        summary = summary,
        description = "$summary。本条目为界面原型示例数据，不对应真实应用，也不提供真实安装包。",
        categoryId = categoryId,
        versionName = versionName,
        sizeBytes = (sizeMb * 1024 * 1024).toLong(),
        minSdk = minSdk,
        abis = abis,
        compatibility = compatibility,
        knownIssues = knownIssues,
    )
}
