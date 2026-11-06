<?php

namespace Tests\Feature\Admin;

use App\Enums\Compatibility;
use App\Enums\VersionStatus;
use App\Filament\Resources\AppVersions\Pages\CreateAppVersion;
use App\Filament\Resources\AppVersions\Pages\EditAppVersion;
use App\Filament\Resources\AppVersions\Pages\ListAppVersions;
use App\Models\AppVersion;
use App\Models\StoreApp;
use App\Providers\AppServiceProvider;
use App\Services\AppVersionPublisher;
use App\Services\VersionRuleException;
use App\Support\Apk\ApkInspector;
use Filament\Actions\Testing\TestAction;
use Illuminate\Filesystem\FilesystemAdapter;
use Illuminate\Http\UploadedFile;
use Illuminate\Support\Facades\Log;
use Illuminate\Support\Facades\Storage;
use Livewire\Features\SupportTesting\Testable;
use Livewire\Livewire;
use Tests\Support\FakeApkInspector;

class AppVersionAdminTest extends AdminTestCase
{
    private FakeApkInspector $inspector;

    private StoreApp $storeApp;

    private FilesystemAdapter $files;

    protected function setUp(): void
    {
        parent::setUp();

        Storage::fake('tmp-for-tests');
        $this->files = Storage::disk(AppServiceProvider::FILES_DISK);
        $this->inspector = new FakeApkInspector;
        $this->app->instance(ApkInspector::class, $this->inspector);
        $this->storeApp = StoreApp::factory()->create(['id' => 'demo', 'package_name' => 'com.example.demo']);
    }

    private function upload(array $info = [], array $form = [], string $fileName = 'demo.apk'): Testable
    {
        $this->inspector->info = FakeApkInspector::info($info);

        return Livewire::test(CreateAppVersion::class)
            ->fillForm([
                'app_id' => $this->storeApp->id,
                'apk' => UploadedFile::fake()->create($fileName, 64, 'application/vnd.android.package-archive'),
                ...$form,
            ])
            ->call('create');
    }

    private function assertNoTemporaryFiles(): void
    {
        $this->assertSame([], Storage::disk('tmp-for-tests')->allFiles());
    }

    public function test_upload_stores_identified_metadata_and_moves_the_file(): void
    {
        $this->upload(
            ['versionCode' => 7, 'versionName' => '1.7', 'abis' => ['armeabi-v7a', 'arm64-v8a'], 'sha256' => strtoupper(str_repeat('ab', 32))],
            ['compatibility' => Compatibility::SpecMatch->value, 'release_notes' => '修复问题', 'known_issues' => '无'],
        )->assertHasNoFormErrors()->assertRedirect();

        $version = AppVersion::query()->sole();
        $sha = str_repeat('ab', 32);
        $this->assertSame('demo', $version->app_id);
        $this->assertSame('com.example.demo', $version->package_name);
        $this->assertSame('1.7', $version->version_name);
        $this->assertSame(7, $version->version_code);
        $this->assertSame(21, $version->min_sdk);
        $this->assertSame(['armeabi-v7a', 'arm64-v8a'], $version->abis);
        $this->assertSame(1024, $version->size_bytes);
        $this->assertSame($sha, $version->sha256);
        $this->assertSame(str_repeat('1', 64), $version->signer_sha256);
        $this->assertSame("apks/{$sha}.apk", $version->file_path);
        $this->assertSame(Compatibility::SpecMatch, $version->compatibility);
        $this->assertSame('修复问题', $version->release_notes);
        $this->assertSame(VersionStatus::Draft, $version->status);
        $this->files->assertExists("apks/{$sha}.apk");
        $this->assertCount(1, $this->inspector->inspectedPaths);
        $this->assertNoTemporaryFiles();
    }

    public function test_package_name_mismatch_is_rejected_without_leaving_files(): void
    {
        $this->upload(['packageName' => 'com.other.app'])->assertHasFormErrors(['apk']);

        $this->assertDatabaseCount('app_versions', 0);
        $this->assertSame([], $this->files->allFiles());
        $this->assertNoTemporaryFiles();
    }

    public function test_signer_mismatch_with_published_version_is_rejected(): void
    {
        AppVersion::factory()->for($this->storeApp, 'app')->published()->create(['version_code' => 1, 'signer_sha256' => str_repeat('1', 64)]);

        $this->upload(['versionCode' => 2, 'signerSha256' => str_repeat('2', 64)])->assertHasFormErrors(['apk']);

        $this->assertDatabaseCount('app_versions', 1);
        $this->assertSame([], $this->files->allFiles());
        $this->assertNoTemporaryFiles();
    }

    public function test_lower_version_code_than_published_is_rejected(): void
    {
        AppVersion::factory()->for($this->storeApp, 'app')->published()->create(['version_code' => 5, 'signer_sha256' => str_repeat('1', 64)]);

        $this->upload(['versionCode' => 4])->assertHasFormErrors(['apk']);

        $this->assertDatabaseCount('app_versions', 1);
        $this->assertSame([], $this->files->allFiles());
        $this->assertNoTemporaryFiles();
    }

    public function test_repeated_version_code_is_rejected(): void
    {
        AppVersion::factory()->for($this->storeApp, 'app')->create(['version_code' => 3]);

        $this->upload(['versionCode' => 3])->assertHasFormErrors(['apk']);

        $this->assertDatabaseCount('app_versions', 1);
        $this->assertNoTemporaryFiles();
    }

    public function test_duplicate_sha256_is_rejected_even_for_another_app(): void
    {
        $other = AppVersion::factory()->create(['sha256' => str_repeat('c', 64)]);
        $this->files->put($other->file_path, 'existing');

        $this->upload(['versionCode' => 9, 'sha256' => str_repeat('c', 64)])->assertHasFormErrors(['apk']);

        $this->assertDatabaseCount('app_versions', 1);
        $this->assertSame('existing', $this->files->get($other->file_path));
        $this->assertNoTemporaryFiles();
    }

    public function test_inspection_failure_shows_the_message_and_removes_the_upload(): void
    {
        $this->inspector->failure = '文件不是有效的 APK。';

        Livewire::test(CreateAppVersion::class)
            ->fillForm(['app_id' => $this->storeApp->id, 'apk' => UploadedFile::fake()->create('broken.apk', 4)])
            ->call('create')
            ->assertHasFormErrors(['apk'])
            ->assertSee('文件不是有效的 APK。');

        $this->assertDatabaseCount('app_versions', 0);
        $this->assertSame([], $this->files->allFiles());
        $this->assertNoTemporaryFiles();
    }

    public function test_only_apk_files_are_accepted(): void
    {
        $this->upload([], [], 'demo.zip')->assertHasFormErrors(['apk']);

        $this->assertSame([], $this->inspector->inspectedPaths);
        $this->assertDatabaseCount('app_versions', 0);
    }

    public function test_files_larger_than_the_configured_limit_are_rejected(): void
    {
        config(['tvstore.max_apk_mb' => 1]);
        $this->inspector->info = FakeApkInspector::info();

        Livewire::test(CreateAppVersion::class)
            ->fillForm(['app_id' => $this->storeApp->id, 'apk' => UploadedFile::fake()->create('big.apk', 2048)])
            ->call('create')
            ->assertHasFormErrors(['apk']);

        $this->assertDatabaseCount('app_versions', 0);
    }

    public function test_edit_only_changes_descriptive_fields(): void
    {
        $version = AppVersion::factory()->for($this->storeApp, 'app')->create(['version_code' => 1]);
        $original = $version->only(['package_name', 'version_code', 'sha256', 'signer_sha256', 'file_path', 'app_id']);

        Livewire::test(EditAppVersion::class, ['record' => $version->id])
            ->fillForm([
                'compatibility' => Compatibility::Verified->value,
                'test_device_notes' => 'TCL Q10L 正常',
                'known_issues' => '遥控器长按无效',
                'release_notes' => '首个版本',
            ])
            ->set('data.version_code', 99)
            ->set('data.sha256', str_repeat('f', 64))
            ->call('save')
            ->assertHasNoFormErrors();

        $version->refresh();
        $this->assertSame(Compatibility::Verified, $version->compatibility);
        $this->assertSame('TCL Q10L 正常', $version->test_device_notes);
        $this->assertSame('遥控器长按无效', $version->known_issues);
        $this->assertSame('首个版本', $version->release_notes);
        $this->assertSame($original, $version->only(array_keys($original)));
    }

    public function test_publish_and_withdraw_go_through_the_publisher_and_are_logged(): void
    {
        Log::spy();
        $old = AppVersion::factory()->for($this->storeApp, 'app')->published()->create(['version_code' => 1]);
        $new = AppVersion::factory()->for($this->storeApp, 'app')->create(['version_code' => 2]);

        Livewire::test(EditAppVersion::class, ['record' => $new->id])
            ->assertActionHidden('withdraw')
            ->callAction('publish')
            ->assertNotified('版本已发布');

        $this->assertSame(VersionStatus::Published, $new->fresh()->status);
        $this->assertSame(VersionStatus::Superseded, $old->fresh()->status);

        Livewire::test(ListAppVersions::class)
            ->callAction(TestAction::make('withdraw')->table($new))
            ->assertNotified('版本已撤回');

        $this->assertSame(VersionStatus::Withdrawn, $new->fresh()->status);

        Log::shouldHaveReceived('info')->with('App version published', \Mockery::on(fn ($c) => $c['version_id'] === $new->id && $c['admin_id'] === $this->admin->id))->once();
        Log::shouldHaveReceived('info')->with('App version withdrawn', \Mockery::on(fn ($c) => $c['version_id'] === $new->id && $c['admin_id'] === $this->admin->id))->once();
    }

    public function test_publish_rule_violation_is_reported_and_nothing_changes(): void
    {
        AppVersion::factory()->for($this->storeApp, 'app')->published()->create(['version_code' => 5]);
        $lower = AppVersion::factory()->for($this->storeApp, 'app')->create(['version_code' => 4]);

        Livewire::test(EditAppVersion::class, ['record' => $lower->id])
            ->callAction('publish')
            ->assertNotified('操作未执行');

        $this->assertSame(VersionStatus::Draft, $lower->fresh()->status);
    }

    public function test_draft_can_be_deleted_with_its_file(): void
    {
        Log::spy();
        $draft = AppVersion::factory()->for($this->storeApp, 'app')->create();
        $this->files->put($draft->file_path, 'apk');

        Livewire::test(EditAppVersion::class, ['record' => $draft->id])
            ->callAction('deleteDraft')
            ->assertNotified('草稿版本已删除')
            ->assertRedirect();

        $this->assertModelMissing($draft);
        $this->files->assertMissing($draft->file_path);
        Log::shouldHaveReceived('info')->with('App version deleted', \Mockery::on(fn ($c) => $c['version_id'] === $draft->id && $c['admin_id'] === $this->admin->id))->once();
    }

    public function test_published_or_withdrawn_versions_cannot_be_deleted(): void
    {
        $published = AppVersion::factory()->for($this->storeApp, 'app')->published()->create(['version_code' => 2]);
        $withdrawn = AppVersion::factory()->for($this->storeApp, 'app')->status(VersionStatus::Withdrawn)->create(['version_code' => 1]);
        $this->files->put($published->file_path, 'apk');

        Livewire::test(EditAppVersion::class, ['record' => $published->id])->assertActionHidden('deleteDraft');
        Livewire::test(ListAppVersions::class)->assertActionHidden(TestAction::make('deleteDraft')->table($withdrawn));

        // Even if the hidden action is forced, the service refuses.
        $this->expectException(VersionRuleException::class);
        try {
            app(AppVersionPublisher::class)->deleteDraft($published);
        } finally {
            $this->assertModelExists($published);
            $this->files->assertExists($published->file_path);
        }
    }
}
