<?php

namespace App\Providers;

use App\Support\Apk\ApkInspector;
use App\Support\Apk\PhpApkInspector;
use Illuminate\Cache\RateLimiting\Limit;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\RateLimiter;
use Illuminate\Support\ServiceProvider;

class AppServiceProvider extends ServiceProvider
{
    // Disk for the static file root that OpenResty serves under /files/.
    public const FILES_DISK = 'tvstore_files';

    /**
     * Register any application services.
     */
    public function register(): void
    {
        $this->app->bind(ApkInspector::class, PhpApkInspector::class);
    }

    /**
     * Bootstrap any application services.
     */
    public function boot(): void
    {
        config([
            'filesystems.disks.'.self::FILES_DISK => [
                'driver' => 'local',
                'root' => config('tvstore.files_root'),
                'url' => rtrim((string) config('tvstore.files_base_url'), '/').'/files',
                'visibility' => 'public',
                'throw' => true,
                'report' => false,
            ],
            // Livewire stores every upload in a temporary directory first; its default 12 MB limit
            // and 5 minute upload window are too small for APKs.
            'livewire.temporary_file_upload.rules' => ['required', 'file', 'max:'.(config('tvstore.max_apk_mb') * 1024)],
            'livewire.temporary_file_upload.max_upload_time' => 30,
        ]);

        RateLimiter::for('feedback', function (Request $request) {
            $limits = config('tvstore.feedback_rate_limit');

            return [
                Limit::perMinute($limits['per_minute'])->by('feedback-minute:'.$request->ip()),
                Limit::perDay($limits['per_day'])->by('feedback-day:'.$request->ip()),
            ];
        });
    }
}
