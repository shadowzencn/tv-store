<?php

namespace Tests\Feature\Admin;

use App\Filament\Resources\Categories\Pages\ManageCategories;
use App\Models\Category;
use App\Models\StoreApp;
use Filament\Actions\CreateAction;
use Filament\Actions\Testing\TestAction;
use Livewire\Livewire;

class CategoryAdminTest extends AdminTestCase
{
    public function test_category_can_be_created(): void
    {
        Livewire::test(ManageCategories::class)
            ->callAction(CreateAction::class, ['id' => 'video', 'name' => '影视', 'sort_order' => 2])
            ->assertHasNoFormErrors();

        $this->assertDatabaseHas('categories', ['id' => 'video', 'name' => '影视', 'sort_order' => 2]);
    }

    public function test_invalid_or_duplicate_slug_is_rejected(): void
    {
        Category::factory()->create(['id' => 'video']);

        Livewire::test(ManageCategories::class)
            ->callAction(CreateAction::class, ['id' => 'Video Apps', 'name' => '影视', 'sort_order' => 0])
            ->assertHasFormErrors(['id' => 'regex']);

        Livewire::test(ManageCategories::class)
            ->callAction(CreateAction::class, ['id' => 'video', 'name' => '影视', 'sort_order' => 0])
            ->assertHasFormErrors(['id' => 'unique']);
    }

    public function test_edit_changes_name_and_order_but_not_slug(): void
    {
        $category = Category::factory()->create(['id' => 'tools', 'name' => '工具', 'sort_order' => 0]);

        Livewire::test(ManageCategories::class)
            ->callAction(TestAction::make('edit')->table($category), ['id' => 'changed', 'name' => '实用工具', 'sort_order' => 5])
            ->assertHasNoFormErrors();

        $this->assertDatabaseHas('categories', ['id' => 'tools', 'name' => '实用工具', 'sort_order' => 5]);
        $this->assertDatabaseMissing('categories', ['id' => 'changed']);
    }

    public function test_empty_category_can_be_deleted(): void
    {
        $category = Category::factory()->create();

        Livewire::test(ManageCategories::class)->callAction(TestAction::make('delete')->table($category));

        $this->assertDatabaseMissing('categories', ['id' => $category->id]);
    }

    public function test_category_with_apps_cannot_be_deleted(): void
    {
        $app = StoreApp::factory()->create();

        Livewire::test(ManageCategories::class)
            ->callAction(TestAction::make('delete')->table($app->category))
            ->assertNotified('不能删除该分类');

        $this->assertDatabaseHas('categories', ['id' => $app->category_id]);
    }
}
