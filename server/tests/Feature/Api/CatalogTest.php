<?php

namespace Tests\Feature\Api;

use App\Enums\VersionStatus;
use App\Models\AppVersion;
use App\Models\Category;
use App\Models\StoreApp;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class CatalogTest extends TestCase
{
    use RefreshDatabase;

    protected function setUp(): void
    {
        parent::setUp();
        config(['tvstore.files_base_url' => 'https://store.example.com/']);
    }

    public function test_catalog_outputs_categories_apps_and_published_version_in_camel_case(): void
    {
        $category = Category::factory()->create(['id' => 'video', 'name' => '影视', 'sort_order' => 2]);
        Category::factory()->create(['id' => 'tools', 'name' => '工具', 'sort_order' => 1]);
        $app = StoreApp::factory()->for($category)->create([
            'id' => 'demo-player',
            'package_name' => 'com.example.player',
            'name' => '示例播放器',
            'is_featured' => true,
            'icon_path' => 'icons/demo.png',
        ]);
        $version = AppVersion::factory()->for($app, 'app')->published()->create([
            'version_name' => '2.1.0',
            'version_code' => 210,
            'compatibility' => 'verified',
            'known_issues' => '无',
        ]);

        $response = $this->getJson('/api/v1/catalog');

        $response->assertOk()
            ->assertHeader('ETag')
            ->assertJsonPath('categories.0.id', 'tools')
            ->assertJsonPath('categories.1', ['id' => 'video', 'name' => '影视', 'sortOrder' => 2])
            ->assertJsonCount(1, 'apps')
            ->assertJsonPath('apps.0.id', 'demo-player')
            ->assertJsonPath('apps.0.packageName', 'com.example.player')
            ->assertJsonPath('apps.0.categoryId', 'video')
            ->assertJsonPath('apps.0.featured', true)
            ->assertJsonPath('apps.0.iconUrl', 'https://store.example.com/files/icons/demo.png')
            ->assertJsonPath('apps.0.version', [
                'packageName' => 'com.example.player',
                'versionName' => '2.1.0',
                'versionCode' => 210,
                'minSdk' => 28,
                'abis' => ['armeabi-v7a', 'arm64-v8a'],
                'sizeBytes' => 12_345_678,
                'sha256' => $version->sha256,
                'signerSha256' => $version->signer_sha256,
                'downloadUrl' => "https://store.example.com/files/apks/{$version->sha256}.apk",
                'compatibility' => 'verified',
                'testDeviceNotes' => null,
                'knownIssues' => '无',
                'releaseNotes' => null,
                'publishedAt' => $version->published_at->toIso8601ZuluString(),
            ]);
    }

    public function test_only_listed_apps_with_a_published_version_appear(): void
    {
        $published = StoreApp::factory()->create(['id' => 'shown']);
        AppVersion::factory()->for($published, 'app')->status(VersionStatus::Superseded)->create(['version_code' => 1]);
        AppVersion::factory()->for($published, 'app')->published()->create(['version_code' => 2]);
        AppVersion::factory()->for($published, 'app')->create(['version_code' => 3]); // draft

        $draftOnly = StoreApp::factory()->create(['id' => 'draft-only']);
        AppVersion::factory()->for($draftOnly, 'app')->create();

        $withdrawn = StoreApp::factory()->create(['id' => 'withdrawn-only']);
        AppVersion::factory()->for($withdrawn, 'app')->status(VersionStatus::Withdrawn)->create();

        $unlisted = StoreApp::factory()->create(['id' => 'unlisted', 'is_listed' => false]);
        AppVersion::factory()->for($unlisted, 'app')->published()->create();

        $response = $this->getJson('/api/v1/catalog')->assertOk();

        $this->assertSame(['shown'], array_column($response->json('apps'), 'id'));
        $response->assertJsonPath('apps.0.version.versionCode', 2);
    }

    public function test_matching_if_none_match_returns_304_and_changes_invalidate_the_etag(): void
    {
        $app = StoreApp::factory()->create();
        $version = AppVersion::factory()->for($app, 'app')->published()->create();

        $etag = $this->getJson('/api/v1/catalog')->assertOk()->headers->get('ETag');
        $this->assertMatchesRegularExpression('/^"[0-9a-f]{64}"$/', $etag);

        $notModified = $this->getJson('/api/v1/catalog', ['If-None-Match' => $etag]);
        $notModified->assertStatus(304);
        $this->assertSame('', $notModified->getContent());

        $this->getJson('/api/v1/catalog', ['If-None-Match' => '"stale"'])->assertOk();

        $version->update(['known_issues' => '新发现的问题']);

        $changed = $this->getJson('/api/v1/catalog', ['If-None-Match' => $etag]);
        $changed->assertOk();
        $this->assertNotSame($etag, $changed->headers->get('ETag'));
    }

    public function test_empty_catalog_is_valid(): void
    {
        $this->getJson('/api/v1/catalog')
            ->assertOk()
            ->assertExactJson(['categories' => [], 'apps' => []]);
    }
}
