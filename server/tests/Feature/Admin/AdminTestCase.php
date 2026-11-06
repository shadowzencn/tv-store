<?php

namespace Tests\Feature\Admin;

use App\Models\User;
use App\Providers\AppServiceProvider;
use Filament\Facades\Filament;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Storage;
use Tests\TestCase;

abstract class AdminTestCase extends TestCase
{
    use RefreshDatabase;

    protected User $admin;

    protected function setUp(): void
    {
        parent::setUp();

        Storage::fake(AppServiceProvider::FILES_DISK);
        $this->admin = User::factory()->create();
        $this->actingAs($this->admin);
        Filament::setCurrentPanel('admin');
    }
}
