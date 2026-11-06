<?php

namespace Tests\Feature\Admin;

use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class AdminAccessTest extends TestCase
{
    use RefreshDatabase;

    public function test_guests_are_redirected_to_login(): void
    {
        foreach (['/admin/categories', '/admin/apps', '/admin/apps/create', '/admin/versions', '/admin/versions/create', '/admin/feedback'] as $path) {
            $this->get($path)->assertRedirect('/admin/login');
        }
    }

    public function test_registration_is_not_offered(): void
    {
        $this->get('/admin/register')->assertNotFound();
    }
}
