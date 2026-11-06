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
阶段 1：电视端 UI 原型，代码已完成并通过编译和单元测试，待 TCL Q10L 实机验证。

## 重要说明
本项目目前处于设计和开发准备阶段。文档中的技术选型为建议方案，部分功能尚未经过实机验证。
