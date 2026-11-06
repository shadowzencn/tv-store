<?php

return [

    // Base URL of the static file host (OpenResty serves /files/... directly, not PHP).
    // An empty value in .env must also fall back to APP_URL, otherwise URLs lose their host.
    'files_base_url' => env('TVSTORE_FILES_BASE_URL') ?: env('APP_URL', 'http://localhost'),

    // Directory served by OpenResty as /files/ (APKs in apks/, icons in icons/).
    // Relative paths are resolved against the Laravel project root.
    'files_root' => (function (): string {
        $root = (string) env('TVSTORE_FILES_ROOT', '');
        if ($root === '') {
            return storage_path('app/public-files');
        }

        return preg_match('#^([A-Za-z]:)?[\\\\/]#', $root) === 1 ? $root : base_path($root);
    })(),

    // Upper limit for one uploaded APK, in megabytes. PHP upload_max_filesize / post_max_size
    // and the OpenResty client_max_body_size must allow at least this much.
    'max_apk_mb' => (int) env('TVSTORE_MAX_APK_MB', 500),

    // Package name of the store client itself, used by /api/v1/client/version.
    'client_package_name' => env('TVSTORE_CLIENT_PACKAGE_NAME', 'com.shadowzen.tvstore'),

    'feedback_rate_limit' => [
        'per_minute' => (int) env('TVSTORE_FEEDBACK_PER_MINUTE', 3),
        'per_day' => (int) env('TVSTORE_FEEDBACK_PER_DAY', 30),
    ],

];
