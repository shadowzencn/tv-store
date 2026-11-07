<?php

namespace App\Console\Commands;

use App\Enums\VersionStatus;
use App\Models\AppVersion;
use App\Models\StoreApp;
use App\Providers\AppServiceProvider;
use App\Services\AppIconStore;
use App\Support\Apk\ApkInspectionException;
use App\Support\Apk\ApkInspector;
use Illuminate\Console\Command;
use Illuminate\Support\Facades\Log;
use Illuminate\Support\Facades\Storage;

class ExtractIconsCommand extends Command
{
    protected $signature = 'tvstore:extract-icons';

    protected $description = '为没有图标的应用从已存 APK 提取图标（完整识别安装包并解析清单里的图标引用；优先已发布版本，否则取版本号最大的版本）';

    public function handle(AppIconStore $icons, ApkInspector $inspector): int
    {
        $apps = StoreApp::query()->whereNull('icon_path')->orderBy('id')->get();
        $disk = Storage::disk(AppServiceProvider::FILES_DISK);

        $stored = 0;
        $skipped = ['无版本' => 0, 'APK 文件缺失' => 0, '识别失败' => 0, '未提取到图标' => 0];

        foreach ($apps as $app) {
            $version = $this->pickVersion($app);
            if ($version === null) {
                $skipped['无版本']++;
                Log::warning('extract-icons: 应用没有任何版本，跳过', ['app_id' => $app->id]);

                continue;
            }

            $path = $disk->path($version->file_path);
            if (! is_file($path)) {
                $skipped['APK 文件缺失']++;
                Log::warning('extract-icons: APK 文件缺失，跳过', ['app_id' => $app->id, 'file_path' => $version->file_path]);

                continue;
            }

            try {
                $info = $inspector->inspect($path);
            } catch (ApkInspectionException $e) {
                $skipped['识别失败']++;
                Log::warning('extract-icons: APK 识别失败，跳过', ['app_id' => $app->id, 'file_path' => $version->file_path, 'error' => $e->getMessage()]);

                continue;
            }

            if ($info->iconBytes === null || $info->iconExtension === null
                || $icons->store($app, $info->iconBytes, $info->iconExtension) === null) {
                $skipped['未提取到图标']++;
                Log::warning('extract-icons: APK 中没有可用的位图图标，跳过', ['app_id' => $app->id, 'file_path' => $version->file_path]);

                continue;
            }

            $stored++;
            Log::info('extract-icons: 已补充图标', ['app_id' => $app->id, 'version_id' => $version->id, 'icon_path' => $app->icon_path]);
        }

        $this->info("处理 {$apps->count()} 个无图标应用：补上 {$stored} 个，跳过 ".array_sum($skipped).' 个。');
        foreach ($skipped as $reason => $count) {
            if ($count > 0) {
                $this->line("  跳过原因：{$reason} {$count} 个");
            }
        }

        return self::SUCCESS;
    }

    /** 优先已发布版本，否则取版本号最大的版本。 */
    private function pickVersion(StoreApp $app): ?AppVersion
    {
        return $app->versions()
            ->orderByRaw("case when status = ? then 0 else 1 end", [VersionStatus::Published->value])
            ->orderByDesc('version_code')
            ->first();
    }
}
