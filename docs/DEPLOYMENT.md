# 部署说明（1Panel）

阶段 4 完成后、里程碑测试 1 之前，按本说明把服务端部署到用户的 1Panel 服务器。部署属于正式发布操作，由管理员（用户）执行。

## 1. 服务器准备

- 1Panel 中安装 OpenResty，并创建 PHP 8.4 运行环境。
- PHP 需要扩展：zip、openssl、pdo_sqlite、sqlite3、mbstring、intl、fileinfo、curl。
- PHP 上传限制（`php.ini` 或 1Panel 的 PHP 配置）：`upload_max_filesize` 和 `post_max_size` 不小于 500M，`max_execution_time` 建议 600。上限由 `TVSTORE_MAX_APK_MB` 控制，改上限时两边一起改。
- OpenResty 网站配置：`client_max_body_size 500m;`。

## 2. 部署代码

```bash
cd /www/sites/<站点目录>   # 实际路径以 1Panel 为准
git clone <仓库地址> tv-store   # 或上传 server/ 目录
cd tv-store/server
composer install --no-dev --optimize-autoloader
cp .env.example .env
php artisan key:generate
php artisan migrate --force
php artisan tvstore:create-admin admin@example.com   # 密码从环境变量 TVSTORE_ADMIN_PASSWORD 或交互输入
php artisan config:cache route:cache
```

`.env` 需要设置（.env 不进 Git）：

```dotenv
APP_ENV=production
APP_DEBUG=false
APP_URL=https://<你的域名>
TVSTORE_FILES_BASE_URL=            # 留空则使用 APP_URL
TVSTORE_FILES_ROOT=                # 留空则使用 storage/app/public-files；也可指向服务器其他目录
TVSTORE_MAX_APK_MB=500
TVSTORE_CLIENT_PACKAGE_NAME=com.shadowzen.tvstore
```

## 3. OpenResty 网站配置

- 网站根目录指向 `server/public/`。
- 申请并启用 HTTPS 证书（电视端默认禁止明文 HTTP）。
- 增加静态文件位置，安装包和图标不经过 PHP，直接由 OpenResty 提供（自动支持 Range 断点续传）：

```nginx
location /files/ {
    alias <TVSTORE_FILES_ROOT 的绝对路径>/;
    add_header Accept-Ranges bytes;
    # 禁止直接访问以外的操作
    limit_except GET { deny all; }
}
```

- 其余请求按 1Panel 的 Laravel 伪静态规则转发给 `index.php`。
- 若 1Panel 以 HTTP 反向代理方式把请求转给 PHP（而不是 FastCGI），需要在 Laravel 配置 TrustProxies，否则反馈限频会把所有访客当成同一个 IP。FastCGI 方式不需要。

## 4. 后台使用

- 后台地址：`https://<你的域名>/admin`，用第 2 步创建的账号登录。
- 流程：分类 → 应用（手填包名，需与 APK 一致）→ 版本里上传 APK（自动识别包名、版本、签名等）→ 填写兼容状态与已知问题 → 发布。
- 关键操作（上传、发布、撤回、删除）记录在 `storage/logs/laravel.log`。

## 5. 电视端配置

在 `android/local.properties`（不进 Git）写入：

```properties
tvstore.serverUrl.internal=https://<你的域名>/
```

重新执行 `gradlew.bat assembleInternal`，把 internal 版 APK 经 115 网盘 → 手机 → 多屏互动安装到 TCL Q10L（里程碑测试 1）。

## 6. 部署后自检

- `curl -i https://<域名>/api/v1/catalog`：返回 200 和 ETag；再带 `If-None-Match` 请求应返回 304。
- `curl -i https://<域名>/files/apks/<某个sha256>.apk -H "Range: bytes=0-99"`：返回 206，确认断点续传可用。
- 后台上传一个真实 APK 并发布，电视端能看到、能下载、能安装。
