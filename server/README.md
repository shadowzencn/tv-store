# TV Store 服务端

Laravel 13 + Filament 5 + SQLite。提供电视端接口（`/api/v1/...`）和管理后台（`/admin`）。
设计依据：`../docs/TDD.md` 第 5、6、11 节。

## 环境（Windows 本机开发）

- PHP 8.4（`D:\php84`，需要扩展 zip、openssl、pdo_sqlite、sqlite3、mbstring、intl、fileinfo、curl）
- Composer 2（`D:\composer`）

在 PowerShell 中调用 `composer.bat` 时，`^` 会被 cmd 当成转义符吞掉；带 `^` 版本约束时请改用
`php D:\composer\composer.phar require "vendor/package:^1.0"`。

## 首次运行

```powershell
cd server
composer install
Copy-Item .env.example .env
php artisan key:generate
New-Item database\database.sqlite -ItemType File   # 已存在则跳过
php artisan migrate
php artisan tvstore:create-admin admin@example.com --name=管理员   # 创建管理员账号（不开放注册）
php artisan serve                                  # http://127.0.0.1:8000/admin
```

## 管理员账号

后台不开放注册，账号只能在服务器上用命令创建：

```powershell
# 交互输入密码（输入时不回显）
php artisan tvstore:create-admin admin@example.com --name=管理员

# 非交互（部署脚本）：密码从环境变量读取，至少 12 个字符；用完立即清除
$env:TVSTORE_ADMIN_PASSWORD = '...'
php artisan tvstore:create-admin admin@example.com --no-interaction
Remove-Item Env:TVSTORE_ADMIN_PASSWORD
```

Filament 自带的 `php artisan make:filament-user` 也可用，但它的 `--password` 参数会留在命令历史里，不建议在服务器上使用。

## 管理后台（`/admin`）

导航：分类、应用、版本、反馈。

- **分类**：标识（slug）创建后不能修改；列表可拖动排序；下面还有应用的分类不能删除。
- **应用**：标识和包名创建时填写（标识不可改，包名在有版本后不可改）；图标只接受 PNG/JPEG/WebP、不超过 1 MB，
  保存到静态文件根目录的 `icons/`，电视端地址为 `TVSTORE_FILES_BASE_URL/files/icons/...`；列表显示当前已发布版本；
  有版本的应用不能删除（只能下架）。
- **版本**：选择应用后上传 APK（只接受 `.apk`，最大 `TVSTORE_MAX_APK_MB`）。服务端用 `ApkInspector` 识别包名、版本、
  minSdk、ABI、大小、SHA-256 和签名，这些字段不能手动修改；只能编辑兼容状态、测试设备说明、已知问题和更新说明。
  上传时按下方版本规则检查，另外相同 SHA-256 的文件不能重复上传；识别或检查失败时显示中文错误，临时文件被删除。
  通过后文件保存为 `{TVSTORE_FILES_ROOT}/apks/{sha256}.apk`，状态为草稿。发布、撤回需要确认；只有草稿可以删除，
  删除时一并删除安装包文件。
- **反馈**：只读列表，可按处理状态、类型、应用筛选；只能修改处理状态和处理说明，不能在后台新建或删除。

上传、发布、撤回、删除写入 Laravel 应用日志（`storage/logs/laravel.log`），包含版本 id 和操作的管理员 id。

### 上传大小

`TVSTORE_MAX_APK_MB`（默认 500）同时用于表单校验和 Livewire 临时上传规则（`AppServiceProvider`）。
服务器上还必须放开：PHP `upload_max_filesize`、`post_max_size`（不小于该值）、`max_execution_time`，
以及 OpenResty `client_max_body_size`。Livewire 先把文件放到 `storage/app/private/livewire-tmp/`，
上传后未提交的临时文件由 Livewire 在 24 小时后清理。

## 测试

```powershell
php artisan test
```

测试使用内存 SQLite（见 `phpunit.xml`），不会改动 `database/database.sqlite`。

## 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/catalog` | 分类 + 已上架且有已发布版本的应用；带 ETag，`If-None-Match` 命中返回 304 |
| GET | `/api/v1/client/version` | 商店客户端（包名 `TVSTORE_CLIENT_PACKAGE_NAME`）当前已发布版本；没有时返回 404 |
| POST | `/api/v1/feedback` | 提交反馈，按 IP 限频（默认每分钟 3 次、每天 30 次） |

错误格式统一为 `{"error": {"code": "...", "message": "..."}}`。

## 配置（`.env`，见 `config/tvstore.php`）

| 变量 | 说明 |
|---|---|
| `TVSTORE_FILES_BASE_URL` | 静态文件站点根地址，`downloadUrl` = 此地址 + `/files/apks/{sha256}.apk`；默认取 `APP_URL` |
| `TVSTORE_FILES_ROOT` | 静态文件根目录（OpenResty 以 `/files/` 直接提供，内含 `apks/`、`icons/`）；相对路径相对项目根目录，默认 `storage/app/public-files` |
| `TVSTORE_MAX_APK_MB` | 后台上传 APK 的大小上限（MB），默认 500 |
| `TVSTORE_CLIENT_PACKAGE_NAME` | 商店客户端包名，默认 `com.shadowzen.tvstore` |
| `TVSTORE_FEEDBACK_PER_MINUTE` / `TVSTORE_FEEDBACK_PER_DAY` | 反馈限频 |

## 版本规则

发布和撤回必须通过 `App\Services\AppVersionPublisher`：包名必须与应用一致；签名必须与该应用发布过的版本一致；
versionCode 不得低于当前已发布版本、同一应用内不得重复；同一个文件（SHA-256）只能上传一次；每个应用最多一个已发布版本，发布新版本时旧版本变为 `superseded`（历史版本），文件保留。
