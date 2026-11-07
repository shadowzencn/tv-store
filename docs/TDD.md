# 国行电视应用商店 — 技术架构设计文档（TDD）

**版本：** V1.1  
**状态：** 建议架构，待实机验证

## 1. 架构目标
建立一套适合个人借助 AI 编程工具开发和维护的电视应用商店。采用简单、清晰、可扩展的架构。首版不采用微服务。

## 2. 系统组成
1. Android TV 客户端。
2. 服务端：一个 Laravel 项目，同时提供客户端接口和 Filament 管理后台。
3. SQLite 数据库。
4. APK 文件存储：服务器本地磁盘，由 Web 服务器（1Panel 的 OpenResty）直接提供静态下载。

客户端通过 HTTPS 接口获取应用目录，安装包下载不经过 PHP，由 Web 服务器直接提供并支持 Range。管理员通过浏览器登录管理后台维护应用、版本和反馈。

> V1.1 调整（用户批准）：应用规模小、只有一名管理员，原方案的 FastAPI + PostgreSQL + React + Docker Compose 过重，改为 Laravel + Filament + SQLite，部署到 1Panel。

## 3. 技术选型

### Android 客户端
| 技术 | 用途 |
|---|---|
| Kotlin | 开发语言 |
| Jetpack Compose for TV | 电视端 UI |
| Android Studio | 开发工具 |
| Room | 本地数据库 |
| OkHttp | 网络请求 |
| WorkManager | 后台任务调度 |
| 自定义下载模块 | 下载和断点续传 |
| PackageInstaller | 系统安装 |

不采用网页套壳方案。

### 目标平台
| 项目 | 决定 |
|---|---|
| 最低版本 | Android 9（minSdk 28） |
| 重点适配版本 | Android 11（API 30），即 TCL Q10L 的系统版本 |
| 重点 CPU 架构 | armeabi-v7a（32 位 ARM），即 TCL Q10L 的应用运行架构 |
| 应用包名 | `com.shadowzen.tvstore` |

- Q10L 的 Android 11 和 armeabi-v7a 信息由用户提供，尚未通过实机核实。
- 客户端本身为纯 Kotlin，不引入原生库；如以后引入原生依赖，必须包含 armeabi-v7a。
- compileSdk 37（Compose 1.12 要求至少 37），targetSdk 36。
- 构建工具链：AGP 9.5.1（内置 Kotlin 支持）、Gradle Wrapper 9.6.1、Kotlin Compose 插件 2.4.21、JDK 17。
- UI 依赖：Compose BOM 2026.09.00、androidx.tv:tv-material 1.1.0、activity-compose 1.13.0、lifecycle-runtime-compose 2.11.0（按生命周期收集数据流）。
- 数据与下载依赖（阶段 3 起）：OkHttp 5.5.1（HTTP 下载）、Room 2.8.5（下载任务持久化，KSP 2.3.13 生成代码，数据库结构导出到 `android/app/schemas/`）、WorkManager 2.12.0（下载队列调度和重启恢复）。
- 测试依赖（仅 JVM 单元测试，不打包进 APK）：JUnit 4.13.2、OkHttp MockWebServer 5.5.1（模拟支持 Range 的文件服务器）、Robolectric 4.17（在电脑上模拟 Android 11 运行 Room、WorkManager 和 Compose 界面测试）、androidx.test core 1.7.0、work-testing 2.12.0、Compose ui-test-junit4（随 BOM）、kotlinx-coroutines-test 1.11.0。Compose 界面测试需要的 ui-test-manifest 只进入 debug 版。
- 构建类型：debug 仅用于开发调试，性能不代表实际体验；release 开启 R8 和资源压缩并打包库自带的基线配置文件；internal 与 release 相同，但使用本机调试证书签名，仅供实机测试，不得用于正式发布。实机体验以 internal 或 release 为准。
- Compose 依赖会带入原生库 libandroidx.graphics.path.so，APK 中已包含 armeabi-v7a 版本。

### 服务端与管理后台
| 技术 | 用途 |
|---|---|
| PHP 8.4（Laravel 13 要求 8.3 及以上） | 开发语言 |
| Laravel 13 | 客户端接口、数据模型、迁移、测试 |
| Filament 5 | 管理后台（登录、应用、版本、反馈管理） |
| SQLite | 数据库，数据量增大后可迁移到 MySQL |
| 1Panel（OpenResty + PHP 运行环境） | 部署、HTTPS 证书、静态文件下载 |
| 服务器本地磁盘 | 安装包存储 |

- APK 信息识别用纯 PHP 实现（读取 zip、解析二进制 AndroidManifest、解析 APK 签名块），不依赖服务器上安装 Java、aapt2 或 apksigner。需要的 PHP 扩展：zip、openssl、pdo_sqlite、mbstring、intl、fileinfo。
- 开发阶段在 Windows 本机用 PHP 8.4 和 Composer 运行和测试（PHPUnit），不需要 Docker。

## 4. 项目结构
```text
tv-store/
├── AGENTS.md
├── README.md
├── CHANGELOG.md
├── docs/
│   ├── PRD.md
│   ├── TDD.md
│   ├── UI-DESIGN.md
│   └── DEVELOPMENT-PLAN.md
├── android/
└── server/             # Laravel 项目：客户端接口 + Filament 管理后台
```

商店客户端采用单一 Gradle 模块 `app`，按功能划分代码包（`android/installtest/` 是仅用于安装验证的测试应用模块，不属于商店客户端）：
```text
android/app/src/main/java/
└── com/shadowzen/tvstore/
    ├── core/
    │   ├── network/
    │   ├── database/
    │   ├── download/
    │   ├── installer/
    │   ├── model/          # 跨功能共享的数据模型
    │   ├── catalog/        # 目录接口、解析和缓存
    │   ├── sample/         # 本地示例数据，只用于测试和界面预览
    │   └── designsystem/
    ├── feature/
    │   ├── home/
    │   ├── category/
    │   ├── search/
    │   ├── detail/
    │   ├── downloads/
    │   ├── favorites/
    │   ├── settings/
    │   └── installtest/    # 安装验证页面，仅 internal 版显示
    ├── TvStoreApp.kt       # 顶部导航和页面切换
    └── MainActivity.kt
```
正式包名（applicationId 和代码根包名）为 `com.shadowzen.tvstore`，发布后不得更改。

## 5. API 设计
应用数量在几十到几百个，客户端一次取回整个目录，分类、搜索和首页分组在电视上完成，也便于离线显示上次的数据。

| 方法 | 路径 | 功能 |
|---|---|---|
| GET | /api/v1/catalog | 分类、已上架应用及各自当前发布的版本 |
| GET | /api/v1/client/version | 商店客户端（`com.shadowzen.tvstore`）当前发布的版本，用于检查自身更新 |
| POST | /api/v1/feedback | 提交用户反馈，无需登录，按 IP 限制频率 |

- 统一采用 JSON 和 HTTPS，字段名使用 camelCase。目录接口返回 ETag，客户端带 If-None-Match 时未变化返回 304。
- 错误响应：`{"error": {"code": "...", "message": "..."}}`，HTTP 状态码表示类别（422 参数错误、429 请求过多、500 服务器错误）。
- 应用的 `id` 是稳定的字符串标识（slug），电视端以它关联下载任务和收藏。
- 版本信息包含：packageName、versionName、versionCode、minSdk、abis、sizeBytes、sha256、signerSha256、downloadUrl。downloadUrl 指向静态文件 `/files/apks/{sha256}.apk`。
- 首页分组由电视端生成：精选（管理员标记）、最近更新（按发布时间，最多 12 个）、各分类；空分组不显示。

目录响应结构（分类和应用已按后台排序返回）：
```json
{"categories": [{"id": "video", "name": "影音娱乐", "sortOrder": 1}],
 "apps": [{"id": "slug", "packageName": "", "name": "", "summary": "", "description": "",
   "categoryId": "video", "featured": true, "sortOrder": 1, "iconUrl": null,
   "version": {"packageName": "", "versionName": "", "versionCode": 1, "minSdk": 28, "abis": ["armeabi-v7a"],
     "sizeBytes": 1, "sha256": "", "signerSha256": "", "downloadUrl": "https://…/files/apks/{sha256}.apk",
     "compatibility": "verified | spec_match | unverified", "testDeviceNotes": null,
     "knownIssues": null, "releaseNotes": null, "publishedAt": "2026-11-06T08:00:00Z"}}]}
```
- `abis` 为空数组表示安装包不含原生库，任何 CPU 架构都可安装。未知的 `compatibility` 值按“尚未验证”显示。
- 只输出已上架且有已发布版本的应用。ETag 为响应体的 SHA-256（强 ETag），响应带 `Cache-Control: no-cache`。
- 电视端缓存（`core/catalog/`）：最近一次成功的响应原文和 ETag 存为一个文件（先写临时文件再替换）。启动时先显示缓存再后台刷新；304 沿用缓存；刷新失败时有缓存继续显示并轻提示，无缓存显示错误页和“重试”。顶层结构损坏视为失败且不覆盖缓存；单个应用字段不合法只跳过该应用。

## 6. 数据库设计
核心数据表（SQLite）：
- categories：分类（slug、名称、排序）。
- apps：应用资料（slug、包名唯一、名称、简介、说明、分类、是否精选、是否上架、排序、可选图标）。
- app_versions：版本和安装包信息，同时记录兼容性（已实测 / 参数匹配 / 尚未验证、测试设备说明、已知问题）和发布状态（草稿 / 已发布 / 已撤回 / 历史版本）。
- feedback：用户反馈（类型、可选应用、描述、设备型号、Android 版本、客户端版本、处理状态、处理说明）。
- users：管理员账号（Laravel 默认表，只有管理员，不开放注册）。

版本表必须保存包名、versionName、versionCode、最低 SDK、CPU ABI、文件大小、SHA-256、签名证书指纹和文件地址。同一应用同一 versionCode 只能有一条记录；每个应用同时最多一个已发布版本，发布新版本时旧版本自动变为历史版本并保留文件。上传的安装包包名必须与应用一致，签名必须与该应用已发布过的版本一致，versionCode 不得低于已发布版本。

首版不做下载统计表，需要时再增加。

## 7. 下载架构
下载器负责下载任务管理、进度和速度统计、暂停和继续、失败重试、HTTP Range 断点续传、临时文件管理及 SHA-256 校验。
WorkManager 负责持久化任务调度和恢复，不代替下载器本身。
服务端必须正确支持 HTTP Range；不能支持时提供重新下载的降级方案。

当前实现（`core/download/`、`core/database/`）：
- `DownloadEngine`：单文件下载器。未完成的数据写入 `.apk.part`；续传时发送 `Range: bytes=N-` 和 `If-Range`（上次响应的强 ETag，否则 Last-Modified）。服务器返回 206 时追加；返回 200（不支持 Range 或文件已变化）时从头下载；返回 416 且已有数据等于文件大小时直接校验。下载完成后计算 SHA-256，一致才改名为 `.apk`，不一致删除文件并报告失败。下载前检查可用空间（含可清理缓存），并核对服务器文件大小与商店记录。请求使用 `Accept-Encoding: identity`，避免压缩传输破坏字节范围。
- 错误分类：网络中断、服务器 5xx/408/429 可自动重试；其他 HTTP 错误、大小不一致、校验失败、空间不足、写入失败不自动重试。
- `DownloadTask`（Room 表 `download_tasks`）：每个应用一个任务，状态为排队、下载中、校验中、已暂停、失败、已完成、已安装。所有状态变更都带前置状态条件，下载线程不会覆盖用户的暂停和删除。
- `DownloadManager`：创建、暂停、继续/重试、删除任务，只改数据库并触发调度。同一应用的新版本任务替换旧任务并删除旧文件。
- `DownloadQueueRunner`：按创建顺序逐个下载（一次一个任务）。监听数据库，任务被暂停或删除时立即停止当前下载；可重试错误自动重试 3 次（间隔 2、5、10 秒），仍失败则标记失败，用户可手动重试。运行开始时把停在“下载中”的任务放回队列，实现进程被杀或重启后的恢复（续传已下载部分）。
- `DownloadWorker`：WorkManager 唯一任务（追加模式），要求网络已连接，以 dataSync 类型前台服务运行以支持超过 10 分钟的下载；应用在后台无法启动前台服务时按普通任务继续，被系统停止后自动重新调度。应用启动时如有未完成任务会重新调度。
- 文件位置：应用私有目录 `files/downloads/`，无需存储权限；安装成功后删除安装包，保留“已安装”记录。
- 下载完成且界面在前台时自动调用安装流程（系统安装确认界面由用户确认）；在后台完成的任务在下载管理页或详情页手动选择“安装”。

自动化测试（电脑上运行，不需要电视）：MockWebServer 模拟正确实现 Range/If-Range 的服务器，覆盖完整下载、续传、文件变化后重下、服务器不支持 Range、416、校验失败、大小不一致、空间不足、断线、取消；Robolectric（Android 11）覆盖暂停后续传、进程被杀后恢复、断线自动重试、重试用尽后手动重试、删除运行中任务、队列顺序、内部异常隔离，以及经 WorkManager 调度的完整下载；Compose 界面测试用方向键和确认键验证下载管理页的操作、删除确认和删除后的焦点去向，以及详情页主按钮随状态变化且焦点不丢失。

## 8. 安装架构
首版支持普通单 APK。使用 Android PackageInstaller 调用系统安装流程。普通第三方应用不能默认静默安装，必须尊重系统安装授权和用户确认。
后续支持分包安装时，解析安装包容器，获取 base APK 和 split APK，检查包名、版本和签名一致，再使用 PackageInstaller Session 安装。
APKM、APKS、XAPK 不等于 Android 原生 APK，必须单独处理。

当前实现（`core/installer/`）：
- `ApkInspector`：读取安装包和已安装应用的包名、versionCode 和签名证书 SHA-256（含密钥轮换历史）。
- `InstallPolicy`：安装前判断。包名必须与预期一致；签名不一致拒绝；versionCode 更低拒绝（不自动降级）；允许全新安装、升级和同版本重装。拒绝时不调用系统安装。
- `ApkInstaller`：创建 PackageInstaller 会话写入 APK 并提交；`InstallResultReceiver` 收到 STATUS_PENDING_USER_ACTION 时打开系统确认界面，最终结果经 `InstallEvents` 送到界面。
- 未允许“安装未知应用”时先引导到系统设置；设备没有该设置页面时直接提交，由系统决定是否提示。
- 软件包可见性：`AndroidManifest.xml` 声明 `<queries><intent><action android:name="android.intent.action.MAIN" /></intent></queries>`，在 Android 11（API 30）及以上可查询所有含 `MAIN` 启动入口的已安装应用（涵盖 `LAUNCHER` 与 `LEANBACK_LAUNCHER`），用于安装前版本/签名检查与安装后启动应用。
- 商店安装流程（`StoreInstaller`）：下载完成的安装包先按商店记录的包名执行 `InstallPolicy` 检查，再提交系统安装；安装状态按应用保存在内存中，安装成功后删除安装包。

安装验证（internal 版专用）：`android/installtest/` 模块产出 v1、v2 和异签 v2 三个测试 APK，只打包进 internal 版 assets；“设置”页显示安装测试入口。异签证书在构建时生成到 `build/`，不进 Git，仅用于签名冲突测试。

## 9. 更新机制
只管理本商店安装的应用；校验包名和签名；签名不一致禁止覆盖；不自动降级；更新失败保留原应用；明确展示版本信息。

## 10. 安全设计
后台管理员认证、上传文件大小和类型限制、SHA-256 校验、签名信息检查、关键后台操作（发布、撤回、删除）写入 Laravel 应用日志（首版不建日志表）、API 错误处理。密钥和凭据不得提交到 Git。未获授权的安装包不得公开分发。

## 11. 部署规划
开发阶段在 Windows 本机运行和测试。里程碑测试 1 前部署到用户的 1Panel 服务器：创建 PHP 运行环境和网站，申请 HTTPS 证书，网站根目录指向 Laravel 的 `public/`，安装包目录由 OpenResty 直接提供静态下载。正式阶段再根据大陆访问情况评估域名备案、对象存储和 CDN。

## 12. 技术验证
正式开发前完成三个验证：
1. Compose for TV 在 TCL Q10L 上的显示和焦点操作。
2. PackageInstaller 普通 APK 安装。
3. 下载器暂停、续传、校验和重启恢复。

最低版本已定为 Android 9（minSdk 28），重点适配 Android 11 和 armeabi-v7a（见第 3 节"目标平台"）。Q10L 的系统版本和架构为用户提供信息，需在实机测试中核实。

## 13. 技术决策管理
技术选型在验证后才能标记为正式确定。涉及技术栈、数据模型、API 或工程结构的重大变化，必须先更新本文档，再修改代码。
