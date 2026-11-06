package com.shadowzen.tvstore

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import com.shadowzen.tvstore.core.designsystem.StoreColors
import com.shadowzen.tvstore.core.designsystem.StoreDimens
import com.shadowzen.tvstore.core.designsystem.formatFileSize
import com.shadowzen.tvstore.core.sample.SampleCatalog
import com.shadowzen.tvstore.feature.detail.DetailScreen
import com.shadowzen.tvstore.feature.downloads.DownloadsScreen
import com.shadowzen.tvstore.feature.downloads.StatusLine
import com.shadowzen.tvstore.feature.downloads.statusLineFor
import com.shadowzen.tvstore.feature.home.FEATURED_SHELF_ID
import com.shadowzen.tvstore.feature.home.HomeFocusState
import com.shadowzen.tvstore.feature.home.HomeScreen
import com.shadowzen.tvstore.feature.home.homeFocusKey
import com.shadowzen.tvstore.feature.installtest.InstallTestScreen
import com.shadowzen.tvstore.feature.installtest.isInstallTestAvailable

enum class TopTab(val title: String) {
    HOME("首页"),
    CATEGORY("分类"),
    SEARCH("搜索"),
    DOWNLOADS("下载"),
    FAVORITES("收藏"),
    SETTINGS("设置"),
}

@Composable
fun TvStoreApp() {
    var selectedTab by rememberSaveable { mutableStateOf(TopTab.HOME) }
    var detailAppId by rememberSaveable { mutableStateOf<String?>(null) }
    val tabFocusRequesters = remember { TopTab.entries.associateWith { FocusRequester() } }
    val context = LocalContext.current
    val installTestAvailable = remember { isInstallTestAvailable(context) }
    val container = remember { (context.applicationContext as TvStoreApplication).container }
    val coordinator = container.downloadCoordinator
    val tasks by container.downloadManager.tasks.collectAsStateWithLifecycle(initialValue = emptyList())
    val installStates by container.storeInstaller.states.collectAsStateWithLifecycle()
    val notices by coordinator.notices.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    // 只在前台自动安装刚下载完成的应用，后台时系统不允许弹出安装界面
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            container.completedDownloads.collect { appId -> coordinator.installCompleted(appId) }
        }
    }
    val homeFocusState = remember {
        HomeFocusState(
            initialFocusKey = SampleCatalog.featured.firstOrNull()?.let { homeFocusKey(FEATURED_SHELF_ID, it.id) },
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        colors = SurfaceDefaults.colors(
            containerColor = StoreColors.PageBackground,
            contentColor = StoreColors.TextPrimary,
        ),
    ) {
        val detailApp = detailAppId?.let(SampleCatalog::findApp)
        if (detailApp != null) {
            BackHandler { detailAppId = null }
            val task = tasks.firstOrNull { it.appId == detailApp.id }
            val notice = notices[detailApp.id]
            DetailScreen(
                app = detailApp,
                categoryName = SampleCatalog.categoryName(detailApp.categoryId),
                task = task,
                statusLine = notice?.let { StatusLine(it, StoreColors.Warning) }
                    ?: task?.let { statusLineFor(it, installStates[it.appId], null) },
                onPrimaryAction = { action -> coordinator.perform(action, detailApp.id, task, detailApp) },
            )
            return@Surface
        }

        // 标签随焦点切换，所以返回首页要把焦点移回“首页”标签，而不是只改 selectedTab
        BackHandler(enabled = selectedTab != TopTab.HOME) {
            tabFocusRequesters.getValue(TopTab.HOME).requestFocus()
        }

        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(
                selectedTab = selectedTab,
                tabFocusRequesters = tabFocusRequesters,
                onTabFocused = { selectedTab = it },
            )
            when (selectedTab) {
                TopTab.HOME -> HomeScreen(
                    featured = SampleCatalog.featured,
                    shelves = SampleCatalog.shelves,
                    categoryName = SampleCatalog::categoryName,
                    focusState = homeFocusState,
                    onAppClick = { shelfId, app ->
                        homeFocusState.pendingFocusKey = homeFocusKey(shelfId, app.id)
                        detailAppId = app.id
                    },
                )
                TopTab.DOWNLOADS -> DownloadsScreen(
                    tasks = tasks,
                    installStates = installStates,
                    notices = notices,
                    freeSpaceText = remember(tasks.size) {
                        "可用空间 ${formatFileSize(container.storageSpace.availableBytes(context.filesDir))}"
                    },
                    onAction = { action, task ->
                        coordinator.perform(action, task.appId, task, SampleCatalog.findApp(task.appId))
                    },
                    onDelete = { task -> coordinator.delete(task.appId) },
                    onListEmptied = { tabFocusRequesters.getValue(TopTab.DOWNLOADS).requestFocus() },
                )
                TopTab.SETTINGS -> if (installTestAvailable) {
                    InstallTestScreen()
                } else {
                    PlaceholderPage(tab = selectedTab)
                }
                else -> PlaceholderPage(tab = selectedTab)
            }
        }
    }
}

@Composable
private fun TopBar(
    selectedTab: TopTab,
    tabFocusRequesters: Map<TopTab, FocusRequester>,
    onTabFocused: (TopTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = StoreDimens.SafeHorizontal,
                end = StoreDimens.SafeHorizontal,
                top = StoreDimens.SafeTop,
                bottom = 4.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "TV Store", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = StoreColors.Accent)
        Spacer(Modifier.width(36.dp))
        TabRow(
            selectedTabIndex = selectedTab.ordinal,
            // 从内容区向上移动时总是回到当前标签，避免误切换到正上方的其他标签
            modifier = Modifier.focusRestorer(tabFocusRequesters.getValue(selectedTab)),
        ) {
            TopTab.entries.forEach { tab ->
                Tab(
                    selected = tab == selectedTab,
                    onFocus = { onTabFocused(tab) },
                    modifier = Modifier.focusRequester(tabFocusRequesters.getValue(tab)),
                ) {
                    Text(
                        text = tab.title,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(text = "本地示例数据", fontSize = 14.sp, color = StoreColors.TextSecondary)
    }
}

@Composable
private fun PlaceholderPage(tab: TopTab) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "${tab.title}页面将在后续任务中实现",
            fontSize = 20.sp,
            color = StoreColors.TextSecondary,
        )
    }
}
