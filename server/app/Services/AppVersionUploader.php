<?php

namespace App\Services;

use App\Models\AppVersion;
use App\Models\StoreApp;
use App\Providers\AppServiceProvider;
use App\Support\Apk\ApkInfo;
use App\Support\Apk\ApkInspectionException;
use App\Support\Apk\ApkInspector;
use Illuminate\Http\File;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Log;
use Illuminate\Support\Facades\Storage;
use Illuminate\Support\Str;
use Throwable;

/**
 * Turns an uploaded APK into a draft AppVersion: reads its metadata, checks the version rules,
 * then stores the file as apks/{sha256}.apk on the static file disk.
 * The caller owns the uploaded temporary file and must delete it afterwards.
 */
class AppVersionUploader
{
    /** Fields the administrator may fill in; everything else comes from the APK. */
    public const EDITABLE_FIELDS = ['compatibility', 'test_device_notes', 'known_issues', 'release_notes'];

    public function __construct(
        private readonly ApkInspector $inspector,
        private readonly AppVersionPublisher $publisher,
    ) {}

    /**
     * @param  array<string, mixed>  $details  values for EDITABLE_FIELDS
     *
     * @throws ApkInspectionException
     * @throws VersionRuleException
     */
    public function upload(StoreApp $app, string $uploadedPath, array $details = []): AppVersion
    {
        return $this->store($app, $this->inspector->inspect($uploadedPath), $uploadedPath, $details);
    }

    /**
     * Creates the StoreApp from the identified APK and then stores the first version.
     *
     * @param  array<string, mixed>  $appAttributes  category_id, is_listed, is_featured
     * @param  array<string, mixed>  $details  values for EDITABLE_FIELDS
     *
     * @throws ApkInspectionException
     * @throws VersionRuleException
     */
    public function uploadToNewApp(string $uploadedPath, array $appAttributes, array $details = []): AppVersion
    {
        $info = $this->inspector->inspect($uploadedPath);

        if (StoreApp::query()->where('package_name', $info->packageName)->exists()) {
            throw new VersionRuleException(
                VersionRuleException::PACKAGE_TAKEN,
                '该包名的应用已存在，请改用已有应用上传。',
            );
        }

        $app = new StoreApp;
        $app->forceFill([
            'id' => self::uniqueSlugFromPackage($info->packageName),
            'package_name' => $info->packageName,
            'name' => mb_substr($info->label ?: $info->packageName, 0, 100),
            'category_id' => $appAttributes['category_id'],
            'is_listed' => (bool) ($appAttributes['is_listed'] ?? true),
            'is_featured' => (bool) ($appAttributes['is_featured'] ?? false),
        ]);

        $version = DB::transaction(function () use ($app, $info, $uploadedPath, $details): AppVersion {
            $app->save();

            Log::info('App created from APK upload', [
                'app_id' => $app->id,
                'package_name' => $app->package_name,
                'admin_id' => Auth::id(),
            ]);

            return $this->store($app, $info, $uploadedPath, $details);
        });

        $this->storeIcon($app, $info);

        return $version;
    }

    private function storeIcon(StoreApp $app, ApkInfo $info): void
    {
        if ($info->iconBytes === null || ! in_array($info->iconExtension, ['png', 'webp', 'jpg'], true)) {
            return;
        }
        if (strlen($info->iconBytes) > 1024 * 1024) {
            return;
        }

        $path = "icons/{$app->id}.{$info->iconExtension}";
        Storage::disk(AppServiceProvider::FILES_DISK)->put($path, $info->iconBytes);
        $app->icon_path = $path;
        $app->save();
    }

    /**
     * @param  array<string, mixed>  $details
     *
     * @throws VersionRuleException
     */
    private function store(StoreApp $app, ApkInfo $info, string $uploadedPath, array $details): AppVersion
    {
        $sha256 = strtolower($info->sha256);

        $version = new AppVersion([
            ...array_intersect_key($details, array_flip(self::EDITABLE_FIELDS)),
            'app_id' => $app->id,
            'package_name' => $info->packageName,
            'version_name' => $info->versionName,
            'version_code' => $info->versionCode,
            'min_sdk' => $info->minSdk,
            'abis' => array_values($info->abis),
            'size_bytes' => $info->sizeBytes,
            'sha256' => $sha256,
            'signer_sha256' => $info->signerSha256,
            'file_path' => "apks/{$sha256}.apk",
        ]);

        $this->publisher->assertAcceptable($version);

        $disk = Storage::disk(AppServiceProvider::FILES_DISK);
        $disk->putFileAs('apks', new File($uploadedPath), "{$sha256}.apk");

        try {
            $version->save();
        } catch (Throwable $e) {
            $disk->delete($version->file_path);

            throw $e;
        }

        Log::info('App version uploaded', [
            'app_id' => $app->id,
            'version_id' => $version->id,
            'version_code' => $version->version_code,
            'sha256' => $sha256,
            'admin_id' => Auth::id(),
        ]);

        return $version;
    }

    /**
     * Slug rule: take the segment after the last "." of the package name, lowercase it and
     * replace every run of characters outside [a-z0-9] with a single "-", then trim "-"
     * from both ends (com.example.myPlayer becomes "myplayer", com.foo.My_App becomes "my-app").
     * Empty results fall back to "app"; taken slugs get "-2", "-3", ... appended.
     */
    private static function uniqueSlugFromPackage(string $packageName): string
    {
        $base = trim((string) preg_replace('/[^a-z0-9]+/', '-', strtolower(Str::afterLast($packageName, '.'))), '-');
        $base = $base === '' ? 'app' : substr($base, 0, 60);

        $slug = $base;
        $suffix = 2;
        while (StoreApp::query()->whereKey($slug)->exists()) {
            $slug = $base.'-'.$suffix;
            $suffix++;
        }

        return $slug;
    }
}
