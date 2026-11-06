<?php

namespace App\Services;

use App\Models\AppVersion;
use App\Models\StoreApp;
use App\Providers\AppServiceProvider;
use App\Support\Apk\ApkInspectionException;
use App\Support\Apk\ApkInspector;
use Illuminate\Http\File;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Facades\Log;
use Illuminate\Support\Facades\Storage;
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
        $info = $this->inspector->inspect($uploadedPath);
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
}
