package com.shadowzen.tvstore

import androidx.activity.compose.BackHandler
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
import com.shadowzen.tvstore.feature.category.CategoryFocusState
import com.shadowzen.tvstore.feature.category.CategoryScreen
import com.shadowzen.tvstore.feature.detail.DetailScreen
import com.shadowzen.tvstore.feature.downloads.DownloadAction
import com.shadowzen.tvstore.feature.downloads.DownloadsScreen
import com.shadowzen.tvstore.feature.downloads.StatusLine
import com.shadowzen.tvstore.feature.downloads.statusLineFor
import com.shadowzen.tvstore.feature.favorites.FavoritesScreen
import com.shadowzen.tvstore.feature.home.CatalogGate
import com.shadowzen.tvstore.feature.home.FEATURED_SHELF_ID
import com.shadowzen.tvstore.feature.home.HomeContent
import com.shadowzen.tvstore.feature.home.HomeFocusState
import com.shadowzen.tvstore.feature.home.HomeScreen
import com.shadowzen.tvstore.feature.home.buildHomeContent
import com.shadowzen.tvstore.feature.home.homeFocusKey
import com.shadowzen.tvstore.feature.installtest.InstallTestScreen
import com.shadowzen.tvstore.feature.installtest.isInstallTestAvailable
import com.shadowzen.tvstore.feature.search.SearchFocusState
import com.shadowzen.tvstore.feature.search.SearchScreen
import com.shadowzen.tvstore.feature.settings.FeedbackScreen
import com.shadowzen.tvstore.feature.settings.SettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var feedbackOpen by rememberSaveable { mutableStateOf(false) }
    var feedbackAppId by rememberSaveable { mutableStateOf<String?>(null) }
    var installTestOpen by rememberSaveable { mutableStateOf(false) }

    val tabFocusRequesters = remember { TopTab.entries.associateWith { FocusRequester() } }
    val context = LocalContext.current
    val installTestAvailable = remember { isInstallTestAvailable(context) }
    val container = remember { (context.applicationContext as TvStoreApplication).container }
    val coordinator = container.downloadCoordinator
    val tasks by container.downloadManager.tasks.collectAsStateWithLifecycle(initialValue = emptyList())
    val installStates by container.storeInstaller.states.collectAsStateWithLifecycle()
    val notices by coordinator.notices.collectAsStateWithLifecycle()
    val favoriteIds by container.favoritesStore.favoriteIds.collectAsStateWithLifecycle()
    val searchHistory by container.searchHistoryStore.history.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    // 只在前台自动安装刚下载完成的应用，后台时系统不允许弹出安装界面
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            container.completedDownloads.collect { appId -> coordinator.installCompleted(appId) }
        }
    }
    val catalogState by container.catalogRepository.state.collectAsStateWithLifecycle()
    val catalog = catalogState.catalog
    val homeContent = remember(catalog) { catalog?.let(::buildHomeContent) }
    val homeFocusState = remember { HomeFocusState() }
    val categoryFocusState = remember { CategoryFocusState() }
    val searchFocusState = remember { SearchFocusState() }
    var favoritePendingAppId by rememberSaveable { mutableStateOf<String?>(null) }

    // 启动时和每次按“重试/刷新”后，让状态页的按钮获得焦点
    var statusFocusPending by remember { mutableStateOf(true) }

    // 目录第一次可用时把焦点交给首页第一张卡片；之后的后台刷新不再移动焦点
    LaunchedEffect(homeContent) {
        val content = homeContent ?: return@LaunchedEffect
        if (!homeFocusState.initialFocusAssigned) {
            firstFocusKey(content)?.let {
                homeFocusState.pendingFocusKey = it
                homeFocusState.initialFocusAssigned = true
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        colors = SurfaceDefaults.colors(
            containerColor = StoreColors.PageBackground,
            contentColor = StoreColors.TextPrimary,
        ),
    ) {
        if (feedbackOpen) {
            BackHandler { feedbackOpen = false }
            FeedbackScreen(
                apps = catalog?.apps.orEmpty(),
                initialAppId = feedbackAppId,
                onSubmitFeedback = { submission -> container.feedbackApi.submit(submission) },
                onBack = { feedbackOpen = false },
            )
            return@Surface
        }

        if (installTestOpen && installTestAvailable) {
            BackHandler { installTestOpen = false }
            InstallTestScreen()
            return@Surface
        }

        val detailApp = detailAppId?.let { catalog?.findApp(it) }
        if (detailApp != null) {
            BackHandler { detailAppId = null }
            val task = tasks.firstOrNull { it.appId == detailApp.id }
            val notice = notices[detailApp.id]
            DetailScreen(
                app = detailApp,
                categoryName = catalog?.categoryName(detailApp.categoryId).orEmpty(),
                task = task,
                statusLine = notice?.let { StatusLine(it, StoreColors.Warning) }
                    ?: task?.let {
                        statusLineFor(
                            task = it,
                            installState = installStates[it.appId],
                            notice = null,
                            latestVersionName = detailApp.versionName,
                            latestVersionCode = detailApp.download?.versionCode,
                        )
                    },
                onPrimaryAction = { action -> coordinator.perform(action, detailApp.id, task, detailApp) },
                isFavorite = detailApp.id in favoriteIds,
                onToggleFavorite = { container.favoritesStore.toggle(detailApp.id) },
                onOpenFeedback = {
                    feedbackAppId = detailApp.id
                    feedbackOpen = true
                },
            )
            return@Surface
        }

        // 标签随焦点切换，所以返回首页要把焦点移回“首页”标签，而不是只改 selectedTab
        BackHandler(enabled = selectedTab != TopTab.HOME) {
            tabFocusRequesters.getValue(TopTab.HOME).requestFocus()
        }

        val freeSpaceText = remember(tasks.size, selectedTab) {
            "可用空间 ${formatFileSize(container.storageSpace.availableBytes(context.filesDir))}"
        }

        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(
                selectedTab = selectedTab,
                tabFocusRequesters = tabFocusRequesters,
                onTabFocused = { selectedTab = it },
                // 有可显示的目录但刷新失败时只给轻提示；没有目录时首页本身显示错误状态
                offlineHint = if (catalog != null && catalogState.errorMessage != null) {
                    "无法更新目录，正在显示上次获取的内容"
                } else {
                    null
                },
            )
            when (selectedTab) {
                TopTab.HOME -> CatalogGate(
                    state = catalogState,
                    isEmpty = { homeContent?.isEmpty ?: true },
                    onRetry = {
                        statusFocusPending = true
                        container.refreshCatalog()
                    },
                    requestFocus = statusFocusPending,
                    onFocusRequested = { statusFocusPending = false },
                ) { readyCatalog ->
                    HomeScreen(
                        content = homeContent ?: buildHomeContent(readyCatalog),
                        categoryName = readyCatalog::categoryName,
                        focusState = homeFocusState,
                        onAppClick = { shelfId, app ->
                            homeFocusState.pendingFocusKey = homeFocusKey(shelfId, app.id)
                            detailAppId = app.id
                        },
                    )
                }

                TopTab.CATEGORY -> CatalogGate(
                    state = catalogState,
                    isEmpty = { it.apps.isEmpty() },
                    onRetry = {
                        statusFocusPending = true
                        container.refreshCatalog()
                    },
                    requestFocus = statusFocusPending,
                    onFocusRequested = { statusFocusPending = false },
                ) { readyCatalog ->
                    CategoryScreen(
                        catalog = readyCatalog,
                        focusState = categoryFocusState,
                        onAppClick = { app -> detailAppId = app.id },
                    )
                }

                TopTab.SEARCH -> CatalogGate(
                    state = catalogState,
                    isEmpty = { it.apps.isEmpty() },
                    onRetry = {
                        statusFocusPending = true
                        container.refreshCatalog()
                    },
                    requestFocus = statusFocusPending,
                    onFocusRequested = { statusFocusPending = false },
                ) { readyCatalog ->
                    SearchScreen(
                        catalog = readyCatalog,
                        history = searchHistory,
                        focusState = searchFocusState,
                        onRecordQuery = { container.searchHistoryStore.record(it) },
                        onClearHistory = { container.searchHistoryStore.clear() },
                        onAppClick = { app -> detailAppId = app.id },
                    )
                }

                TopTab.DOWNLOADS -> DownloadsScreen(
                    tasks = tasks,
                    installStates = installStates,
                    notices = notices,
                    freeSpaceText = freeSpaceText,
                    onAction = { action, task ->
                        coordinator.perform(action, task.appId, task, catalog?.findApp(task.appId))
                    },
                    onDelete = { task -> coordinator.delete(task.appId) },
                    onListEmptied = { tabFocusRequesters.getValue(TopTab.DOWNLOADS).requestFocus() },
                    iconUrl = { appId -> catalog?.findApp(appId)?.iconUrl },
                    catalogApp = { appId -> catalog?.findApp(appId) },
                )

                TopTab.FAVORITES -> {
                    val favoriteApps = remember(catalog, favoriteIds) {
                        favoriteIds.mapNotNull { id -> catalog?.findApp(id) }
                    }
                    FavoritesScreen(
                        favoriteApps = favoriteApps,
                        categoryName = { id -> catalog?.categoryName(id) ?: "未分类" },
                        pendingAppId = favoritePendingAppId,
                        onFocusRestored = { favoritePendingAppId = null },
                        onOpenDetail = { app ->
                            favoritePendingAppId = app.id
                            detailAppId = app.id
                        },
                        onRemoveFavorite = { app -> container.favoritesStore.remove(app.id) },
                        onListEmptied = { tabFocusRequesters.getValue(TopTab.FAVORITES).requestFocus() },
                    )
                }

                TopTab.SETTINGS -> SettingsScreen(
                    catalogApps = catalog?.apps.orEmpty(),
                    tasks = tasks,
                    freeSpaceText = freeSpaceText,
                    searchHistoryCount = searchHistory.size,
                    installTestAvailable = installTestAvailable,
                    onUpdateApps = { appsToUpdate ->
                        appsToUpdate.forEach { app ->
                            val task = tasks.firstOrNull { it.appId == app.id }
                            coordinator.perform(DownloadAction.UPDATE, app.id, task, app)
                        }
                    },
                    onCheckClientVersion = { container.clientVersionApi.fetchLatest() },
                    onEnqueueClientUpdate = { request ->
                        container.appScope.launch { container.downloadManager.enqueue(request) }
                    },
                    onCleanDownloadCache = {
                        withContext(Dispatchers.IO) { container.downloadFiles.cleanOrphans(tasks) }
                    },
                    onClearSearchHistory = { container.searchHistoryStore.clear() },
                    onOpenFeedback = {
                        feedbackAppId = null
                        feedbackOpen = true
                    },
                    onOpenInstallTest = { installTestOpen = true },
                )
            }
        }
    }
}

@Composable
private fun TopBar(
    selectedTab: TopTab,
    tabFocusRequesters: Map<TopTab, FocusRequester>,
    onTabFocused: (TopTab) -> Unit,
    offlineHint: String?,
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
        if (offlineHint != null) {
            Text(text = offlineHint, fontSize = 16.sp, color = StoreColors.Warning)
        }
    }
}

private fun firstFocusKey(content: HomeContent): String? =
    content.featured.firstOrNull()?.let { homeFocusKey(FEATURED_SHELF_ID, it.id) }
        ?: content.shelves.firstOrNull()?.let { shelf -> shelf.apps.firstOrNull()?.let { homeFocusKey(shelf.id, it.id) } }
