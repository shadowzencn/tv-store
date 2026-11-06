<?php

namespace App\Services;

use App\Enums\VersionStatus;
use App\Models\AppVersion;
use App\Providers\AppServiceProvider;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Log;
use Illuminate\Support\Facades\Storage;

/**
 * Single place for the version rules of TDD section 6:
 * package name must match the app, signer must match every version the app has ever published,
 * versionCode must not be lower than the currently published version, the same file (SHA-256)
 * cannot be uploaded twice, and at most one published version per app (the previous one becomes Superseded).
 * Publish, withdraw and delete are logged with the acting administrator's id.
 */
class AppVersionPublisher
{
    /**
     * Checks a (possibly unsaved) version against its app. Call before saving an uploaded package
     * and again when publishing.
     *
     * @throws VersionRuleException
     */
    public function assertAcceptable(AppVersion $version): void
    {
        $app = $version->app()->firstOrFail();

        if ($version->package_name !== $app->package_name) {
            throw new VersionRuleException(
                VersionRuleException::PACKAGE_MISMATCH,
                "安装包包名 {$version->package_name} 与应用包名 {$app->package_name} 不一致。",
            );
        }

        $sameFile = AppVersion::query()
            ->where('sha256', strtolower($version->sha256))
            ->when($version->exists, fn ($q) => $q->whereKeyNot($version->getKey()))
            ->first();
        if ($sameFile !== null) {
            throw new VersionRuleException(
                VersionRuleException::DUPLICATE_FILE,
                "相同的安装包（SHA-256 {$version->sha256}）已经上传过（应用 {$sameFile->app_id}，versionCode {$sameFile->version_code}）。",
            );
        }

        $others = AppVersion::query()
            ->where('app_id', $app->id)
            ->when($version->exists, fn ($q) => $q->whereKeyNot($version->getKey()));

        if ((clone $others)->where('version_code', $version->version_code)->exists()) {
            throw new VersionRuleException(
                VersionRuleException::DUPLICATE_VERSION_CODE,
                "该应用已存在 versionCode {$version->version_code} 的版本。",
            );
        }

        $publishedSigners = (clone $others)->whereNotNull('published_at')->distinct()->pluck('signer_sha256');
        if ($publishedSigners->isNotEmpty() && ! $publishedSigners->contains(strtolower($version->signer_sha256))) {
            throw new VersionRuleException(
                VersionRuleException::SIGNER_MISMATCH,
                '安装包签名与该应用已发布过的版本不一致。',
            );
        }

        $current = (clone $others)->where('status', VersionStatus::Published->value)->first();
        if ($current !== null && $version->version_code < $current->version_code) {
            throw new VersionRuleException(
                VersionRuleException::VERSION_CODE_DOWNGRADE,
                "versionCode {$version->version_code} 低于当前已发布版本 {$current->version_code}。",
            );
        }
    }

    /**
     * @throws VersionRuleException
     */
    public function publish(AppVersion $version): AppVersion
    {
        return DB::transaction(function () use ($version) {
            $version->refresh();

            if ($version->status === VersionStatus::Published) {
                throw new VersionRuleException(VersionRuleException::INVALID_STATUS, '该版本已经是发布状态。');
            }

            $this->assertAcceptable($version);

            // Demote first so the partial unique index never sees two published rows.
            $superseded = AppVersion::query()
                ->where('app_id', $version->app_id)
                ->where('status', VersionStatus::Published->value)
                ->get();
            foreach ($superseded as $old) {
                $old->status = VersionStatus::Superseded;
                $old->save();
            }

            $version->status = VersionStatus::Published;
            $version->published_at = now();
            $version->save();

            Log::info('App version published', [
                'app_id' => $version->app_id,
                'version_id' => $version->id,
                'version_code' => $version->version_code,
                'superseded_version_ids' => $superseded->pluck('id')->all(),
                'admin_id' => Auth::id(),
            ]);

            return $version;
        });
    }

    /**
     * @throws VersionRuleException
     */
    public function withdraw(AppVersion $version): AppVersion
    {
        $version->refresh();

        if ($version->status !== VersionStatus::Published) {
            throw new VersionRuleException(VersionRuleException::INVALID_STATUS, '只有已发布的版本可以撤回。');
        }

        $version->status = VersionStatus::Withdrawn;
        $version->save();

        Log::info('App version withdrawn', [
            'app_id' => $version->app_id,
            'version_id' => $version->id,
            'version_code' => $version->version_code,
            'admin_id' => Auth::id(),
        ]);

        return $version;
    }

    /**
     * Deletes a draft and its APK file. Versions that were ever published are kept as history.
     *
     * @throws VersionRuleException
     */
    public function deleteDraft(AppVersion $version): void
    {
        $version->refresh();

        if ($version->status !== VersionStatus::Draft || $version->published_at !== null) {
            throw new VersionRuleException(VersionRuleException::INVALID_STATUS, '只有草稿版本可以删除。');
        }

        $version->delete();
        Storage::disk(AppServiceProvider::FILES_DISK)->delete($version->file_path);

        Log::info('App version deleted', [
            'app_id' => $version->app_id,
            'version_id' => $version->id,
            'version_code' => $version->version_code,
            'sha256' => $version->sha256,
            'admin_id' => Auth::id(),
        ]);
    }
}
