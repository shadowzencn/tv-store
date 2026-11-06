# CHANGELOG

本文件记录项目的重要修改。

## [Unreleased]

### Changed
- AGENTS.md：禁止通过 ADB 连接或调试 TCL Q10L。
- PRD / TDD：确定最低版本 Android 9（minSdk 28）、重点适配 Android 11、重点架构 armeabi-v7a、包名 com.shadowzen.tvstore。

### Added
- android/：创建最小 Compose for TV 空工程（AGP 9.5.1、Gradle 9.6.1、compileSdk 37、targetSdk 36、minSdk 28），assembleDebug 编译通过。
- .gitattributes：统一 gradlew、.bat 和二进制文件的换行与差异规则。

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
