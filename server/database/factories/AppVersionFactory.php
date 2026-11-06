<?php

namespace Database\Factories;

use App\Enums\VersionStatus;
use App\Models\AppVersion;
use App\Models\StoreApp;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends Factory<AppVersion>
 */
class AppVersionFactory extends Factory
{
    protected $model = AppVersion::class;

    public const SIGNER = 'aa11bb22cc33dd44ee55ff66aa11bb22cc33dd44ee55ff66aa11bb22cc33dd44';

    public function definition(): array
    {
        $sha256 = hash('sha256', fake()->unique()->uuid());

        return [
            'app_id' => StoreApp::factory(),
            'package_name' => fn (array $attributes) => StoreApp::findOrFail($attributes['app_id'])->package_name,
            'version_name' => '1.0.0',
            'version_code' => 1,
            'min_sdk' => 28,
            'abis' => ['armeabi-v7a', 'arm64-v8a'],
            'size_bytes' => 12_345_678,
            'sha256' => $sha256,
            'signer_sha256' => self::SIGNER,
            'file_path' => "apks/{$sha256}.apk",
            'compatibility' => 'unverified',
        ];
    }

    public function published(): static
    {
        return $this->afterMaking(function (AppVersion $version) {
            $version->status = VersionStatus::Published;
            $version->published_at = now();
        });
    }

    public function status(VersionStatus $status): static
    {
        return $this->afterMaking(function (AppVersion $version) use ($status) {
            $version->status = $status;
            $version->published_at = $status === VersionStatus::Draft ? null : now()->subDay();
        });
    }
}
