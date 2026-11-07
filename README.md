# TV Store

面向中国大陆国行 Android 智能电视用户的第三方应用商店。

## 项目目标
通过电视遥控器实现应用浏览、搜索、下载、安装和更新。

## 首要测试设备
TCL Q10L。

## 技术架构（建议方案）
- Android 客户端：Kotlin + Jetpack Compose for TV、Room、OkHttp、WorkManager、Coil 3（应用图标加载）、PackageInstaller
- 服务端和管理后台：同一个 Laravel 13 项目（PHP 8.4），管理后台使用 Filament 5
- 数据库：SQLite
- 安装包存储：服务器本地磁盘，由 OpenResty 直接提供静态下载（支持 HTTP Range）
- 部署：用户服务器上的 1Panel
- 客户端接口：`GET /api/v1/catalog`、`GET /api/v1/client/version`、`POST /api/v1/feedback`（详见 docs/TDD.md）

## 项目目录
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
├── android/            # 电视端客户端（Gradle 工程）
└── server/             # Laravel 项目：客户端接口 + Filament 管理后台
```

## 开发原则
- 先完成电视端，再逐步接入服务端。
- 优先保障遥控器操作体验。
- 所有核心功能必须经过实际测试。
- 使用 Git 管理代码版本。
- AI 编程必须遵守 AGENTS.md。

## 当前进度
阶段 0 已完成（空工程已在 TCL Q10L 上安装）。
阶段 1 已完成：电视端 UI 原型（本地示例数据）已用 internal 构建在 TCL Q10L 上通过遥控器实机验证（用户确认）。
阶段 2 已完成：APK 安装（PackageInstaller）已用 internal 构建在 TCL Q10L 上实机验证通过（用户确认）。
阶段 3 已完成：下载管理（断点续传、SHA-256 校验、暂停/继续/删除/重试、重启恢复、下载完成后安装），电脑端自动化测试通过。
阶段 4 已完成：服务端与管理后台（Laravel 13 + Filament 5 + SQLite）已部署至用户 1Panel 服务器，电视端接入真实目录与网络图标加载；里程碑测试 1（目录显示、真实下载校验、安装与软件包可见性打开应用）验证通过（用户确认）。
阶段 5 已完成：完整联调（分类页、搜索页及本地搜索历史、收藏页及详情页收藏、本商店应用更新检查、商店客户端自身更新 `/api/v1/client/version`、用户反馈提交 `/api/v1/feedback`、设置页），电脑端自动化测试全部通过。下一步：里程碑测试 2。

## 测试
日常以电脑端自动化测试为准（在 `android/` 目录执行）：
```text
gradlew.bat testDebugUnitTest lintDebug assembleInternal
```
单元测试包含 Robolectric（模拟 Android 11）和 MockWebServer，不需要电视或模拟器。

服务端测试（在 `server/` 目录执行，需要本机安装 PHP 8.4 和 Composer）：
```text
php artisan test
```

## 重要说明
本项目目前处于设计和开发准备阶段。文档中的技术选型为建议方案，部分功能尚未经过实机验证。
