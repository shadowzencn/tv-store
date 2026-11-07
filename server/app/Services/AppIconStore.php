<?php

namespace App\Services;

use App\Models\StoreApp;
use App\Providers\AppServiceProvider;
use App\Support\Apk\ApkIcon;
use Illuminate\Support\Facades\Storage;

/** Writes an app icon to the shared files disk and records it on the app. */
class AppIconStore
{
    public function store(StoreApp $app, string $bytes, string $extension): ?string
    {
        if (! in_array($extension, ['png', 'webp', 'jpg'], true)) {
            return null;
        }
        if (strlen($bytes) > ApkIcon::MAX_BYTES) {
            return null;
        }

        $path = "icons/{$app->id}.{$extension}";
        Storage::disk(AppServiceProvider::FILES_DISK)->put($path, $bytes);
        $app->icon_path = $path;
        $app->save();

        return $path;
    }
}
