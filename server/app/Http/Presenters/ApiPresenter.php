<?php

namespace App\Http\Presenters;

use App\Models\AppVersion;
use App\Models\Category;
use App\Models\StoreApp;

/**
 * Converts models to the camelCase JSON shape of the client API (TDD section 5).
 */
class ApiPresenter
{
    public static function category(Category $category): array
    {
        return [
            'id' => $category->id,
            'name' => $category->name,
            'sortOrder' => $category->sort_order,
        ];
    }

    public static function app(StoreApp $app): array
    {
        return [
            'id' => $app->id,
            'packageName' => $app->package_name,
            'name' => $app->name,
            'summary' => $app->summary,
            'description' => $app->description,
            'categoryId' => $app->category_id,
            'featured' => $app->is_featured,
            'sortOrder' => $app->sort_order,
            'iconUrl' => $app->icon_path === null
                ? null
                : rtrim((string) config('tvstore.files_base_url'), '/').'/files/'.ltrim($app->icon_path, '/'),
            'version' => self::version($app->publishedVersion),
        ];
    }

    public static function version(AppVersion $version): array
    {
        return [
            'packageName' => $version->package_name,
            'versionName' => $version->version_name,
            'versionCode' => $version->version_code,
            'minSdk' => $version->min_sdk,
            'abis' => array_values($version->abis ?? []),
            'sizeBytes' => $version->size_bytes,
            'sha256' => $version->sha256,
            'signerSha256' => $version->signer_sha256,
            'downloadUrl' => $version->downloadUrl(),
            'compatibility' => $version->compatibility->value,
            'testDeviceNotes' => $version->test_device_notes,
            'knownIssues' => $version->known_issues,
            'releaseNotes' => $version->release_notes,
            'publishedAt' => $version->published_at?->toIso8601ZuluString(),
        ];
    }
}
