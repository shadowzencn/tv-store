# 国行电视应用商店 — 技术架构设计文档（TDD）

**版本：** V1.0  
**状态：** 建议架构，待实机验证

## 1. 架构目标
建立一套适合个人借助 AI 编程工具开发和维护的电视应用商店。采用简单、清晰、可扩展的架构。首版不采用微服务。

## 2. 系统组成
1. Android TV 客户端。
2. FastAPI 业务服务端。
3. React + Vite 管理后台。
4. PostgreSQL 数据库。
5. APK 文件存储服务。

客户端通过 HTTPS API 获取应用资料，文件服务负责安装包下载。管理后台通过 API 管理应用、版本和反馈。

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

### 服务端
| 技术 | 用途 |
|---|---|
| Python | 开发语言 |
| FastAPI | API 框架 |
| PostgreSQL | 数据库 |
| Docker Compose | 部署 |
| VPS 本地存储 | 初期文件存储 |
| S3 兼容对象存储 | 后续扩展 |

### 管理后台
React、Vite、TypeScript。

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
├── server/
└── admin/
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
    │   ├── sample/         # 界面原型阶段的本地示例数据，接入 API 后移除
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
| 方法 | 路径 | 功能 |
|---|---|---|
| GET | /v1/home | 首页内容 |
| GET | /v1/categories | 分类列表 |
| GET | /v1/apps | 应用列表 |
| GET | /v1/apps/{id} | 应用详情 |
| GET | /v1/search | 搜索 |
| GET | /v1/apps/{id}/versions/latest | 最新版本 |
| POST | /v1/feedback | 用户反馈 |
| GET | /v1/client/version | 商店客户端版本 |

统一采用 JSON 和 HTTPS。实际接口参数、分页、错误码和响应结构应在 API-SPEC.md 中进一步确定。

## 6. 数据库设计
核心数据表：
- apps：应用基本资料。
- app_versions：版本和安装包信息。
- categories：分类。
- compatibility_tests：兼容性测试。
- feedback：用户反馈。
- download_events：必要的下载统计。
- admin_users：管理员账号。

版本表必须保存包名、版本号、versionCode、最低 SDK、CPU ABI、文件大小、SHA-256、签名证书指纹和文件地址。

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
- 软件包可见性：internal 版只对测试包声明 `<queries>`。正式版查询其他应用的方式在接入真实应用数据时确定。
- 商店安装流程（`StoreInstaller`）：下载完成的安装包先按商店记录的包名执行 `InstallPolicy` 检查，再提交系统安装；安装状态按应用保存在内存中，安装成功后删除安装包。正式版尚未声明 `<queries>`：Android 11 起查询不到的应用会按全新安装处理，此时签名冲突和降级仍由系统安装程序拒绝；通过本商店安装的应用能否被查询，在里程碑测试 1 中实机核实后再确定可见性方案。

安装验证（internal 版专用）：`android/installtest/` 模块产出 v1、v2 和异签 v2 三个测试 APK，只打包进 internal 版 assets；“设置”页显示安装测试入口。异签证书在构建时生成到 `build/`，不进 Git，仅用于签名冲突测试。

## 9. 更新机制
只管理本商店安装的应用；校验包名和签名；签名不一致禁止覆盖；不自动降级；更新失败保留原应用；明确展示版本信息。

## 10. 安全设计
后台管理员认证、上传文件大小和类型限制、SHA-256 校验、签名信息检查、操作日志、API 错误处理。密钥和凭据不得提交到 Git。未获授权的安装包不得公开分发。

## 11. 部署规划
开发阶段使用现有 VPS 或本地环境。内测阶段部署 API、数据库和文件存储。正式阶段根据大陆访问情况评估云服务器、域名备案、对象存储和 CDN。

## 12. 技术验证
正式开发前完成三个验证：
1. Compose for TV 在 TCL Q10L 上的显示和焦点操作。
2. PackageInstaller 普通 APK 安装。
3. 下载器暂停、续传、校验和重启恢复。

最低版本已定为 Android 9（minSdk 28），重点适配 Android 11 和 armeabi-v7a（见第 3 节"目标平台"）。Q10L 的系统版本和架构为用户提供信息，需在实机测试中核实。

## 13. 技术决策管理
技术选型在验证后才能标记为正式确定。涉及技术栈、数据模型、API 或工程结构的重大变化，必须先更新本文档，再修改代码。
