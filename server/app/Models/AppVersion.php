<?php

namespace App\Models;

use App\Enums\Compatibility;
use App\Enums\VersionStatus;
use Database\Factories\AppVersionFactory;
use Illuminate\Database\Eloquent\Casts\Attribute;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/**
 * Status changes (publish / withdraw) must go through App\Services\AppVersionPublisher.
 */
class AppVersion extends Model
{
    /** @use HasFactory<AppVersionFactory> */
    use HasFactory;

    protected $fillable = [
        'app_id', 'package_name', 'version_name', 'version_code', 'min_sdk', 'abis',
        'size_bytes', 'sha256', 'signer_sha256', 'file_path', 'compatibility',
        'test_device_notes', 'known_issues', 'release_notes',
    ];

    protected $attributes = [
        'status' => 'draft',
        'compatibility' => 'unverified',
    ];

    protected function casts(): array
    {
        return [
            'version_code' => 'integer',
            'min_sdk' => 'integer',
            'abis' => 'array',
            'size_bytes' => 'integer',
            'compatibility' => Compatibility::class,
            'status' => VersionStatus::class,
            'published_at' => 'datetime',
        ];
    }

    public function app(): BelongsTo
    {
        return $this->belongsTo(StoreApp::class, 'app_id');
    }

    protected function sha256(): Attribute
    {
        return Attribute::make(set: fn (string $value) => strtolower($value));
    }

    protected function signerSha256(): Attribute
    {
        return Attribute::make(set: fn (string $value) => strtolower($value));
    }

    public function downloadUrl(): string
    {
        return rtrim((string) config('tvstore.files_base_url'), '/').'/files/apks/'.$this->sha256.'.apk';
    }
}
