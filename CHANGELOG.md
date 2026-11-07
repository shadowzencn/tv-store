# CHANGELOG

本文件记录项目的重要修改。

## [Unreleased]

### Verified
- 里程碑测试 1 验证通过（用户确认）：1Panel 线上服务端目录读取、APK 混淆资源图标提取与 Coil 图标渲染、真实安装包下载与 SHA-256 校验、系统安装及声明 `ACTION_MAIN` `<queries>` 后打开已安装应用均正常。
- 阶段 2 APK 安装（internal 构建）在 TCL Q10L 上实机验证通过（用户确认）：首次安装、用户取消、打开测试应用、升级到 v2、降级被拒绝、异签版本被拒绝均符合预期。
- 阶段 1 界面原型（internal 构建）在 TCL Q10L 上实机验证通过（用户确认）：遥控器操作流畅，焦点清晰无裁切，标签焦点恢复、详情页返回焦点恢复、返回键行为和远距离可读性均正常。debug 构建在该设备上明显卡顿，实机体验以 internal/release 构建为准。

### Changed
- 管理后台「创建应用」改为只需上传 APK：服务端识别包名、名称和启动图标并生成草稿版本，分类先归入排序最靠前的分类，创建后可再修改资料。安装包里没有位图图标时，编辑页仍可补传。
- 架构精简（用户批准）：服务端和管理后台改用同一个 Laravel 13 + Filament 5 + SQLite 项目（PHP 8.4），部署到 1Panel，安装包由 OpenResty 静态提供；取消 Python/FastAPI、PostgreSQL、React/Vite/TypeScript 管理后台和 Docker Compose。客户端接口精简为 catalog、client/version、feedback 三个，首版不做下载统计。开发计划阶段 4、5 合并为“阶段 4：服务端与管理后台”，原阶段 6 完整联调改为阶段 5；里程碑测试 1 在阶段 4 后、里程碑测试 2 在阶段 5 后。涉及 AGENTS.md、README.md、PRD（V1.1）、TDD（V1.1）、DEVELOPMENT-PLAN（V1.1）。
- AGENTS.md / DEVELOPMENT-PLAN.md：改为按阶段连续推进、自测通过后统一汇报；日常以电脑端自动化测试验证，TCL Q10L 实机测试只在里程碑进行（阶段 3+4 完成后一次、阶段 6 一次；按架构精简后的编号为阶段 4 完成后一次、阶段 5 完成后一次）。
- AGENTS.md：禁止通过 ADB 连接或调试 TCL Q10L。
- PRD / TDD：确定最低版本 Android 9（minSdk 28）、重点适配 Android 11、重点架构 armeabi-v7a、包名 com.shadowzen.tvstore。

### Added
- 下载与安装统计（`POST /api/v1/events` + Filament 后台统计面板）：服务端新增 `app_events` 表与 `AppEventResource`（「下载与安装统计」菜单，记录用户 IP、电视设备型号、Android 版本、CPU 架构、应用版本及下载完成/安装成功状态，并在「应用」列表显示各应用累计下载与安装次数）；电视端在 `StoreHttpClient` 注入包含电视品牌型号、系统版本与架构的标准 `User-Agent`，并在下载校验完成与系统安装成功时通过 `EventReporter` 自动异步上报。
- 交互与模拟器体验优化：新增 `PointerClick` 修饰符为全站所有 `androidx.tv.material3` 组件补齐鼠标与触控点击响应；搜索页左侧内置 6×6 `A–Z`/`0–9` 屏幕软键盘与退格/清空键；修复问题反馈页输入框方向键焦点被困问题；为商店客户端补齐位图 `ic_launcher.png` 图标。
- 阶段 5 完整联调（电视端全功能闭环）：新增分类页（`feature/category/`，顶部全部分类与各分类筛选胶囊、网格展示与详情返回焦点恢复）、搜索页（`feature/search/`，支持中英文、多关键词与子序列模糊搜索，本地持久化搜索历史与一键清除）、收藏功能（`feature/favorites/` 与详情页收藏按钮，按收藏时间倒序本地原子文件持久化，支持查看详情与取消收藏）、本商店应用更新检查（只管理本商店安装过的应用，对比目录最新 `versionCode` 与签名一致性，在详情页、下载页和设置页提示并触发升级下载；已卸载应用自动清理失效已安装记录）、商店客户端自身更新（`core/network/ClientVersionApi.kt`，对接 `GET /api/v1/client/version` 并复用下载与安装流程升级客户端）、用户反馈（`core/network/FeedbackApi.kt` 与 `feature/settings/FeedbackScreen.kt`，对接 `POST /api/v1/feedback`，支持 5 类问题、关联应用、快捷短语点选与自定义说明）以及设置页（`feature/settings/SettingsScreen.kt`，汇总应用更新、客户端更新、残留下载包清理、搜索历史清除、问题反馈、关于信息与 internal 安装测试入口）。
- 服务端 `php artisan tvstore:extract-icons` 命令：为没有图标的应用从已存安装包补提取启动图标（优先已发布版本，否则取版本号最大的版本）。只支持位图图标；只有自适应图标（XML）且资源名混淆的安装包提取不到，需在编辑页手动补传。图标存储逻辑抽为 AppIconStore 共用服务，上传流程行为不变。服务端测试 100 个全部通过。
- 电视端应用图标显示：新增 Coil 3.6.3（coil-compose + coil-network-okhttp；Compose 标准图片加载库，内存/磁盘缓存开箱即用，网络层复用 OkHttp，Apache-2.0）。目录解析新增 app 级 iconUrl 字段（只接受 http(s) 绝对地址或以 / 开头的路径，其他取值回退占位）；AppIcon 组件在占位图标上叠加网络图标层，无 iconUrl、加载中、加载失败均显示原占位图，首页精选、最近更新、分类列表、详情页头部和下载管理列表自动生效。同时在 `AndroidManifest.xml` 中声明 `ACTION_MAIN` 软件包可见性（`<queries>`），修复 Android 11+ 安装后无法查询和打开已安装应用的问题。电视端测试 74 个全部通过，lint 保持 4 个已知警告，assembleInternal 构建成功。
- 阶段 4 服务端与管理后台（server/，Laravel 13.40 + Filament 5.11 + SQLite，PHP 8.4）：数据表 categories、apps、app_versions（草稿/已发布/已撤回/历史版本，包名一致、签名一致、versionCode 不降、每应用最多一个已发布）、feedback、users；接口 GET /api/v1/catalog（ETag/304）、GET /api/v1/client/version、POST /api/v1/feedback（按 IP 限频），统一 camelCase 和 {"error":{"code","message"}} 错误格式。纯 PHP 的 APK 识别（app/Support/Apk/：二进制 AndroidManifest、resources.arsc 资源引用、v1/v2/v3 签名块，不依赖 Java/aapt2/apksigner，只提取证书不验证签名）。Filament 管理后台（/admin）：分类、应用、版本上传/发布/撤回/删除草稿、反馈处理，关键操作写入 Laravel 日志；管理员经 make:filament-user 或 tvstore:create-admin 创建，不开放注册。服务端测试 82 个全部通过。
- docs/DEPLOYMENT.md：1Panel 部署说明（PHP 8.4 扩展、上传限制、OpenResty /files/ 静态下载、HTTPS、后台使用、电视端配置、部署后自检）。
- 阶段 4 电视端接入目录接口（core/catalog/）：OkHttp 读取 catalog（ETag/If-None-Match，304 沿用缓存），org.json 解析（无新增依赖），单文件缓存（先写临时文件再替换）；启动先显示缓存再后台刷新，刷新失败有缓存继续显示并轻提示，无缓存显示错误页和“重试”，空目录显示空状态和“刷新”；首页分组由电视端生成（精选、最近更新最多 12 个、各分类，空分组隐藏）；生产代码不再使用示例数据（SampleCatalog 保留给测试和预览）。服务器地址经 BuildConfig 配置（android/local.properties 的 tvstore.serverUrl[.internal]，默认占位地址）。电视端测试 73 个全部通过，lint 保持 4 个已知警告；新增契约测试：用服务端真实输出（PHP 端到端流程生成）验证电视端解析结果与 aapt2 读取一致。- android/：创建最小 Compose for TV 空工程（AGP 9.5.1、Gradle 9.6.1、compileSdk 37、targetSdk 36、minSdk 28），assembleDebug 编译通过。
- .gitattributes：统一 gradlew、.bat 和二进制文件的换行与差异规则。
- 阶段 1 界面原型（本地示例数据）：顶部横向导航、精选推荐、常用应用和 6 个分类横向列表、卡片焦点描边与放大、应用详情页、返回键处理和返回后焦点恢复；其他标签页为占位页。
- 单元测试：格式化函数和示例数据完整性（新增测试依赖 JUnit 4.13.2）。
- 阶段 2 APK 安装：core/installer（安装包检查、安装前判断、PackageInstaller 会话安装、结果接收），新增 REQUEST_INSTALL_PACKAGES 权限；android/installtest 测试应用模块（v1、v2、异签 v2），仅打包进 internal 版并在“设置”页提供安装测试入口。
- 阶段 3 下载管理：core/download（DownloadEngine 支持 HTTP Range 续传、If-Range、SHA-256 校验、进度与速度、空间检查；DownloadQueueRunner 顺序队列、暂停/删除即时停止、自动重试 3 次、重启恢复；DownloadWorker 经 WorkManager 调度并以前台服务运行）、core/database（Room 下载任务表，导出 schemas/1.json）、core/installer/StoreInstaller（下载后安装检查与系统安装）、下载管理页（进度条、大小与速度、暂停/继续/重试/安装/打开、删除二次确认、删除后焦点移到相邻任务）、详情页主按钮随下载状态变化。新增 INTERNET、FOREGROUND_SERVICE、FOREGROUND_SERVICE_DATA_SYNC 权限。新增依赖 OkHttp、Room（KSP）、WorkManager、lifecycle-runtime-compose，测试依赖 MockWebServer、Robolectric、work-testing、Compose 界面测试。本地示例数据没有安装包，接入服务端后才能实际下载。
- internal 构建类型：R8 优化、资源压缩、打包基线配置文件，使用调试证书签名，用于实机流畅度测试；release 同时开启 R8 和资源压缩。

## [0.1.0] - 项目初始化

### Added
- 建立项目产品需求文档 PRD V1.0。
- 建立技术架构设计文档 TDD V1.0。
- 建立电视端 UI 设计规范 V1.0。
- 建立开发计划 V1.0。
- 建立 AI 编程规则 AGENTS.md。

### Status
- 尚未完成 Android 客户端开发。
- 尚未建立服务端。
- 尚未建立管理后台。
- 尚未进行 TCL Q10L 实机测试。
