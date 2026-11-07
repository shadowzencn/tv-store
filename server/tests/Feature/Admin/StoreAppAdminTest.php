<?php

namespace Tests\Feature\Admin;

use App\Enums\VersionStatus;
use App\Filament\Resources\StoreApps\Pages\CreateStoreApp;
use App\Filament\Resources\StoreApps\Pages\EditStoreApp;
use App\Filament\Resources\StoreApps\Pages\ListStoreApps;
use App\Filament\Resources\StoreApps\StoreAppResource;
use App\Models\AppVersion;
use App\Models\Category;
use App\Models\StoreApp;
use App\Providers\AppServiceProvider;
use App\Support\Apk\ApkInspector;
use Filament\Actions\DeleteAction;
use Illuminate\Http\UploadedFile;
use Illuminate\Support\Facades\Storage;
use Livewire\Livewire;
use Tests\Support\FakeApkInspector;

class StoreAppAdminTest extends AdminTestCase
{
    // The test PHP build has no GD, so images are built from a fixed 1x1 PNG instead of UploadedFile::fake()->image().
    private static function png(string $name, int $paddingBytes = 0): UploadedFile
    {
        $png = base64_decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=');

        return UploadedFile::fake()->createWithContent($name, $png.str_repeat("\0", $paddingBytes));
    }

    public function test_creating_an_app_only_requires_an_apk(): void
    {
        Storage::fake('tmp-for-tests');
        $inspector = new FakeApkInspector;
        $this->app->instance(ApkInspector::class, $inspector);
        $inspector->info = FakeApkInspector::info([
            'packageName' => 'com.example.myPlayer',
            'label' => 'My Player',
            'sha256' => str_repeat('d', 64),
        ]);
        Category::factory()->create(['id' => 'tools', 'sort_order' => 5]);
        Category::factory()->create(['id' => 'video', 'sort_order' => 1]);

        Livewire::test(CreateStoreApp::class)
            ->fillForm([
                'apk' => UploadedFile::fake()->create('player.apk', 64, 'application/vnd.android.package-archive'),
            ])
            ->call('create')
            ->assertHasNoFormErrors()
            ->assertNotified('应用已从安装包创建')
            ->assertRedirect(StoreAppResource::getUrl('edit', ['record' => 'myplayer']));

        $app = StoreApp::query()->findOrFail('myplayer');
        $this->assertSame('com.example.myPlayer', $app->package_name);
        $this->assertSame('My Player', $app->name);
        $this->assertSame('video', $app->category_id);
        $this->assertTrue($app->is_listed);
        $this->assertFalse($app->is_featured);
        $this->assertNull($app->summary);
        $this->assertNull($app->icon_path);

        $version = AppVersion::query()->sole();
        $this->assertSame('myplayer', $version->app_id);
        $this->assertSame('com.example.myPlayer', $version->package_name);
        $this->assertSame(VersionStatus::Draft, $version->status);
        Storage::disk(AppServiceProvider::FILES_DISK)->assertExists("apks/".str_repeat('d', 64).'.apk');
        $this->assertSame([], Storage::disk('tmp-for-tests')->allFiles());
    }

    public function test_create_stores_the_icon_taken_from_the_apk(): void
    {
        $png = base64_decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=');
        $inspector = new FakeApkInspector;
        $this->app->instance(ApkInspector::class, $inspector);
        $inspector->info = FakeApkInspector::info([
            'packageName' => 'com.example.iconapp',
            'label' => 'Icon App',
            'sha256' => str_repeat('b', 64),
            'iconBytes' => $png,
            'iconExtension' => 'png',
        ]);
        Category::factory()->create();

        Livewire::test(CreateStoreApp::class)
            ->fillForm(['apk' => UploadedFile::fake()->create('icon.apk', 64)])
            ->call('create')
            ->assertHasNoFormErrors();

        $app = StoreApp::query()->findOrFail('iconapp');
        $this->assertSame('icons/iconapp.png', $app->icon_path);
        $this->assertSame($png, Storage::disk(AppServiceProvider::FILES_DISK)->get($app->icon_path));
    }

    public function test_create_uses_the_package_name_when_the_apk_has_no_label(): void
    {
        $inspector = new FakeApkInspector;
        $this->app->instance(ApkInspector::class, $inspector);
        $inspector->info = FakeApkInspector::info([
            'packageName' => 'tv.example.remote',
            'label' => null,
            'sha256' => str_repeat('e', 64),
        ]);
        Category::factory()->create();

        Livewire::test(CreateStoreApp::class)
            ->fillForm(['apk' => UploadedFile::fake()->create('remote.apk', 64)])
            ->call('create')
            ->assertHasNoFormErrors();

        $this->assertSame('tv.example.remote', StoreApp::query()->findOrFail('remote')->name);
    }

    public function test_create_rejects_a_package_name_that_already_exists(): void
    {
        Storage::fake('tmp-for-tests');
        StoreApp::factory()->create(['package_name' => 'com.example.demo']);
        Category::factory()->create();
        $inspector = new FakeApkInspector;
        $this->app->instance(ApkInspector::class, $inspector);
        $inspector->info = FakeApkInspector::info();

        Livewire::test(CreateStoreApp::class)
            ->fillForm(['apk' => UploadedFile::fake()->create('demo.apk', 64)])
            ->call('create')
            ->assertHasFormErrors(['apk'])
            ->assertSee('该包名的应用已存在');

        $this->assertDatabaseCount('apps', 1);
        $this->assertDatabaseCount('app_versions', 0);
        $this->assertSame([], Storage::disk(AppServiceProvider::FILES_DISK)->allFiles());
    }

    public function test_create_requires_a_category_and_an_apk_file(): void
    {
        $inspector = new FakeApkInspector;
        $this->app->instance(ApkInspector::class, $inspector);
        $inspector->info = FakeApkInspector::info(['packageName' => 'com.example.fresh']);

        Livewire::test(CreateStoreApp::class)
            ->fillForm(['apk' => UploadedFile::fake()->create('fresh.apk', 64)])
            ->call('create')
            ->assertHasFormErrors(['apk'])
            ->assertSee('请先创建一个分类');

        $this->assertSame([], $inspector->inspectedPaths);
        $this->assertDatabaseCount('apps', 0);

        Category::factory()->create();
        Livewire::test(CreateStoreApp::class)
            ->fillForm(['apk' => UploadedFile::fake()->create('notes.zip', 4)])
            ->call('create')
            ->assertHasFormErrors(['apk']);

        $this->assertSame([], $inspector->inspectedPaths);
        $this->assertDatabaseCount('apps', 0);
    }

    public function test_icon_must_be_a_small_image(): void
    {
        $app = StoreApp::factory()->create();

        Livewire::test(EditStoreApp::class, ['record' => $app->id])
            ->fillForm(['icon_path' => UploadedFile::fake()->create('icon.pdf', 10, 'application/pdf')])
            ->call('save')
            ->assertHasFormErrors(['icon_path']);

        Livewire::test(EditStoreApp::class, ['record' => $app->id])
            ->fillForm(['icon_path' => self::png('big.png', 2 * 1024 * 1024)])
            ->call('save')
            ->assertHasFormErrors(['icon_path']);

        $this->assertNull($app->fresh()->icon_path);
    }

    public function test_edit_rejects_an_invalid_package_name(): void
    {
        $app = StoreApp::factory()->create();

        Livewire::test(EditStoreApp::class, ['record' => $app->id])
            ->fillForm(['package_name' => 'not a package'])
            ->call('save')
            ->assertHasFormErrors(['package_name' => 'regex']);

        $this->assertSame($app->package_name, $app->fresh()->package_name);
    }

    public function test_edit_updates_fields_but_keeps_slug(): void
    {
        $app = StoreApp::factory()->create(['id' => 'demo', 'is_listed' => true]);
        $other = Category::factory()->create();

        Livewire::test(EditStoreApp::class, ['record' => $app->id])
            ->fillForm(['id' => 'renamed', 'name' => '新名字', 'category_id' => $other->id, 'is_listed' => false])
            ->call('save')
            ->assertHasNoFormErrors();

        $app->refresh();
        $this->assertSame('demo', $app->id);
        $this->assertSame('新名字', $app->name);
        $this->assertSame($other->id, $app->category_id);
        $this->assertFalse($app->is_listed);
        $this->assertDatabaseMissing('apps', ['id' => 'renamed']);
    }

    public function test_package_name_is_locked_once_versions_exist(): void
    {
        $version = AppVersion::factory()->create();
        $original = $version->app->package_name;

        Livewire::test(EditStoreApp::class, ['record' => $version->app_id])
            ->assertFormFieldIsDisabled('package_name')
            ->fillForm(['package_name' => 'com.changed.app'])
            ->call('save');

        $this->assertSame($original, $version->app->fresh()->package_name);
    }

    public function test_replacing_icon_deletes_the_old_file(): void
    {
        $disk = Storage::disk(AppServiceProvider::FILES_DISK);
        $disk->put('icons/old.png', 'old');
        $app = StoreApp::factory()->create(['icon_path' => 'icons/old.png']);

        Livewire::test(EditStoreApp::class, ['record' => $app->id])
            ->fillForm(['icon_path' => [self::png('new.png')]])
            ->call('save')
            ->assertHasNoFormErrors();

        $app->refresh();
        $this->assertNotSame('icons/old.png', $app->icon_path);
        $disk->assertExists($app->icon_path);
        $disk->assertMissing('icons/old.png');
    }

    public function test_list_shows_the_published_version(): void
    {
        $published = AppVersion::factory()->published()->create(['version_name' => '2.5.0']);

        Livewire::test(ListStoreApps::class)
            ->assertCanSeeTableRecords([$published->app])
            ->assertSee('2.5.0');
    }

    public function test_app_with_versions_cannot_be_deleted(): void
    {
        $version = AppVersion::factory()->create();
        $empty = StoreApp::factory()->create();

        Livewire::test(EditStoreApp::class, ['record' => $version->app_id])->assertActionHidden(DeleteAction::class);

        Livewire::test(EditStoreApp::class, ['record' => $empty->id])->callAction(DeleteAction::class);
        $this->assertDatabaseMissing('apps', ['id' => $empty->id]);
    }
}
