# TV Store

面向中国大陆国行 Android 智能电视用户的第三方应用商店。

## 项目目标
通过电视遥控器实现应用浏览、搜索、下载、安装和更新。

## 首要测试设备
TCL Q10L。

## 技术架构（建议方案）
- Android 客户端：Kotlin + Jetpack Compose for TV
- 服务端：Python + FastAPI
- 管理后台：React + Vite + TypeScript
- 数据库：PostgreSQL
- 部署：Docker Compose

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
├── android/
├── server/
└── admin/
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
阶段 3 已完成：下载管理（断点续传、SHA-256 校验、暂停/继续/删除/重试、重启恢复、下载完成后安装），电脑端自动化测试通过；实机验证在阶段 4 完成后的里程碑测试 1 中进行。
下一阶段：阶段 4，服务端 API。

## 测试
日常以电脑端自动化测试为准（在 `android/` 目录执行）：
```text
gradlew.bat testDebugUnitTest lintDebug assembleInternal
```
单元测试包含 Robolectric（模拟 Android 11）和 MockWebServer，不需要电视或模拟器。

## 重要说明
本项目目前处于设计和开发准备阶段。文档中的技术选型为建议方案，部分功能尚未经过实机验证。
