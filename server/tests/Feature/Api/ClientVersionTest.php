<?php

namespace Tests\Feature\Api;

use App\Enums\VersionStatus;
use App\Models\AppVersion;
use App\Models\StoreApp;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class ClientVersionTest extends TestCase
{
    use RefreshDatabase;

    public function test_returns_the_published_version_of_the_store_client(): void
    {
        config(['tvstore.files_base_url' => 'https://store.example.com']);
        $client = StoreApp::factory()->create([
            'id' => 'tv-store',
            'package_name' => 'com.shadowzen.tvstore',
            'is_listed' => false,
        ]);
        AppVersion::factory()->for($client, 'app')->status(VersionStatus::Superseded)->create(['version_code' => 1]);
        $current = AppVersion::factory()->for($client, 'app')->published()->create([
            'version_code' => 2,
            'version_name' => '0.2.0',
            'release_notes' => '修复若干问题',
        ]);

        $this->getJson('/api/v1/client/version')
            ->assertOk()
            ->assertJsonPath('packageName', 'com.shadowzen.tvstore')
            ->assertJsonPath('versionCode', 2)
            ->assertJsonPath('versionName', '0.2.0')
            ->assertJsonPath('releaseNotes', '修复若干问题')
            ->assertJsonPath('downloadUrl', "https://store.example.com/files/apks/{$current->sha256}.apk");
    }

    public function test_returns_404_error_when_no_client_version_is_published(): void
    {
        $client = StoreApp::factory()->create(['package_name' => 'com.shadowzen.tvstore']);
        AppVersion::factory()->for($client, 'app')->create(); // draft only

        $this->getJson('/api/v1/client/version')
            ->assertNotFound()
            ->assertJsonPath('error.code', 'not_found')
            ->assertJsonStructure(['error' => ['code', 'message']]);
    }
}
