<?php

namespace Tests\Feature\Console;

use App\Enums\VersionStatus;
use App\Models\AppVersion;
use App\Models\StoreApp;
use App\Providers\AppServiceProvider;
use App\Support\Apk\ApkInspector;
use Illuminate\Filesystem\FilesystemAdapter;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Storage;
use Tests\Support\FakeApkInspector;
use Tests\TestCase;

class ExtractIconsCommandTest extends TestCase
{
    use RefreshDatabase;

    private const PNG = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=';

    private FilesystemAdapter $files;

    private FakeApkInspector $inspector;

    protected function setUp(): void
    {
        parent::setUp();

        Storage::fake(AppServiceProvider::FILES_DISK);
        $this->files = Storage::disk(AppServiceProvider::FILES_DISK);
        $this->inspector = new FakeApkInspector;
        $this->app->instance(ApkInspector::class, $this->inspector);
    }

    /** 放一个 APK 文件到 files 磁盘（内容无关，识别走 FakeApkInspector），返回 file_path。 */
    private function putApk(string $sha256): string
    {
        $path = "apks/{$sha256}.apk";
        $this->files->put($path, 'apk-bytes');

        return $path;
    }

    private function withIcon(): void
    {
        $this->inspector->info = FakeApkInspector::info([
            'iconBytes' => base64_decode(self::PNG),
            'iconExtension' => 'png',
        ]);
    }

    private function makeApp(string $id, array $attributes = []): StoreApp
    {
        return StoreApp::factory()->create(['id' => $id, 'package_name' => "com.example.{$id}", ...$attributes]);
    }

    private function makeVersion(StoreApp $app, int $code, ?VersionStatus $status = null, ?string $filePath = null): AppVersion
    {
        $factory = AppVersion::factory()->for($app, 'app')->state(['version_code' => $code]);
        if ($status !== null) {
            $factory = $factory->status($status);
        }
        if ($filePath !== null) {
            $factory = $factory->state(['file_path' => $filePath]);
        }

        return $factory->create();
    }

    public function test_apps_with_icons_are_not_touched(): void
    {
        $app = $this->makeApp('hasicon', ['icon_path' => 'icons/hasicon.png']);

        $this->artisan('tvstore:extract-icons')
            ->expectsOutputToContain('处理 0 个')
            ->assertSuccessful();

        $this->assertSame('icons/hasicon.png', $app->fresh()->icon_path);
        $this->assertSame([], $this->files->allFiles());
        $this->assertSame([], $this->inspector->inspectedPaths);
    }

    public function test_extracts_icon_from_the_stored_apk(): void
    {
        $this->withIcon();
        $app = $this->makeApp('noicon');
        $this->makeVersion($app, 1, VersionStatus::Published, $this->putApk(str_repeat('a', 64)));

        $this->artisan('tvstore:extract-icons')
            ->expectsOutputToContain('补上 1 个')
            ->assertSuccessful();

        $this->assertSame('icons/noicon.png', $app->fresh()->icon_path);
        $this->files->assertExists('icons/noicon.png');
        $this->assertSame(base64_decode(self::PNG), $this->files->get('icons/noicon.png'));
        $this->assertSame(
            [$this->files->path('apks/'.str_repeat('a', 64).'.apk')],
            $this->inspector->inspectedPaths,
        );
    }

    public function test_prefers_the_published_version_over_a_higher_draft(): void
    {
        $this->withIcon();
        $app = $this->makeApp('published');
        $this->makeVersion($app, 9, VersionStatus::Draft, 'apks/missing.apk');
        $this->makeVersion($app, 3, VersionStatus::Published, $this->putApk(str_repeat('b', 64)));

        $this->artisan('tvstore:extract-icons')
            ->expectsOutputToContain('补上 1 个')
            ->assertSuccessful();

        $this->assertSame('icons/published.png', $app->fresh()->icon_path);
        $this->assertSame(
            [$this->files->path('apks/'.str_repeat('b', 64).'.apk')],
            $this->inspector->inspectedPaths,
        );
    }

    public function test_missing_apk_file_is_skipped_without_failing(): void
    {
        $app = $this->makeApp('gone');
        $this->makeVersion($app, 1, VersionStatus::Published, 'apks/not-there.apk');

        $this->artisan('tvstore:extract-icons')
            ->expectsOutputToContain('补上 0 个')
            ->expectsOutputToContain('APK 文件缺失 1 个')
            ->assertSuccessful();

        $this->assertNull($app->fresh()->icon_path);
        $this->assertSame([], $this->inspector->inspectedPaths);
    }

    public function test_apk_without_a_bitmap_icon_is_skipped(): void
    {
        $this->inspector->info = FakeApkInspector::info();
        $app = $this->makeApp('noimg');
        $this->makeVersion($app, 1, VersionStatus::Published, $this->putApk(str_repeat('c', 64)));

        $this->artisan('tvstore:extract-icons')
            ->expectsOutputToContain('未提取到图标 1 个')
            ->assertSuccessful();

        $this->assertNull($app->fresh()->icon_path);
    }

    public function test_uninspectable_apk_is_skipped_without_failing(): void
    {
        $this->inspector->failure = '文件不是有效的 APK（ZIP 格式）。';
        $app = $this->makeApp('broken');
        $this->makeVersion($app, 1, VersionStatus::Published, $this->putApk(str_repeat('e', 64)));

        $this->artisan('tvstore:extract-icons')
            ->expectsOutputToContain('识别失败 1 个')
            ->assertSuccessful();

        $this->assertNull($app->fresh()->icon_path);
    }

    public function test_second_run_is_a_no_op(): void
    {
        $this->withIcon();
        $app = $this->makeApp('twice');
        $this->makeVersion($app, 1, VersionStatus::Published, $this->putApk(str_repeat('d', 64)));

        $this->artisan('tvstore:extract-icons')->assertSuccessful();
        $this->assertSame('icons/twice.png', $app->fresh()->icon_path);

        $this->artisan('tvstore:extract-icons')
            ->expectsOutputToContain('处理 0 个')
            ->assertSuccessful();

        $this->assertSame('icons/twice.png', $app->fresh()->icon_path);
        $this->files->assertExists('icons/twice.png');
    }
}
