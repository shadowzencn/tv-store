<?php

namespace Tests\Feature\Admin;

use App\Filament\Resources\StoreApps\Pages\CreateStoreApp;
use App\Filament\Resources\StoreApps\Pages\EditStoreApp;
use App\Filament\Resources\StoreApps\Pages\ListStoreApps;
use App\Models\AppVersion;
use App\Models\Category;
use App\Models\StoreApp;
use App\Providers\AppServiceProvider;
use Filament\Actions\DeleteAction;
use Illuminate\Http\UploadedFile;
use Illuminate\Support\Facades\Storage;
use Livewire\Livewire;

class StoreAppAdminTest extends AdminTestCase
{
    // The test PHP build has no GD, so images are built from a fixed 1x1 PNG instead of UploadedFile::fake()->image().
    private static function png(string $name, int $paddingBytes = 0): UploadedFile
    {
        $png = base64_decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=');

        return UploadedFile::fake()->createWithContent($name, $png.str_repeat("\0", $paddingBytes));
    }

    private function validData(array $overrides = []): array
    {
        return array_merge([
            'id' => 'demo-player',
            'package_name' => 'com.example.player',
            'name' => '示例播放器',
            'summary' => '一句话简介',
            'description' => '详细说明',
            'category_id' => Category::query()->firstOrCreate(['id' => 'video'], ['name' => '影视'])->id,
            'is_featured' => true,
            'is_listed' => true,
            'sort_order' => 3,
        ], $overrides);
    }

    public function test_app_can_be_created_with_icon(): void
    {
        Livewire::test(CreateStoreApp::class)
            ->fillForm($this->validData(['icon_path' => self::png('icon.png')]))
            ->call('create')
            ->assertHasNoFormErrors();

        $app = StoreApp::findOrFail('demo-player');
        $this->assertSame('com.example.player', $app->package_name);
        $this->assertTrue($app->is_featured);
        $this->assertTrue($app->is_listed);
        $this->assertSame(3, $app->sort_order);
        $this->assertStringStartsWith('icons/', $app->icon_path);
        Storage::disk(AppServiceProvider::FILES_DISK)->assertExists($app->icon_path);
    }

    public function test_icon_must_be_a_small_image(): void
    {
        Livewire::test(CreateStoreApp::class)
            ->fillForm($this->validData(['icon_path' => UploadedFile::fake()->create('icon.pdf', 10, 'application/pdf')]))
            ->call('create')
            ->assertHasFormErrors(['icon_path']);

        Livewire::test(CreateStoreApp::class)
            ->fillForm($this->validData(['icon_path' => self::png('big.png', 2 * 1024 * 1024)]))
            ->call('create')
            ->assertHasFormErrors(['icon_path']);

        $this->assertDatabaseCount('apps', 0);
    }

    public function test_invalid_slug_and_package_name_are_rejected(): void
    {
        Livewire::test(CreateStoreApp::class)
            ->fillForm($this->validData(['id' => 'Bad Slug', 'package_name' => 'not a package']))
            ->call('create')
            ->assertHasFormErrors(['id' => 'regex', 'package_name' => 'regex']);
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
