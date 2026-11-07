# TV Store（国行电视应用商店）

面向中国大陆国行 Android 智能电视（Android TV / AOSP TV）的轻量、无广告第三方应用商店。

- **最新版本**：[v1.0.0 (Releases 下载)](https://github.com/shadowzencn/tv-store/releases/latest)
- **首要测试设备**：TCL Q10L（Android 11 / API 30，`armeabi-v7a`）

---

## 核心特性

- **大屏与遥控器优先**：基于 Jetpack Compose for TV 打造，支持遥控器方向键、确认键、返回键焦点记忆恢复，同时兼容模拟器鼠标与飞鼠点击。
- **内置电视屏幕软键盘搜索**：内置 `A–Z` / `0–9` 6×6 屏幕软键盘，支持应用名、拼音首字母缩写（如 `HG`、`ST`）与关键词实时检索及历史记录。
- **可靠断点续传与安全校验**：基于 `OkHttp` + `Room` + `WorkManager` 实现 HTTP Range 断点续传、实时网速展示、SHA-256 完整性校验、包名与签名证书一致性防篡改检查。
- **应用更新与客户端自升级**：仅管理本商店安装过的应用（不扫描、不干涉其他渠道应用），支持应用一键更新与商店客户端自身在线升级（`GET /api/v1/client/version`）。
- **无需登录的问题反馈**：电视端一键点选常见故障短语并自动附带设备型号与系统版本提交反馈（`POST /api/v1/feedback`）。
- **下载与安装统计看板**：管理后台实时记录每次下载完成与安装成功的用户 IP、具体电视型号、Android 版本与 CPU 架构（`POST /api/v1/events`）。
- **纯 PHP APK 自动解析后台**：基于 Laravel 13 + Filament 5 + SQLite，上传 APK 自动提取包名、版本号、最低 SDK、CPU 架构、V1/V2/V3 签名证书指纹与应用图标，无需服务端安装 Java / `aapt2`。

---

## 技术架构

| 模块 | 技术选型 |
|---|---|
| **Android 电视端** | Kotlin、Jetpack Compose for TV、Room、OkHttp 5、WorkManager、Coil 3、PackageInstaller |
| **服务端与管理后台** | Laravel 13（PHP 8.4+）+ Filament 5 + SQLite |
| **安装包与图标存储** | 服务器本地磁盘，由 OpenResty 直接提供静态下载（支持 `HTTP 206 Range` 断点续传） |
| **客户端接口** | `GET /api/v1/catalog`、`GET /api/v1/client/version`、`POST /api/v1/feedback`、`POST /api/v1/events` |

---

## 项目结构

```text
tv-store/
├── AGENTS.md               # AI 协作与工程规范
├── README.md               # 项目说明文档
├── CHANGELOG.md            # 版本变更记录
├── docs/
│   ├── PRD.md              # 产品需求文档
│   ├── TDD.md              # 技术架构设计文档
│   ├── UI-DESIGN.md        # 电视端 UI 设计规范
│   ├── DEVELOPMENT-PLAN.md # 阶段开发计划
│   └── DEPLOYMENT.md       # 1Panel 服务端部署指南
├── android/                # Android TV 电视端工程（Gradle）
└── server/                 # Laravel 服务端接口 + Filament 管理后台
```

---

## 开发阶段完成状态

| 阶段 | 内容 | 状态 |
|---|---|---|
| **阶段 0** | 项目初始化、文档体系、Android 空工程构建 | ✅ 已完成 |
| **阶段 1** | 电视端 Compose for TV 首页、遥控器焦点与详情页原型 | ✅ 已完成（TCL Q10L 实机通过） |
| **阶段 2** | `PackageInstaller` 会话安装、降级与异签冲突拦截 | ✅ 已完成（TCL Q10L 实机通过） |
| **阶段 3** | Room 下载队列、HTTP Range 断点续传、SHA-256 校验与重启恢复 | ✅ 已完成 |
| **阶段 4** | Laravel 13 + Filament 5 管理后台、纯 PHP APK 解析与 1Panel 部署 | ✅ 已完成（里程碑测试 1 通过） |
| **阶段 5** | 分类页、屏幕软键盘搜索、本地收藏、应用/客户端自更新、问题反馈与下载安装统计 | ✅ 已完成（v1.0.0 正式发布） |

---

## 本地测试与构建

### 1. Android 电视端（`android/` 目录）
```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleRelease
```
- 包含 90 项单元测试与 Robolectric 界面/焦点自动化测试（模拟 Android 11 大屏环境 + MockWebServer）。

### 2. Laravel 服务端（`server/` 目录）
```bash
php artisan test
```
- 包含 103 项 PHPUnit 特性与单元测试（覆盖 APK 二进制清单/签名/图标解析、目录 ETag/304、反馈限频、统计上报与后台权限）。
